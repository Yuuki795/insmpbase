package com.yuuki795.basePlugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record SyncConfig(
        String apiUrl,
        String apiToken,
        Duration timeout,
        long refreshIntervalMillis,
        long maxAgeOnJoinMillis,
        boolean syncOnJoin,
        String roleField,
        Map<String, String> groups,
        String fallbackGroup,
        String unregisteredGroup,
        boolean exclusive,
        boolean setPrimaryGroup,
        boolean removeGroupsWhenUnregistered,
        boolean kickIfNotRegistered,
        String kickMessage,
        boolean notifyPlayer,
        String notifyMessage,
        boolean debug
) {

    public Set<String> managedGroups() {
        Set<String> managed = new HashSet<>();
        for (String group : groups.values()) {
            managed.add(group.toLowerCase(Locale.ROOT));
        }
        if (fallbackGroup != null) managed.add(fallbackGroup.toLowerCase(Locale.ROOT));
        if (unregisteredGroup != null) managed.add(unregisteredGroup.toLowerCase(Locale.ROOT));
        return Set.copyOf(managed);
    }

    public String groupForRole(String role) {
        if (role == null) return null;
        return groups.get(role.trim().toLowerCase(Locale.ROOT));
    }

    public static SyncConfig load(FileConfiguration cfg) {
        String token = cfg.getString("api.token", "");
        if (token == null || token.isBlank()) {
            // Keeps the token out of the repo for anyone who prefers env vars.
            token = System.getenv("INSMP_API_TOKEN");
        }

        Map<String, String> groups = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("sync.groups");
        if (section != null) {
            for (String role : section.getKeys(false)) {
                String group = section.getString(role);
                if (group != null && !group.isBlank()) {
                    groups.put(role.trim().toLowerCase(Locale.ROOT), group.trim());
                }
            }
        }

        return new SyncConfig(
                cfg.getString("api.url", "https://auth.insmp.org/api/whitelist"),
                token == null ? "" : token.trim(),
                Duration.ofSeconds(Math.max(1, cfg.getInt("api.timeout-seconds", 10))),
                Math.max(0L, cfg.getLong("cache.refresh-seconds", 300L)) * 1000L,
                Math.max(0L, cfg.getLong("cache.max-age-on-join-seconds", 60L)) * 1000L,
                cfg.getBoolean("sync.on-join", true),
                cfg.getString("sync.role-field", "nation_name"),
                Map.copyOf(groups),
                blankToNull(cfg.getString("sync.fallback-group", "")),
                blankToNull(cfg.getString("sync.unregistered-group", "")),
                cfg.getBoolean("sync.exclusive", true),
                cfg.getBoolean("sync.set-primary-group", true),
                cfg.getBoolean("sync.remove-groups-when-unregistered", false),
                cfg.getBoolean("sync.kick-if-not-registered", false),
                cfg.getString("sync.kick-message", "<red>You are not linked on our Discord yet."),
                cfg.getBoolean("sync.notify-player", false),
                cfg.getString("sync.notify-message", "<gray>Synced your rank: <white><group>"),
                cfg.getBoolean("debug", false)
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
