package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.resources.R;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.joml.Matrix4f;

import static org.academy.AcademyCraft.academy;

public final class ElectromasterWeaponEffectRenderer implements EffectRenderer {
    public static final ElectromasterWeaponEffectRenderer INSTANCE =
            new ElectromasterWeaponEffectRenderer();
    public static final ContextKey<MagneticWeapon.Data> MAGNETIC_CONTEXT =
            new ContextKey<>(academy("magnetic_weapon"));
    public static final ContextKey<IronSandArsenal.Data> IRON_SAND_CONTEXT =
            new ContextKey<>(academy("iron_sand_operation"));
    private ElectromasterWeaponEffectRenderer() {
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        var ironSand = state.getRenderDataOrDefault(IRON_SAND_CONTEXT, IronSandArsenal.Data.DEFAULT);
        var minecraft = Minecraft.getInstance();
        var source = minecraft.level == null ? null : minecraft.level.getEntity(state.getRenderDataOrDefault(
                WingEffectRenderer.ENTITY_ID_CONTEXT, -1));
        if (!(source instanceof Player player)) return;

        if (ironSand.active()) {
            renderIronSand(poseStack, collector, packedLight, ironSand, state.ageInTicks);
        }
    }

    private static void renderIronSand(PoseStack poseStack, SubmitNodeCollector collector,
                                       int packedLight, IronSandArsenal.Data data, float time) {
        for (var i = 0; i < 12; i++) {
            var angle = time * 0.075f + i * (float) (Math.PI * 2.0 / 12.0);
            var radius = 0.72f + (i % 3) * 0.13f;
            var y = -0.25f - (i % 4) * 0.43f;
            poseStack.pushPose();
            poseStack.translate(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            poseStack.mulPose(Axis.YP.rotationDegrees(-time * 5.0f + i * 31.0f));
            poseStack.scale(0.22f, 0.22f, 0.22f);
            submitSandQuad(poseStack, collector, packedLight, 0.72f);
            poseStack.popPose();
        }

        if (data.swingTicks() <= 0) return;
        var progress = Math.clamp((data.swingTicks() - 1.0f) / 9.0f, 0.0f, 1.0f);
        var eased = progress * progress * (3.0f - 2.0f * progress);
        var sweepAngle = -60.0f + eased * 120.0f;
        var segments = 24;
        for (var i = 0; i < segments; i++) {
            var radialProgress = i / (float) (segments - 1);
            var radius = 0.8f + radialProgress * 11.2f;
            var trailingAngle = (1.0f - radialProgress) * 24.0f;
            var angle = Math.toRadians(sweepAngle - trailingAngle);
            poseStack.pushPose();
            poseStack.translate(Math.sin(angle) * radius,
                    -1.0 + Math.sin(progress * Math.PI) * 0.16 + Math.sin(i * 0.72) * 0.06,
                    -Math.cos(angle) * radius);
            poseStack.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(angle) + 90.0f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-18.0f + radialProgress * 36.0f));
            var scale = 0.24f + radialProgress * 0.34f;
            poseStack.scale(scale, scale, scale);
            submitSandQuad(
                    poseStack,
                    collector,
                    packedLight,
                    (0.9f - radialProgress * 0.18f) * (1.0f - progress * 0.22f)
            );
            poseStack.popPose();
        }
    }

    private static void submitSandQuad(PoseStack poseStack, SubmitNodeCollector collector,
                                       int packedLight, float alpha) {
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(R.textures.iron_sand_arsenal_effect),
                (pose, consumer) -> {
                    addQuad(consumer, pose.pose(), packedLight, alpha, false);
                    addQuad(consumer, pose.pose(), packedLight, alpha, true);
                }
        );
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f matrix, int light,
                                float alpha, boolean reverse) {
        if (reverse) {
            vertex(consumer, matrix, 1, -1, 1, 0, light, alpha);
            vertex(consumer, matrix, -1, -1, 0, 0, light, alpha);
            vertex(consumer, matrix, -1, 1, 0, 1, light, alpha);
            vertex(consumer, matrix, 1, 1, 1, 1, light, alpha);
        } else {
            vertex(consumer, matrix, -1, -1, 0, 0, light, alpha);
            vertex(consumer, matrix, 1, -1, 1, 0, light, alpha);
            vertex(consumer, matrix, 1, 1, 1, 1, light, alpha);
            vertex(consumer, matrix, -1, 1, 0, 1, light, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y,
                               float u, float v, int light, float alpha) {
        consumer.addVertex(matrix, x, y, 0)
                .setColor(0.78f, 0.82f, 0.88f, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 0, 1);
    }
}
