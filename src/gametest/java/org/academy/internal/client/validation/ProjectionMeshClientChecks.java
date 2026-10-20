package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.client.renderer.entity.state.NativeStructureProjectionRenderState;
import org.academy.internal.common.structure.space.NativeStructureView;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Client-model regression checks for a disposable scene probe; never writes blocks or entities. */
public final class ProjectionMeshClientChecks {
    private ProjectionMeshClientChecks() {}

    public static void run(ClientLevel level) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                .startsWith("native-sublevel-scene-smoke-")) {
            throw new IllegalStateException("Mesh checks require the disposable scene-smoke directory");
        }
        var origin = minecraft.player == null ? new Vec3(0, 90, 0) : minecraft.player.position();
        var pose = StructurePose.identity(origin);
        var adjacent = view(List.of(
                new NativeStructureView.Cell(BlockPos.ZERO, Blocks.STONE.defaultBlockState()),
                new NativeStructureView.Cell(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState())));
        var adjacentState = extract(adjacent, level, pose);
        require(quads(adjacentState) == 10, "Adjacent cubes must omit only their two touching faces");
        var sparse = view(List.of(
                new NativeStructureView.Cell(BlockPos.ZERO, Blocks.STONE.defaultBlockState()),
                new NativeStructureView.Cell(new BlockPos(2, 0, 0), Blocks.STONE.defaultBlockState())));
        require(quads(extract(sparse, level, pose)) == 12, "A gap in a structure must retain its facing surfaces");

        var cells = List.of(
                new NativeStructureView.Cell(BlockPos.ZERO, Blocks.DIRT.defaultBlockState()),
                new NativeStructureView.Cell(new BlockPos(2, 0, 0), Blocks.GRASS_BLOCK.defaultBlockState()),
                new NativeStructureView.Cell(new BlockPos(4, 0, 0), Blocks.OAK_LEAVES.defaultBlockState()),
                new NativeStructureView.Cell(new BlockPos(6, 0, 0), Blocks.GLASS.defaultBlockState()),
                new NativeStructureView.Cell(new BlockPos(8, 0, 0), Blocks.STONE_SLAB.defaultBlockState()),
                new NativeStructureView.Cell(new BlockPos(10, 0, 0), Blocks.CHEST.defaultBlockState()));
        var mixed = view(cells);
        var state = extract(mixed, level, pose);
        require(state.unbatchedBlocks.isEmpty(), "Grass, leaves, glass and slabs must not fall back per block");
        require(!state.solidBatches.isEmpty() && !state.translucentBatches.isEmpty(),
                "Opaque and translucent geometry need separate material batches");
        require(state.chests.size() == 1, "Chest animation must remain on its dedicated model path");
        var all = new ArrayList<NativeStructureProjectionRenderState.BatchRenderData>();
        all.addAll(state.solidBatches);
        all.addAll(state.cutoutBatches);
        all.addAll(state.translucentBatches);
        boolean grassTinted = false;
        boolean leavesTinted = false;
        boolean slabSeen = false;
        for (var batch : all) for (var quad : batch.quads) {
            double x = (quad.position0().x() + quad.position1().x() + quad.position2().x() + quad.position3().x()) / 4;
            int tintIndex = quad.materialInfo().tintIndex();
            if (x >= 2 && x <= 3 && tintIndex >= 0) {
                int expected = minecraft.getBlockColors().getTintSources(Blocks.GRASS_BLOCK.defaultBlockState())
                        .get(tintIndex).colorInWorld(Blocks.GRASS_BLOCK.defaultBlockState(), level,
                                BlockPos.containing(origin.add(2.5, 0.5, 0.5)));
                require(batch.tint(tintIndex) == expected, "Grass batch must retain its host biome tint");
                grassTinted = true;
            }
            if (x >= 4 && x <= 5 && tintIndex >= 0) {
                require(batch.tint(tintIndex) != -1, "Leaves must retain their registered tint");
                leavesTinted = true;
            }
            if (x >= 8 && x <= 9) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    require(quad.position(vertex).y() <= .50001, "Bottom slabs must keep their model height");
                }
                slabSeen = true;
            }
        }
        require(grassTinted && leavesTinted && slabSeen, "Expected mixed-material geometry was not compiled");
        verifyVertexLighting(state);
        org.academy.internal.client.renderer.entity.ProjectionBatchVertexChecks.run(state);
        verifyExposedFaceLighting(level, origin);
        var geometry = state.solidBatches.getFirst().quads;
        state.updateBlocks(mixed, level, new StructurePose(origin.add(3, 2, 1),
                new Quaterniond().rotateY(Math.PI / 3)), ignored -> 0);
        require(state.solidBatches.getFirst().quads == geometry, "A pose change must reuse local geometry");
        state.updateCracks(Map.of(new BlockPos(2, 0, 0), 4), minecraft.getModelManager());
        require(state.cracks.size() == 1 && state.cracks.getFirst().stage() == 4,
                "A batched grass block must still produce its vanilla crack model");

        boolean ambientOcclusion = minecraft.options.ambientOcclusion().get();
        try {
            minecraft.options.ambientOcclusion().set(!ambientOcclusion);
            state.updateBlocks(mixed, level, pose, ignored -> 0);
            require(state.solidBatches.getFirst().quads != geometry,
                    "Changing ambient occlusion must invalidate the cached lighting geometry");
        } finally {
            minecraft.options.ambientOcclusion().set(ambientOcclusion);
        }
        AcademyCraft.LOGGER.info("PROJECTION_MESH_CHECKS PASS adjacent=10 sparse=12 mixed_fallback=0 "
                + "grass_tint=true leaves_tint=true glass_sorted_layer=true slab_height=true "
                + "pose_reuse=true crack_stage=4 ao_invalidation=true vanilla_vertex_light=true exposed_face_light=true "
                + "submitted_uv=true submitted_light=true submitted_position=true outline_fallback=true");
    }

    private static void verifyExposedFaceLighting(ClientLevel level, Vec3 origin) {
        var center = BlockPos.containing(origin);
        for (int dx = -24; dx <= 24; dx += 4) for (int dz = -24; dz <= 24; dz += 4) {
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            var pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (!level.getBlockState(pos).isSolidRender()
                    || LightCoordsUtil.getLightCoords(level, pos) != 0
                    || LightCoordsUtil.getLightCoords(level, pos.above()) == 0) continue;
            var state = extract(view(List.of(new NativeStructureView.Cell(BlockPos.ZERO,
                    Blocks.STONE.defaultBlockState()))), level, StructurePose.identity(Vec3.atLowerCornerOf(pos)));
            verifyVertexLighting(state);
            boolean lit = false;
            for (var batch : state.solidBatches) for (int index = 0; index < batch.quads.size(); index++) {
                if (batch.quads.get(index).direction() != Direction.UP) continue;
                for (int vertex = 0; vertex < 4; vertex++) lit |= batch.vertexLight(index, vertex) != 0;
            }
            require(lit, "A lit exposed face must not inherit the opaque host cell's zero center light");
            return;
        }
        throw new AssertionError("Scene probe needs a loaded opaque surface with dark center and lit upper face");
    }

    /** Compare actual lightmap coordinates, including AO face/corner samples, with vanilla. */
    private static void verifyVertexLighting(NativeStructureProjectionRenderState state) {
        var minecraft = Minecraft.getInstance();
        var renderer = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), true,
                minecraft.getBlockColors());
        var expected = new HashMap<String, int[]>();
        for (var block : state.blocks) {
            var input = block.renderState();
            var local = block.localPosition();
            var model = minecraft.getModelManager().getBlockStateModelSet().get(input.blockState);
            renderer.tesselateBlock((x, y, z, quad, instance) -> {
                expected.put(quadKey(quad, local.getX() + x, local.getY() + y, local.getZ() + z),
                        new int[]{instance.getLightCoords(0), instance.getLightCoords(1),
                                instance.getLightCoords(2), instance.getLightCoords(3)});
            }, 0, 0, 0, input, local, input.blockState, model, input.blockState.getSeed(local));
        }
        var batches = new ArrayList<>(state.solidBatches);
        batches.addAll(state.cutoutBatches);
        batches.addAll(state.translucentBatches);
        int compared = 0;
        for (var batch : batches) for (int quadIndex = 0; quadIndex < batch.quads.size(); quadIndex++) {
            var quad = batch.quads.get(quadIndex);
            var light = expected.get(quadKey(quad, 0, 0, 0));
            require(light != null, "Batched quad must have a corresponding vanilla lighting sample");
            for (int vertex = 0; vertex < 4; vertex++) {
                require(batch.vertexLight(quadIndex, vertex) == light[vertex],
                        "Batched lightmap must match vanilla at quad " + quadIndex + ", vertex " + vertex
                                + ": actual=" + batch.vertexLight(quadIndex, vertex) + ", expected=" + light[vertex]);
                compared++;
            }
        }
        require(compared > 0, "Lighting comparison must inspect actual vertices");
    }

    private static String quadKey(BakedQuad quad, float x, float y, float z) {
        var key = new StringBuilder().append(quad.direction()).append(':').append(quad.materialInfo().tintIndex());
        for (int vertex = 0; vertex < 4; vertex++) {
            var position = quad.position(vertex);
            key.append(':').append(Float.floatToIntBits(position.x() + x))
                    .append(':').append(Float.floatToIntBits(position.y() + y))
                    .append(':').append(Float.floatToIntBits(position.z() + z))
                    .append(':').append(quad.packedUV(vertex));
        }
        return key.toString();
    }

    private static NativeStructureView view(List<NativeStructureView.Cell> cells) {
        return new NativeStructureView(cells);
    }

    private static NativeStructureProjectionRenderState extract(NativeStructureView view, ClientLevel level,
                                                                 StructurePose pose) {
        var state = new NativeStructureProjectionRenderState();
        state.updateBlocks(view, level, pose, ignored -> 0);
        return state;
    }

    private static int quads(NativeStructureProjectionRenderState state) {
        int count = 0;
        for (var batch : state.solidBatches) count += batch.quads.size();
        for (var batch : state.cutoutBatches) count += batch.quads.size();
        for (var batch : state.translucentBatches) count += batch.quads.size();
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
