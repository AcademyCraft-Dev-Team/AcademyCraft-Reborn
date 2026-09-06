package org.academy.api.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.server.storage.SpatialStorageService;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Explicit attribution for ability block destruction, including secondary container/neighbor drops.
 * Block movement and the teleport program's block-teleport node must not enter this scope.
 */
public final class AbilityBlockDrops {
    private static final ThreadLocal<ServerPlayer> BREAKER = new ThreadLocal<>();

    private AbilityBlockDrops() {
    }

    public static @Nullable ServerPlayer currentBreaker() {
        return BREAKER.get();
    }

    public static Scope capture(@Nullable ServerPlayer player) {
        var previous = BREAKER.get();
        if (player != null) BREAKER.set(player);
        return new Scope(previous);
    }

    public static boolean run(ServerPlayer player, BooleanSupplier action) {
        return run(player.level(), player, action);
    }

    /** Use the effect level when a controlled entity or delayed action is in another dimension. */
    public static boolean run(Level level, ServerPlayer player, BooleanSupplier action) {
        if (AbilityEffectPolicy.blockDestruction(level) == AbilityEffectPolicy.Decision.DENY) return false;
        try (var ignored = capture(player)) {
            return action.getAsBoolean();
        }
    }

    /**
     * Evaluates ability loot with the caster's perception, including controlled entities and
     * drops computed before destruction. Keeps the actual breaker and tool in the loot table.
     */
    public static List<ItemStack> getDrops(ServerPlayer player, BlockState state, ServerLevel level,
                                          BlockPos pos, @Nullable BlockEntity blockEntity,
                                          @Nullable Entity breaker, ItemInstance tool) {
        try (var ignored = capture(player)) {
            return Block.getDrops(state, level, pos, blockEntity, breaker, tool);
        }
    }

    public static boolean destroyBlock(Level level, BlockPos pos, boolean drops, ServerPlayer player) {
        return run(level, player, () -> level.destroyBlock(pos,
                drops || SpatialStorageService.hasEnabledUnit(player), player));
    }

    public static final class Scope implements AutoCloseable {
        private final @Nullable ServerPlayer previous;
        private boolean closed;

        private Scope(@Nullable ServerPlayer previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) BREAKER.remove();
            else BREAKER.set(previous);
        }
    }
}
