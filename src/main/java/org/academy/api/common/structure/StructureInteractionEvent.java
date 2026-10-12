package org.academy.api.common.structure;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Server-side protection hook for the native empty-hand lever adapter. The actor remains
 * in the host world. Cancel to deny. This is not a vanilla PlayerInteractEvent substitute
 * for arbitrary blocks; hit/context coordinates explicitly identify the structure space.
 */
public final class StructureInteractionEvent extends Event implements ICancellableEvent {
    private final ServerPlayer actor;
    private final StructureHit hit;
    private final SpaceContext context;

    public StructureInteractionEvent(ServerPlayer actor, StructureHit hit, SpaceContext context) {
        this.actor = actor;
        this.hit = hit;
        this.context = context;
    }

    public ServerPlayer actor() { return actor; }
    public StructureHit hit() { return hit; }
    public SpaceContext context() { return context; }
}
