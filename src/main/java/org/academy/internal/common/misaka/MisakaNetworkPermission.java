package org.academy.internal.common.misaka;

/**
 * Network-side permissions for Misaka topology governance.
 * {@link #ADMIN} implies all except {@link #OWNER}; {@link #OWNER} implies all.
 */
public enum MisakaNetworkPermission {
    ACCESS,
    CONFIGURE,
    MEMBER,
    ENTITY_CONFIG,
    SATELLITE_USE,
    SATELLITE_MANAGE,
    DEVICE_MANAGE,
    DESTROY,
    ADMIN,
    OWNER;

    /** Whether holding {@code this} satisfies a check for {@code required}. */
    public boolean implies(MisakaNetworkPermission required) {
        if (required == null || this == required) {
            return true;
        }
        if (this == OWNER) {
            return true;
        }
        if (this == ADMIN) {
            return required != OWNER;
        }
        return false;
    }

    /** Whether any of {@code held} implies {@code required}. */
    public static boolean anyImplies(Iterable<MisakaNetworkPermission> held, MisakaNetworkPermission required) {
        if (held == null || required == null) {
            return false;
        }
        for (var perm : held) {
            if (perm != null && perm.implies(required)) {
                return true;
            }
        }
        return false;
    }
}
