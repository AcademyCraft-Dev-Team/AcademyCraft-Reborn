package org.academy.mixin.client;

import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A structure projection's broad-phase envelope is not its actual collision surface. */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class MixinEntityHitboxDebugRenderer {
    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void academy$skipProjectionEnvelope(Entity entity, float partialTick,
                                                boolean serverEntity, CallbackInfo ci) {
        if (entity instanceof NativeStructureProjectionEntity) {
            ci.cancel();
        }
    }
}
