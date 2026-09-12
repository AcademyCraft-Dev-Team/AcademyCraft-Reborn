package org.academy.internal.client.renderer.blockentity;

import com.geckolib.constant.dataticket.DataTicket;
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
import org.academy.internal.client.renderer.RelayPlatformGeo;
import org.academy.internal.common.world.level.block.entity.SatelliteLaunchPadBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Renders the launch pad geo; when a satellite is seated, also draws the shared satellite geo
 * in the same model space (pad + sat were authored against one atlas / origin).
 */
public final class SatelliteLaunchPadRenderer
        extends GeoBlockRenderer<SatelliteLaunchPadBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Boolean> HAS_SATELLITE =
            DataTicket.create("academy_launch_pad_has_sat", Boolean.class);

    private final GeoBlockRenderer<SatelliteLaunchPadBlockEntity, BlockEntityRenderState> satelliteRenderer;

    public SatelliteLaunchPadRenderer(BlockEntityRendererProvider.Context context) {
        super(context, RelayPlatformGeo.blockModel(AcademyCraft.academy("satellite_launch_pad")));
        this.satelliteRenderer = new GeoBlockRenderer<>(
                context,
                RelayPlatformGeo.blockModel(AcademyCraft.academy("relay_satellite"))
        ) {};
    }

    @Override
    public void extractRenderState(
            SatelliteLaunchPadBlockEntity blockEntity,
            BlockEntityRenderState renderState,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
    ) {
        super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
        asGeo(renderState).addGeckolibData(HAS_SATELLITE, blockEntity.hasSeatedSatellite());
        satelliteRenderer.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
    }

    @Override
    public void submit(
            BlockEntityRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector renderTasks,
            CameraRenderState cameraRenderState
    ) {
        super.submit(renderState, poseStack, renderTasks, cameraRenderState);
        if (Boolean.TRUE.equals(asGeo(renderState).getOrDefaultGeckolibData(HAS_SATELLITE, false))) {
            satelliteRenderer.submit(renderState, poseStack, renderTasks, cameraRenderState);
        }
    }

    @Override
    public AABB getRenderBoundingBox(SatelliteLaunchPadBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }

    private static GeoRenderState asGeo(BlockEntityRenderState state) {
        return (GeoRenderState) (Object) state;
    }
}
