#!/bin/bash

echo "$(date)"

CONTAINER_NAME="mavros_hitl"
# If you prefer the ID instead:
# CONTAINER_NAME="076e3268e2bf"

LOG_DIR="log"
mkdir -p "$LOG_DIR"
rm -f .stop___MAS

ros_sampler=""
mavros_sampler=""
jas_sampler=""
cleanup_done=0

join_by_comma() { local IFS=,; echo "$*"; }

# -------------------------------------------------
# Basic checks
# -------------------------------------------------
if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
    echo "Container '$CONTAINER_NAME' is not running."
    exit 1
fi

# -------------------------------------------------
# Run ros2 commands inside the container with ROS env sourced
# -------------------------------------------------
docker_ros_service_list() {
    docker exec "$CONTAINER_NAME" bash -lc '
        source /opt/ros/*/setup.bash 2>/dev/null
        [ -f /root/ws/install/setup.bash ] && source /root/ws/install/setup.bash
        [ -f /ws/install/setup.bash ] && source /ws/install/setup.bash
        [ -f ~/ws/install/setup.bash ] && source ~/ws/install/setup.bash
        ros2 service list 2>/dev/null
    '
}

# -------------------------------------------------
# Wait for MAVROS services inside the container
# Keep this a bit loose so it works across setups
# -------------------------------------------------
wait_for_mavros_ready() {
    local timeout=60
    local elapsed=0

    while (( elapsed < timeout )); do
        local services
        services="$(docker_ros_service_list)"

        if echo "$services" | grep -qx '/mavros/cmd/arming' &&
           echo "$services" | grep -qx '/mavros/set_mode'; then
            return 0
        fi

        sleep 1
        ((elapsed++))
    done

    echo "Timed out waiting for MAVROS services inside container."
    echo "Services currently visible in container:"
    docker_ros_service_list
    return 1
}

echo "Waiting for MAVROS services inside '$CONTAINER_NAME'..."
wait_for_mavros_ready || exit 1

# -------------------------------------------------
# Start application on the Pi host (Jason/JaCaMo side)
# -------------------------------------------------
./gradlew -q --console=plain &
APP_PID=$!
sleep 5

# -------------------------------------------------
# Generate incremental log names starting at 0
# -------------------------------------------------
i=0
while [[ -f "$LOG_DIR/ros_${i}.log" || -f "$LOG_DIR/mavros_${i}.log" || -f "$LOG_DIR/jason_${i}.log" ]]; do
    ((i++))
done

LOG_ROS="$LOG_DIR/ros_${i}.log"
LOG_MAVROS="$LOG_DIR/mavros_${i}.log"
LOG_JAS="$LOG_DIR/jason_${i}.log"

echo "ROS log:    $LOG_ROS"
echo "MAVROS log: $LOG_MAVROS"
echo "Jason log:  $LOG_JAS"

# -------------------------------------------------
# Wait for Jason/JaCaMo PIDs on the Pi host
# -------------------------------------------------
mapfile -t JASON_PID_ARRAY < <(
    for _ in {1..20}; do
        mapfile -t found < <(jps -l | awk '/JaCaMoLauncher|jacamo|jason/ {print $1}')
        if [[ ${#found[@]} -gt 0 ]]; then
            printf '%s\n' "${found[@]}"
            break
        fi
        sleep 1
    done | awk '!seen[$0]++'
)


# -------------------------------------------------
# Find MAVROS PIDs inside the container
# -------------------------------------------------
mapfile -t MAVROS_PID_ARRAY < <(
    docker exec "$CONTAINER_NAME" ps -eo pid=,args= 2>/dev/null | awk '
        ($0 ~ /mavros/) &&
        ($0 !~ /awk/) &&
        ($0 !~ /grep/) { print $1 }'
)

# -------------------------------------------------
# Find ROS PIDs inside the container
# Includes rosbridge explicitly
# Excludes MAVROS so the groups stay separate
# -------------------------------------------------
mapfile -t ROS_PID_ARRAY < <(
    docker exec "$CONTAINER_NAME" ps -eo pid=,args= 2>/dev/null | awk '
        ($0 ~ /roscore|rosmaster|rosout|roslaunch|ros2|\/ros\/|launch\.py|component_container|component_container_mt|rosbridge/) &&
        ($0 !~ /mavros/) &&
        ($0 !~ /awk/) &&
        ($0 !~ /grep/) { print $1 }'
)

PIDS_JASON=$(join_by_comma "${JASON_PID_ARRAY[@]}")
PIDS_MAVROS=$(join_by_comma "${MAVROS_PID_ARRAY[@]}")
PIDS_ROS=$(join_by_comma "${ROS_PID_ARRAY[@]}")

echo "MAVROS PIDs (container): ${PIDS_MAVROS:-none}"
echo "ROS PIDs (container):    ${PIDS_ROS:-none}"
echo "Jason PIDs (host):       ${PIDS_JASON:-none}"

cleanup() {
    [[ "$cleanup_done" -eq 1 ]] && return
    cleanup_done=1

    echo "Stopping benchmark..."

    [[ -n "${ros_sampler:-}" ]] && kill "$ros_sampler" 2>/dev/null
    [[ -n "${mavros_sampler:-}" ]] && kill "$mavros_sampler" 2>/dev/null
    [[ -n "${jas_sampler:-}" ]] && kill "$jas_sampler" 2>/dev/null

    touch .stop___MAS
}

on_signal() {
    local sig="$1"
    echo "Received $sig, cleaning up..."
    exit 130
}

trap cleanup EXIT
trap 'on_signal INT' INT
trap 'on_signal TERM' TERM
trap 'on_signal TSTP' TSTP

# -------------------------------------------------
# Sampler for host PIDs
# -------------------------------------------------
start_host_sampler() {
    local pid_list="$1"
    local logfile="$2"

    (
        count=1
        while true; do
            read cpu mem < <(
                ps -p "$pid_list" -o %cpu=,rss= 2>/dev/null | awk '
                    BEGIN{sCPU=0;sMEM=0}
                    NF>=2{sCPU+=$1;sMEM+=$2}
                    END{printf "%.2f %.2f\n", sCPU, sMEM/1024}'
            )

            cpu="${cpu:-0.00}"
            mem="${mem:-0.00}"

            printf "Sample %d - CPU: %6.2f - MEM: %6.2f\n" \
                "$count" "$cpu" "$mem" >> "$logfile"

            ((count++))
            sleep 1
        done
    ) >/dev/null 2>&1 &

    echo $!
}

# -------------------------------------------------
# Sampler for container PIDs
# -------------------------------------------------
start_container_sampler() {
    local pid_list="$1"
    local logfile="$2"

    (
        count=1
        while true; do
            read cpu mem < <(
                docker exec "$CONTAINER_NAME" ps -p "$pid_list" -o %cpu=,rss= 2>/dev/null | awk '
                    BEGIN{sCPU=0;sMEM=0}
                    NF>=2{sCPU+=$1;sMEM+=$2}
                    END{printf "%.2f %.2f\n", sCPU, sMEM/1024}'
            )

            cpu="${cpu:-0.00}"
            mem="${mem:-0.00}"

            printf "Sample %d - CPU: %6.2f - MEM: %6.2f\n" \
                "$count" "$cpu" "$mem" >> "$logfile"

            ((count++))
            sleep 1
        done
    ) >/dev/null 2>&1 &

    echo $!
}

# -------------------------------------------------
# Start samplers
# -------------------------------------------------
if [[ -n "$PIDS_ROS" ]]; then
    ros_sampler=$(start_container_sampler "$PIDS_ROS" "$LOG_ROS")
fi

if [[ -n "$PIDS_MAVROS" ]]; then
    mavros_sampler=$(start_container_sampler "$PIDS_MAVROS" "$LOG_MAVROS")
fi

if [[ -n "$PIDS_JASON" ]]; then
    jas_sampler=$(start_host_sampler "$PIDS_JASON" "$LOG_JAS")
fi

# -------------------------------------------------
# Run benchmark for 30 seconds
# -------------------------------------------------
sleep 30

echo "$(date)"
echo "Benchmark finished."

exit 0
