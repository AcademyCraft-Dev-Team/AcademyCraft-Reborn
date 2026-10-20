package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.academy.internal.client.renderer.entity.state.NativeStructureProjectionRenderState;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.internal.client.structure.NativeStructureMiningClient;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** State-only models, including an explicit single chest path, without inventory or block entities. */
public final class NativeStructureProjectionRenderer
        extends EntityRenderer<NativeStructureProjectionEntity, NativeStructureProjectionRenderState> {
    private static final boolean SMOKE_DIAGNOSTICS = Boolean.getBoolean("academy.nativeStructureProjectionSmoke")
            || Boolean.getBoolean("academy.nativeStructureChestSmoke")
            || Boolean.getBoolean("academy.nativeStructurePlacementSmoke")
            || Boolean.getBoolean("academy.nativeStructureMiningSmoke")
            || Boolean.getBoolean("academy.nativeStructurePhysicsSmoke")
            || Boolean.getBoolean("academy.nativeStructureMultiplayerSmoke")
            || Boolean.getBoolean("academy.sublevelClientSmoke")
            || Boolean.getBoolean("academy.sublevelSceneSmoke");
    private static final Map<Integer, Submission> SMOKE_SUBMISSIONS = new HashMap<>();
    private static final boolean FORCE_BLOCK_RENDERER =
            Boolean.getBoolean("academy.nativeStructureForceBlockRenderer");
    private static final java.util.Set<Integer> DIAGNOSED_MESHES = new java.util.HashSet<>();
    private final ChestModel chestModel;
    private final SpriteGetter sprites;
    private final SpriteId chestSprite;

    public NativeStructureProjectionRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
        chestModel = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
        sprites = context.getSprites();
        chestSprite = Sheets.chooseSprite(ChestRenderer.xmasTextures()
                ? ChestRenderState.ChestMaterialType.CHRISTMAS : ChestRenderState.ChestMaterialType.REGULAR,
                ChestType.SINGLE);
    }

    @Override
    public NativeStructureProjectionRenderState createRenderState() {
        return new NativeStructureProjectionRenderState();
    }

    @Override
    public void extractRenderState(NativeStructureProjectionEntity entity,
                                   NativeStructureProjectionRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        var pose = entity.renderPose(partialTick);
        // Vanilla position interpolation must not disagree with the pose used by exact picking.
        state.x = pose.worldOrigin().x;
        state.y = pose.worldOrigin().y;
        state.z = pose.worldOrigin().z;
        state.projectionEntityId = entity.getId();
        state.runtimeRevision = entity.viewState().revision();
        if (entity.level() instanceof ClientLevel level) {
            state.updateBlocks(entity.view(), level, pose, position -> entity.chestOpenness(position, partialTick));
            var cracks = new HashMap<>(NativeStructureMiningClient.cracks(entity));
            var subLevelId = org.academy.internal.client.sublevel.SubLevelClientState.subLevelOf(entity);
            if (subLevelId != null) {
                cracks.putAll(org.academy.internal.client.sublevel.SubLevelMiningClient.cracks(subLevelId, entity.view()));
            }
            state.updateCracks(cracks, Minecraft.getInstance().getModelManager());
            if (Boolean.getBoolean("academy.sublevelMeshDiagnostics") && DIAGNOSED_MESHES.size() < 256
                    && DIAGNOSED_MESHES.add(entity.getId())) {
                diagnoseMesh(state);
            }
        }
    }

    @Override
    public void submit(NativeStructureProjectionRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (state.blocks.isEmpty() && state.chests.isEmpty()) return;
        poseStack.pushPose();
        var pose = state.pose;
        poseStack.mulPose(new Quaternionf((float) pose.rotationX(), (float) pose.rotationY(),
                (float) pose.rotationZ(), (float) pose.rotationW()).normalize());
        boolean blockRenderer = usesBlockRenderer(state);
        if (!blockRenderer) {
            submitBatch(state.solidBatches, state, poseStack, nodeCollector,
                    net.minecraft.client.renderer.rendertype.RenderTypes.solidMovingBlock());
            submitBatch(state.cutoutBatches, state, poseStack, nodeCollector,
                    net.minecraft.client.renderer.rendertype.RenderTypes.cutoutMovingBlock());
            submitBatch(state.translucentBatches, state, poseStack, nodeCollector,
                    net.minecraft.client.renderer.rendertype.RenderTypes.translucentMovingBlock());
        }
        for (var cell : blockRenderer ? state.blocks : state.unbatchedBlocks) {
            var position = cell.localPosition();
            poseStack.pushPose();
            poseStack.translate(position.getX(), position.getY(), position.getZ());
            nodeCollector.submitMovingBlock(poseStack, cell.renderState(), state.outlineColor);
            poseStack.popPose();
        }
        int maximumCrackStage = -1;
        for (var crack : state.cracks) {
            poseStack.pushPose();
            var position = crack.localPosition();
            poseStack.translate(position.getX(), position.getY(), position.getZ());
            poseStack.translate(crack.modelOffset());
            nodeCollector.submitBreakingBlockModel(poseStack, crack.parts(), crack.stage());
            poseStack.popPose();
            maximumCrackStage = Math.max(maximumCrackStage, crack.stage());
        }
        float maximumChestOpenness = 0;
        for (var chest : state.chests) {
            poseStack.pushPose();
            var position = chest.localPosition;
            poseStack.translate(position.getX(), position.getY(), position.getZ());
            var crumbling = chest.crackStage < 0 || chest.crackStage > 9 ? null
                    : new ModelFeatureRenderer.CrumblingOverlay(chest.crackStage, poseStack.last().copy());
            poseStack.mulPose(ChestRenderer.modelTransformation(chest.facing));
            var closed = 1.0f - chest.openness;
            var easedOpen = 1.0f - closed * closed * closed;
            nodeCollector.submitModel(chestModel, easedOpen, poseStack, chest.lightCoords,
                    OverlayTexture.NO_OVERLAY, -1, chestSprite, sprites, state.outlineColor, crumbling);
            maximumChestOpenness = Math.max(maximumChestOpenness, chest.openness);
            maximumCrackStage = Math.max(maximumCrackStage, chest.crackStage);
            poseStack.popPose();
        }
        poseStack.popPose();
        if (SMOKE_DIAGNOSTICS) {
            var previous = SMOKE_SUBMISSIONS.get(state.projectionEntityId);
            int batchCount = (state.solidBatches.isEmpty() ? 0 : 1)
                    + (state.cutoutBatches.isEmpty() ? 0 : 1) + (state.translucentBatches.isEmpty() ? 0 : 1);
            if (blockRenderer) batchCount = 0;
            int quadCount = 0;
            for (var cell : state.solidBatches) quadCount += cell.quads.size();
            for (var cell : state.cutoutBatches) quadCount += cell.quads.size();
            for (var cell : state.translucentBatches) quadCount += cell.quads.size();
            SMOKE_SUBMISSIONS.put(state.projectionEntityId, new Submission(
                    previous == null ? 1 : previous.frames() + 1, state.runtimeRevision,
                    (previous == null ? 0 : previous.chestFrames()) + (state.chests.isEmpty() ? 0 : 1),
                    maximumChestOpenness,
                    (previous == null ? 0 : previous.crackFrames()) + (maximumCrackStage < 0 ? 0 : 1), maximumCrackStage,
                    batchCount, blockRenderer ? state.blocks.size() : state.unbatchedBlocks.size(),
                    blockRenderer ? 0 : quadCount));
        }
    }

    static boolean usesBlockRenderer(NativeStructureProjectionRenderState state) {
        // Preserve vanilla glowing/team outline output until the batch path has an outline pass.
        return FORCE_BLOCK_RENDERER || state.outlineColor != 0;
    }

    private static void submitBatch(List<NativeStructureProjectionRenderState.BatchRenderData> cells,
                                    NativeStructureProjectionRenderState state, PoseStack poseStack,
                                    SubmitNodeCollector collector,
                                    net.minecraft.client.renderer.rendertype.RenderType renderType) {
        if (cells.isEmpty()) return;
        collector.submitCustomGeometry(poseStack, renderType,
                (batchPose, buffer) -> emitBatchGeometry(cells, state, batchPose, buffer));
    }

    /** Shared with the recording-consumer regression check; use the vanilla vertex encoder. */
    static void emitBatchGeometry(List<NativeStructureProjectionRenderState.BatchRenderData> cells,
                                  NativeStructureProjectionRenderState state,
                                  PoseStack.Pose pose, VertexConsumer buffer) {
        var instance = new QuadInstance();
        for (var cell : cells) for (int quadIndex = 0; quadIndex < cell.quads.size(); quadIndex++) {
            var quad = cell.quads.get(quadIndex);
            float shade = quad.materialInfo().shade()
                    ? state.batchLighting.byFace(quad.direction()) : state.batchLighting.up();
            instance.setColor(ARGB.multiply(cell.tint(quad.materialInfo().tintIndex()), ARGB.gray(shade)));
            for (int vertex = 0; vertex < 4; vertex++) {
                instance.setLightCoords(vertex, cell.vertexLight(quadIndex, vertex));
            }
            // UVPair stores U in the high 32 bits and V in the low bits. Delegating the entire
            // vertex encoding also preserves vanilla overlay, emission and baked-normal rules.
            buffer.putBakedQuad(pose, quad, instance);
        }
    }

    private static void diagnoseMesh(NativeStructureProjectionRenderState state) {
        int vertices = 0, black = 0, transparent = 0, unlitCells = 0;
        int minColor = 255, maxColor = 0, minLight = Integer.MAX_VALUE, maxLight = 0;
        var cells = new java.util.ArrayList<>(state.solidBatches);
        cells.addAll(state.cutoutBatches);
        cells.addAll(state.translucentBatches);
        for (var cell : cells) {
            minLight = Math.min(minLight, cell.lightCoords);
            maxLight = Math.max(maxLight, cell.lightCoords);
            if (cell.lightCoords == 0) unlitCells++;
            for (var quad : cell.quads) for (int vertex = 0; vertex < 4; vertex++) {
                int color = ARGB.multiply(cell.tint(quad.materialInfo().tintIndex()), quad.bakedColors().color(vertex));
                vertices++;
                if ((color & 0xFFFFFF) == 0) black++;
                if ((color >>> 24) == 0) transparent++;
                minColor = Math.min(minColor, Math.min(ARGB.red(color), Math.min(ARGB.green(color), ARGB.blue(color))));
                maxColor = Math.max(maxColor, Math.max(ARGB.red(color), Math.max(ARGB.green(color), ARGB.blue(color))));
            }
        }
        org.academy.AcademyCraft.LOGGER.info("PROJECTION_MESH_DIAGNOSTICS entity={} origin={} cells={} "
                        + "vertices={} black_vertices={} transparent_vertices={} color_range={}:{} "
                        + "unlit_cells={} light_range={}:{} directional={} force_block_renderer={}",
                state.projectionEntityId, state.pose.worldOrigin(), cells.size(), vertices, black, transparent,
                minColor, maxColor, unlitCells, minLight, maxLight, state.batchLighting, FORCE_BLOCK_RENDERER);
    }

    /** Test-only evidence of actual nonempty submit calls, disabled in ordinary clients. */
    public static long submittedFrames(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? 0 : submission.frames();
    }

    public static long lastSubmittedRevision(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? -1 : submission.revision();
    }

    public static long submittedChestFrames(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? 0 : submission.chestFrames();
    }

    public static float lastSubmittedChestOpenness(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? 0 : submission.chestOpenness();
    }

    public static long submittedCrackFrames(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? 0 : submission.crackFrames();
    }

    public static int lastSubmittedCrackStage(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? -1 : submission.crackStage();
    }

    public static int lastSubmittedBatchCount(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? 0 : submission.batches();
    }

    public static int lastSubmittedUnbatchedCells(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? 0 : submission.unbatchedCells();
    }

    public static int lastSubmittedBatchedQuads(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? 0 : submission.batchedQuads();
    }

    private record Submission(long frames, long revision, long chestFrames, float chestOpenness, long crackFrames,
                              int crackStage, int batches, int unbatchedCells, int batchedQuads) {
    }
}
