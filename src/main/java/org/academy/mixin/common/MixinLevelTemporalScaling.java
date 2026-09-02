package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Level.class, priority = 1900)
public abstract class MixinLevelTemporalScaling {
    @Redirect(
            method = "tickBlockEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"
            )
    )
    private void academy$dispatchScaledBlockEntityTicks(
            TickingBlockEntity ticker
    ) {
        var level = (Level) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)) {
            ticker.tick();
            return;
        }
        var context = (MinecraftServerContext) serverLevel.getServer();
        if (!context.hasAcademyCraftServer()) {
            ticker.tick();
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchBlockEntityTicks(serverLevel, ticker);
    }
}
