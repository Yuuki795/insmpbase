package com.yuuki795.basePlugin.sync;

import com.yuuki795.basePlugin.api.WhitelistEntry;
import com.yuuki795.basePlugin.config.SyncConfig;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GroupSyncService {

    private final LuckPerms luckPerms;
    private final Supplier<SyncConfig> config;
    private final Logger logger;

    public GroupSyncService(LuckPerms luckPerms, Supplier<SyncConfig> config, Logger logger) {
        this.luckPerms = luckPerms;
        this.config = config;
        this.logger = logger;
    }

    public CompletableFuture<SyncResult> apply(UUID uuid, String username, WhitelistEntry entry) {
        SyncConfig cfg = config.get();
        String role = entry == null ? null : entry.role();
        String target;
        SyncResult.Status successStatus;

        if (entry == null) {
            target = cfg.unregisteredGroup();
            successStatus = target == null ? SyncResult.Status.NOT_REGISTERED : SyncResult.Status.APPLIED;
            if (target == null && !cfg.removeGroupsWhenUnregistered()) {
                return CompletableFuture.completedFuture(SyncResult.of(SyncResult.Status.NOT_REGISTERED, null, null));
            }
        } else {
            target = cfg.groupForRole(role);
            if (target == null) target = cfg.fallbackGroup();
            successStatus = SyncResult.Status.APPLIED;
            if (target == null) {
                logger.warning("No group mapping for " + cfg.roleField() + "='" + role + "' (player " + username
                        + "). Add it under sync.groups in config.yml, or set sync.fallback-group.");
                return CompletableFuture.completedFuture(SyncResult.of(SyncResult.Status.NO_MAPPING, null, role));
            }
        }

        final String finalTarget = target;
        final String finalRole = role;
        final SyncResult.Status finalStatus = successStatus;
        final Set<String> managed = cfg.managedGroups();
        final boolean setPrimary = cfg.setPrimaryGroup();

        final boolean strip = cfg.exclusive() || (finalTarget == null && cfg.removeGroupsWhenUnregistered());

        CompletableFuture<Boolean> groupReady = finalTarget == null
                ? CompletableFuture.completedFuture(Boolean.TRUE)
                : luckPerms.getGroupManager().loadGroup(finalTarget).thenApply(Optional::isPresent);

        return groupReady
                .thenCompose(exists -> {
                    if (!exists) {
                        logger.warning("LuckPerms has no group named '" + finalTarget + "' - create it with "
                                + "/lp creategroup " + finalTarget + " or fix the mapping in config.yml.");
                        return CompletableFuture.completedFuture(
                                SyncResult.of(SyncResult.Status.GROUP_MISSING, finalTarget, finalRole));
                    }
                    return luckPerms.getUserManager()
                            .modifyUser(uuid, user -> mutate(user, finalTarget, managed, strip))
                            // Second pass: LuckPerms only accepts a primary group the user already
                            // inherits, and that is only true once the first pass has been saved.
                            .thenCompose(ignored -> setPrimary && finalTarget != null
                                    ? luckPerms.getUserManager().modifyUser(uuid, user -> applyPrimary(user, finalTarget))
                                    : CompletableFuture.completedFuture(null))
                            .thenApply(ignored -> SyncResult.of(finalStatus, finalTarget, finalRole));
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    logger.log(Level.SEVERE, "Failed to sync groups for " + username, cause);
                    return new SyncResult(SyncResult.Status.ERROR, finalTarget, finalRole, String.valueOf(cause));
                });
    }

    private void mutate(User user, String target, Set<String> managed, boolean strip) {
        String targetKey = target == null ? null : target.toLowerCase(Locale.ROOT);
        if (strip) {
            user.data().clear(NodeType.INHERITANCE.predicate(node -> {
                String group = node.getGroupName().toLowerCase(Locale.ROOT);
                return managed.contains(group) && !group.equals(targetKey);
            }));
        }
        if (target != null) {
            Group group = luckPerms.getGroupManager().getGroup(target);
            user.data().add(group == null
                    ? InheritanceNode.builder(target).build()
                    : InheritanceNode.builder(group).build());
        }
    }

    private void applyPrimary(User user, String target) {
        DataMutateResult result = user.setPrimaryGroup(target);
        if (result != DataMutateResult.SUCCESS && result != DataMutateResult.FAIL_ALREADY_HAS) {
            logger.warning("LuckPerms refused to set the primary group of " + user.getUsername()
                    + " to  + target + : " + result);
        }
    }
}
