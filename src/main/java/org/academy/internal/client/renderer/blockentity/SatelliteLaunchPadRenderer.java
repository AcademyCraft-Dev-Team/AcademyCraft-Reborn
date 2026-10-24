package org.academy.internal.client.renderer.blockentity;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.client.render.Render;
import org.academy.internal.client.renderer.RelayPlatformGeo;
import org.academy.internal.common.world.level.block.entity.SatelliteLaunchPadBlockEntity;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Renders the launch pad geo; when a satellite is seated, also draws the shared satellite geo
 * in the same model space (pad + sat were authored against one atlas / origin).
 * Coolant surface is a BER translucent box driven by {@code waterMb}
 * (vanilla waterlogged mesh is full-or-empty and unsuitable for tank levels).
 */
public final class SatelliteLaunchPadRenderer
        extends GeoBlockRenderer<SatelliteLaunchPadBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Boolean> HAS_SATELLITE =
            DataTicket.create("academy_launch_pad_has_sat", Boolean.class);
    private static final DataTicket<Float> WATER_FILL =
            DataTicket.create("academy_launch_pad_water_fill", Float.class);
    private static final DataTicket<Integer> WATER_COLOR =
            DataTicket.create("academy_launch_pad_water_color", Integer.class);

    /** Matches vanilla source height (~8/9). */
    private static final float WATER_MAX_HEIGHT = 8.0f / 9.0f;
    private static final float WATER_INSET = 0.02f;
    private static final int WATER_ALPHA = 160;

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
        var geo = asGeo(renderState);
        geo.addGeckolibData(HAS_SATELLITE, blockEntity.hasSeatedSatellite());
        float fill = blockEntity.getWaterMb() / (float) SatelliteLaunchPadBlockEntity.WATER_CAPACITY_MB;
        geo.addGeckolibData(WATER_FILL, Mth.clamp(fill, 0.0f, 1.0f));
        int color = 0x3F76E4;
        var level = blockEntity.getLevel();
        if (level != null) {
            color = level.getBiome(blockEntity.getBlockPos()).value().getWaterColor();
        }
        geo.addGeckolibData(WATER_COLOR, color);
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
        submitWater(renderState, poseStack, renderTasks);
        if (Boolean.TRUE.equals(asGeo(renderState).getOrDefaultGeckolibData(HAS_SATELLITE, false))) {
            satelliteRenderer.submit(renderState, poseStack, renderTasks, cameraRenderState);
        }
    }

    private static void submitWater(
            BlockEntityRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector renderTasks
    ) {
        float fill = asGeo(renderState).getOrDefaultGeckolibData(WATER_FILL, 0.0f);
        if (fill <= 0.001f) {
            return;
        }
        float height = fill * WATER_MAX_HEIGHT;
        int argb = asGeo(renderState).getOrDefaultGeckolibData(WATER_COLOR, 0x3F76E4);
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float x0 = WATER_INSET;
        float x1 = 1.0f - WATER_INSET;
        float z0 = WATER_INSET;
        float z1 = 1.0f - WATER_INSET;
        float y0 = 0.01f;
        float y1 = height;

        renderTasks.submitCustomGeometry(poseStack, Render.RenderTypes.POS_COLOR_QUADS, (pose, consumer) -> {
            Matrix4f matrix = pose.pose();
            quad(consumer, matrix, r, g, b, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
            quad(consumer, matrix, r, g, b, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
            quad(consumer, matrix, r, g, b, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
            quad(consumer, matrix, r, g, b, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
            quad(consumer, matrix, r, g, b, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
            quad(consumer, matrix, r, g, b, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
        });
    }

    private static void quad(
            VertexConsumer consumer,
            Matrix4f matrix,
            int r,
            int g,
            int b,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3
    ) {
        vertex(consumer, matrix, x0, y0, z0, r, g, b);
        vertex(consumer, matrix, x1, y1, z1, r, g, b);
        vertex(consumer, matrix, x2, y2, z2, r, g, b);
        vertex(consumer, matrix, x3, y3, z3, r, g, b);
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            int r,
            int g,
            int b
    ) {
        consumer.addVertex(matrix, x, y, z).setColor(r, g, b, WATER_ALPHA);
    }

    @Override
    public AABB getRenderBoundingBox(SatelliteLaunchPadBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }

    private static GeoRenderState asGeo(BlockEntityRenderState state) {
        return (GeoRenderState) (Object) state;
    }
}
