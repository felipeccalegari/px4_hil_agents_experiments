/* Takeoff example */
/* !setmode.
!arm.

+!setmode
   <- 
      .set_mode("AUTO.TAKEOFF");
      .wait(1000);
      .print("Set mode to AUTO.TAKEOFF.").

+!arm
   <- 
      .arming(false);
      .wait(1000);
      .print("Armed the drone."). */

/* End of takeoff example*/

/* Mission example */
/* !start.
+!start <-
    // 1. Clear 
    .time(_, M, S, MS);
    .print("Starting clearing mission at: ", M, ":", S, ":", MS);
    .mission_clear;
    .time(_, M1, S1, MS1);
    .print("Finishing clearing mission at: ", M1, ":", S1, ":", MS1);
    .wait(1500);
    .time(_, M2, S2, MS2);
    .print("Starting mission push at: ", M2, ":", S2, ":", MS2);
    .mission_push([0], [
      [0, 22, true, true, 0, 0, 0, 0, 47.3977419, 8.5455938, 7],
      [0, 16, false, true, 0, 0, 0, 0, 47.3977569, 8.5456338, 7],
      [0, 16, false, true, 0, 0, 0, 0, 47.3977919, 8.5456438, 7],
      [0, 16, false, true, 0, 0, 0, 0, 47.3977869, 8.5456538, 7],
      [0, 16, false, true, 0, 0, 0, 0, 47.3978959, 8.5456638, 7]
      ]);
    .time(_, M3, S3, MS3);
    .print("Finishing mission push at: ", M3, ":", S3, ":", MS3);
    .wait(500);
    .time(_, M4, S4, MS4);
    .print("Starting set mode at: ", M4, ":", S4, ":", MS4);
    .set_mode("AUTO.MISSION");
    .time(_, M5, S5, MS5);
    .print("Finishing set mode at: ", M5, ":", S5, ":", MS5);
    .wait(1000);
    .time(_, M6, S6, MS6);
    .print("Starting arming at: ", M6, ":", S6, ":", MS6);
    .arming(true);
    .time(_, M7, S7, MS7);
    .print("Finishing arming at: ", M7, ":", S7, ":", MS7). */

/* End of mission example */

/* Offboard Mode example*/

/* !pub_waypoints.
+!pub_waypoints : true
   <- .setpoint_local([[0,0], 'map'],[[10.0, 5.0, 8.0], [0.0, 0.0, 0.0, 1.0]]);
      
      .wait(100);
      !pub_waypoints.

!mode.
+!mode : true
   <- .set_mode("OFFBOARD").

!arm.
+!arm : true
   <- .arming(true).  */

/* End of Offboard Mode example */

/* Battery monitoring example. Used alongside the Mission example*/
/* +battery(percentage(P))
   <- 
      .nano_time(T);
      if (P < 0.55) {
         .nano_time(T1);
         .print("Total Time after agent percepted: ", T1 - T);
         .print("Battery level getting low: ", P);
         .nano_time(T2);
         .set_mode("AUTO.LAND");
         .nano_time(T3);
         .print("Total time to enter AUTO.LAND mode: ", T3 - T2);
         .print("Entered AUTO.LAND mode");
         }
      
      .wait(5000). */

/* End of Battery monitoring example */

/* Reposition counter example
Agent sends initial takeoff to Z = 1.0m, then percepts position updates and if the Z value is within
0.35m of the target, it sends a new coordinate incrementing Z value in 1 unit until it reaches Z = 10m.
 */
/* !start.

+!start <-
    -awaiting_z(_);
    -step_transitioning(_);
    .print("Starting TAKEOFF + REPOSITION Z counter...");
    .arming(true);
    .wait(300);
    -+awaiting_z(1.0);
    .takeoff_cmd(0.0, 0.0, 47.3979710, 8.5461637, 1.0).

+position(pose(position(x(_), y(_), z(Z)), orientation(x(_), y(_), z(_), w(_))))
  : awaiting_z(Target)
    & not step_transitioning(true)
  <-
    -+step_transitioning(true);
    if (Z >= (Target - 0.35) & Z <= (Target + 0.35)) {
        -awaiting_z(_);
        .nano_time(T);
        .print(T,";",Z);
        if (Target < 10.0) {
            Next = Target + 1.0;
            -+awaiting_z(Next);
            .reposition(false, 3, 192, 0, 0, -1.0, 1.0, 0.0, 0.0, 473979710, 85461637, Next);
        } else {
            .print("Reposition Z counter finished.");
        };
    };
    -step_transitioning(_). */
