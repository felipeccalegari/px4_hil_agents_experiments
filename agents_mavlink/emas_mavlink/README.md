# emas_mavlink
Customized classes from Embedded-Mas framework to work with MAVLink protocol using DroneFleet 1.1.11 library.

### Environment:
- Ubuntu 24.04 machine running PX4 v1.17 with Gazebo
- Raspberry Pi 5 (Jason Agents) <-Serial-> Raspberry Pi 4 (Send/Receive messages) <-UDP-> Ubuntu/PX4 Simulation
- Simulation startup scripts were adapted to connect with Raspberry Pi 4 IP and then started with: `MAV_BROADCAST=1 make px4_sitl gz_x500`

- Users can run via a "virtual serial" with *Socat* tool (`sudo apt install socat`) with the following commands:
`socat -d -d pty,raw,echo=0,link=/dev/ttyV1,wait-slave udp:127.0.0.1:14560,sourceport=14561`

`make px4_sitl gz_x500`

*After PX4 loads and the 'Ready for Takeoff' messages shows up, run in PX4's terminal:*
`mavlink start -u 14560 -o 14561 -t 127.0.0.1 -m onboard -r 40000`

### Agent side:
**Examples**: ./examples/jacamo/serial_device/perception_action/src/agt
- Running the agent:

`cd examples/jacamo/serial_device/perception_action/`

`./gradlew run`

- Agent can:
  - Arm.
  - Set modes (Tested: AUTO.TAKEOFF and AUTO.MISSION).
  - Add waypoints to missions (Mission mode).
  - Start mission.
  - Works with many commands that uses _MAV_CMD_*_ dialects from MAVLink _common.xml_ as long as they're present in the MavCmd enum from DroneFleet.

- Agent can't:
  - Set mode with OFFBOARD option - agent succesfully changes to Offboard mode in PX4 but drone wont takeoff/go to specific coordinates with that mode.
  - Commands from MAVLink dialect that don't start with "MAV_CMD_*" need to be tested.

- For this project, mode changes are currently handled via *SET_MODE* for compatibility with tested PX4 modes

- Waypoints are set up via Missions (internal action "_.mission_item_") only using Lat (Degrees), Lon (Degrees) and Alt (Meters) parameters, which by default uses its own standards but the Mavlink4EmbeddedMas class will automatically multiply the Lat and Lon coordinates by multiplier so Agent Programmer only needs to insert normal degrees (eg. 45.273333) instead of a large number. Also the code uploads mission items as *MISSION_ITEM_INT*, where a *Takeoff* is the first item, and the rest are considered _Waypoints_.

- Adding new agent actions: in the sample_agent.yaml, user can add "*actionName*", which will be the action used in the .asl file and the respective Mavlink code in "*actuationName*".

### Perceptions summary:
Incoming MAVLink telemetry is also converted automatically into Jason perceptions. The perception name is derived from the MAVLink message type by removing `_`, joining the words, and converting the result to lowercase. For example, `GLOBAL_POSITION_INT` becomes `globalpositionint(...)` and `LOCAL_POSITION_NED` becomes `localpositionned(...)`. To discover the exact argument order for a perception, the easiest approach is to temporarily add a debug plan in the `.asl` file with variables for all arguments, print them, and then refine the final perception handler with only the fields you need.

| MAVLink message | Jason perception |
| --- | --- |
| `HEARTBEAT` | `heartbeat(...)` |
| `GLOBAL_POSITION_INT` | `globalpositionint(...)` |
| `LOCAL_POSITION_NED` | `localpositionned(...)` |
| `ATTITUDE` | `attitude(...)` |
| `SYS_STATUS` | `sysstatus(...)` |
| `STATUSTEXT` | `statustext(...)` |
