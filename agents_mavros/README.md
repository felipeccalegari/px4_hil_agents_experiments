## Guide to run MAVROS (ROS 2 Humble) with HIL via Docker container:

1) Make sure the parameters are properly set on Pixhawk:
Connect Pixhawk to PC via USB, open QGC, Vehicle configuration and parameters:
SYS_HITL=1, MAV_1_CONFIG=102, MAV_1_MODE=2, SER_TEL2_BAUD=921600

Reboot the Pixhawk, then disconnect and close QGC.

2) Pi connected to TELEM2 of Pixhawk
- GND - GND
- TX - RX
- RX - TX

3) Power up all devices (Pi and Pixhawk)

4) Run jMavSim simulator on PC (using Headless mode) and connect to QGC:
`cd ~/PX4-Autopilot`
*Check if the USB port is recognized: ls /dev/ttyACM*
Should see /dev/ttyACM0 (if a different number, adjust the port value)

`HEADLESS=1 ./Tools/simulation/jmavsim/jmavsim_run.sh -q -s -d /dev/ttyACM0 -b 921600 -r 250`

Open QGC (should connect automatically).

5) Start the Docker container on the PI:
*Check first the serial port is properly connected with: ls /dev/ttyAMA*
Should see `/dev/ttyAMA0` (if not, check connection or adjust the value)

`docker run -it --rm --name mavros_hitl --restart unless-stopped --device=/dev/ttyAMA0 --network=host --privileged mavros_humble /root/start.sh`

If the container doesnt open automatically:
`docker exec -it mavros_hitl bash`

*Extra command inside the container: `source /opt/ros/humble/setup.bash`

6) Make sure Rosbridge is listening:
`ss -tlnp | grep 9090`

7) Inside the Agents folder:
`./gradlew run`

8) Stop the container:
`docker stop mavros_hitl`

Container should be persistent so to run again later:
`docker start mavros_hitl`
