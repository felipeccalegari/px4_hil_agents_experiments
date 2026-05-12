#!/bin/bash
source /opt/ros/humble/setup.bash

echo "[start.sh] Launching MAVROS..."
ros2 launch mavros px4.launch \
  fcu_url:=/dev/ttyAMA0:921600 \
  gcs_url:=udp://@192.168.x.x:14550 &

echo "[start.sh] Waiting for MAVROS to initialize..."
sleep 5

echo "[start.sh] Launching rosbridge on port 9090..."
ros2 launch rosbridge_server rosbridge_websocket_launch.xml \
  port:=9090