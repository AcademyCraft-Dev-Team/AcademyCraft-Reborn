package org.academy.api.common.structure;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.Objects;

/**
 * A native structure placement proposal, dispatched before any item is reserved or block written.
 * The real actor stays in the host world; target/before/proposedState use the local structure grid.
 * Cancel to deny. The runtime write guard is held during the callback, so public structure writes,
 * pose changes, saves and unloads are refused. This is not a vanilla EntityPlaceEvent substitute.
 */
public final class StructurePlacementEvent extends Event implements ICancellableEvent {
    private final ServerPlayer actor;
    private final StructureHit hit;
    private final SpaceContext context;
    private final InteractionHand hand;
    private final SpaceBlockRef target;
    private final StructureBlockData before;
    private final BlockState proposedState;
    private final ItemStack item;

    public StructurePlacementEvent(ServerPlayer actor, StructureHit hit, SpaceContext context,
                                   InteractionHand hand, SpaceBlockRef target, StructureBlockData before,
                                   BlockState proposedState, ItemStack item) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.hit = Objects.requireNonNull(hit, "hit");
        this.context = Objects.requireNonNull(context, "context");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.target = Objects.requireNonNull(target, "target");
        this.before = Objects.requireNonNull(before, "before");
        this.proposedState = Objects.requireNonNull(proposedState, "proposedState");
        this.item = Objects.requireNonNull(item, "item").copyWithCount(1);
    }

    public ServerPlayer actor() { return actor; }
    public StructureHit hit() { return hit; }
    public SpaceContext context() { return context; }
    public InteractionHand hand() { return hand; }
    public SpaceBlockRef target() { return target; }
    public StructureBlockData before() { return before; }
    public BlockState proposedState() { return proposedState; }

    /** A detached single-item preview, never the actor's inventory stack or the reserved item. */
    public ItemStack item() { return item.copy(); }
}
