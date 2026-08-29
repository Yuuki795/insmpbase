package com.yuuki795.basePlugin.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

public record WhitelistEntry(
        UUID uuid,
        String username,
        String xuid,
        String accountType,
        String role,
        JsonObject raw
) {

    public boolean bedrock() {
        return "bedrock".equalsIgnoreCase(accountType);
    }

    public static WhitelistEntry from(JsonObject json, String roleField) {
        return new WhitelistEntry(
                parseUuid(string(json, "minecraft_uuid")),
                string(json, "minecraft_username"),
                string(json, "minecraft_xuid"),
                string(json, "minecraft_account_type"),
                string(json, roleField),
                json
        );
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String string(JsonObject json, String key) {
        if (json == null || key == null) return null;
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return null;
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }
}
