package org.academy.api.common.misaka;

/** How a Misaka compute device counts as "on the player". */
public enum MisakaComputePresence {
    /** Curios accessory slot. */
    WORN,
    /** Inventory stack that reports loaded via {@code isLoaded}. */
    LOADED,
    /** Any inventory/hand stack; ACTUAL spend only while actively using. */
    HELD
}
