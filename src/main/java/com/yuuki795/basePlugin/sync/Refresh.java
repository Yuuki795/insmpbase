package com.yuuki795.basePlugin.sync;

/** How hard a sync should try to get up-to-date data before looking a player up. */
public enum Refresh {
    /** Always pull from the API first. */
    FORCE,
    /** Pull only if the cache is older than {@code cache.max-age-on-join-seconds}. */
    IF_STALE,
    /** Use whatever is cached; the caller has already refreshed. */
    NONE
}
