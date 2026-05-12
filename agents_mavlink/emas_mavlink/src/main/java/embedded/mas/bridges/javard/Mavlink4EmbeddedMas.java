package embedded.mas.bridges.javard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import embedded.mas.bridges.jacamo.EmbeddedAction;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.CommandInt;
import io.dronefleet.mavlink.common.CommandLong;
import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MavFrame;
import io.dronefleet.mavlink.common.MavMode;
import io.dronefleet.mavlink.common.MavParamType;
import io.dronefleet.mavlink.common.MavMissionResult;
import io.dronefleet.mavlink.common.MavMissionType;
import io.dronefleet.mavlink.common.MissionAck;
import io.dronefleet.mavlink.common.MissionClearAll;
import io.dronefleet.mavlink.common.MissionCount;
import io.dronefleet.mavlink.common.MissionItemInt;
import io.dronefleet.mavlink.common.MissionRequest;
import io.dronefleet.mavlink.common.MissionRequestInt;
import io.dronefleet.mavlink.common.MissionSetCurrent;
import io.dronefleet.mavlink.common.ParamRequestRead;
import io.dronefleet.mavlink.common.ParamSet;
import io.dronefleet.mavlink.common.SetPositionTargetLocalNed;
import io.dronefleet.mavlink.common.Timesync;
import io.dronefleet.mavlink.minimal.MavAutopilot;
import io.dronefleet.mavlink.minimal.Heartbeat;
import io.dronefleet.mavlink.minimal.MavModeFlag;
import io.dronefleet.mavlink.minimal.MavState;
import io.dronefleet.mavlink.minimal.MavType;
import io.dronefleet.mavlink.util.EnumValue;

public class Mavlink4EmbeddedMas extends NRJ4EmbeddedMas {

    private int systemId = 255;
    private int componentId = 190;
    private int targetSystem = 1;
    private int targetComponent = 1;

    private boolean constructorReady = false;
    private boolean mavlinkStarted = false;
    private boolean mavTxInit = false;
    private boolean missionBusy = false;
    private boolean heartbeatRunning = false;
    private boolean mavRxRunning = false;

    private MavlinkConnection mavTxConn;
    private ByteArrayOutputStream mavTxOut;
    private Object mavTxLock;
    private MavlinkConnection mavRxConn;
    private Thread mavRxThread;
    private Thread heartbeatThread;
    private BlockingQueue<Object> missionProtocolQueue;
    private Map<String, Long> telemetryEmitMs;
    private Map<String, String> latestTelemetryJsonByKey;

    private static class MissionWp {
        final double latDeg;
        final double lonDeg;
        final float altM;
        final boolean isTakeoff;

        MissionWp(double latDeg, double lonDeg, float altM, boolean isTakeoff) {
            this.latDeg = latDeg;
            this.lonDeg = lonDeg;
            this.altM = altM;
            this.isTakeoff = isTakeoff;
        }
    }

    private List<MissionWp> missionBuffer;

    private static final String[] DIALECT_PACKAGES = new String[] {
            "io.dronefleet.mavlink.common",
            "io.dronefleet.mavlink.minimal",
            "io.dronefleet.mavlink.ardupilotmega",
            "io.dronefleet.mavlink.uavionix"
    };

    private static final Set<MavCmd> COMMAND_INT_CANDIDATES = new HashSet<>(Arrays.asList(
            MavCmd.MAV_CMD_NAV_WAYPOINT,
            MavCmd.MAV_CMD_NAV_LOITER_UNLIM,
            MavCmd.MAV_CMD_NAV_LOITER_TURNS,
            MavCmd.MAV_CMD_NAV_LOITER_TIME,
            MavCmd.MAV_CMD_NAV_LAND,
            MavCmd.MAV_CMD_NAV_TAKEOFF,
            MavCmd.MAV_CMD_DO_REPOSITION
    ));

    public Mavlink4EmbeddedMas(String portDescription, int baudRate)
            throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException {
        super(portDescription, baudRate);
        constructorReady = true;
        initMavlinkMode();
    }

    @Override
    public String read() {
        return serialRead();
    }

