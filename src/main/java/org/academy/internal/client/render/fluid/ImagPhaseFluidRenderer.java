package org.academy.internal.client.render.fluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.fluid.CustomFluidRenderer;
import org.academy.AcademyCraft;
import org.academy.api.client.render.post.PostEffect;
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
    private static final RenderType BLACK_BACKGROUND =
            RenderTypes.entitySolid(academy("textures/block/black.png"));
    private static final RenderType[] STAR_LAYERS = {
            RenderTypes.entityTranslucent(academy("textures/particle/imag_phase_fluid_0.png")),
            RenderTypes.entityTranslucent(academy("textures/particle/imag_phase_fluid_1.png")),
            RenderTypes.entityTranslucent(academy("textures/particle/imag_phase_fluid_2.png")),
            RenderTypes.entityTranslucent(academy("textures/particle/imag_phase_fluid_3.png"))
    };
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
        // Keep the ordinary fluid mesh as a dark, animated foundation. The solid black
        // background is submitted separately during level rendering.
        return false;
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

        var visibleSections = new ArrayList<SectionSurfaces>();
        stored.long2ObjectEntrySet().forEach(entry -> {
            long section = entry.getLongKey();
            List<FluidSurface> surfaces = entry.getValue();
            if (surfaces.isEmpty()) {
                return;
            }

            int sectionX = SectionPos.x(section);
            int sectionY = SectionPos.y(section);
            int sectionZ = SectionPos.z(section);
            if (!level.hasChunk(sectionX, sectionZ)) {
                return;
            }

            BlockPos origin = new BlockPos(
                    SectionPos.sectionToBlockCoord(sectionX),
                    SectionPos.sectionToBlockCoord(sectionY),
                    SectionPos.sectionToBlockCoord(sectionZ)
            );
            AABB bounds = new AABB(
                    origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + 16, origin.getY() + 16, origin.getZ() + 16
            );
            if (event.getFrustum().isVisible(bounds)) {
                visibleSections.add(new SectionSurfaces(origin, List.copyOf(surfaces)));
            }
        });
        event.getRenderState().setRenderData(
                CONTEXT_KEY,
                visibleSections.isEmpty()
                        ? ImagPhaseRenderState.EMPTY
                        : new ImagPhaseRenderState(List.copyOf(visibleSections), level.getGameTime())
        );
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        ImagPhaseRenderState state = event.getLevelRenderState().getRenderData(CONTEXT_KEY);
        if (state == null || state.sections().isEmpty()) {
            return;
        }

        var camera = event.getLevelRenderState().cameraRenderState.pos;
        var collector = event.getSubmitNodeCollector();
        state.sections().forEach(section -> submitBackground(
                section,
                event.getPoseStack(),
                collector,
                camera.x,
                camera.y,
                camera.z
        ));

        // The normal particle pass is submitted before custom opaque geometry. Buffer the
        // colored stars into the final post phase so the black surface can never overwrite them.
        for (int layer = 0; layer < STAR_LAYERS.length; layer++) {
            VertexConsumer starBuffer = PostEffect.getPost().getBuffer(STAR_LAYERS[layer]);
            int finalLayer = layer;
            state.sections().forEach(section -> bufferStars(
                    section,
                    event.getPoseStack(),
                    starBuffer,
                    finalLayer,
                    state.animationTick(),
                    camera.x,
                    camera.y,
                    camera.z
            ));
        }
    }

    private static void submitBackground(
            SectionSurfaces section,
            PoseStack poseStack,
            net.minecraft.client.renderer.SubmitNodeCollector collector,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        BlockPos origin = section.origin();
        List<FluidSurface> surfaces = section.surfaces();

        poseStack.pushPose();
        poseStack.translate(origin.getX() - cameraX, origin.getY() - cameraY, origin.getZ() - cameraZ);
        collector.submitCustomGeometry(poseStack, BLACK_BACKGROUND, (pose, buffer) -> {
            for (FluidSurface surface : surfaces) {
                surface.renderBackground(pose.pose(), buffer);
            }
        });
        poseStack.popPose();
    }

    private static void bufferStars(
            SectionSurfaces section,
            PoseStack poseStack,
            VertexConsumer buffer,
            int layer,
            long animationTick,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        BlockPos origin = section.origin();
        List<FluidSurface> surfaces = section.surfaces();

        poseStack.pushPose();
        poseStack.translate(origin.getX() - cameraX, origin.getY() - cameraY, origin.getZ() - cameraZ);
        for (FluidSurface surface : surfaces) {
            surface.renderStars(poseStack.last().pose(), buffer, layer, animationTick);
        }
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

    private record ImagPhaseRenderState(List<SectionSurfaces> sections, long animationTick) {
        private static final ImagPhaseRenderState EMPTY = new ImagPhaseRenderState(List.of(), 0L);
    }

    private record SectionSurfaces(BlockPos origin, List<FluidSurface> surfaces) {
    }

    private record FluidSurface(
            BlockPos pos,
            float northWest,
            float northEast,
            float southEast,
            float southWest,
            int faceMask
    ) {
        private static final float BACKGROUND_OFFSET = 0.002F;
        private static final float STAR_OFFSET = 0.008F;
        private static final int STARS_PER_SURFACE = 7;
        private static final int[][] STAR_COLORS = {
                {245, 144, 144},
                {178, 232, 243},
                {209, 170, 225},
                {194, 238, 138},
                {255, 190, 139},
                {238, 174, 220}
        };

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

        private void renderBackground(Matrix4fc pose, VertexConsumer buffer) {
            float x = pos.getX() & 15;
            float y = pos.getY() & 15;
            float z = pos.getZ() & 15;
            if (has(Direction.UP)) {
                quad(buffer, pose, 0, 1, 0,
                        x, y + northWest - BACKGROUND_OFFSET, z,
                        x, y + southWest - BACKGROUND_OFFSET, z + 1,
                        x + 1, y + southEast - BACKGROUND_OFFSET, z + 1,
                        x + 1, y + northEast - BACKGROUND_OFFSET, z);
            }
            if (has(Direction.DOWN)) {
                quad(buffer, pose, 0, -1, 0,
                        x, y + BACKGROUND_OFFSET, z + 1,
                        x, y + BACKGROUND_OFFSET, z,
                        x + 1, y + BACKGROUND_OFFSET, z,
                        x + 1, y + BACKGROUND_OFFSET, z + 1);
            }
            if (has(Direction.NORTH)) {
                quad(buffer, pose, 0, 0, -1,
                        x + 1, y + northEast, z + BACKGROUND_OFFSET,
                        x + 1, y, z + BACKGROUND_OFFSET,
                        x, y, z + BACKGROUND_OFFSET,
                        x, y + northWest, z + BACKGROUND_OFFSET);
            }
            if (has(Direction.SOUTH)) {
                quad(buffer, pose, 0, 0, 1,
                        x, y + southWest, z + 1 - BACKGROUND_OFFSET,
                        x, y, z + 1 - BACKGROUND_OFFSET,
                        x + 1, y, z + 1 - BACKGROUND_OFFSET,
                        x + 1, y + southEast, z + 1 - BACKGROUND_OFFSET);
            }
            if (has(Direction.WEST)) {
                quad(buffer, pose, -1, 0, 0,
                        x + BACKGROUND_OFFSET, y + northWest, z,
                        x + BACKGROUND_OFFSET, y, z,
                        x + BACKGROUND_OFFSET, y, z + 1,
                        x + BACKGROUND_OFFSET, y + southWest, z + 1);
            }
            if (has(Direction.EAST)) {
                quad(buffer, pose, 1, 0, 0,
                        x + 1 - BACKGROUND_OFFSET, y + southEast, z + 1,
                        x + 1 - BACKGROUND_OFFSET, y, z + 1,
                        x + 1 - BACKGROUND_OFFSET, y, z,
                        x + 1 - BACKGROUND_OFFSET, y + northEast, z);
            }
        }

        private void renderStars(Matrix4fc pose, VertexConsumer buffer, int layer, long animationTick) {
            if (!has(Direction.UP)) {
                return;
            }

            float blockX = pos.getX() & 15;
            float blockY = pos.getY() & 15;
            float blockZ = pos.getZ() & 15;
            long baseSeed = mix(pos.asLong());
            for (int index = 0; index < STARS_PER_SURFACE; index++) {
                long seed = mix(baseSeed + index * 0x9E3779B97F4A7C15L);
                if ((seed & 3L) != layer) {
                    continue;
                }

                float u = 0.08F + randomUnit(seed) * 0.84F;
                float v = 0.08F + randomUnit(seed + 1L) * 0.84F;
                float northHeight = lerp(u, northWest, northEast);
                float southHeight = lerp(u, southWest, southEast);
                float surfaceHeight = lerp(v, northHeight, southHeight);

                float phase = randomUnit(seed + 2L) * ((float) Math.PI * 2.0F);
                float pulse = 0.78F + 0.22F * (float) Math.sin(animationTick * 0.18F + phase);
                float size = (0.035F + randomUnit(seed + 3L) * 0.075F) * pulse;
                float angle = phase + animationTick * (0.004F + randomUnit(seed + 4L) * 0.006F);
                float cos = (float) Math.cos(angle) * size;
                float sin = (float) Math.sin(angle) * size;
                float centerX = blockX + u;
                float centerY = blockY + surfaceHeight + STAR_OFFSET;
                float centerZ = blockZ + v;
                int[] color = STAR_COLORS[(int) Math.floorMod(seed >>> 8, STAR_COLORS.length)];

                starVertex(buffer, pose, centerX - cos + sin, centerY, centerZ - sin - cos,
                        0.0F, 0.0F, color);
                starVertex(buffer, pose, centerX - cos - sin, centerY, centerZ - sin + cos,
                        0.0F, 1.0F, color);
                starVertex(buffer, pose, centerX + cos - sin, centerY, centerZ + sin + cos,
                        1.0F, 1.0F, color);
                starVertex(buffer, pose, centerX + cos + sin, centerY, centerZ + sin - cos,
                        1.0F, 0.0F, color);
            }
        }

        private static float lerp(float delta, float start, float end) {
            return start + delta * (end - start);
        }

        private static long mix(long value) {
            value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
            value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
            return value ^ (value >>> 31);
        }

        private static float randomUnit(long seed) {
            return (float) ((mix(seed) >>> 40) & 0xFFFFFFL) / 0xFFFFFF;
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
                float normalX,
                float normalY,
                float normalZ,
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
            vertex(buffer, pose, x0, y0, z0, 0.0F, 0.0F, normalX, normalY, normalZ);
            vertex(buffer, pose, x1, y1, z1, 0.0F, 1.0F, normalX, normalY, normalZ);
            vertex(buffer, pose, x2, y2, z2, 1.0F, 1.0F, normalX, normalY, normalZ);
            vertex(buffer, pose, x3, y3, z3, 1.0F, 0.0F, normalX, normalY, normalZ);
        }

        private static void vertex(
                VertexConsumer buffer,
                Matrix4fc pose,
                float x,
                float y,
                float z,
                float u,
                float v,
                float normalX,
                float normalY,
                float normalZ
        ) {
            buffer.addVertex(pose, x, y, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0x00F000F0)
                    .setNormal(normalX, normalY, normalZ);
        }

        private static void starVertex(
                VertexConsumer buffer,
                Matrix4fc pose,
                float x,
                float y,
                float z,
                float u,
                float v,
                int[] color
        ) {
            buffer.addVertex(pose, x, y, z)
                    .setColor(color[0], color[1], color[2], 255)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0x00F000F0)
                    .setNormal(0.0F, 1.0F, 0.0F);
        }
    }
}
