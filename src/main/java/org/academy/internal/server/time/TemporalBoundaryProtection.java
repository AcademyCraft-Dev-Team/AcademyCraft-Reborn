package org.academy.internal.server.time;

import java.util.function.BooleanSupplier;

import net.minecraft.world.entity.Entity;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Protects the vanilla simulation boundary, including cancellations inside a fallback tick. */
public final class TemporalBoundaryProtection {
    private TemporalBoundaryProtection() {}

    public static boolean isProtected(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return false;
        var server = entity.level().getServer();
        return server instanceof MinecraftServerContext context && context.hasAcademyCraftServer()
                && context.getAcademyCraftServer().getTemporalService()
                .isImmune(entity, TemporalPauseSource.EXTERNAL_COMPATIBILITY);
    }

    public static CallbackInfo protectTickCallback(CallbackInfo callback, Entity entity) {
        return protectCallback(callback, isProtected(entity));
    }

    public static CallbackInfo protectCallback(CallbackInfo callback, boolean protectedBoundary) {
        return protectCallback(callback, protectedBoundary, () -> false);
    }

    public static CallbackInfo protectCallback(CallbackInfo callback, boolean protectedBoundary,
                                               BooleanSupplier ownedControl) {
        return protectedBoundary ? new UncancelableCallback(callback, ownedControl) : callback;
    }

    private static final class UncancelableCallback extends CallbackInfo {
        private final CallbackInfo original;
        private final BooleanSupplier ownedControl;

        private UncancelableCallback(CallbackInfo original, BooleanSupplier ownedControl) {
            super(original.getId(), true);
            this.original = original;
            this.ownedControl = ownedControl;
        }

        @Override public void cancel() {
            if (ownedControl.getAsBoolean()) {
                original.cancel();
                super.cancel();
            }
        }
    }
}
