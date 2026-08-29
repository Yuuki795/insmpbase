package com.yuuki795.basePlugin.api;

import com.yuuki795.basePlugin.config.SyncConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WhitelistCache {

    public record Snapshot(
            Map<UUID, WhitelistEntry> byUuid,
            Map<String, WhitelistEntry> byName,
            Map<String, WhitelistEntry> byXuid,
            long fetchedAt
    ) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), 0L);
        }

        public int size() {
            return byUuid.size() + byName.size();
        }
    }

    private final Object lock = new Object();
    private final WhitelistClient client;
    private final Supplier<SyncConfig> config;
    private final Logger logger;

    private volatile Snapshot snapshot = Snapshot.empty();
    private volatile String lastError;

    public WhitelistCache(WhitelistClient client, Supplier<SyncConfig> config, Logger logger) {
        this.client = client;
        this.config = config;
        this.logger = logger;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public String lastError() {
        return lastError;
    }

    public long ageMillis() {
        long fetchedAt = snapshot.fetchedAt();
        return fetchedAt == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - fetchedAt;
    }

    public boolean ensureFresh(long maxAgeMillis) {
        if (ageMillis() <= maxAgeMillis) return true;
        synchronized (lock) {
            if (ageMillis() <= maxAgeMillis) return true;
            return refreshLocked();
        }
    }

    public boolean refresh() {
        synchronized (lock) {
            return refreshLocked();
        }
    }

    private boolean refreshLocked() {
        SyncConfig cfg = config.get();
        try {
            List<WhitelistEntry> entries = client.fetchAll(cfg);
            snapshot = index(entries);
            lastError = null;
            if (cfg.debug()) {
                logger.info("Whitelist refreshed: " + entries.size() + " record(s) from " + cfg.apiUrl());
            }
            return true;
        } catch (WhitelistClient.ApiException e) {
            lastError = e.getMessage();
            // Keep serving the previous snapshot; a flaky API should not strip anyone's rank.
            logger.log(Level.WARNING, "Whitelist refresh failed: " + e.getMessage());
            return false;
        }
    }

    private static Snapshot index(List<WhitelistEntry> entries) {
        Map<UUID, WhitelistEntry> byUuid = new HashMap<>();
        Map<String, WhitelistEntry> byName = new HashMap<>();
        Map<String, WhitelistEntry> byXuid = new HashMap<>();
        for (WhitelistEntry entry : entries) {
            if (entry.uuid() != null) byUuid.put(entry.uuid(), entry);
            if (entry.username() != null && !entry.username().isBlank()) {
                byName.put(entry.username().toLowerCase(Locale.ROOT), entry);
            }
            if (entry.xuid() != null && !entry.xuid().isBlank()) byXuid.put(entry.xuid(), entry);
        }
        return new Snapshot(Map.copyOf(byUuid), Map.copyOf(byName), Map.copyOf(byXuid), System.currentTimeMillis());
    }

    public Optional<WhitelistEntry> find(UUID uuid, String username) {
        Snapshot current = snapshot;
        WhitelistEntry entry = uuid == null ? null : current.byUuid().get(uuid);
        if (entry == null && username != null) {
            String key = username.toLowerCase(Locale.ROOT);
            entry = current.byName().get(key);
            if (entry == null && key.startsWith(".")) {
                // Floodgate's default username prefix is not part of the linked account name.
                entry = current.byName().get(key.substring(1));
            }
        }
        return Optional.ofNullable(entry);
    }
}
