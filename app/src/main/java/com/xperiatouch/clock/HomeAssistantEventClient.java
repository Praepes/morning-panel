package com.xperiatouch.clock;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal RFC 6455 client for Home Assistant's authenticated event WebSocket. */
final class HomeAssistantEventClient {
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    static final String ACTION_STATE_CHANGED = "com.xperiatouch.clock.HA_STATE_CHANGED";
    static final String EXTRA_STATE = "state";
    private final Context context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger messageId = new AtomicInteger(10);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Socket socket;

    HomeAssistantEventClient(Context context) { this.context = context.getApplicationContext(); }

    void start() {
        if (!running.compareAndSet(false, true)) return;
        worker.execute(this::connectionLoop);
    }

    void reconnect() {
        Socket current = socket;
        if (current != null) try { current.close(); } catch (Exception ignored) { }
    }

    void stop() {
        running.set(false);
        reconnect();
        worker.shutdownNow();
    }

    private void connectionLoop() {
        while (running.get()) {
            if (AppPrefs.haUrl(context).isEmpty() || !SecretStore.hasHomeAssistantToken(context)) {
                pause(5000);
                continue;
            }
            try { connectAndListen(); }
            catch (Exception error) { android.util.Log.w("XperiaTouchHA", "Home Assistant event connection failed", error); }
            finally {
                Socket current = socket;
                socket = null;
                if (current != null) try { current.close(); } catch (Exception ignored) { }
            }
            pause(3000);
        }
    }

