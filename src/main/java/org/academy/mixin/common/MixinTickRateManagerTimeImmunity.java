package org.academy.mixin.common;

import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TickRateManager.class, priority = 2000)
public abstract class MixinTickRateManagerTimeImmunity {
    @Inject(method = "isEntityFrozen", at = @At("RETURN"), cancellable = true)
    private void academy$allowTimeImmuneEntity(
            Entity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) return;

        var server = entity.level().getServer();
        if (server == null) return;
        var context = (MinecraftServerContext) server;
        if (!context.hasAcademyCraftServer()) return;

        if (context.getAcademyCraftServer().getTemporalService().isImmune(
                entity,
                TemporalPauseSource.VANILLA_FREEZE
        )) {
            cir.setReturnValue(false);
        }
    }
}
