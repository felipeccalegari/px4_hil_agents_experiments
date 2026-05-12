/* Reposition example*/

/* !reposition.

+!reposition <-
    -awaiting_z(_);
    -step_transitioning(_);
    .print("Starting TAKEOFF + REPOSITION Z counter...");
    .arming(1,21196);
    .wait(300);
    -+awaiting_z(1.0);
    .takeoff(0, 0, 0, 0, 47.3979710, 8.5461637, 1.0).

+localpositionned(_,_,_,Zned,_,_,_)
  : awaiting_z(Target)
    & not step_transitioning(true)
  <-
    -+step_transitioning(true);
    Z = -Zned; //Needed because of the NED (North-East-Down) frame, so need to adjust Z value; In Mavros its not necessary, already done under the hood.
    if (Z >= (Target - 0.35) & Z <= (Target + 0.35)) {
        -awaiting_z(_);
        .nano_time(T);
        .print(T,";",Z);
        if (Target < 10.0) {
            Next = Target + 1.0;
            -+awaiting_z(Next);
            .reposition(-1, 1, 0, 0, 47.3979710, 8.5461637, Next);
        } else {
            .print("Reposition Z counter finished.");
        };
    };
    -step_transitioning(_). */
/* End of Reposition example */

/* Takeoff and land example.*/
/* !demo_takeoff_land.
+!demo_takeoff_land <-
    .print("Demo: arm -> takeoff -> land.");
    .arming(1);
    .wait(500);
    .takeoff(0, 0, 0, 0, 47.3979710, 8.5461637, 3.0);
    .wait(8000);
    .land(0, 0, 0, 0, 47.3979710, 8.5461637, 0.0). */

/* End of Takeoff and land example. */

/* Takeoff and RTL (Return to Launch) example. */
/* !demo_takeoff_rtl.
+!demo_takeoff_rtl <-
    .print("Demo: arm -> takeoff -> RTL.");
    .arming(1);
    .wait(500);
    .takeoff(0, 0, 0, 0, 47.3979710, 8.5461637, 4.0);
    .wait(8000);
    .rtl. */

/* End of Takeoff and RTL example. */

/* Reposition and Land example. */
/* !demo_square.
+!demo_square <-
    .print("Demo: arm -> takeoff -> 3 reposition steps -> land.");
    .arming(1);
    .wait(500);
    .takeoff(0, 0, 0, 0, 47.3979710, 8.5461637, 4.0);
    .wait(7000);
    .reposition(-1, 1, 0, 0, 47.3979710, 8.5461637, 4.0);
    .wait(4000);
    .reposition(-1, 1, 0, 0, 47.3980210, 8.5461637, 4.0);
    .wait(4000);
    .reposition(-1, 1, 0, 0, 47.3980210, 8.5462237, 4.0);
    .wait(4000);
    .land(0, 0, 0, 0, 47.3980210, 8.5462237, 0.0). */
/* End of Reposition and Land example */

/* Mission mode and mission start example. */

/* !demo_mission.
+!demo_mission <-
    .print("Demo: upload a short mission and start AUTO mission.");
    .mission_clear;
    .wait(500);
    .mission_item(47.3979710, 8.5461637, 4.0); //mission 0
    .mission_item(47.3980210, 8.5461637, 4.0); //mission 1
    .mission_item(47.3980210, 8.5462237, 4.0); //mission 2
    .wait(500);
    .arming(1);
    .wait(500);
    .mission_start(0, 2); //(first item (0), last item (2)) - if 2nd parameter is -1, it'll run until the last added item (treated on class-side).
    .wait(20000);
    .rtl. */
/* End of Mission mode and mission start example. */

/* Common MAVLink perception examples.*/

