package org.academy.api.common.ability.vector;

/** Capabilities are independent: observing an effect does not grant control or native casting. */
public enum EffectOperation {
    OBSERVE,
    REDIRECT_EXISTING,
    CONTROL_EXISTING
}
