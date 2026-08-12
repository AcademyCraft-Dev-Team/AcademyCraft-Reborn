package org.academy.internal.client.render.fluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IRenderableSection;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.fluid.CustomFluidRenderer;
import org.academy.AcademyCraft;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.internal.common.world.level.material.Fluids;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static org.academy.AcademyCraft.academy;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ImagPhaseFluidRenderer implements CustomFluidRenderer {
    public static final ImagPhaseFluidRenderer INSTANCE = new ImagPhaseFluidRenderer();
    private static final ContextKey<ImagPhaseRenderState> CONTEXT_KEY =
            new ContextKey<>(academy("imag_phase_fluid"));
    private static final Object LOCK = new Object();
    private static final Map<Level, Long2ObjectOpenHashMap<List<FluidSurface>>> SURFACES_BY_LEVEL =
            new WeakHashMap<>();

    private ImagPhaseFluidRenderer() {
    }

    @Override
    public boolean renderFluid(
            FluidRenderer renderer,
            FluidState fluidState,
            BlockAndTintGetter level,
            BlockPos pos,
            FluidRenderer.Output output,
            BlockState blockState
    ) {
        return true;
    }

    @SubscribeEvent
    public static void onAddSectionGeometry(AddSectionGeometryEvent event) {
        Level level = event.getLevel();
        BlockPos origin = event.getSectionOrigin().immutable();
        long sectionKey = sectionKey(origin);

        boolean containsImagPhase = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16 && !containsImagPhase; x++) {
            for (int y = 0; y < 16 && !containsImagPhase; y++) {
                for (int z = 0; z < 16; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (isImagPhase(level.getFluidState(cursor))) {
                        containsImagPhase = true;
                        break;
                    }
                }
            }
        }
        if (!containsImagPhase) {
            synchronized (LOCK) {
                var sections = SURFACES_BY_LEVEL.get(level);
                if (sections != null) {
                    sections.remove(sectionKey);
                }
            }
            return;
        }

        event.addRenderer(context -> {
            var surfaces = new ArrayList<FluidSurface>();
            BlockAndTintGetter region = context.getRegion();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        BlockState blockState = region.getBlockState(pos);
                        FluidState fluidState = blockState.getFluidState();
                        if (isImagPhase(fluidState)) {
                            surfaces.add(FluidSurface.extract(region, pos, blockState, fluidState));
                        }
                    }
                }
            }
            synchronized (LOCK) {
                SURFACES_BY_LEVEL.computeIfAbsent(level, _ -> new Long2ObjectOpenHashMap<>())
                        .put(sectionKey, List.copyOf(surfaces));
            }
        });
    }

    @SubscribeEvent
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        var level = event.getLevel();
        Long2ObjectOpenHashMap<List<FluidSurface>> stored;
        synchronized (LOCK) {
            var sections = SURFACES_BY_LEVEL.get(level);
            stored = sections == null ? new Long2ObjectOpenHashMap<>() : new Long2ObjectOpenHashMap<>(sections);
        }

        if (stored.isEmpty()) {
            event.getRenderState().setRenderData(CONTEXT_KEY, ImagPhaseRenderState.EMPTY);
            return;
        }

        var surfacesBySection = new Long2ObjectOpenHashMap<List<FluidSurface>>();
        stored.forEach((section, surfaces) -> surfacesBySection.put(section, List.copyOf(surfaces)));
        event.getRenderState().setRenderData(CONTEXT_KEY, new ImagPhaseRenderState(surfacesBySection));
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        ImagPhaseRenderState state = event.getLevelRenderState().getRenderData(CONTEXT_KEY);
        if (state == null || state.surfacesBySection().isEmpty()) {
            return;
        }

        var camera = event.getLevelRenderState().cameraRenderState.pos;
        var collector = event.getSubmitNodeCollector();
        RenderType renderType = RenderTypes.endGateway();
        if (IrisCompat.isShaderPackInUse()) {
            var blockStateIds = WorldRenderingSettings.INSTANCE.getBlockStateIds();
            if (blockStateIds != null) {
                int endPortalId = blockStateIds.applyAsInt(net.minecraft.world.level.block.Blocks.END_PORTAL.defaultBlockState());
                CapturedRenderingState.INSTANCE.setCurrentBlockEntity(endPortalId);
            }
            renderType = RenderTypes.entitySolid(TheEndPortalRenderer.END_PORTAL_LOCATION);
        }

        RenderType finalRenderType = renderType;
        event.getRenderableSections().forEach(section -> submitSection(
                section,
                state,
                event.getPoseStack(),
                collector,
                finalRenderType,
                camera.x,
                camera.y,
                camera.z
        ));
    }

    private static void submitSection(
            IRenderableSection section,
            ImagPhaseRenderState state,
            PoseStack poseStack,
            net.minecraft.client.renderer.SubmitNodeCollector collector,
            RenderType renderType,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        BlockPos origin = section.getRenderOrigin();
        long sectionKey = sectionKey(origin);
        List<FluidSurface> surfaces = state.surfacesBySection().get(sectionKey);
        if (surfaces == null || surfaces.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(origin.getX() - cameraX, origin.getY() - cameraY, origin.getZ() - cameraZ);
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            for (FluidSurface surface : surfaces) {
                surface.render(pose.pose(), buffer);
            }
        });
        poseStack.popPose();
    }

    private static long sectionKey(BlockPos origin) {
        return SectionPos.asLong(
                SectionPos.blockToSectionCoord(origin.getX()),
                SectionPos.blockToSectionCoord(origin.getY()),
                SectionPos.blockToSectionCoord(origin.getZ())
        );
    }

    private static boolean isImagPhase(FluidState state) {
        Fluid type = state.getType();
        return type == Fluids.IMAG_PHASE.get() || type == Fluids.FLOWING_IMAG_PHASE.get();
    }

    private static float getHeight(BlockAndTintGetter level, Fluid fluid, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluidState = state.getFluidState();
        if (fluid.isSame(fluidState.getType())) {
            return fluid.isSame(level.getFluidState(pos.above()).getType()) ? 1.0F : fluidState.getOwnHeight();
        }
        return state.isSolid() ? -1.0F : 0.0F;
    }

    private static float averageHeight(
            BlockAndTintGetter level,
            Fluid fluid,
            float center,
            float adjacentA,
            float adjacentB,
            BlockPos corner
    ) {
        if (adjacentA >= 1.0F || adjacentB >= 1.0F) {
            return 1.0F;
        }
        float weighted = 0.0F;
        float weight = 0.0F;
        if (adjacentA > 0.0F || adjacentB > 0.0F) {
            float cornerHeight = getHeight(level, fluid, corner);
            if (cornerHeight >= 1.0F) {
                return 1.0F;
            }
            float[] result = addWeightedHeight(weighted, weight, cornerHeight);
            weighted = result[0];
            weight = result[1];
        }
        for (float height : new float[]{center, adjacentA, adjacentB}) {
            float[] result = addWeightedHeight(weighted, weight, height);
            weighted = result[0];
            weight = result[1];
        }
        return weight == 0.0F ? 0.0F : weighted / weight;
    }

    private static float[] addWeightedHeight(float weighted, float weight, float height) {
        if (height >= 0.8F) {
            return new float[]{weighted + height * 10.0F, weight + 10.0F};
        }
        if (height >= 0.0F) {
            return new float[]{weighted + height, weight + 1.0F};
        }
        return new float[]{weighted, weight};
    }

    private record ImagPhaseRenderState(Long2ObjectOpenHashMap<List<FluidSurface>> surfacesBySection) {
        private static final ImagPhaseRenderState EMPTY =
                new ImagPhaseRenderState(new Long2ObjectOpenHashMap<>());
    }

    private record FluidSurface(
            BlockPos pos,
            float northWest,
            float northEast,
            float southEast,
            float southWest,
            int faceMask
    ) {
        private static final float TOP_OFFSET = 0.001F;

        private static FluidSurface extract(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState blockState,
                FluidState fluidState
        ) {
            Fluid fluid = fluidState.getType();
            float center = getHeight(level, fluid, pos);
            float northWest;
            float northEast;
            float southEast;
            float southWest;
            if (center >= 1.0F) {
                northWest = northEast = southEast = southWest = 1.0F;
            } else {
                float north = getHeight(level, fluid, pos.north());
                float south = getHeight(level, fluid, pos.south());
                float east = getHeight(level, fluid, pos.east());
                float west = getHeight(level, fluid, pos.west());
                northEast = averageHeight(level, fluid, center, north, east, pos.north().east());
                northWest = averageHeight(level, fluid, center, north, west, pos.north().west());
                southEast = averageHeight(level, fluid, center, south, east, pos.south().east());
                southWest = averageHeight(level, fluid, center, south, west, pos.south().west());
            }

            int mask = 0;
            for (Direction direction : Direction.values()) {
                BlockState neighbor = level.getBlockState(pos.relative(direction));
                if (direction == Direction.UP) {
                    if (!fluid.isSame(neighbor.getFluidState().getType())) {
                        mask |= bit(direction);
                    }
                } else if (FluidRenderer.shouldRenderFace(fluidState, blockState, direction, neighbor)
                        && !fluid.isSame(neighbor.getFluidState().getType())) {
                    mask |= bit(direction);
                }
            }
            return new FluidSurface(pos.immutable(), northWest, northEast, southEast, southWest, mask);
        }

        private void render(Matrix4fc pose, VertexConsumer buffer) {
            float x = pos.getX() & 15;
            float y = pos.getY() & 15;
            float z = pos.getZ() & 15;
            if (has(Direction.UP)) {
                quad(buffer, pose,
                        x, y + northWest - TOP_OFFSET, z,
                        x, y + southWest - TOP_OFFSET, z + 1,
                        x + 1, y + southEast - TOP_OFFSET, z + 1,
                        x + 1, y + northEast - TOP_OFFSET, z);
            }
            if (has(Direction.DOWN)) {
                quad(buffer, pose,
                        x, y + TOP_OFFSET, z + 1,
                        x, y + TOP_OFFSET, z,
                        x + 1, y + TOP_OFFSET, z,
                        x + 1, y + TOP_OFFSET, z + 1);
            }
            if (has(Direction.NORTH)) {
                quad(buffer, pose,
                        x + 1, y + northEast, z + TOP_OFFSET,
                        x + 1, y, z + TOP_OFFSET,
                        x, y, z + TOP_OFFSET,
                        x, y + northWest, z + TOP_OFFSET);
            }
            if (has(Direction.SOUTH)) {
                quad(buffer, pose,
                        x, y + southWest, z + 1 - TOP_OFFSET,
                        x, y, z + 1 - TOP_OFFSET,
                        x + 1, y, z + 1 - TOP_OFFSET,
                        x + 1, y + southEast, z + 1 - TOP_OFFSET);
            }
            if (has(Direction.WEST)) {
                quad(buffer, pose,
                        x + TOP_OFFSET, y + northWest, z,
                        x + TOP_OFFSET, y, z,
                        x + TOP_OFFSET, y, z + 1,
                        x + TOP_OFFSET, y + southWest, z + 1);
            }
            if (has(Direction.EAST)) {
                quad(buffer, pose,
                        x + 1 - TOP_OFFSET, y + southEast, z + 1,
                        x + 1 - TOP_OFFSET, y, z + 1,
                        x + 1 - TOP_OFFSET, y, z,
                        x + 1 - TOP_OFFSET, y + northEast, z);
            }
        }

        private boolean has(Direction direction) {
            return (faceMask & bit(direction)) != 0;
        }

        private static int bit(Direction direction) {
            return 1 << direction.ordinal();
        }

        private static void quad(
                VertexConsumer buffer,
                Matrix4fc pose,
                float x0,
                float y0,
                float z0,
                float x1,
                float y1,
                float z1,
                float x2,
                float y2,
                float z2,
                float x3,
                float y3,
                float z3
        ) {
            buffer.addVertex(pose, x0, y0, z0);
            buffer.addVertex(pose, x1, y1, z1);
            buffer.addVertex(pose, x2, y2, z2);
            buffer.addVertex(pose, x3, y3, z3);
        }
    }
}
