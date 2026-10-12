package org.academy.api.common.structure;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Server-side protection hook for native empty-hand block adapters. The actor remains
 * in the host world. Cancel to deny. This is not a vanilla PlayerInteractEvent substitute
 * for arbitrary blocks; hit/context coordinates explicitly identify the structure space.
 */
public final class StructureInteractionEvent extends Event implements ICancellableEvent {
    public enum Action { USE_LEVER, OPEN_CONTAINER }
    private final ServerPlayer actor;
    private final StructureHit hit;
    private final SpaceContext context;
    private final Action action;

    public StructureInteractionEvent(ServerPlayer actor, StructureHit hit, SpaceContext context) {
        this(actor, hit, context, Action.USE_LEVER);
    }

    public StructureInteractionEvent(ServerPlayer actor, StructureHit hit, SpaceContext context, Action action) {
        this.actor = actor;
        this.hit = hit;
        this.context = context;
        this.action = java.util.Objects.requireNonNull(action, "action");
    }

    public ServerPlayer actor() { return actor; }
    public StructureHit hit() { return hit; }
    public SpaceContext context() { return context; }
    public Action action() { return action; }
}
