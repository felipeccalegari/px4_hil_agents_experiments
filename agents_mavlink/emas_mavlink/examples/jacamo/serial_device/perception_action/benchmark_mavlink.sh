#!/bin/bash

echo "$(date)"

LOG_DIR="log"
mkdir -p "$LOG_DIR"
rm -f .stop___MAS

mavlink_sampler=""
jas_sampler=""
cleanup_done=0

join_by_comma() { local IFS=,; echo "$*"; }

find_jacamo_pid() {
    jps -l | awk '/JaCaMoLauncher|jacamo|jason/ {print $1; exit}'
}

find_serial_owner_pid() {
    fuser /dev/ttyAMA0 2>/dev/null | awk '{print $1}'
}

start_sampler() {
    local pid_list="$1"
    local logfile="$2"

    (
        count=1
        while true; do
            read cpu mem < <(
                ps -p "$pid_list" -o %cpu=,rss= 2>/dev/null |
                awk '
                    BEGIN{sCPU=0;sMEM=0}
                    NF>=2{sCPU+=$1;sMEM+=$2}
                    END{printf "%.2f %.2f\n",sCPU,sMEM/1024}'
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

cleanup() {
    [[ "$cleanup_done" -eq 1 ]] && return
    cleanup_done=1

    echo "Stopping benchmark..."

    [[ -n "${mavlink_sampler:-}" ]] && kill "$mavlink_sampler" 2>/dev/null
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
# Generate incremental log names starting at 0
# -------------------------------------------------
i=0
while [[ -f "$LOG_DIR/mavlink_${i}.log" || -f "$LOG_DIR/jason_${i}.log" ]]; do
    ((i++))
done

LOG_MAVLINK="$LOG_DIR/mavlink_${i}.log"
LOG_JAS="$LOG_DIR/jason_${i}.log"

echo "MAVLink log: $LOG_MAVLINK"
echo "Jason log:   $LOG_JAS"

# -------------------------------------------------
# Start application
# -------------------------------------------------
./gradlew -q --console=plain &
APP_PID=$!
sleep 5

# -------------------------------------------------
# Wait for JaCaMo PID
# -------------------------------------------------
PID_JAS=""
for _ in {1..20}; do
    PID_JAS=$(find_jacamo_pid)
    [[ -n "$PID_JAS" ]] && break
    sleep 1
done

# -------------------------------------------------
# Find PID owning the serial port
# -------------------------------------------------
PID_MAVLINK=""
for _ in {1..20}; do
    PID_MAVLINK=$(find_serial_owner_pid)
    [[ -n "$PID_MAVLINK" ]] && break
    sleep 1
done

echo "JaCaMo PID:         ${PID_JAS:-none}"
echo "Serial owner PID:   ${PID_MAVLINK:-none}"

if [[ -n "$PID_JAS" && -n "$PID_MAVLINK" && "$PID_JAS" == "$PID_MAVLINK" ]]; then
    echo "Jason and MAVLink are running in the same process."
fi

# -------------------------------------------------
# Start samplers
# -------------------------------------------------
if [[ -n "$PID_JAS" ]]; then
    jas_sampler=$(start_sampler "$PID_JAS" "$LOG_JAS")
fi

if [[ -n "$PID_MAVLINK" && "$PID_MAVLINK" != "$PID_JAS" ]]; then
    mavlink_sampler=$(start_sampler "$PID_MAVLINK" "$LOG_MAVLINK")
fi

# -------------------------------------------------
# Run benchmark for 30 seconds
# -------------------------------------------------
sleep 30

echo "$(date)"
echo "Benchmark finished."

exit 0
