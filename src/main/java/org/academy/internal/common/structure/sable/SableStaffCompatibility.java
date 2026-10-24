package org.academy.internal.common.structure.sable;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.academy.internal.common.compat.PhysicsStaffHandlerAccess;
import org.academy.internal.common.compat.SableStaffMixinGate;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Cooperative exclusivity with the pinned Suite physics staff; never loads its SavedData. */
public final class SableStaffCompatibility {
    private static final Map<MinecraftServer, Map<ServerLevel, PhysicsStaffHandlerAccess>> HANDLERS = new IdentityHashMap<>();

    private SableStaffCompatibility() {}

    public static Status status(ServerLevel host, ServerSubLevel ship) {
        SableStructureAccess.requireThread(host.getServer());
        Objects.requireNonNull(ship, "ship");
        var container = SubLevelContainer.getContainer(host);
        if (host.getServer().getLevel(host.dimension()) != host || container == null
                || ship.getLevel() != host || ship.isRemoved() || container.getSubLevel(ship.getUniqueId()) != ship) {
            return Status.UNAVAILABLE;
        }
        var gate = SableStaffMixinGate.state();
        if (gate == SableStaffMixinGate.State.ABSENT) return Status.FREE;
        if (gate != SableStaffMixinGate.State.VERIFIED) return Status.UNAVAILABLE;
        var hosts = HANDLERS.get(host.getServer());
        var handler = hosts == null ? null : hosts.get(host);
        if (handler == null) return Status.UNAVAILABLE;
        return switch (handler.academy$staffOccupancy(SableReflection.unwrap(ship), ship.getUniqueId())) {
            case FREE -> Status.FREE;
            case OCCUPIED -> Status.OCCUPIED;
            case UNAVAILABLE -> Status.UNAVAILABLE;
        };
    }

    /** Observes a completed existing get() call; callers must never invoke get() on our behalf. */
    public static void observedHandler(ServerLevel host, Object handler) {
        SableStructureAccess.requireThread(host.getServer());
        if (SableStaffMixinGate.state() != SableStaffMixinGate.State.VERIFIED) return;
        if (!(handler instanceof PhysicsStaffHandlerAccess access)) {
            throw new IllegalStateException("Verified physics staff is missing its occupancy bridge");
        }
        HANDLERS.computeIfAbsent(host.getServer(), ignored -> new IdentityHashMap<>()).put(host, access);
    }

    public static void shutdown(MinecraftServer server) {
        SableStructureAccess.requireThread(server);
        HANDLERS.remove(server);
    }

    public enum Status { FREE, OCCUPIED, UNAVAILABLE }
}