// Used nanoseconds to avoid perceptions spamming in the terminal and affect simulation behavior.
/* hb_gap_ns(5000000000).
lp_gap_ns(7000000000).
att_gap_ns(7000000000).
sys_gap_ns(3000000000).
gps_gap_ns(7000000000).

last_hb_ns(0).
last_lp_ns(0).
last_att_ns(0).
last_sys_ns(0).
last_gps_ns(0). */

/* Mavlink HEARTBEAT perception example */
/* +heartbeat(A,B,C,D,E,F)
  : last_hb_ns(Last) & hb_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_hb_ns(_);
      +last_hb_ns(Now);
      .print("1. Heartbeat: type=", A, ", autopilot=", B, ", base_mode=", C, ", custom_mode=", D,
             ", system_status=", E, ", mavlink_version=", F)
    }. */
/* End of Mavlink HEARTBEAT perception example */

/* Mavlink LOCAL_POSITION_NED perception example. */
/* +localpositionned(X,Y,Zned,Vx,Vy,Vz)
  : last_lp_ns(Last) & lp_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_lp_ns(_);
      +last_lp_ns(Now);
      Alt = -Zned;
      .print("Local position NED: x=", X, ", y=", Y, ", alt=", Alt,
             ", vx=", Vx, ", vy=", Vy, ", vz=", Vz)
    }. */
/* End of Mavlink LOCAL_POSITION_NED perception example */

/* Mavlink ATTITUDE perception example. */
/* +attitude(Roll,Pitch,Yaw,_,_,_,_)
  : last_att_ns(Last) & att_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_att_ns(_);
      +last_att_ns(Now);
      .print("Attitude: roll=", Roll, ", pitch=", Pitch, ", yaw=", Yaw)
    }. */
/* End of Mavlink ATTITUDE perception example */

/* Mavlink SYS_STATUS perception example.
Note: PX4 is not sending Battery data via telemetry, will update code once figure out how to adjust it.
*/
/* +sysstatus(_,_,_,_,_,_,_,BatteryRemaining,_,_,_,_,_)
  : last_sys_ns(Last) & sys_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_sys_ns(_);
      +last_sys_ns(Now);
      .print("Battery remaining: ", BatteryRemaining)
    }. */
/* End of Mavlink SYS_STATUS perception example */

/* Mavlink STATUSTEXT perception example. */
/* +statustext(Severity,Text,_,_)
  <-
    .print("PX4 status [", Severity, "]: ", Text).
 */
/* End of STATUSTEXT perception example */

/* Mavlink GLOBAL_POSITION_INT perception example. */
/* +globalpositionint(_,Lat,Lon,Alt,RelAlt,_,_,_,_)
  : last_gps_ns(Last) & gps_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_gps_ns(_);
      +last_gps_ns(Now);
      .print("[GPS] lat=",Lat," lon=",Lon," alt=",Alt," relAlt=",RelAlt)
    }. */
/* End of GLOBAL_POSITION_INT perception example */

/* MAVLink parameter counter.

Starts at 1 and only sends the next increment after PX4 confirms
the last published value through PARAM_VALUE.
*/
!demo_param_counter.

+!demo_param_counter <-
    -expected_param_value(_);
    +expected_param_value(1.0);
    .print("Starting direct MAVLink parameter counter at 1.");
    .wait(500);
    .param_set("MPC_Z_VEL_MAX_UP", 1.0).

+paramvalue("MPC_Z_VEL_MAX_UP", Value, _, _, _)
  : expected_param_value(Expected)
  <-
    if (Value == Expected) {
      .nano_time(Timestamp);
      .print(Timestamp, ";", Value);

      if (Expected < 100.0) {
        NextValue = Expected + 1.0;
        -expected_param_value(_);
        +expected_param_value(NextValue);
        .param_set("MPC_Z_VEL_MAX_UP", NextValue);
        .wait(120)
      } else {
        .print("MAVLink parameter counter finished at value ", Value, ".");
        -expected_param_value(_)
      }
    }.

/* End of MAVLink parameter counter. */
