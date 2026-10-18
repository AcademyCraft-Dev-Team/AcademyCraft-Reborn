package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
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
import org.academy.internal.client.renderer.entity.state.NativeStructureProjectionRenderState;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.internal.client.structure.NativeStructureMiningClient;
import org.joml.Quaternionf;

import java.util.HashMap;
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
            || Boolean.getBoolean("academy.sublevelClientSmoke");
    private static final Map<Integer, Submission> SMOKE_SUBMISSIONS = new HashMap<>();
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
            state.updateCracks(NativeStructureMiningClient.cracks(entity), Minecraft.getInstance().getModelManager());
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
        for (var cell : state.blocks) {
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
            SMOKE_SUBMISSIONS.put(state.projectionEntityId, new Submission(
                    previous == null ? 1 : previous.frames() + 1, state.runtimeRevision,
                    (previous == null ? 0 : previous.chestFrames()) + (state.chests.isEmpty() ? 0 : 1),
                    maximumChestOpenness,
                    (previous == null ? 0 : previous.crackFrames()) + (maximumCrackStage < 0 ? 0 : 1), maximumCrackStage));
        }
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

    private record Submission(long frames, long revision, long chestFrames, float chestOpenness, long crackFrames, int crackStage) {
    }
}
