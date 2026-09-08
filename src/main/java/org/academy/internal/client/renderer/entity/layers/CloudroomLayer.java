package org.academy.internal.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.render.Render;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.skills.lv3.Cloudroom;

import java.util.function.Function;

import static org.academy.AcademyCraft.academy;

/** Animated, full-bright model fill: the real posed model bounds the smoke even behind walls. */
public final class CloudroomLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {
    public static final ContextKey<Boolean> VISIBLE = new ContextKey<>(academy("cloudroom_visible"));
    private static final Function<Identifier, RenderType> RENDER_TYPES = Util.memoize(texture ->
            RenderType.create("cloudroom", RenderSetup.builder(Render.RenderPipelines.CLOUDROOM)
                    .withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));
    private final LivingEntityRenderer<?, S, M> renderer;

    public CloudroomLayer(LivingEntityRenderer<?, S, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    public static boolean shouldReveal(LivingEntity entity) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || entity == player || entity.level() != player.level()
                || !entity.isAlive() || entity.isSpectator()) return false;
        var skill = Skills.CLOUDROOM.get();
        if (!AbilitySystemClient.getSkillData(skill).map(data -> data.isEnabled()).orElse(false)) return false;
        var milestone = AbilitySystemClient.getSkillProficiencyMilestone(skill);
        if (entity.isInvisibleTo(player) && milestone < 3) return false;
        var radius = Cloudroom.detectionRadius(milestone);
        return player.getBoundingBox().inflate(radius).intersects(entity.getBoundingBox());
    }

    @Override
    public void submit(PoseStack poses, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        if (!Boolean.TRUE.equals(state.getRenderData(VISIBLE))) return;
        collector.order(2).submitModel(getParentModel(), state, poses,
                RENDER_TYPES.apply(renderer.getTextureLocation(state)), LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 0, null);
    }
}
