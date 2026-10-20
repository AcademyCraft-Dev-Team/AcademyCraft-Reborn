package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import org.academy.internal.common.structure.space.NativeStructureView;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Content-owned local meshes. Cells share one submission per material layer; translucent
 * vertices use the vanilla sorted moving-block render type. A pose never recompiles geometry.
 */
final class BatchedBlockGeometry {
    private static final Map<NativeStructureView, Batch> CACHE = new WeakHashMap<>();
    private static final CardinalLighting UNSHADED = new CardinalLighting(1, 1, 1, 1, 1, 1);
    private static final BlockTintSource WHITE = state -> -1;
    private static Object cachedModels;
    private static boolean cachedAmbientOcclusion;
    private static boolean cachedCutoutLeaves;

    private BatchedBlockGeometry() {
    }

    static synchronized Batch buildCached(NativeStructureView view,
                                          List<NativeStructureProjectionRenderState.BlockRenderData> blocks) {
        var minecraft = Minecraft.getInstance();
        var models = minecraft.getModelManager().getBlockStateModelSet();
        boolean ambientOcclusion = minecraft.options.ambientOcclusion().get();
        boolean cutoutLeaves = minecraft.options.cutoutLeaves().get();
        if (cachedModels != models || cachedAmbientOcclusion != ambientOcclusion
                || cachedCutoutLeaves != cutoutLeaves) {
            CACHE.clear();
            cachedModels = models;
            cachedAmbientOcclusion = ambientOcclusion;
            cachedCutoutLeaves = cutoutLeaves;
        }
        return CACHE.computeIfAbsent(view, ignored -> build(blocks, ambientOcclusion, cutoutLeaves));
    }