    @Override
    // Sends either a MAVLink command or raw serial text, depending on the input.
    public boolean write(String s) {
        if (s == null || s.trim().isEmpty()) return false;

        String command = s.trim();
        try {
            initMavlinkMode();
            sendGcsHeartbeatNow();
            if (isMavlinkCommand(command)) {
                processMavlinkCommand(command);
            } else {
                serialWrite(command);
            }
            return true;
        } catch (Exception e) {
            System.err.println("[NRJ] Failed to process command: " + command);
            e.printStackTrace();
            return false;
        }
    }

    @Override
    // Opens the base serial connection and then starts the MAVLink side if needed.
    public boolean openConnection() {
        boolean ok = super.openConnection();
        if (ok && constructorReady) {
            initMavlinkMode();
        }
        return ok;
    }

    @Override
    // Stops background MAVLink threads before closing the serial connection.
    public void closeConnection() {
        heartbeatRunning = false;
        mavRxRunning = false;
        super.closeConnection();
    }

    @Override
    public void execEmbeddedAction(EmbeddedAction action) {
        // not used here
    }

    @Override
    // Returns the latest cached MAVLink telemetry converted to JSON beliefs.
    public String serialRead() {
        try {
            initMavlinkMode();
            if (missionBusy) {
                return "";
            }

            String mav = readMavlinkTelemetryNonBlocking();
            if (mav != null && !mav.isEmpty()) return mav;

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // Initializes queues, RX/TX helpers, and background MAVLink threads once.
    private synchronized void initMavlinkMode() {
        if (mavlinkStarted) return;

        mavTxLock = new Object();
        missionProtocolQueue = new LinkedBlockingQueue<>();
        telemetryEmitMs = new ConcurrentHashMap<>();
        latestTelemetryJsonByKey = new LinkedHashMap<>();
        missionBuffer = new ArrayList<>();

        try { ensureMavlinkTx(); } catch (Exception ignored) {}
        try { initMavlinkRx(); } catch (Exception ignored) {}
        startMavlinkReader();
        startGcsHeartbeat();
        mavlinkStarted = true;
    }

    // Prepares the MAVLink transmit connection that writes bytes to the serial port.
    private void ensureMavlinkTx() throws IOException {
        if (mavTxLock == null) {
            mavTxLock = new Object();
        }
        synchronized (mavTxLock) {
            if (mavTxInit) return;
            mavTxOut = new ByteArrayOutputStream();
            mavTxConn = MavlinkConnection.create(new java.io.PipedInputStream(), mavTxOut);
            mavTxInit = true;
        }
    }

    // Serializes one MAVLink payload and pushes it to the external port.
    private void sendMavlink(Object payload) throws IOException {
        ensureMavlinkTx();

        synchronized (mavTxLock) {
            mavTxOut.reset();
            mavTxConn.send2(systemId, componentId, payload);
            byte[] bytes = mavTxOut.toByteArray();

            if (bytes.length == 0) return;

            comPort.getOutputStream().write(bytes);
            comPort.getOutputStream().flush();
        }
    }

    // Detects whether the outgoing text looks like a MAVLink command expression.
    private boolean isMavlinkCommand(String text) {
        return text.matches("^[A-Z0-9_]+(?:\\s*\\(.*\\))?$");
    }

    // Creates the MAVLink receive connection from the serial input stream.
    private void initMavlinkRx() {
        if (mavRxConn != null) return;
        try {
            InputStream is = comPort.getInputStream();
            mavRxConn = MavlinkConnection.create(is, new ByteArrayOutputStream());
        } catch (Exception ignored) {
        }
    }

    // Starts the background reader that turns incoming MAVLink packets into beliefs.
    private void startMavlinkReader() {
        if (mavRxRunning) return;
        mavRxRunning = true;

        mavRxThread = new Thread(() -> {
            while (mavRxRunning) {
                try {
                    if (mavRxConn == null) {
                        initMavlinkRx();
                        if (mavRxConn == null) {
                            sleepQuiet(50);
                            continue;
                        }
                    }

                    MavlinkMessage<?> msg = mavRxConn.next();
                    if (msg == null || msg.getPayload() == null) continue;

                    Object payload = msg.getPayload();

                    if (payload instanceof Timesync) {
                        Timesync ts = (Timesync) payload;
                        if (ts.tc1() == 0) {
                            Timesync reply = Timesync.builder()
                                    .tc1(System.nanoTime())
                                    .ts1(ts.ts1())
                                    .targetSystem(msg.getOriginSystemId())
                                    .targetComponent(msg.getOriginComponentId())
                                    .build();
                            sendMavlink(reply);
                        }
                    }

                    if (payload instanceof MissionRequestInt
                            || payload instanceof MissionRequest
                            || payload instanceof MissionAck) {
                        missionProtocolQueue.offer(payload);
                        continue;
                    }

                    String json = mavPayloadToJson(payload);
                    if (json != null && !json.isEmpty()) {
                        cacheLatestTelemetry(json);
                    }
                } catch (Exception e) {
                    if (mavRxRunning) {
                        System.err.println("[NRJ] MAVLink RX reader failed, retrying...");
                        e.printStackTrace();
                        sleepQuiet(100);
                    }
                }
            }
        });

        mavRxThread.setName("nrj-mavlink-rx");
        mavRxThread.setDaemon(true);
        mavRxThread.start();
    }

    // Starts a periodic GCS heartbeat so PX4 keeps the MAVLink link active.
    private void startGcsHeartbeat() {
        if (heartbeatRunning) return;
        heartbeatRunning = true;

        heartbeatThread = new Thread(() -> {
            while (heartbeatRunning) {
                try {
                    sendGcsHeartbeatNow();
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[GCS] Heartbeat send failed, retrying...");
                    e.printStackTrace();
                    sleepQuiet(250);
                }
            }
        });

        heartbeatThread.setName("nrj-mavlink-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    // Sends one immediate GCS heartbeat packet.
    private void sendGcsHeartbeatNow() throws IOException {
        Heartbeat hb = Heartbeat.builder()
                .type(MavType.MAV_TYPE_GCS)
                .autopilot(MavAutopilot.MAV_AUTOPILOT_INVALID)
                .baseMode(EnumValue.of(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED))
                .customMode(0)
                .systemStatus(MavState.MAV_STATE_ACTIVE)
                .build();
        sendMavlink(hb);
    }

    // Returns the newest telemetry snapshot and clears the cache.
    private String readMavlinkTelemetryNonBlocking() {
        synchronized (latestTelemetryJsonByKey) {
            if (latestTelemetryJsonByKey.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder(512);
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : latestTelemetryJsonByKey.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            }
            sb.append("}");
            latestTelemetryJsonByKey.clear();
            return sb.toString();
        }
    }

    // Stores the latest payload per MAVLink message type so multiple beliefs can be read together.
    private void cacheLatestTelemetry(String json) {
        int keyStart = json.indexOf('"');
        if (keyStart < 0) return;

        int keyEnd = json.indexOf('"', keyStart + 1);
        if (keyEnd < 0) return;

        int valueStart = json.indexOf(':', keyEnd);
        if (valueStart < 0) return;

        String key = json.substring(keyStart + 1, keyEnd);
        if (key.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean throttleEnabled = !"paramvalue".equals(key);
        if (throttleEnabled) {
            Long last = telemetryEmitMs.get(key);
            long minGapMs = 100L;
            if (last != null && (now - last) < minGapMs) {
                return;
            }
            telemetryEmitMs.put(key, now);
        }

        String valueJson = json.substring(valueStart + 1, json.length() - 1).trim();
        synchronized (latestTelemetryJsonByKey) {
            latestTelemetryJsonByKey.put(key, valueJson);
        }
    }

    // Converts one MAVLink payload object into the compact JSON format used by the agent.
    private String mavPayloadToJson(Object payload) {
        try {
            Class<?> cls = payload.getClass();
            String beliefName = cls.getSimpleName().toLowerCase(Locale.ROOT);

            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"").append(beliefName).append("\":[");

            boolean first = true;
            Field[] fields = cls.getDeclaredFields();
            for (Field f : fields) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod)) continue;
                if (f.isSynthetic()) continue;

                f.setAccessible(true);
                Object v = f.get(payload);

                if (v instanceof byte[]) continue;
                if (v == null) continue;

                if (v instanceof Float) {
                    float fv = ((Float) v).floatValue();
                    if (Float.isNaN(fv) || Float.isInfinite(fv)) continue;
                } else if (v instanceof Double) {
                    double dv = ((Double) v).doubleValue();
                    if (Double.isNaN(dv) || Double.isInfinite(dv)) continue;
                }

                if (!first) sb.append(",");
                first = false;
                appendJsonValue(sb, v);
            }

            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // Appends one Java value to the JSON builder, including enums and arrays.
    private void appendJsonValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
            return;
        }

        if (v instanceof EnumValue) {
            EnumValue<?> ev = (EnumValue<?>) v;
            Object entry = ev.entry();
            if (entry != null) {
                sb.append("\"").append(escapeJson(entry.toString())).append("\"");
            } else {
                sb.append(ev.value());
            }
            return;
        }

        Class<?> vc = v.getClass();
        if (vc.isArray()) {
            Class<?> ct = vc.getComponentType();
            if (ct == byte.class) {
                sb.append("[]");
                return;
            }

            sb.append("[");
            int n = Array.getLength(v);
            boolean first = true;
            for (int i = 0; i < n; i++) {
                Object elem = Array.get(v, i);
                if (elem == null) continue;

                if (elem instanceof Float) {
                    float fv = ((Float) elem).floatValue();
                    if (Float.isNaN(fv) || Float.isInfinite(fv)) continue;
                } else if (elem instanceof Double) {
                    double dv = ((Double) elem).doubleValue();
                    if (Double.isNaN(dv) || Double.isInfinite(dv)) continue;
                }

                if (!first) sb.append(",");
                first = false;

                if (elem instanceof EnumValue) {
                    EnumValue<?> ev = (EnumValue<?>) elem;
                    Object entry = ev.entry();
                    if (entry != null) sb.append("\"").append(escapeJson(entry.toString())).append("\"");
                    else sb.append(ev.value());
                } else if (elem instanceof String) {
                    sb.append("\"").append(escapeJson((String) elem)).append("\"");
                } else if (elem instanceof Character) {
                    sb.append("\"").append(escapeJson(String.valueOf(elem))).append("\"");
                } else if (elem instanceof Boolean || elem instanceof Number) {
                    sb.append(elem.toString());
                } else {
                    sb.append("\"").append(escapeJson(elem.toString())).append("\"");
                }
            }
            sb.append("]");
            return;
        }

        if (v instanceof String) {
            sb.append("\"").append(escapeJson((String) v)).append("\"");
            return;
        }

        if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
            return;
        }

        if (v.getClass().isEnum()) {
            sb.append("\"").append(escapeJson(v.toString())).append("\"");
            return;
        }

        sb.append("\"").append(escapeJson(v.toString())).append("\"");
    }