/* End of Reposition counter example */

/* MAVROS parameter counter.

Starts at 1 and only sends the next increment after PX4 confirms
the last published value through /mavros/param/event.
*/

!demo_param_counter.

+!demo_param_counter <-
    -expected_param_value(_);
    +expected_param_value(1.0);
    .print("Starting MAVROS parameter counter at 1.");
    .wait(500);
    .param_set("MPC_Z_VEL_MAX_UP", 1.0).

+param_event(param_id("MPC_Z_VEL_MAX_UP"),
             value(type(_),
                   bool_value(_),
                   integer_value(_),
                   double_value(DoubleValue),
                   string_value(_),
                   byte_array_value(_),
                   bool_array_value(_),
                   integer_array_value(_),
                   double_array_value(_),
                   string_array_value(_)))
  : expected_param_value(Expected)
  <-
    if (DoubleValue == Expected) {
      .nano_time(Timestamp);
      .print(Timestamp, ";", DoubleValue);

      if (Expected < 100.0) {
        NextValue = Expected + 1.0;
        -expected_param_value(_);
        +expected_param_value(NextValue);
        .param_set("MPC_Z_VEL_MAX_UP", NextValue);
        .wait(120)
      } else {
        .print("MAVROS parameter counter finished at value ", DoubleValue, ".");
        -expected_param_value(_)
      }
    }.



/* End of MAVROS parameter counter. */

/* "High-level" Offboard example for PX4. */
/* !demo_offboard_body_relative_position.
+!demo_offboard_body_relative_position
  : not position(pose(position(x(_), y(_), z(_)), orientation(x(_), y(_), z(_), w(_))))
  <-
    .print("Waiting for local position...");
    .wait(500);
    !demo_offboard_body_relative_position.

+!demo_offboard_body_relative_position
  : position(pose(position(x(_), y(_), z(_)), orientation(x(_), y(_), z(_), w(_))))
  <-
    .print("Demo: Offboard mode using high-level setpoint local with (Forward, Right, Up).");
    -offboard_body_relative_stream_enabled;
    +offboard_body_relative_stream_enabled;
    -body_relative_target(_,_,_);
    +body_relative_target(0.0, 0.0, 2.0); // take off 2 m relative to the current pose
    !!offboard_body_relative_position_stream;
    .wait(1200);
    .set_mode("OFFBOARD");
    .wait(500);
    .arming(true);
    .wait(5000);
    -body_relative_target(_,_,_);
    +body_relative_target(0.0, -3.0, 0.0); // move 3 m to the drone's current left
    .wait(5000);
    -body_relative_target(_,_,_);
    +body_relative_target(2.0, 0.0, 0.0); // then move 2 m forward from the drone's current heading
    .wait(5000);
    -body_relative_target(_,_,_);
    +body_relative_target(2.0, 2.0, 0.0); // forward-right
    .wait(5000);
    -body_relative_target(_,_,_);
    +body_relative_target(0.0, 3.0, 0.0); // move 3 m to the drone's current right
    .wait(5000);
    -body_relative_target(_,_,_);
    +body_relative_target(-2.0, 0.0, 0.0); // move 2 m backward from the drone's current heading
    .wait(5000);
    .set_mode("AUTO.RTL");
    .wait(500);
    -offboard_body_relative_stream_enabled;
    -body_relative_target(_,_,_);
    .print("Returning to launch and finishing flight.").

+!offboard_body_relative_position_stream
  : body_relative_target(Forward, Right, Up) & offboard_body_relative_stream_enabled
  <-
    .setpoint_local(Forward, Right, Up);
    .wait(100);
    !offboard_body_relative_position_stream.

+!offboard_body_relative_position_stream
  : not offboard_body_relative_stream_enabled
  <-
    true. */
/* End of "High-level" Offboard example for PX4. */