    private static Batch build(List<NativeStructureProjectionRenderState.BlockRenderData> blocks,
                               boolean ambientOcclusion, boolean cutoutLeaves) {
        var minecraft = Minecraft.getInstance();
        var models = minecraft.getModelManager().getBlockStateModelSet();
        var colors = minecraft.getBlockColors();
        // Bake only local ambient occlusion into vertex colors. Biome tint and directional
        // shade are sampled separately, so moving/rotating a mesh cannot freeze either one.
        var neutralColors = new BlockColors() {
            @Override
            public List<BlockTintSource> getTintSources(BlockState state) {
                return Collections.nCopies(colors.getTintSources(state).size(), WHITE);
            }
        };
        var renderer = new ModelBlockRenderer(ambientOcclusion, true, neutralColors);
        var random = RandomSource.createThreadLocalInstance();
        var cells = new ArrayList<Cell>();
        var included = new HashSet<BlockPos>();
        for (var block : blocks) {
            var input = block.renderState();
            var state = input.blockState;
            // The view explicitly audits state-only furnace/barrel/hopper models. Their BE
            // storage does not require a special draw; animated chests are extracted separately.
            if (state.hasBlockEntity() && !NativeStructureView.isSupportedState(state)
                    || !state.getFluidState().isEmpty()) continue;
            var model = models.get(state);
            random.setSeed(state.getSeed(block.localPosition()));
            var parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(input, block.localPosition(), state, random, parts);
            if (!supported(parts, colors.getTintSources(state))) continue;
            var layers = new EnumMap<ChunkSectionLayer, List<BakedQuad>>(ChunkSectionLayer.class);
            var lightLayers = new EnumMap<ChunkSectionLayer, List<LightQuad>>(ChunkSectionLayer.class);
            var lightInputs = lightingInputs(parts, ambientOcclusion,
                    state.getLightEmission(input, block.localPosition()) == 0);
            if (lightInputs == null) continue;
            boolean[] unsupportedLighting = {false};
            boolean forceOpaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, state);
            BlockQuadOutput output = (x, y, z, quad, instance) -> {
                var layer = forceOpaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
                var vertexColors = BakedColors.of(
                        ARGB.multiply(instance.getColor(0), quad.bakedColors().color(0)),
                        ARGB.multiply(instance.getColor(1), quad.bakedColors().color(1)),
                        ARGB.multiply(instance.getColor(2), quad.bakedColors().color(2)),
                        ARGB.multiply(instance.getColor(3), quad.bakedColors().color(3)));
                layers.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(translate(quad,
                        block.localPosition().getX() + x, block.localPosition().getY() + y,
                        block.localPosition().getZ() + z, vertexColors));
                var lightInput = lightInputs.get(quad);
                if (lightInput == null) unsupportedLighting[0] = true;
                else lightLayers.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(lightInput);
            };
            var lighting = input.cardinalLighting;
            input.cardinalLighting = UNSHADED;
            try {
                renderer.tesselateBlock(output, 0, 0, 0, input, block.localPosition(), state,
                        model, state.getSeed(block.localPosition()));
            } finally {
                input.cardinalLighting = lighting;
            }
            // A dynamic model that replaces its quad identities during extraction retains the
            // normal model renderer; never cache a guessed lighting contract for a custom quad.
            if (unsupportedLighting[0]) continue;
            included.add(block.localPosition());
            for (var layer : layers.entrySet()) {
                if (!layer.getValue().isEmpty()) {
                    cells.add(new Cell(block.localPosition(), layer.getKey(), List.copyOf(layer.getValue()),
                            List.copyOf(lightLayers.get(layer.getKey()))));
                }
            }
        }
        return new Batch(List.copyOf(cells), Set.copyOf(included));
    }

    /** Keep original cell-local quads for the vanilla per-vertex lighting calculation. */
    private static Map<BakedQuad, LightQuad> lightingInputs(List<BlockStateModelPart> parts,
                                                           boolean ambientOcclusion, boolean nonEmissive) {
        var result = new IdentityHashMap<BakedQuad, LightQuad>();
        boolean perPart = net.neoforged.neoforge.client.config.NeoForgeClientConfig.INSTANCE
                .handleAmbientOcclusionPerPart.getAsBoolean();
        boolean modelAo = !parts.isEmpty() && switch (parts.getFirst().ambientOcclusion()) {
            case TRUE -> true;
            case DEFAULT -> nonEmissive;
            case FALSE -> false;
        };
        for (var part : parts) {
            boolean partAo = ambientOcclusion && (perPart ? switch (part.ambientOcclusion()) {
                case TRUE -> true;
                case DEFAULT -> nonEmissive;
                case FALSE -> false;
            } : modelAo);
            for (var quad : part.getQuads(null)) {
                if (!putLightInput(result, new LightQuad(quad,
                        partAo && quad.materialInfo().ambientOcclusion(), null))) return null;
            }
            for (var face : Direction.values()) for (var quad : part.getQuads(face)) {
                boolean quadAo = partAo && quad.materialInfo().ambientOcclusion();
                // AO-disabled material quads in an AO part use CHECK_LIGHT, just as the model renderer.
                if (!putLightInput(result, new LightQuad(quad, quadAo, partAo ? null : face))) return null;
            }
        }
        return result;
    }

    private static boolean putLightInput(Map<BakedQuad, LightQuad> inputs, LightQuad value) {
        var previous = inputs.putIfAbsent(value.quad(), value);
        return previous == null || previous.ambientOcclusion() == value.ambientOcclusion()
                && previous.flatFace() == value.flatFace();
    }

    private static boolean supported(List<BlockStateModelPart> parts, List<BlockTintSource> tints) {
        for (var part : parts) {
            if (!supported(part.getQuads(null), tints)) return false;
            for (var face : Direction.values()) if (!supported(part.getQuads(face), tints)) return false;
        }
        return true;
    }

    private static boolean supported(Iterable<BakedQuad> quads, List<BlockTintSource> tints) {
        for (var quad : quads) {
            // Dynamic mod tint callbacks without a registered source retain their vanilla path.
            if (quad.materialInfo().isTinted() && tints.isEmpty()) return false;
        }
        return true;
    }

    private static BakedQuad translate(BakedQuad quad, float x, float y, float z, BakedColors colors) {
        return new BakedQuad(
                new Vector3f(quad.position0()).add(x, y, z), new Vector3f(quad.position1()).add(x, y, z),
                new Vector3f(quad.position2()).add(x, y, z), new Vector3f(quad.position3()).add(x, y, z),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                quad.direction(), quad.materialInfo(), quad.bakedNormals(), colors);
    }

    record LightQuad(BakedQuad quad, boolean ambientOcclusion, Direction flatFace) {}
    record Cell(BlockPos position, ChunkSectionLayer layer, List<BakedQuad> quads, List<LightQuad> lightQuads) {}
    record Batch(List<Cell> cells, Set<BlockPos> positions) {}
}
