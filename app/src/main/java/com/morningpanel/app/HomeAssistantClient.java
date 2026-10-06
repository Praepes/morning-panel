package com.morningpanel.app;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class HomeAssistantClient {
    private final String baseUrl;
    private final String token;
    private final boolean allowHttp;

    HomeAssistantClient(String baseUrl, String token, boolean allowHttp) {
        this.baseUrl = trimSlash(baseUrl);
        this.token = token;
        this.allowHttp = allowHttp;
    }

    String ping() throws Exception {
        return request("GET", "/api/", null);
    }

    void validateForSettings() throws Exception { validateEndpoint(); }

    void fireEvent(String eventType, JSONObject eventData) throws Exception {
        if (eventType == null || !eventType.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Invalid Home Assistant event type");
        request("POST", "/api/events/" + eventType, eventData);
    }

    JSONObject state(String entityId) throws Exception {
        validateEntity(entityId);
        return new JSONObject(request("GET", "/api/states/" + entityId, null));
    }

    JSONArray states() throws Exception {
        return new JSONArray(request("GET", "/api/states", null));
    }

    void setSwitch(String entityId, boolean enabled) throws Exception {
        String domain = validateEntity(entityId);
        if (!"switch".equals(domain) && !"light".equals(domain)
                && !"input_boolean".equals(domain) && !"fan".equals(domain))
            throw new IllegalArgumentException("This entity does not support a simple on/off control");
        JSONObject body = new JSONObject().put("entity_id", entityId);
        request("POST", "/api/services/" + domain + "/" + (enabled ? "turn_on" : "turn_off"), body);
    }

    void selectOption(String entityId, String option) throws Exception {
        if (!"select".equals(validateEntity(entityId))) throw new IllegalArgumentException("This entity is not a select option");
        JSONObject body = new JSONObject().put("entity_id", entityId).put("option", option);
        request("POST", "/api/services/select/select_option", body);
    }

    void setBrightness(String entityId, int percent) throws Exception {
        if (!"light".equals(validateEntity(entityId))) throw new IllegalArgumentException("Brightness requires a light entity");
        JSONObject body = new JSONObject().put("entity_id", entityId).put("brightness_pct", Math.max(1, Math.min(100, percent)));
        request("POST", "/api/services/light/turn_on", body);
    }

    private String request(String method, String path, JSONObject body) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new IllegalStateException("Home Assistant token is not configured");
        validateEndpoint();
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String result = stream == null ? "" : read(stream);
            if (code < 200 || code >= 300) {
                if (code == 401) throw new SecurityException("Home Assistant rejected the token (HTTP 401)");
                if (code == 404) throw new IllegalStateException("Home Assistant entity was not found (HTTP 404)");
                throw new IllegalStateException("Home Assistant returned HTTP " + code);
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private void validateEndpoint() throws Exception {
        URI uri = new URI(baseUrl);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Enter a Home Assistant URL without a username, query, or fragment");
        if ("https".equalsIgnoreCase(uri.getScheme())) return;
        if (!"http".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("URL must start with https://");
        if (!allowHttp) throw new SecurityException("HTTP is off. Enable the LAN HTTP option to continue.");
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if ("localhost".equals(host) || "homeassistant".equals(host) || host.endsWith(".local")) return;
        InetAddress address = InetAddress.getByName(host);
        if (!address.isSiteLocalAddress() && !address.isLoopbackAddress() && !address.isLinkLocalAddress())
            throw new SecurityException("Unencrypted HTTP is restricted to a local network address");
    }

    private static String validateEntity(String entityId) {
        if (entityId == null || !entityId.matches("[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Entity ID must look like switch.projector");
        return entityId.substring(0, entityId.indexOf('.'));
    }

    private static String trimSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String read(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
}
