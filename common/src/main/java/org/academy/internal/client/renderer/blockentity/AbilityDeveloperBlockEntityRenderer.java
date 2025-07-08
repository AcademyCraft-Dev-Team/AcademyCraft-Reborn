package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.RenderTypes;
import org.academy.internal.client.model.AbilityDeveloperBlockEntityModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperBlockEntityRenderer implements BlockEntityRenderer<AbilityDeveloperBlockEntity> {
    public static final BlockEntityRenderer<AbilityDeveloperBlockEntity> INSTANCE = new AbilityDeveloperBlockEntityRenderer();
    public static final AbilityDeveloperBlockEntityModel MODEL = new AbilityDeveloperBlockEntityModel(AbilityDeveloperBlockEntityModel.createBodyLayer().bakeRoot());

    private AbilityDeveloperBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull AbilityDeveloperBlockEntity be, float partialTick, @NotNull PoseStack ps, @NotNull MultiBufferSource bf, int packedLight, int packedOverlay) {
        if (be.isMain()) {
            ps.pushPose();
            var facing = be.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            var yRot = facing.getOpposite().toYRot();

            ps.translate(0, 1.5f, 1);
            ps.mulPose(Axis.XP.rotationDegrees(180));
            ps.rotateAround(Axis.YP.rotationDegrees(yRot), 0.5f, 0, 0.5f);
            ps.translate(0.5f, 0, 0);

            MODEL.setupAnim(be, partialTick);
            var vc = bf.getBuffer(RenderTypes.ABILITY_DEVELOPER);
            MODEL.setupAnim(be, partialTick);
            MODEL.renderToBuffer(ps, vc, packedLight, packedOverlay, 1f, 1f, 1f, 1f);
            ps.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull AbilityDeveloperBlockEntity newBlockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(@NotNull AbilityDeveloperBlockEntity newBlockEntity, @NotNull Vec3 newCameraPos) {
        return true;
    }
}