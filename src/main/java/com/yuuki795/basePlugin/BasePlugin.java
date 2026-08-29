package com.yuuki795.basePlugin;

import com.yuuki795.basePlugin.api.WhitelistCache;
import com.yuuki795.basePlugin.api.WhitelistClient;
import com.yuuki795.basePlugin.command.InsmpCommand;
import com.yuuki795.basePlugin.config.SyncConfig;
import com.yuuki795.basePlugin.listener.JoinListener;
import com.yuuki795.basePlugin.sync.GroupSyncService;
import com.yuuki795.basePlugin.sync.PlayerSync;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class BasePlugin extends JavaPlugin {

    private volatile SyncConfig syncConfig;
    private WhitelistCache cache;
    private PlayerSync playerSync;
    private ScheduledTask refreshTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        syncConfig = SyncConfig.load(getConfig());

        LuckPerms luckPerms;
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            getLogger().severe("LuckPerms is not available - disabling. Install LuckPerms and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        cache = new WhitelistCache(new WhitelistClient(), this::syncConfig, getLogger());
        GroupSyncService groupSync = new GroupSyncService(luckPerms, this::syncConfig, getLogger());
        playerSync = new PlayerSync(this, cache, groupSync, this::syncConfig);

        getServer().getPluginManager().registerEvents(new JoinListener(playerSync, this::syncConfig), this);

        PluginCommand command = getCommand("insmp");
        if (command != null) {
            InsmpCommand executor = new InsmpCommand(this, playerSync, cache);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        refreshCacheAsync();
        scheduleRefresh();

        if (syncConfig.apiToken() == null || syncConfig.apiToken().isBlank()) {
            getLogger().warning("No API token set. Put one in config.yml under api.token, "
                    + "or set the INSMP_API_TOKEN environment variable.");
        }
        getLogger().info("Whitelist sync ready: " + syncConfig.groups().size() + " role -> group mapping(s) on '"
                + syncConfig.roleField() + "'.");
    }

    @Override
    public void onDisable() {
        cancelRefresh();
    }


    public CompletableFuture<Void> refreshCacheAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        getServer().getAsyncScheduler().runNow(this, task -> {
            cache.refresh();
            future.complete(null);
        });
        return future;
    }

    public SyncConfig syncConfig() {
        return syncConfig;
    }


    public void reloadSyncConfig() {
        reloadConfig();
        syncConfig = SyncConfig.load(getConfig());
        cancelRefresh();
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        long intervalMillis = syncConfig.refreshIntervalMillis();
        if (intervalMillis <= 0L) return;
        refreshTask = getServer().getAsyncScheduler().runAtFixedRate(
                this, task -> cache.refresh(), intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void cancelRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }
}
