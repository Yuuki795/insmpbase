package com.yuuki795.basePlugin.sync;

import com.yuuki795.basePlugin.api.WhitelistCache;
import com.yuuki795.basePlugin.api.WhitelistEntry;
import com.yuuki795.basePlugin.config.SyncConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class PlayerSync {

    private final Plugin plugin;
    private final WhitelistCache cache;
    private final GroupSyncService groupSync;
    private final Supplier<SyncConfig> config;

    public PlayerSync(Plugin plugin, WhitelistCache cache, GroupSyncService groupSync, Supplier<SyncConfig> config) {
        this.plugin = plugin;
        this.cache = cache;
        this.groupSync = groupSync;
        this.config = config;
    }

    public CompletableFuture<SyncResult> sync(Player player, Refresh refresh) {
        return syncOffline(player.getUniqueId(), player.getName(), refresh)
                .thenApply(result -> {
                    followUp(player, result);
                    return result;
                });
    }

    public CompletableFuture<SyncResult> syncOffline(UUID uuid, String username, Refresh refresh) {
        CompletableFuture<SyncResult> future = new CompletableFuture<>();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                SyncConfig cfg = config.get();
                switch (refresh) {
                    case FORCE -> cache.refresh();
                    case IF_STALE -> cache.ensureFresh(cfg.maxAgeOnJoinMillis());
                    case NONE -> { /* caller already refreshed */ }
                }

                Optional<WhitelistEntry> entry = cache.find(uuid, username);
                if (cfg.debug()) {
                    plugin.getLogger().info("Lookup for " + username + " (" + uuid + "): "
                            + entry.map(e -> cfg.roleField() + "=" + e.role()).orElse("no API record"));
                }

                groupSync.apply(uuid, username, entry.orElse(null))
                        .whenComplete((result, throwable) -> future.complete(throwable == null
                                ? result
                                : new SyncResult(SyncResult.Status.ERROR, null, null, String.valueOf(throwable))));
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Sync failed for " + username + ": " + e);
                future.complete(new SyncResult(SyncResult.Status.ERROR, null, null, String.valueOf(e)));
            }
        });
        return future;
    }

    private void followUp(Player player, SyncResult result) {
        SyncConfig cfg = config.get();
        boolean kick = result.status() == SyncResult.Status.NOT_REGISTERED && cfg.kickIfNotRegistered();
        boolean notify = result.applied() && cfg.notifyPlayer();
        if (!kick && !notify) return;

        // Entity scheduler: runs on whichever thread owns the player, and is skipped if they left.
        player.getScheduler().run(plugin, task -> {
            if (kick) {
                player.kick(deserialize(cfg.kickMessage(), result));
            } else {
                player.sendMessage(deserialize(cfg.notifyMessage(), result));
            }
        }, null);
    }

    private static Component deserialize(String template, SyncResult result) {
        String text = template
                .replace("<group>", result.group() == null ? "" : result.group())
                .replace("<role>", result.role() == null ? "" : result.role());
        return MiniMessage.miniMessage().deserialize(text);
    }
}
