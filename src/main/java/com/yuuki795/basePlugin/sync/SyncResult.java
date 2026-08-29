package com.yuuki795.basePlugin.sync;

/**
 * Outcome of a single group sync.
 *
 * @param group the LuckPerms group that was applied, or null when nothing was applied
 * @param role  the raw role value the API returned, for logging
 */
public record SyncResult(Status status, String group, String role, String detail) {

    public enum Status {
        /** The player now sits in the mapped group. */
        APPLIED,
        /** The player is in the API but their role has no mapping and no fallback is configured. */
        NO_MAPPING,
        /** The player is not in the API at all. */
        NOT_REGISTERED,
        /** The mapping points at a group that does not exist in LuckPerms. */
        GROUP_MISSING,
        /** LuckPerms threw while applying the change. */
        ERROR
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    static SyncResult of(Status status, String group, String role) {
        return new SyncResult(status, group, role, null);
    }
}
