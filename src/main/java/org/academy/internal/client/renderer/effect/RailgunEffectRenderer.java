package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.item.Items;

import static org.academy.AcademyCraft.academy;
import static org.academy.internal.common.ability.electromaster.skills.lv5.Railgun.CHARGE_TIME;

public final class RailgunEffectRenderer implements EffectRenderer {
    public static final RailgunEffectRenderer INSTANCE = new RailgunEffectRenderer();
    public static final ContextKey<Railgun.Data> CONTEXT_KEY = new ContextKey<>(academy("railgun_data"));

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        var data = renderState.getRenderData(CONTEXT_KEY);
        if (data == null) return;
        poseStack.pushPose();
        var xOffset = 0.35f;
        xOffset = data.rightHand() ? -xOffset : xOffset;
        var zOffset = 0.4f;
        var ticks = data.ticks() + renderState.partialTick;
        var yCurve = Math.max(0, -4.0f * ticks * (ticks - CHARGE_TIME) / (CHARGE_TIME * CHARGE_TIME));
        poseStack.translate(xOffset, -yCurve + 0.5f, -zOffset);
        poseStack.mulPose(Axis.XP.rotationDegrees(renderState.ageInTicks * 50));
        var state = new ItemStackRenderState();
        Minecraft.getInstance()
                .getItemModelResolver()
                .updateForTopItem(
                        state,
                        Items.COIN.get().getDefaultInstance(),
                        ItemDisplayContext.FIXED,
                        null, null, 0
                );
        poseStack.scale(0.5f, 0.5f, 0.5f);
        state.submit(poseStack, submitNodeCollector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector, LocalPlayer player, int packedLight, float partialTick) {
        if (!Railgun.Client.isCharging()) return;
        var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
        if (data == null) return;
        poseStack.pushPose();
        var xOffset = 0.35f;
        xOffset = data.rightHand() ? xOffset : -xOffset;
        var zOffset = 0.5f;
        var ticks = data.ticks() + partialTick;
        var yCurve = MathUtil.getParabolaHeight(CHARGE_TIME, 1, ticks);
        poseStack.translate(xOffset, yCurve * 0.5f - 0.125f, -zOffset);

        poseStack.mulPose(Axis.XP.rotationDegrees((player.tickCount + partialTick) * 50));
        var state = new ItemStackRenderState();
        Minecraft.getInstance()
                .getItemModelResolver()
                .updateForTopItem(
                        state,
                        Items.COIN.get().getDefaultInstance(),
                        ItemDisplayContext.FIXED,
                        null, null, 0
                );
        poseStack.scale(0.125f, 0.125f, 0.125f);
        state.submit(poseStack, nodeCollector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}