    // Escapes plain text so it can be placed safely into JSON.
    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    private static class ParsedCommand {
        final String name;
        final String[] params;

        ParsedCommand(String name, String[] params) {
            this.name = name;
            this.params = params;
        }
    }

    // Parses NAME(...) commands into a command name plus raw string parameters.
    private ParsedCommand parseCommand(String text) throws Exception {
        text = text.trim();
        Pattern pattern = Pattern.compile("^([A-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*$");
        Matcher matcher = pattern.matcher(text);

        if (!matcher.matches()) {
            if (text.matches("^[A-Z0-9_]+$")) return new ParsedCommand(text, new String[0]);
            throw new Exception("Invalid command format: " + text);
        }

        String name = matcher.group(1).trim();
        String paramsStr = matcher.group(2).trim();

        String[] params;
        if (paramsStr.isEmpty()) params = new String[0];
        else params = paramsStr.split("\\s*,\\s*");

        return new ParsedCommand(name, params);
    }

    // Parses an integer parameter, defaulting to zero on invalid input.
    private int toInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // Parses a float parameter, defaulting to zero on invalid input.
    private float toFloat(String s) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return 0f; }
    }

    // Checks whether the command parameters include a non-zero location.
    private boolean hasLocationParams(String[] params) {
        if (params.length < 7) return false;
        return !isZeroish(params[4]) || !isZeroish(params[5]) || !isZeroish(params[6]);
    }

    // Treats invalid or near-zero numeric text as zero for routing decisions.
    private boolean isZeroish(String raw) {
        try {
            return Math.abs(Double.parseDouble(raw.trim())) < 1e-9;
        } catch (Exception e) {
            return true;
        }
    }

    // Chooses COMMAND_INT only for commands that benefit from location-aware encoding.
    private boolean shouldUseCommandInt(MavCmd command, String[] params) {
        return COMMAND_INT_CANDIDATES.contains(command) && hasLocationParams(params);
    }

    // Resolves a MAV_CMD name and fails with a clearer error if the enum is missing it.
    private MavCmd resolveMavCmd(String name) throws Exception {
        String normalized = name.trim();
        if (!normalized.startsWith("MAV_CMD_")) {
            normalized = "MAV_CMD_" + normalized;
        }
        try {
            return MavCmd.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new Exception("Unsupported MAV_CMD in DroneFleet enum: " + normalized, e);
        }
    }

    // Sends a MAV_CMD through COMMAND_LONG, filling missing params with zero.
    private void sendCommandLong(MavCmd command, String[] params) throws IOException {
        float[] p = new float[7];
        for (int i = 0; i < 7; i++) p[i] = (i < params.length) ? toFloat(params[i]) : 0f;

        if (command == MavCmd.MAV_CMD_NAV_LAND
                && p[4] == 0f && p[5] == 0f && p[6] == 0f) {
            p[4] = Float.NaN;
            p[5] = Float.NaN;
            p[6] = Float.NaN;
        }

        CommandLong msg = CommandLong.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .command(command)
                .confirmation((short) 0)
                .param1(p[0]).param2(p[1]).param3(p[2]).param4(p[3]).param5(p[4]).param6(p[5]).param7(p[6])
                .build();

        sendMavlink(msg);
    }

    // Sends a MAV_CMD through COMMAND_INT using global-relative lat/lon/alt.
    private void sendCommandInt(MavCmd command, String[] params) throws IOException {
        float[] p = new float[7];
        for (int i = 0; i < 7; i++) p[i] = (i < params.length) ? toFloat(params[i]) : 0f;

        int latE7 = (int) Math.round(p[4] * 1e7);
        int lonE7 = (int) Math.round(p[5] * 1e7);

        CommandInt msg = CommandInt.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .frame(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT)
                .command(command)
                .current((short) 0)
                .autocontinue((short) 0)
                .param1(p[0]).param2(p[1]).param3(p[2]).param4(p[3])
                .x(latE7)
                .y(lonE7)
                .z(p[6])
                .build();

        sendMavlink(msg);
    }

    // Buffers one mission waypoint so the full mission can be uploaded later.
    private void handleBufferedMissionItemInt(String[] params) throws Exception {
        if (params.length < 3) {
            throw new Exception("MISSION_ITEM_INT expects at least 3 params: latDeg, lonDeg, altM");
        }

        double latDeg;
        double lonDeg;
        float altM;

        try {
            latDeg = Double.parseDouble(params[0]);
            lonDeg = Double.parseDouble(params[1]);
            altM = toFloat(params[2]);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid MISSION_ITEM_INT parameters: " + Arrays.toString(params));
        }

        synchronized (missionBuffer) {
            boolean isTakeoff = missionBuffer.isEmpty();
            missionBuffer.add(new MissionWp(latDeg, lonDeg, altM, isTakeoff));
        }
    }

    // Uploads the buffered mission and starts it from the requested item range.
    private void startMissionUpload(int firstItem, int lastItem) throws IOException {
        final List<MissionWp> missionSnapshot;
        synchronized (missionBuffer) {
            missionSnapshot = new ArrayList<>(missionBuffer);
        }

        int n = missionSnapshot.size();
        if (n <= 0) {
            return;
        }

        if (firstItem < 0 || firstItem >= n) firstItem = 0;
        if (lastItem < 0 || lastItem >= n) lastItem = n - 1;

        missionBusy = true;
        missionProtocolQueue.clear();
        try {
            MissionClearAll clear = MissionClearAll.builder()
                    .targetSystem((short) targetSystem)
                    .targetComponent((short) targetComponent)
                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .build();
            sendMavlink(clear);
            sleepQuiet(80);

            MissionCount count = MissionCount.builder()
                    .targetSystem((short) targetSystem)
                    .targetComponent((short) targetComponent)
                    .count((short) n)
                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .build();
            sendMavlink(count);
            sleepQuiet(80);

            boolean usedHandshake = false;
            try {
                Set<Integer> sentSeqs = new HashSet<>();
                while (sentSeqs.size() < missionSnapshot.size()) {
                    int requestedSeq = waitForMissionRequest(3000, missionSnapshot.size());
                    if (sentSeqs.contains(requestedSeq)) continue;

                    MissionWp wp = missionSnapshot.get(requestedSeq);
                    MissionItemInt item = buildMissionItem(wp, requestedSeq);
                    sendMavlink(item);
                    sentSeqs.add(requestedSeq);
                }
                waitForMissionAck(3000);
                usedHandshake = true;
            } catch (IOException e) {
                missionProtocolQueue.clear();
                sendMissionItemsBulk(missionSnapshot);
                sleepQuiet(250);
            }
            if (!usedHandshake) {
                // keep flow identical but silent
            }

            MissionSetCurrent setCur = MissionSetCurrent.builder()
                    .targetSystem((short) targetSystem)
                    .targetComponent((short) targetComponent)
                    .seq((short) firstItem)
                    .build();
            sendMavlink(setCur);
            sleepQuiet(80);

            CommandLong start = CommandLong.builder()
                    .targetSystem((short) targetSystem)
                    .targetComponent((short) targetComponent)
                    .command(MavCmd.MAV_CMD_MISSION_START)
                    .confirmation((short) 0)
                    .param1((float) firstItem)
                    .param2((float) lastItem)
                    .param3(0f).param4(0f).param5(0f).param6(0f).param7(0f)
                    .build();
            sendMavlink(start);
        } finally {
            missionBusy = false;
            synchronized (missionBuffer) {
                missionBuffer.clear();
            }
        }
    }

    // Clears the local mission buffer used before upload.
    private void clearMissionBuffer() {
        synchronized (missionBuffer) {
            missionBuffer.clear();
        }
    }

    // Builds one MISSION_ITEM_INT from a buffered waypoint.
    private MissionItemInt buildMissionItem(MissionWp wp, int seq) {
        int latE7 = (int) Math.round(wp.latDeg * 1e7);
        int lonE7 = (int) Math.round(wp.lonDeg * 1e7);

        MavCmd cmd = wp.isTakeoff
                ? MavCmd.MAV_CMD_NAV_TAKEOFF
                : MavCmd.MAV_CMD_NAV_WAYPOINT;

        return MissionItemInt.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .seq((short) seq)
                .frame(EnumValue.of(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT))
                .command(EnumValue.of(cmd))
                .current((short) (seq == 0 ? 1 : 0))
                .autocontinue((short) 1)
                .param1(0f)
                .param2(1f)
                .param3(0f)
                .param4(Float.NaN)
                .x(latE7)
                .y(lonE7)
                .z(wp.altM)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .build();
    }

    // Waits for PX4 to request the next mission item during upload.
    private int waitForMissionRequest(long timeoutMs, int missionSize) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object payload = missionProtocolQueue.poll();
            if (payload == null) {
                sleepQuiet(20);
                continue;
            }

            if (payload instanceof MissionRequestInt) {
                int seq = ((MissionRequestInt) payload).seq();
                if (seq >= 0 && seq < missionSize) return seq;
            }
            if (payload instanceof MissionRequest) {
                int seq = ((MissionRequest) payload).seq();
                if (seq >= 0 && seq < missionSize) return seq;
            }
            if (payload instanceof MissionAck) {
                MissionAck ack = (MissionAck) payload;
                Object entry = ack.type().entry();
                if (entry == MavMissionResult.MAV_MISSION_ACCEPTED) {
                    continue;
                }
                throw new IOException("Mission upload ended early with ACK: " + (entry != null ? entry : ack.type().value()));
            }
        }
        throw new IOException("Timed out waiting for MISSION_REQUEST(_INT).");
    }

    // Waits for the final mission ACK after all mission items are sent.
    private void waitForMissionAck(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object payload = missionProtocolQueue.poll();
            if (payload == null) {
                sleepQuiet(20);
                continue;
            }

            if (payload instanceof MissionAck) {
                MissionAck ack = (MissionAck) payload;
                Object entry = ack.type().entry();
                if (entry == MavMissionResult.MAV_MISSION_ACCEPTED) return;
                throw new IOException("Mission rejected by PX4: " + (entry != null ? entry : ack.type().value()));
            }
        }
        throw new IOException("Timed out waiting for MISSION_ACK.");
    }

    // Sends all mission items without waiting for per-item requests as a fallback path.
    private void sendMissionItemsBulk(List<MissionWp> missionSnapshot) throws IOException {
        for (int seq = 0; seq < missionSnapshot.size(); seq++) {
            MissionWp wp = missionSnapshot.get(seq);
            MissionItemInt item = buildMissionItem(wp, seq);
            sendMavlink(item);
            sleepQuiet(50);
        }
    }

    // Sleeps without propagating interruption errors into the control flow.
    private void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // Finds a generated MAVLink payload class by checking the known dialect packages.
    private Class<?> resolvePayloadClass(String msgName) {
        String className = toClassName(msgName);
        for (String pkg : DIALECT_PACKAGES) {
            try {
                return Class.forName(pkg + "." + className);
            } catch (ClassNotFoundException ignored) { }
        }
        return null;
    }

    // Converts MAVLink snake case names to the Java class name expected by DroneFleet.
    private String toClassName(String msgName) {
        String lower = msgName.toLowerCase(Locale.ROOT);
        String[] parts = lower.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    // Builds non-command MAVLink payloads generically by reflecting over builder fields.
    private Object buildGenericPayload(String msgName, String[] params) throws Exception {
        Class<?> payloadClass = resolvePayloadClass(msgName);
        if (payloadClass == null) throw new Exception("Unknown MAVLink message: " + msgName);

        Method builderFactory = payloadClass.getMethod("builder");
        Object builder = builderFactory.invoke(null);
        Class<?> builderClass = builder.getClass();

        Field[] all = payloadClass.getDeclaredFields();
        List<Field> fields = new ArrayList<>();
        for (Field f : all) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod)) continue;
            if (f.isSynthetic()) continue;
            fields.add(f);
        }

        Method[] methods = builderClass.getMethods();

        for (int i = 0; i < params.length && i < fields.size(); i++) {
            String fieldName = fields.get(i).getName();
            String rawVal = params[i];

            Method setter = null;
            for (Method m : methods) {
                if (!m.getName().equals(fieldName)) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> candidateType = m.getParameterTypes()[0];
                if (candidateType.isArray() || java.util.Collection.class.isAssignableFrom(candidateType)) continue;
                setter = m;
                break;
            }

            if (setter == null) continue;

            Class<?> t = setter.getParameterTypes()[0];
            Object converted = convertArg(rawVal, t);
            setter.invoke(builder, converted);
        }

        Method build = builderClass.getMethod("build");
        return build.invoke(builder);
    }

    // Converts one raw string argument into the builder field type expected by DroneFleet.
    private Object convertArg(String raw, Class<?> t) throws Exception {
        if (t == String.class) return raw;
        if (t == int.class || t == Integer.class) return Integer.parseInt(raw);
        if (t == long.class || t == Long.class) return Long.parseLong(raw);
        if (t == short.class || t == Short.class) return Short.parseShort(raw);
        if (t == byte.class || t == Byte.class) return Byte.parseByte(raw);
        if (t == float.class || t == Float.class) return Float.parseFloat(raw);
        if (t == double.class || t == Double.class) return Double.parseDouble(raw);
        if (t == boolean.class || t == Boolean.class) return Boolean.parseBoolean(raw);
        if (t == char.class || t == Character.class) return raw.charAt(0);

        if (EnumValue.class.isAssignableFrom(t)) {
            Type g = t.getGenericSuperclass();
            if (g instanceof ParameterizedType) {
                Type[] ta = ((ParameterizedType) g).getActualTypeArguments();
                if (ta.length == 1 && ta[0] instanceof Class) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) ta[0];
                    try {
                        int numeric = Integer.parseInt(raw);
                        if (enumClass.getSimpleName().endsWith("Typemask")) {
                            return EnumValue.create(numeric);
                        }
                        return EnumValue.create((Class) enumClass, numeric);
                    } catch (NumberFormatException nfe) {
                        @SuppressWarnings("unchecked")
                        Enum<?> e = Enum.valueOf((Class) enumClass, raw);
                        return EnumValue.of(e);
                    }
                }
            }
        }

        if (t.isEnum()) {
            @SuppressWarnings("unchecked")
            Object e = Enum.valueOf((Class<Enum>) t, raw);
            return e;
        }

        try {
            Constructor<?> c = t.getConstructor(String.class);
            return c.newInstance(raw);
        } catch (NoSuchMethodException ignored) { }

        throw new Exception("Unsupported builder argument type: " + t.getName() + " for value " + raw);
    }

    // Dispatches parsed MAVLink commands to the specific send/upload helper (most used commands).
    private void processMavlinkCommand(String text) throws Exception {
        ParsedCommand cmd = parseCommand(text);
        String name = cmd.name;
        String[] params = cmd.params;

        if ("SET_MODE".equals(name)) {
            if (params.length < 3) throw new Exception("SET_MODE requires (targetSystem, baseMode, customMode)");

            int tgtSys = toInt(params[0]);
            int baseMode = toInt(params[1]);
            long customMode = Long.parseLong(params[2].trim());

            io.dronefleet.mavlink.common.SetMode msg = io.dronefleet.mavlink.common.SetMode.builder()
                    .targetSystem((short) tgtSys)
                    .baseMode(EnumValue.create(MavMode.class, baseMode))
                    .customMode(customMode)
                    .build();
            sendMavlink(msg);
            return;
        }

        if ("SET_POSITION_TARGET_LOCAL_NED".equals(name)) {
            if (params.length < 16) {
                throw new Exception("SET_POSITION_TARGET_LOCAL_NED requires 16 params");
            }

            int frameValue = toInt(params[3]);
            SetPositionTargetLocalNed msg = SetPositionTargetLocalNed.builder()
                    .timeBootMs((long) (System.nanoTime() / 1_000_000L))
                    .targetSystem((short) toInt(params[1]))
                    .targetComponent((short) toInt(params[2]))
                    .coordinateFrame(EnumValue.create(MavFrame.class, frameValue))
                    .typeMask(EnumValue.create(toInt(params[4])))
                    .x(toFloat(params[5]))
                    .y(toFloat(params[6]))
                    .z(toFloat(params[7]))
                    .vx(toFloat(params[8]))
                    .vy(toFloat(params[9]))
                    .vz(toFloat(params[10]))
                    .afx(toFloat(params[11]))
                    .afy(toFloat(params[12]))
                    .afz(toFloat(params[13]))
                    .yaw(toFloat(params[14]))
                    .yawRate(toFloat(params[15]))
                    .build();
            sendMavlink(msg);
            return;
        }

        if ("MISSION_ITEM_INT".equals(name)) {
            handleBufferedMissionItemInt(params);
            return;
        }

        if ("MISSION_START".equals(name) || "MAV_CMD_MISSION_START".equals(name)) {
            int first = params.length > 0 ? toInt(params[0]) : 0;
            int last = params.length > 1 ? toInt(params[1]) : -1;
            startMissionUpload(first, last);
            return;
        }

        if ("MISSION_CLEAR_ALL".equals(name)) {
            clearMissionBuffer();
            return;
        }

        if ("PARAM_REQUEST_READ".equals(name)) {
            if (params.length < 4) {
                throw new Exception("PARAM_REQUEST_READ requires (targetSystem, targetComponent, paramId, paramIndex)");
            }

            ParamRequestRead msg = ParamRequestRead.builder()
                    .targetSystem(toInt(params[0]))
                    .targetComponent(toInt(params[1]))
                    .paramId(params[2])
                    .paramIndex(toInt(params[3]))
                    .build();
            sendMavlink(msg);
            return;
        }

        if ("PARAM_SET".equals(name)) {
            if (params.length < 5) {
                throw new Exception("PARAM_SET requires (targetSystem, targetComponent, paramId, paramValue, paramType)");
            }

            EnumValue<MavParamType> paramType;
            try {
                paramType = EnumValue.of(MavParamType.valueOf(params[4]));
            } catch (IllegalArgumentException ex) {
                paramType = EnumValue.create(MavParamType.class, toInt(params[4]));
            }

            ParamSet msg = ParamSet.builder()
                    .targetSystem(toInt(params[0]))
                    .targetComponent(toInt(params[1]))
                    .paramId(params[2])
                    .paramValue(toFloat(params[3]))
                    .paramType(paramType)
                    .build();
            sendMavlink(msg);
            return;
        }

        if (name.startsWith("MAV_CMD_")) {
            MavCmd command = resolveMavCmd(name);
            if (shouldUseCommandInt(command, params)) sendCommandInt(command, params);
            else sendCommandLong(command, params);
            return;
        }

        Object payload = buildGenericPayload(name, params);
        sendMavlink(payload);
    }
}
