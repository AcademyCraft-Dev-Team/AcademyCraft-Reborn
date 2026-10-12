package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Explicit local coordinates with a real host-world actor. The item is one reserved material. */
public final class NativeStructurePlaceContext extends BlockPlaceContext {
    private final ServerLevel host;
    private final StructurePose pose;
    private final NativeStructurePlacementDirections directions;
    private BooleanSupplier mayWrite = () -> false;
    private @Nullable BlockState approvedState;
    private boolean writeAttempted;
    private boolean written;
    private @Nullable PlacementSound sound;

    public NativeStructurePlaceContext(ServerLevel storage, ServerPlayer actor, InteractionHand hand,
                                       ItemStack item, StructureHit hit) {
        super(storage, actor, hand, item, new BlockHitResult(hit.localPoint(), hit.localFace(),
                hit.block().localPosition(), hit.inside()));
        host = actor.level();
        pose = hit.pose();
        directions = new NativeStructurePlacementDirections(pose, actor.getLookAngle(),
                Vec3.atLowerCornerOf(actor.getDirection().getUnitVec3i()));
    }

    @Override public Direction getHorizontalDirection() { return directions.horizontal(); }
    @Override public float getRotation() { return directions.rotation(); }
    @Override public Direction getNearestLookingDirection() { return directions.nearest()[0]; }
    @Override public Direction getNearestLookingVerticalDirection() { return directions.vertical(); }
    @Override public Direction[] getNearestLookingDirections() {
        return directions.placementOrder(getClickedFace(), replacingClickedOnBlock());
    }

    /** One authorization check at the actual write, after vanilla's own placement callbacks. */
    void authorizeWrite(BlockState state, BooleanSupplier validator) {
        approvedState = Objects.requireNonNull(state, "state");
        mayWrite = Objects.requireNonNull(validator, "validator");
    }

    /** Called only by the narrowly scoped BlockItem.placeBlock redirect. */
    public boolean writeBlock(Level level, BlockPos position, BlockState state, int flags) {
        if (level != getLevel() || !position.equals(getClickedPos())) {
            throw new IllegalStateException("Native placement attempted a different world or target");
        }
        var validator = mayWrite;
        mayWrite = () -> false;
        if (state != approvedState || !validator.getAsBoolean()) return false;
        var storage = (ServerLevel) level;
        var before = storage.getBlockState(position);
        long revision = NativeStructureChanges.revision(storage);
        writeAttempted = true;
        try {
            boolean changed = storage.setBlock(position, state, flags);
            written |= changed;
            return changed;
        } finally {
            // A callback may throw after LevelChunk wrote its palette but before setBlock returned.
            // That material already belongs to the world, even when the operation is quarantined.
            try {
                written |= storage.getBlockState(position) != before
                        || NativeStructureChanges.revision(storage) != revision;
            } catch (RuntimeException uncertainWrite) {
                written = true;
            }
        }
    }

    public boolean hasWritten() { return written; }
    public boolean hasAttemptedWrite() { return writeAttempted; }

    /** Defer the otherwise inaudible storage sound until placement has completed successfully. */
    public void captureSound(BlockPos position, SoundEvent event, SoundSource source, float volume, float pitch) {
        sound = new PlacementSound(position.immutable(), event, source, volume, pitch);
    }

    void playSound() {
        if (sound == null) return;
        var point = pose.localToWorldPoint(Vec3.atCenterOf(sound.position));
        // This client did not execute a predicted vanilla placement sound.
        host.playSound(null, point.x, point.y, point.z, sound.event, sound.source, sound.volume, sound.pitch);
    }

    private record PlacementSound(BlockPos position, SoundEvent event, SoundSource source, float volume, float pitch) {}
}
