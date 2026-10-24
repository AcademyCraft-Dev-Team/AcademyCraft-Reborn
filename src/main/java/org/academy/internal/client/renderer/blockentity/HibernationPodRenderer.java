package org.academy.internal.client.renderer.blockentity;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.entity.HibernationPodBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * GeckoLib block renderer for the hibernation pod. Only the MAIN segment draws the full 1×3 mesh.
 */
public final class HibernationPodRenderer
        extends GeoBlockRenderer<HibernationPodBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Boolean> IS_MAIN =
            DataTicket.create("academy_hibernation_pod_main", Boolean.class);

    public HibernationPodRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new DefaultedBlockGeoModel<>(AcademyCraft.academy("hibernation_pod")));
    }

    @Override
    public void extractRenderState(
            HibernationPodBlockEntity blockEntity,
            BlockEntityRenderState renderState,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
    ) {
        super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
        asGeo(renderState).addGeckolibData(IS_MAIN, blockEntity.isMain());
    }

    @Override
    public void submit(
            BlockEntityRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector renderTasks,
            CameraRenderState cameraRenderState
    ) {
        if (!Boolean.TRUE.equals(asGeo(renderState).getOrDefaultGeckolibData(IS_MAIN, false))) {
            return;
        }
        super.submit(renderState, poseStack, renderTasks, cameraRenderState);
    }

    @Override
    public AABB getRenderBoundingBox(HibernationPodBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }

    private static GeoRenderState asGeo(BlockEntityRenderState state) {
        return (GeoRenderState) (Object) state;
    }
}
