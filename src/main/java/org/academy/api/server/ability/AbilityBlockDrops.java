package org.academy.api.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.academy.internal.server.storage.SpatialStorageService;
import org.jspecify.annotations.Nullable;

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
        try (var ignored = capture(player)) {
            return action.getAsBoolean();
        }
    }

    public static boolean destroyBlock(Level level, BlockPos pos, boolean drops, ServerPlayer player) {
        return run(player, () -> level.destroyBlock(pos,
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
