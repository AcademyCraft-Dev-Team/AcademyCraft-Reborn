package org.academy.api.common.structure;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.Objects;

/**
 * Cancellable native mining protection hook, before starting or committing a break.
 * The real player remains in the host world. The hit and before value describe a local cell.
 * A runtime guard is held during callbacks; no client progress or inventory copy is trusted.
 */
public final class StructureMiningEvent extends Event implements ICancellableEvent {
    public enum Phase { START, FINISH }

    private final ServerPlayer actor;
    private final StructureHit hit;
    private final SpaceContext context;
    private final Phase phase;
    private final StructureBlockData before;
    private final ItemStack tool;

    public StructureMiningEvent(ServerPlayer actor, StructureHit hit, SpaceContext context,
                                Phase phase, StructureBlockData before, ItemStack tool) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.hit = Objects.requireNonNull(hit, "hit");
        this.context = Objects.requireNonNull(context, "context");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.before = Objects.requireNonNull(before, "before");
        this.tool = Objects.requireNonNull(tool, "tool").copy();
    }

    public ServerPlayer actor() { return actor; }
    public StructureHit hit() { return hit; }
    public SpaceContext context() { return context; }
    public Phase phase() { return phase; }
    public StructureBlockData before() { return before; }
    public ItemStack tool() { return tool.copy(); }
}
