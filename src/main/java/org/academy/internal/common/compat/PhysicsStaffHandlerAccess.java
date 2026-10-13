package org.academy.internal.common.compat;

import java.util.UUID;

/** Read-only, optional-class-free view injected into the fingerprinted staff handler. */
public interface PhysicsStaffHandlerAccess {
    Occupancy academy$staffOccupancy(Object exactSubLevel, UUID subLevelId);

    enum Occupancy { FREE, OCCUPIED, UNAVAILABLE }
}
