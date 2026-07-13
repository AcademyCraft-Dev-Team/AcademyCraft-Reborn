package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.common.world.level.block.entity.WindGenPillarBlockEntity;

import static org.academy.api.client.Resource.Textures.BLOCK_WIND_GEN_PILLAR;

public final class WindGenPillarRenderer implements BlockEntityRenderer<WindGenPillarBlockEntity, BlockEntityRenderState> {
    public static final WindGenPillarRenderer INSTANCE = new WindGenPillarRenderer();
    public static final RenderType PILLAR_RENDER_TYPE = RenderTypes.entitySolid(BLOCK_WIND_GEN_PILLAR);
    public static final float[][] PILLAR_VERTEX_BUFFER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.3f, 8, true);
    public static final float[][] PILLAR_OUTLINE_VERTEX_BUFFER = VertexUtil.Cylinder.getCylinderWireframeBuffer(0, 1, 0.3f, 8);

    private WindGenPillarRenderer() {
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        renderPillar(poseStack, submitNodeCollector, state.lightCoords);
    }

    public static void renderPillar(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.mulPose(Axis.YN.rotationDegrees(22.5f));
        submitNodeCollector.submitCustomGeometry(poseStack, PILLAR_RENDER_TYPE,
                (pose, consumer) ->
                        CylinderRenderer.renderCylinder(
                                pose, consumer, PILLAR_VERTEX_BUFFER,
                                1, 1, 1, 1,
                                lightCoords, OverlayTexture.NO_OVERLAY
                        )
        );
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }
}