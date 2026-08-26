package org.academy.internal.common.ability.accelerator.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;

import java.util.*;

/**
 * Guides program-displaced blocks with the same homing profile as Magnet Manipulation.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AcceleratorBlockDisplacementRuntime {
    private static final int BLOCK_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS;
    private static final int MAX_CONTROL_TICKS = 100;
    private static final double STOP_DISTANCE = 0.45;
    private static final Set<Movement> ACTIVE =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private AcceleratorBlockDisplacementRuntime() {
    }

    static Movement start(
            ServerPlayer player,
            BlockPos source,
            BlockPos destination,
            BlockState state,
            double maximumSpeed
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(state, "state");
        if (!(player.level() instanceof ServerLevel level)
                || !Double.isFinite(maximumSpeed)
                || maximumSpeed <= 0.0
                || !level.getBlockState(source).equals(state)
                || !level.getBlockState(destination).isAir()) {
            throw new IllegalStateException("Controlled block movement became invalid");
        }

        var fallingBlock = FallingBlockEntity.fall(level, source, state);
        if (fallingBlock == null || fallingBlock.isRemoved()) {
            if (level.getBlockState(source).isAir()) {
                level.setBlock(source, state, BLOCK_UPDATE_FLAGS);
            }
            throw new IllegalStateException("Failed to create controlled falling block");
        }
        fallingBlock.setNoGravity(true);
        fallingBlock.setDeltaMovement(Vec3.ZERO);
        fallingBlock.hurtMarked = true;

        var movement = new Movement(
                player,
                level,
                source.immutable(),
                destination.immutable(),
                state,
                fallingBlock,
                maximumSpeed
        );
        ACTIVE.add(movement);
        return movement;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        for (var movement : List.copyOf(ACTIVE)) movement.tick();
    }

    static final class Movement {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final BlockPos source;
        private final BlockPos destination;
        private final BlockState state;
        private final FallingBlockEntity fallingBlock;
        private final double maximumSpeed;
        private int controlledTicks;
        private boolean active = true;
        private boolean placed;

        private Movement(
                ServerPlayer player,
                ServerLevel level,
                BlockPos source,
                BlockPos destination,
                BlockState state,
                FallingBlockEntity fallingBlock,
                double maximumSpeed
        ) {
            this.player = player;
            this.level = level;
            this.source = source;
            this.destination = destination;
            this.state = state;
            this.fallingBlock = fallingBlock;
            this.maximumSpeed = maximumSpeed;
        }

        private void tick() {
            if (!active) return;
            if (player.hasDisconnected()
                    || !player.isAlive()
                    || player.level() != level
                    || !Skills.KINETIC_ENERGY_APPLIED.get().isEnabled(player)
                    || fallingBlock.isRemoved()
                    || !fallingBlock.isAlive()) {
                releaseToGravity();
                return;
            }
            if (++controlledTicks > MAX_CONTROL_TICKS
                    || !level.hasChunkAt(destination)
                    || !level.getWorldBorder().isWithinBounds(destination)) {
                releaseToGravity();
                return;
            }

            var target = new Vec3(
                    destination.getX() + 0.5,
                    destination.getY(),
                    destination.getZ() + 0.5
            );
            var origin = fallingBlock.position();
            var difference = target.subtract(origin);
            if (difference.length() <= STOP_DISTANCE) {
                placeAtDestination();
                return;
            }
            var velocity = MagnetManipulation.calculateControlledBlockVelocity(
                    fallingBlock.getDeltaMovement(),
                    origin,
                    target,
                    difference,
                    maximumSpeed,
                    STOP_DISTANCE
            );
            fallingBlock.setDeltaMovement(velocity);
            fallingBlock.setNoGravity(true);
            fallingBlock.hurtMarked = true;
            fallingBlock.resetFallDistance();
        }

        private void placeAtDestination() {
            if (!level.getBlockState(destination).isAir()
                    || !state.canSurvive(level, destination)
                    || !level.getEntities(fallingBlock, new AABB(destination)).isEmpty()) {
                releaseToGravity();
                return;
            }
            if (!level.setBlock(destination, state, BLOCK_UPDATE_FLAGS)) {
                releaseToGravity();
                return;
            }
            placed = true;
            fallingBlock.discard();
            finish();
        }

        private void releaseToGravity() {
            if (!fallingBlock.isRemoved()) {
                fallingBlock.setNoGravity(false);
                fallingBlock.hurtMarked = true;
            }
            finish();
        }

        private void finish() {
            active = false;
            ACTIVE.remove(this);
        }

        void rollback() {
            finish();
            if (!fallingBlock.isRemoved()) fallingBlock.discard();
            if (placed && level.hasChunkAt(destination)
                    && level.getBlockState(destination).equals(state)) {
                level.setBlock(destination, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
            }
            if (level.hasChunkAt(source) && level.getBlockState(source).isAir()) {
                level.setBlock(source, state, BLOCK_UPDATE_FLAGS);
            }
        }
    }
}
