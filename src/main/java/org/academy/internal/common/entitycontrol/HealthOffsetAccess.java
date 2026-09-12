package org.academy.internal.common.entitycontrol;

import org.jspecify.annotations.Nullable;

/** Instance storage bridge; respawn never inherits another instance's state. */
public interface HealthOffsetAccess {
    @Nullable HealthOffsetState academy$getHealthOffset();
    void academy$setHealthOffset(@Nullable HealthOffsetState state);
    void academy$syncHealthOffset(float ceiling);
}
