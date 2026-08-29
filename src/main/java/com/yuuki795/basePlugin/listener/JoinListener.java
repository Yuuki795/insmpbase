package com.yuuki795.basePlugin.listener;

import com.yuuki795.basePlugin.config.SyncConfig;
import com.yuuki795.basePlugin.sync.PlayerSync;
import com.yuuki795.basePlugin.sync.Refresh;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.function.Supplier;

public final class JoinListener implements Listener {

    private final PlayerSync playerSync;
    private final Supplier<SyncConfig> config;

    public JoinListener(PlayerSync playerSync, Supplier<SyncConfig> config) {
        this.playerSync = playerSync;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.get().syncOnJoin()) return;
        // Fire and forget: the sync itself hops to an async thread immediately.
        playerSync.sync(event.getPlayer(), Refresh.IF_STALE);
    }
}
