package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.UUID;

/** Server-thread notification after an entire instant relocation batch has committed. */
public final class StructureTeleportedEvent extends Event {
    private final ServerLevel host;
    private final Entity actor;
    private final List<Relocation> relocations;

    public StructureTeleportedEvent(ServerLevel host, Entity actor, List<Relocation> relocations) {
        this.host = host; this.actor = actor; this.relocations = List.copyOf(relocations);
    }
    public ServerLevel host() { return host; }
    public Entity actor() { return actor; }
    public List<Relocation> relocations() { return relocations; }

    public record Relocation(StructureRef reference, UUID runtimeId, StructurePose sourcePose,
                             StructurePose destinationPose) {}
}
