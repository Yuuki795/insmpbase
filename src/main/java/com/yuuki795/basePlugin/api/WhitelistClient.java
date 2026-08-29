package com.yuuki795.basePlugin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yuuki795.basePlugin.config.SyncConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public final class WhitelistClient {

    private static final List<String> ARRAY_KEYS = List.of("data", "players", "whitelist", "results", "entries", "items");

    private final HttpClient http;

    public WhitelistClient() {
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static final class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public List<WhitelistEntry> fetchAll(SyncConfig config) throws ApiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiUrl()))
                .timeout(config.timeout())
                .header("Accept", "application/json")
                .header("User-Agent", "insmpbase-whitelist-sync")
                .GET();

        if (config.apiToken() != null && !config.apiToken().isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiToken());
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ApiException("could not reach " + config.apiUrl() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("request interrupted", e);
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ApiException("API rejected the token (HTTP " + response.statusCode() + ") - check api.token in config.yml");
        }
        if (response.statusCode() / 100 != 2) {
            throw new ApiException("API returned HTTP " + response.statusCode());
        }

        return parse(response.body(), config.roleField());
    }

    List<WhitelistEntry> parse(String body, String roleField) throws ApiException {
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            throw new ApiException("API returned a body that is not valid JSON", e);
        }

        JsonArray array = extractArray(root);
        if (array == null) {
            throw new ApiException("could not find a list of players in the API response");
        }

        List<WhitelistEntry> entries = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                entries.add(WhitelistEntry.from(element.getAsJsonObject(), roleField));
            }
        }
        return entries;
    }

    private static JsonArray extractArray(JsonElement root) {
        if (root == null || root.isJsonNull()) return null;
        if (root.isJsonArray()) return root.getAsJsonArray();
        if (!root.isJsonObject()) return null;

        JsonObject object = root.getAsJsonObject();
        for (String key : ARRAY_KEYS) {
            JsonElement candidate = object.get(key);
            if (candidate != null && candidate.isJsonArray()) return candidate.getAsJsonArray();
        }
        if (object.has("minecraft_uuid") || object.has("minecraft_username")) {
            JsonArray single = new JsonArray();
            single.add(object);
            return single;
        }
        for (Map.Entry<String, JsonElement> member : object.entrySet()) {
            if (member.getValue().isJsonArray()) return member.getValue().getAsJsonArray();
        }
        return null;
    }
}