    private void connectAndListen() throws Exception {
        String base = AppPrefs.haUrl(context).trim();
        URI server = new URI(base);
        boolean tls = "https".equalsIgnoreCase(server.getScheme());
        if (!tls && !"http".equalsIgnoreCase(server.getScheme())) throw new IllegalArgumentException("Unsupported HA URL scheme");
        new HomeAssistantClient(base, SecretStore.homeAssistantToken(context), AppPrefs.haAllowHttp(context)).validateForSettings();
        int port = server.getPort() >= 0 ? server.getPort() : (tls ? 443 : 80);
        String path = server.getRawPath() == null ? "" : server.getRawPath();
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        path += "/api/websocket";
        Socket connection = tls ? javax.net.ssl.SSLSocketFactory.getDefault().createSocket() : new Socket();
        socket = connection;
        connection.connect(new InetSocketAddress(server.getHost(), port), 9000);
        connection.setSoTimeout(0);
        if (connection instanceof javax.net.ssl.SSLSocket) {
            javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) connection;
            javax.net.ssl.SSLParameters parameters = ssl.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(parameters);
            ssl.startHandshake();
        }
        InputStream input = new BufferedInputStream(connection.getInputStream());
        OutputStream output = connection.getOutputStream();
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        String hostName = server.getHost().contains(":") ? "[" + server.getHost() + "]" : server.getHost();
        String host = hostName + (server.getPort() < 0 ? "" : ":" + server.getPort());
        String request = "GET " + path + " HTTP/1.1\r\nHost: " + host
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key
                + "\r\nSec-WebSocket-Version: 13\r\n\r\n";
        output.write(request.getBytes(StandardCharsets.US_ASCII));
        output.flush();
        String headers = readHeaders(input);
        String expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + WS_GUID).getBytes(StandardCharsets.US_ASCII)));
        if (!headers.startsWith("HTTP/1.1 101") || !headers.toLowerCase(Locale.ROOT).contains("sec-websocket-accept: " + expected.toLowerCase(Locale.ROOT)))
            throw new IllegalStateException("Home Assistant WebSocket handshake failed");

        JSONObject greeting = new JSONObject(readTextFrame(input, output));
        if (!"auth_required".equals(greeting.optString("type"))) throw new IllegalStateException("Home Assistant did not request WebSocket authentication");
        writeTextFrame(output, new JSONObject().put("type", "auth")
                .put("access_token", SecretStore.homeAssistantToken(context)).toString());
        JSONObject auth = new JSONObject(readTextFrame(input, output));
        if (!"auth_ok".equals(auth.optString("type"))) throw new SecurityException("Home Assistant WebSocket authentication failed");
        writeTextFrame(output, new JSONObject().put("id", 1).put("type", "subscribe_events")
                .put("event_type", "xperia_touch_command").toString());
        writeTextFrame(output, new JSONObject().put("id", 2).put("type", "subscribe_events")
                .put("event_type", "state_changed").toString());
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                int id = messageId.incrementAndGet();
                writeTextFrame(output, new JSONObject().put("id", id).put("type", "ping").toString());
            } catch (Exception error) {
                try { connection.close(); } catch (Exception ignored) { }
            }
        }, 20, 20, TimeUnit.SECONDS);
        try {
          while (running.get() && !connection.isClosed()) {
            JSONObject message = new JSONObject(readTextFrame(input, output));
            if ("pong".equals(message.optString("type"))) continue;
            if (!"event".equals(message.optString("type"))) continue;
            JSONObject event = message.optJSONObject("event");
            if (event == null) continue;
            String eventType = event.optString("event_type");
            if ("state_changed".equals(eventType)) {
                publishEntityState(event.optJSONObject("data"));
                continue;
            }
            if (!"xperia_touch_command".equals(eventType)) continue;
            JSONObject data = event.optJSONObject("data");
            if (data != null) {
                JSONObject result = LocalDeviceControl.handle(context, data);
                try {
                    HomeAssistantClient client = new HomeAssistantClient(AppPrefs.haUrl(context),
                            SecretStore.homeAssistantToken(context), AppPrefs.haAllowHttp(context));
                    client.fireEvent("xperia_touch_command_result", result);
                    client.fireEvent("xperia_touch_update", LocalDeviceStatus.capture(context));
                } catch (Exception error) {
                    android.util.Log.w("XperiaTouchHA", "Unable to report command result and device state", error);
                }
            }
          }
        } finally {
            heartbeat.shutdownNow();
        }
    }

    private void publishEntityState(JSONObject data) {
        if (data == null) return;
        JSONObject state = data.optJSONObject("new_state");
        if (state == null) return;
        String entityId = state.optString("entity_id", "");
        if (entityId.isEmpty() || !isHomeEntity(entityId)) return;
        context.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_STATE, state.toString()));
    }

    private boolean isHomeEntity(String entityId) {
        String[] configured = AppPrefs.haHomeEntities(context).split("[\\r\\n,]+");
        for (String entity : configured) if (entityId.equals(entity.trim())) return true;
        return false;
    }

    private static String readHeaders(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (bytes.size() < 8192) {
            int value = input.read();
            if (value < 0) throw new java.io.EOFException("WebSocket closed during handshake");
            bytes.write(value);
            int[] marker = {13, 10, 13, 10};
            if (value == marker[matched]) matched++; else matched = value == marker[0] ? 1 : 0;
            if (matched == marker.length) return bytes.toString("US-ASCII");
        }
        throw new IllegalStateException("Home Assistant WebSocket headers are too large");
    }

    private static String readTextFrame(InputStream input, OutputStream output) throws Exception {
        ByteArrayOutputStream fragments = new ByteArrayOutputStream();
        int messageOpcode = -1;
        while (true) {
            int first = input.read();
            int second = input.read();
            if (first < 0 || second < 0) throw new java.io.EOFException("Home Assistant WebSocket disconnected");
            int opcode = first & 0x0f;
            long length = second & 0x7f;
            if (length == 126) length = ((long) readByte(input) << 8) | readByte(input);
            else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) length = (length << 8) | readByte(input);
            }
            if (length > 1024 * 1024) throw new IllegalStateException("Home Assistant WebSocket frame is too large");
            byte[] mask = (second & 0x80) == 0 ? null : readBytes(input, 4);
            byte[] payload = readBytes(input, (int) length);
            if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
            if (opcode == 8) throw new java.io.EOFException("Home Assistant closed WebSocket");
            if (opcode == 9) { writeControlFrame(output, 10, payload); continue; }
            if (opcode == 10) continue;
            if (opcode == 1) messageOpcode = 1;
            if (opcode == 1 || opcode == 0) fragments.write(payload);
            if ((first & 0x80) != 0 && messageOpcode == 1) return fragments.toString("UTF-8");
        }
    }

    private static int readByte(InputStream input) throws Exception {
        int value = input.read();
        if (value < 0) throw new java.io.EOFException("Home Assistant WebSocket disconnected");
        return value;
    }

    private static byte[] readBytes(InputStream input, int count) throws Exception {
        byte[] bytes = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = input.read(bytes, offset, count - offset);
            if (read < 0) throw new java.io.EOFException("Home Assistant WebSocket disconnected");
            offset += read;
        }
        return bytes;
    }

    private static void writeTextFrame(OutputStream output, String text) throws Exception {
        writeControlFrame(output, 1, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeControlFrame(OutputStream output, int opcode, byte[] payload) throws Exception {
        if (payload.length > 65535) throw new IllegalArgumentException("WebSocket client message is too large");
        synchronized (output) {
            SecureRandom random = new SecureRandom();
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            output.write(0x80 | opcode);
            if (payload.length < 126) output.write(0x80 | payload.length);
            else { output.write(0x80 | 126); output.write((payload.length >> 8) & 0xff); output.write(payload.length & 0xff); }
            output.write(mask);
            byte[] encoded = payload.clone();
            for (int i = 0; i < encoded.length; i++) encoded[i] ^= mask[i % 4];
            output.write(encoded);
            output.flush();
        }
    }

    private void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
