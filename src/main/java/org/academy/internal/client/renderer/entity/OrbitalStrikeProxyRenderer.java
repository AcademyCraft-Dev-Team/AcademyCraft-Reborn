package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.academy.internal.client.renderer.entity.state.OrbitalStrikeProxyRenderState;
import org.academy.internal.common.world.entity.misaka.OrbitalStrikeProxyEntity;

/**
 * Proxy is VFX / damage only — no sat model here.
 * The visible satellite is the sky mark retargeted via laser-tower strike aim
 * (same tip as the power beam and downlink).
 * The downlink beam is drawn by {@code OrbitalStrikeProxyVfxClient}
 * on {@code LevelRenderEvent} so it is not lost to entity frustum culls when the player
 * stands at the impact and looks at the ground.
 */
public final class OrbitalStrikeProxyRenderer
        extends EntityRenderer<OrbitalStrikeProxyEntity, OrbitalStrikeProxyRenderState> {
    public OrbitalStrikeProxyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public OrbitalStrikeProxyRenderState createRenderState() {
        return new OrbitalStrikeProxyRenderState();
    }

    @Override
    public void extractRenderState(
            OrbitalStrikeProxyEntity entity,
            OrbitalStrikeProxyRenderState state,
            float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);
        state.firing = entity.isFiring();
        state.hyper = entity.isHyper();
        state.impact = entity.getImpact();
        state.spin = (entity.tickCount + partialTick) * 4.0f;
        var impact = state.impact;
        state.beamEndRelative.set(
                (float) (impact.getX() + 0.5 - entity.getX()),
                (float) (impact.getY() + 0.1 - entity.getY()),
                (float) (impact.getZ() + 0.5 - entity.getZ())
        );
    }

    @Override
    public void submit(
            OrbitalStrikeProxyRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera
    ) {
        // Intentionally empty: sky mark owns the sat silhouette during strike.
    }

    @Override
    protected boolean affectedByCulling(OrbitalStrikeProxyEntity entity) {
        // Tiny AABB at orbit altitude is outside the frustum when looking at the impact pad.
        return false;
    }
}
