package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.space.NativeStructureView;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** One-node opaque batch with exterior-face culling for fully solid views. */
final class BatchedBlockGeometry {
    private static final Map<NativeStructureView, BlockStateModelPart> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BatchedBlockGeometry() {
    }

    static BlockStateModelPart buildCached(NativeStructureView view, BlockAndTintGetter level) {
        return CACHE.computeIfAbsent(view, ignored -> build(view, level));
    }

    static boolean eligible(NativeStructureView view) {
        if (!view.solid() || view.cells().size() < 512) return false;
        for (var cell : view.cells()) {
            BlockState state = cell.state();
            if (state.getRenderShape() != RenderShape.MODEL || state.hasBlockEntity()
                    || !state.canOcclude() || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    static BlockStateModelPart build(NativeStructureView view, BlockAndTintGetter level) {
        var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        var random = RandomSource.createThreadLocalInstance();
        var quads = new ArrayList<BakedQuad>();
        BlockStateModelPart source = null;
        var bounds = view.bounds();
        for (var cell : view.cells()) {
            var local = cell.position();
            var state = cell.state();
            random.setSeed(state.getSeed(local));
            var parts = new ArrayList<BlockStateModelPart>();
            models.get(state).collectParts(level, local, state, random, parts);
            for (var part : parts) {
                if (source == null) source = part;
                for (var direction : Direction.values()) {
                    var neighbor = local.relative(direction);
                    if (neighbor.getX() >= (int) bounds.minX && neighbor.getX() < (int) bounds.maxX
                            && neighbor.getY() >= (int) bounds.minY && neighbor.getY() < (int) bounds.maxY
                            && neighbor.getZ() >= (int) bounds.minZ && neighbor.getZ() < (int) bounds.maxZ) continue;
                    for (var quad : part.getQuads(direction)) quads.add(translate(quad, local));
                }
                for (var quad : part.getQuads(null)) quads.add(translate(quad, local));
            }
        }
        AcademyCraft.LOGGER.debug("SubLevel mesh batch cells={} quads={}", view.cells().size(), quads.size());
        return source == null ? null : new SinglePart(List.copyOf(quads), source);
    }

    private static BakedQuad translate(BakedQuad quad, BlockPos offset) {
        return new BakedQuad(
                new Vector3f(quad.position0()).add(offset.getX(), offset.getY(), offset.getZ()),
                new Vector3f(quad.position1()).add(offset.getX(), offset.getY(), offset.getZ()),
                new Vector3f(quad.position2()).add(offset.getX(), offset.getY(), offset.getZ()),
                new Vector3f(quad.position3()).add(offset.getX(), offset.getY(), offset.getZ()),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                quad.direction(), quad.materialInfo(), quad.bakedNormals(), quad.bakedColors());
    }

    private record SinglePart(List<BakedQuad> quads, BlockStateModelPart source) implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(Direction direction) {
            if (direction == null) return quads;
            var filtered = new ArrayList<BakedQuad>();
            for (var quad : quads) if (quad.direction() == direction) filtered.add(quad);
            return filtered;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public Material.Baked particleMaterial() {
            return source.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return source.materialFlags();
        }
    }
}
