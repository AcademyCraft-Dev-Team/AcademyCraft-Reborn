package org.academy.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import org.academy.internal.client.ability.aeromanip.HighSpeedJetHighlightClient;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {
    @WrapOperation(method = "shouldRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;shouldRender(DDD)Z"))
    private boolean academy$measureRtsEntityDistanceFromFocus(Entity entity, double x, double y, double z,
            Operation<Boolean> original) {
        if (WideAreaInterferenceClientState.isRtsView()) {
            var focus = WideAreaInterferenceClientState.focusPosition();
            var range = Math.max(128, Minecraft.getInstance().options.renderDistance().get() * 16);
            return new AABB(focus, focus).inflate(range).intersects(entity.getBoundingBox());
        }
        return original.call(entity, x, y, z);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void academy$highlightHighSpeedJetSupport(
            T entity,
            S state,
            float partialTick,
            CallbackInfo ci
    ) {
        if (WideAreaInterferenceClientState.shouldOutline(entity)) {
            state.outlineColor = WideAreaInterferenceClientState.outlineColor(entity);
        } else if (HighSpeedJetHighlightClient.shouldHighlightEntity(entity)) {
            state.outlineColor = HighSpeedJetHighlightClient.WHITE_OUTLINE;
        }
    }
}
