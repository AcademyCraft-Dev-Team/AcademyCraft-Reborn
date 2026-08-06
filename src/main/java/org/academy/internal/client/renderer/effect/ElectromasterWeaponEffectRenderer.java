package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
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
    private static final int ATTACK_ANIMATION_TICKS = 10;

    private ElectromasterWeaponEffectRenderer() {
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        var magnetic = state.getRenderDataOrDefault(MAGNETIC_CONTEXT, MagneticWeapon.Data.DEFAULT);
        var ironSand = state.getRenderDataOrDefault(IRON_SAND_CONTEXT, IronSandArsenal.Data.DEFAULT);
        var minecraft = Minecraft.getInstance();
        var source = minecraft.level == null ? null : minecraft.level.getEntity(state.getRenderDataOrDefault(
                WingEffectRenderer.ENTITY_ID_CONTEXT, -1));
        if (!(source instanceof Player player)) return;

        if (magnetic.active() && !player.getMainHandItem().isEmpty()) {
            renderMagneticWeapon(poseStack, collector, packedLight, player, magnetic, state.ageInTicks);
        }
        if (ironSand.active()) {
            renderIronSand(poseStack, collector, packedLight, ironSand, state.ageInTicks);
        }
    }

    private static void renderMagneticWeapon(PoseStack poseStack, SubmitNodeCollector collector,
                                             int packedLight, Player player, MagneticWeapon.Data data,
                                             float time) {
        poseStack.pushPose();
        poseStack.translate(0.0, -1.2, 0.52);

        if (data.targetId() >= 0 && data.animationTicks() > 0 && player.level().getEntity(data.targetId()) != null) {
            var target = player.level().getEntity(data.targetId());
            var delta = target.getBoundingBox().getCenter().subtract(player.position().add(0, 1.0, 0));
            var yaw = Math.toRadians(-player.getYRot());
            var localX = delta.x * Math.cos(yaw) - delta.z * Math.sin(yaw);
            var localZ = delta.x * Math.sin(yaw) + delta.z * Math.cos(yaw);
            var linear = Math.clamp(data.animationTicks() / (float) ATTACK_ANIMATION_TICKS, 0.0f, 1.0f);
            var flight = 1.0f - Math.abs(linear * 2.0f - 1.0f);
            poseStack.translate(localX * flight, -delta.y * flight, localZ * flight);
        }

        poseStack.mulPose(Axis.ZP.rotationDegrees(138.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 8.0f));
        poseStack.scale(1.45f, 1.45f, 1.45f);
        var itemState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                itemState,
                player.getMainHandItem(),
                ItemDisplayContext.FIXED,
                player.level(),
                player,
                player.getId()
        );
        itemState.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
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
        var progress = Math.clamp(data.swingTicks() / 10.0f, 0.0f, 1.0f);
        for (var i = 0; i < 10; i++) {
            var angle = Math.toRadians(-60.0 + i * (120.0 / 9.0));
            var radius = 1.0f + i * 0.20f;
            poseStack.pushPose();
            poseStack.translate(Math.sin(angle) * radius, -1.0 + Math.sin(progress * Math.PI) * 0.2,
                    -Math.cos(angle) * radius);
            poseStack.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(angle)));
            poseStack.scale(0.34f, 0.34f, 0.34f);
            submitSandQuad(poseStack, collector, packedLight, 0.9f * (1.0f - progress * 0.35f));
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
