package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.api.common.structure.SpaceBlockRef;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Additional veto for an explicitly authorized launch; never substitutes a fake player. */
public final class StructureLaunchBreakEvent extends Event implements ICancellableEvent {
    private final ServerLevel host;
    private final StructureLaunches.Grant grant;
    private final @Nullable Entity actor;
    private final BlockPos position;
    private final BlockState state;
    private final Optional<SpaceBlockRef> cell;

    public StructureLaunchBreakEvent(ServerLevel host, StructureLaunches.Grant grant, @Nullable Entity actor,
                                     BlockPos position, BlockState state, Optional<SpaceBlockRef> cell) {
        this.host = host; this.grant = grant; this.actor = actor;
        this.position = position.immutable(); this.state = state; this.cell = cell;
    }
    public ServerLevel host() { return host; }
    public StructureLaunches.Grant grant() { return grant; }
    public @Nullable Entity actor() { return actor; }
    public BlockPos position() { return position; }
    public BlockState state() { return state; }
    public Optional<SpaceBlockRef> cell() { return cell; }
}
