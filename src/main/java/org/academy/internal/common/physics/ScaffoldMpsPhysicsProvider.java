package org.academy.internal.common.physics;

import org.academy.api.common.physics.PhysicsProvider;
import org.academy.api.common.physics.PhysicsResult;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Metadata only. This class must not initialize FFM, JNI, an arena or a native world. */
public final class ScaffoldMpsPhysicsProvider implements PhysicsProvider {
    public static final String ID = "academy:mps_candidate";
    private static final Descriptor DESCRIPTOR = new Descriptor(
            new Identity(ID, "mps", "0.1.4", "2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee", 0),
            Readiness.SCAFFOLD_UNAVAILABLE, Set.of(), EnumSet.allOf(Capability.class),
            "mps_adapter_unimplemented;planned_capabilities_are_not_verified");

    @Override public Descriptor descriptor() { return DESCRIPTOR; }

    @Override public PhysicsResult<World> openWorld(WorldRequest request) {
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-ENGINE-01] 锁定 MPS/Rapier/Parry 与 ABI 后实现隔离适配；当前不得加载库或分配世界。
        return PhysicsResult.rejected(PhysicsResult.Status.UNSUPPORTED, "mps_adapter_unimplemented");
    }
}
