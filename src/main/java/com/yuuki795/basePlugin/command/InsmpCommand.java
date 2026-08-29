package com.yuuki795.basePlugin.command;

import com.yuuki795.basePlugin.BasePlugin;
import com.yuuki795.basePlugin.api.WhitelistCache;
import com.yuuki795.basePlugin.api.WhitelistEntry;
import com.yuuki795.basePlugin.sync.PlayerSync;
import com.yuuki795.basePlugin.sync.Refresh;
import com.yuuki795.basePlugin.sync.SyncResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class InsmpCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("sync", "reload", "status", "lookup");

    private final BasePlugin plugin;
    private final PlayerSync playerSync;
    private final WhitelistCache cache;

    public InsmpCommand(BasePlugin plugin, PlayerSync playerSync, WhitelistCache cache) {
        this.plugin = plugin;
        this.playerSync = playerSync;
        this.cache = cache;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadSyncConfig();
                info(sender, "Config reloaded. Groups mapped: " + plugin.syncConfig().groups().size());
            }
            case "status" -> status(sender);
            case "sync" -> sync(sender, args);
            case "lookup" -> lookup(sender, args);
            default -> usage(sender, label);
        }
        return true;
    }

    private void status(CommandSender sender) {
        WhitelistCache.Snapshot snapshot = cache.snapshot();
        if (snapshot.fetchedAt() == 0L) {
            info(sender, "No successful API pull yet.");
        } else {
            long seconds = Duration.ofMillis(cache.ageMillis()).toSeconds();
            info(sender, snapshot.byUuid().size() + " record(s) cached, refreshed " + seconds + "s ago.");
        }
        String error = cache.lastError();
        if (error != null) {
            sender.sendMessage(Component.text("[insmp] Last error: " + error, NamedTextColor.RED));
        }
    }

    private void sync(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            info(sender, "Refreshing the whitelist and re-syncing " + online.size() + " player(s)...");
            // One refresh up front, then sync everyone against that snapshot.
            plugin.refreshCacheAsync().thenRun(() -> {
                for (Player player : online) {
                    playerSync.sync(player, Refresh.NONE).thenAccept(result -> report(sender, player.getName(), result));
                }
            });
            return;
        }

        String name = args.length >= 2 ? args[1] : (sender instanceof Player p ? p.getName() : null);
        if (name == null) {
            info(sender, "Usage: /insmp sync <player|all>");
            return;
        }

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            playerSync.sync(online, Refresh.FORCE).thenAccept(result -> report(sender, online.getName(), result));
            return;
        }

        // Offline: refresh first, otherwise someone who linked a minute ago looks unknown.
        plugin.refreshCacheAsync().thenRun(() -> {
            UUID uuid = cache.find(null, name).map(WhitelistEntry::uuid).orElse(null);
            if (uuid == null) {
                info(sender, name + " is not online and has no API record with a usable UUID.");
                return;
            }
            playerSync.syncOffline(uuid, name, Refresh.NONE).thenAccept(result -> report(sender, name, result));
        });
    }

    private void lookup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            info(sender, "Usage: /insmp lookup <player>");
            return;
        }
        String name = args[1];
        Player online = Bukkit.getPlayerExact(name);
        UUID uuid = online == null ? null : online.getUniqueId();
        Optional<WhitelistEntry> entry = cache.find(uuid, name);
        if (entry.isEmpty()) {
            info(sender, "No API record for " + name + ".");
            return;
        }
        WhitelistEntry found = entry.get();
        info(sender, name + " -> " + plugin.syncConfig().roleField() + "=" + found.role()
                + ", group=" + plugin.syncConfig().groupForRole(found.role())
                + ", uuid=" + found.uuid()
                + ", platform=" + found.accountType());
    }

    private void report(CommandSender sender, String name, SyncResult result) {
        String message = switch (result.status()) {
            case APPLIED -> name + " -> " + result.group() + " (" + result.role() + ")";
            case NO_MAPPING -> name + ": role '" + result.role() + "' has no group mapping.";
            case NOT_REGISTERED -> name + ": no record in the API.";
            case GROUP_MISSING -> name + ": group '" + result.group() + "' does not exist in LuckPerms.";
            case ERROR -> name + ": sync failed - " + result.detail();
        };
        NamedTextColor color = result.applied() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        sender.sendMessage(Component.text("[insmp] " + message, color));
    }

    private void usage(CommandSender sender, String label) {
        info(sender, "/" + label + " <sync [player|all] | reload | status | lookup <player>>");
    }

    private void info(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[insmp] " + message, NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("sync") || args[0].equalsIgnoreCase("lookup"))) {
            List<String> names = new ArrayList<>();
            if (args[0].equalsIgnoreCase("sync")) names.add("all");
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
