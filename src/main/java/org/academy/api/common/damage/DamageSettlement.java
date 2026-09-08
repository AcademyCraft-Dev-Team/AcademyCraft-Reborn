package org.academy.api.common.damage;

/** Supported Academy damage routes. No route retries a blocked hit as another damage type. */
public enum DamageSettlement {
    STANDARD,
    DIRECT,
    TRUE_HEALTH
}
