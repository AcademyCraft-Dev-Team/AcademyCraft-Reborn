package org.academy.internal.client.ability.mentalout;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.entitycontrol.BlockWorkRegion;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the temporary RTS camera, up to nine cached target feeds, and client-only hidden blocks. */
public final class WideAreaInterferenceClientState {
    public static final int MAX_TARGET_VIEWS = 9;
    public static final int WHITE_OUTLINE = 0xFFFFFFFF;
    public static final int CONTROLLED_OUTLINE = 0xFFFF7A18;
    public static final int SELECTED_OUTLINE = 0xFF35D45A;
    private static final float GOD_PITCH = 62.0f;
    private static final int MAX_HIDDEN_BLOCKS = 32_768;
    private static final LinkedHashMap<UUID, ViewFrame> VIEW_FRAMES = new LinkedHashMap<>();
    private static final Map<UUID, CameraSmoothingBridge.CameraSmoothingState> CAMERA_SMOOTHING_STATES = new HashMap<>();
    private static final Set<Long> HIDDEN_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SELECTED_TARGETS = new HashSet<>();
    private static final Matrix4f FRAME_VIEW = new Matrix4f();
    private static final Matrix4f FRAME_PROJECTION = new Matrix4f();
    private static final Matrix4f FRAME_INVERSE_VIEW = new Matrix4f();
    private static final Matrix4f FRAME_INVERSE_PROJECTION = new Matrix4f();
    private static Mode mode = Mode.NONE;
    private static Entity originalCamera;
    private static CameraType originalCameraType;
    private static UUID captureTargetUuid;
    private static int captureTargetEntityId = -1;
    private static int captureIndex;
    private static CapturePass capturePass = CapturePass.NONE;
    private static @Nullable TextureTarget playerFrameDisplay;
    private static @Nullable TextureTarget playerFrameCapture;
    private static boolean playerFrameCaptured;
    private static boolean playerFramePending;
    private static Vec3 godFocus = Vec3.ZERO;
    private static float godYaw = 45.0f;
    private static double godDistance = 24.0;
    private static double godTargetDistance = 24.0;
    private static double moveForward;
    private static double moveRight;
    private static double velocityForward;
    private static double velocityRight;
    private static Vec3 frameCameraPosition = Vec3.ZERO;
    private static boolean frameCameraValid;
    private static boolean originalHudHidden;
    private static boolean hudVisibilityCaptured;
    private static @Nullable Bounds hiddenBounds;
    private static @Nullable Bounds workPreviewBounds;

    private WideAreaInterferenceClientState() {
    }

    public static void open() {
        if (mode != Mode.NONE) return;
        var minecraft = Minecraft.getInstance();
        CAMERA_SMOOTHING_STATES.clear();
        originalCamera = minecraft.getCameraEntity();
        originalCameraType = minecraft.options.getCameraType();
        var player = minecraft.player;
        if (player != null) {
            godFocus = player.position().add(0.0, 1.0, 0.0);
            godYaw = player.getYRot();
        }
        godDistance = godTargetDistance = 24.0;
        originalHudHidden = minecraft.gui.hud.isHidden();
        hudVisibilityCaptured = true;
        ensureHudHidden(minecraft);
        mode = Mode.RTS;
        applyRtsCamera(minecraft);
    }

    public static void setViewedTargets(Collection<MentaloutRosterClientState.Entry> entries) {
        var retained = new LinkedHashSet<UUID>();
        for (var entry : entries) {
            if (retained.size() >= MAX_TARGET_VIEWS) break;
            retained.add(entry.targetUuid());
            var existing = VIEW_FRAMES.get(entry.targetUuid());
            if (existing == null) {
                VIEW_FRAMES.put(entry.targetUuid(), new ViewFrame(entry.entityId()));
            } else {
                existing.entityId = entry.entityId();
            }
        }
        for (var id : List.copyOf(VIEW_FRAMES.keySet())) {
            if (!retained.contains(id)) {
                var removed = VIEW_FRAMES.remove(id);
                if (removed != null) removed.close();
            }
        }
        if (VIEW_FRAMES.isEmpty() && mode == Mode.TARGETS) showRts();
        captureIndex = Math.min(captureIndex, Math.max(0, VIEW_FRAMES.size() - 1));
        if (mode == Mode.TARGETS && captureTargetUuid != null
                && !retained.contains(captureTargetUuid)) {
            schedulePlayerCapture(Minecraft.getInstance());
        }
    }

    public static boolean showTargetViews() {
        if (VIEW_FRAMES.isEmpty()) return false;
        mode = Mode.TARGETS;
        captureIndex = 0;
        schedulePlayerCapture(Minecraft.getInstance());
        return true;
    }

    public static void showRts() {
        var minecraft = Minecraft.getInstance();
        if (mode == Mode.NONE) open();
        mode = Mode.RTS;
        captureTargetUuid = null;
        captureTargetEntityId = -1;
        capturePass = CapturePass.NONE;
        applyRtsCamera(minecraft);
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        if (mode == Mode.NONE || minecraft.player == null || minecraft.level == null) return;
        ensureHudHidden(minecraft);
        if (mode == Mode.RTS) {
            var acceleration = 0.42 * Math.max(0.75, godDistance / 24.0);
            velocityForward = (velocityForward + moveForward * acceleration) * 0.72;
            velocityRight = (velocityRight + moveRight * acceleration) * 0.72;
            pan(velocityForward, velocityRight);
            godDistance += (godTargetDistance - godDistance) * 0.28;
            applyRtsCamera(minecraft);
            return;
        }
        if (VIEW_FRAMES.isEmpty()) {
            showRts();
            return;
        }
        if (capturePass == CapturePass.NONE) schedulePlayerCapture(minecraft);
    }

    public static void setMovementInput(double forward, double right) {
        moveForward = Math.clamp(forward, -1.0, 1.0);
        moveRight = Math.clamp(right, -1.0, 1.0);
    }

    public static void close() {
        if (mode == Mode.NONE) return;
        clearHiddenBlocks();
        for (var frame : VIEW_FRAMES.values()) frame.close();
        VIEW_FRAMES.clear();
        destroyPlayerFrames();
        playerFrameCaptured = false;
        playerFramePending = false;
        SELECTED_TARGETS.clear();
        workPreviewBounds = null;
        var minecraft = Minecraft.getInstance();
        var fallback = minecraft.player;
        var restored = originalCamera != null && !originalCamera.isRemoved() ? originalCamera : fallback;
        if (originalCameraType != null) minecraft.options.setCameraType(originalCameraType);
        if (restored != null) switchCameraEntity(minecraft, restored);
        CAMERA_SMOOTHING_STATES.clear();
        mode = Mode.NONE;
        originalCamera = null;
        originalCameraType = null;
        captureTargetUuid = null;
        captureTargetEntityId = -1;
        capturePass = CapturePass.NONE;
        moveForward = moveRight = velocityForward = velocityRight = 0.0;
        frameCameraValid = false;
        if (hudVisibilityCaptured && minecraft.gui.hud.isHidden() != originalHudHidden) {
            minecraft.gui.hud.toggle();
        }
        hudVisibilityCaptured = false;
    }

    public static boolean isRtsView() {
        return mode == Mode.RTS;
    }

    public static boolean isTargetView() {
        return mode == Mode.TARGETS;
    }

    public static boolean hasTargetViews() {
        return !VIEW_FRAMES.isEmpty();
    }

    public static List<UUID> viewedTargets() {
        return List.copyOf(VIEW_FRAMES.keySet());
    }

    public static @Nullable GpuTextureView targetFrame(UUID targetUuid) {
        var frame = VIEW_FRAMES.get(targetUuid);
        return frame == null || !frame.captured || frame.target == null
                ? null : frame.target.getColorTextureView();
    }

    public static @Nullable GpuTextureView playerFrame() {
        return !playerFrameCaptured || playerFrameDisplay == null
                ? null : playerFrameDisplay.getColorTextureView();
    }

    public static boolean hasCameraOverride() {
        return mode == Mode.RTS;
    }

    public static boolean shouldOutline(Entity entity) {
        return mode == Mode.RTS && entity != null && entity.isAlive() && !entity.isRemoved()
                && Minecraft.getInstance().player != entity;
    }

    public static int outlineColor(Entity entity) {
        if (SELECTED_TARGETS.contains(entity.getUUID())) return SELECTED_OUTLINE;
        return MentaloutRosterClientState.isControlledTarget(entity.getUUID())
                ? CONTROLLED_OUTLINE : WHITE_OUTLINE;
    }

    public static void setSelectedTargets(Collection<UUID> targetIds) {
        SELECTED_TARGETS.clear();
        if (targetIds != null) SELECTED_TARGETS.addAll(targetIds);
    }

    public static void setWorkRegionPreview(
            BlockPos first,
            BlockPos second,
            int height,
            int verticalOffset
    ) {
        if (first == null || second == null) {
            workPreviewBounds = null;
            return;
        }
        var topY = Math.min(first.getY(), second.getY()) + verticalOffset;
        var clampedHeight = Math.clamp(height, 1, BlockWorkRegion.MAX_AXIS_LENGTH);
        workPreviewBounds = new Bounds(
                new BlockPos(
                        Math.min(first.getX(), second.getX()),
                        topY - clampedHeight + 1,
                        Math.min(first.getZ(), second.getZ())),
                new BlockPos(
                        Math.max(first.getX(), second.getX()),
                        topY,
                        Math.max(first.getZ(), second.getZ()))
        );
    }

    public static void clearWorkRegionPreview() {
        workPreviewBounds = null;
    }

    public static Vec3 cameraPosition() {
        var forward = Vec3.directionFromRotation(GOD_PITCH, godYaw);
        return godFocus.subtract(forward.scale(godDistance));
    }

    public static float cameraYaw() {
        return godYaw;
    }

    public static float cameraPitch() {
        return GOD_PITCH;
    }

    public static void pan(double forwardAmount, double rightAmount) {
        if (mode != Mode.RTS) return;
        var yaw = Math.toRadians(godYaw);
        var forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        var right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        godFocus = godFocus.add(forward.scale(forwardAmount)).add(right.scale(rightAmount));
    }

    /** Caches the exact matrices used by the most recently rendered world frame. */
    public static void captureRenderCamera(
            Vec3 position,
            Matrix4fc viewRotation,
            Matrix4fc projection
    ) {
        if (mode != Mode.RTS || position == null || viewRotation == null || projection == null) return;
        frameCameraPosition = position;
        FRAME_VIEW.set(viewRotation);
        FRAME_PROJECTION.set(projection);
        FRAME_INVERSE_VIEW.set(viewRotation).invert();
        FRAME_INVERSE_PROJECTION.set(projection).invert();
        frameCameraValid = true;
    }

    public static @Nullable ScreenProjection projectWorld(
            Vec3 point,
            double screenWidth,
            double screenHeight
    ) {
        if (!frameCameraValid || point == null || screenWidth <= 0.0 || screenHeight <= 0.0) return null;
        var clip = new Vector4f(
                (float) (point.x - frameCameraPosition.x),
                (float) (point.y - frameCameraPosition.y),
                (float) (point.z - frameCameraPosition.z),
                1.0f
        ).mul(FRAME_VIEW).mul(FRAME_PROJECTION);
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y)
                || !Float.isFinite(clip.w) || clip.w <= 1.0e-5f) return null;
        var inverseW = 1.0 / clip.w;
        return new ScreenProjection(
                (clip.x * inverseW + 1.0) * screenWidth * 0.5,
                (1.0 - clip.y * inverseW) * screenHeight * 0.5,
                clip.w
        );
    }

    public static @Nullable Vec3 rayDirection(
            double mouseX,
            double mouseY,
            double screenWidth,
            double screenHeight
    ) {
        if (!frameCameraValid || screenWidth <= 0.0 || screenHeight <= 0.0) return null;
        var ndcX = (float) (mouseX * 2.0 / screenWidth - 1.0);
        var ndcY = (float) (1.0 - mouseY * 2.0 / screenHeight);
        var viewPoint = new Vector4f(ndcX, ndcY, 1.0f, 1.0f).mul(FRAME_INVERSE_PROJECTION);
        if (!Float.isFinite(viewPoint.w) || Math.abs(viewPoint.w) <= 1.0e-5f) return null;
        viewPoint.div(viewPoint.w);
        var worldDirection = new Vector4f(viewPoint.x, viewPoint.y, viewPoint.z, 0.0f)
                .mul(FRAME_INVERSE_VIEW);
        var direction = new Vec3(worldDirection.x, worldDirection.y, worldDirection.z);
        return direction.lengthSqr() <= 1.0e-10 ? null : direction.normalize();
    }

    private static void ensureHudHidden(Minecraft minecraft) {
        if (!minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
    }

    public static void panFromMouse(double deltaX, double deltaY) {
        if (mode != Mode.RTS) return;
        var scale = Math.max(0.025, godDistance / 900.0);
        pan(deltaY * scale, -deltaX * scale);
    }

    public static void rotate(float amount) {
        if (mode == Mode.RTS) godYaw += amount;
    }

    public static void zoom(double amount) {
        if (mode == Mode.RTS) {
            godTargetDistance = Math.clamp(godTargetDistance + amount, 8.0, 64.0);
        }
    }

    public static void hideRegion(BlockPos first, BlockPos second) {
        if (first == null || second == null) return;
        var min = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        var max = new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
        var count = 0;
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (count++ >= MAX_HIDDEN_BLOCKS) break;
            HIDDEN_BLOCKS.add(pos.asLong());
        }
        hiddenBounds = hiddenBounds == null ? new Bounds(min, max) : hiddenBounds.union(min, max);
        rebuildHiddenBounds();
    }

    public static void clearHiddenBlocks() {
        if (HIDDEN_BLOCKS.isEmpty()) return;
        var oldBounds = hiddenBounds;
        HIDDEN_BLOCKS.clear();
        hiddenBounds = null;
        rebuild(oldBounds);
    }

    public static boolean isBlockHidden(BlockPos pos) {
        return pos != null && HIDDEN_BLOCKS.contains(pos.asLong());
    }

    private static boolean scheduleTargetCapture(Minecraft minecraft) {
        if (minecraft.level == null || VIEW_FRAMES.isEmpty()) return false;
        var entries = List.copyOf(VIEW_FRAMES.entrySet());
        if (captureIndex >= entries.size()) captureIndex = 0;
        for (var attempt = 0; attempt < entries.size(); attempt++) {
            var selected = entries.get(captureIndex);
            captureIndex = (captureIndex + 1) % entries.size();
            var entity = minecraft.level.getEntity(selected.getValue().entityId);
            if (entity == null || entity.isRemoved() || !selected.getKey().equals(entity.getUUID())) {
                selected.getValue().invalid = true;
                continue;
            }
            selected.getValue().invalid = false;
            captureTargetUuid = selected.getKey();
            captureTargetEntityId = entity.getId();
            capturePass = CapturePass.TARGET;
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            switchCameraEntity(minecraft, entity);
            return true;
        }
        schedulePlayerCapture(minecraft);
        return false;
    }

    private static void schedulePlayerCapture(Minecraft minecraft) {
        capturePass = CapturePass.PLAYER;
        captureTargetUuid = null;
        captureTargetEntityId = -1;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        if (minecraft.player != null) switchCameraEntity(minecraft, minecraft.player);
    }

    private static void applyRtsCamera(Minecraft minecraft) {
        if (minecraft.player == null) return;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        if (minecraft.getCameraEntity() != minecraft.player) switchCameraEntity(minecraft, minecraft.player);
    }

    private static void switchCameraEntity(Minecraft minecraft, Entity entity) {
        var camera = minecraft.gameRenderer.mainCamera();
        if (!(camera instanceof CameraSmoothingBridge bridge)) {
            minecraft.setCameraEntity(entity);
            return;
        }

        var current = camera.entity();
        if (current != null) {
            CAMERA_SMOOTHING_STATES.put(current.getUUID(), bridge.academy$captureSmoothingState());
        }
        minecraft.setCameraEntity(entity);
        var state = CAMERA_SMOOTHING_STATES.computeIfAbsent(
                entity.getUUID(),
                ignored -> initialCameraState(minecraft, entity)
        );
        bridge.academy$restoreSmoothingState(state);
    }

    private static CameraSmoothingBridge.CameraSmoothingState initialCameraState(
            Minecraft minecraft,
            Entity entity
    ) {
        var eyeHeight = entity.getEyeHeight();
        var fovModifier = 1.0f;
        if (entity instanceof AbstractClientPlayer player) {
            fovModifier = player.getFieldOfViewModifier(
                    minecraft.options.getCameraType().isFirstPerson(),
                    minecraft.options.fovEffectScale().get().floatValue()
            );
        }
        return new CameraSmoothingBridge.CameraSmoothingState(
                eyeHeight,
                eyeHeight,
                fovModifier,
                fovModifier
        );
    }

    private static void captureCurrentFrame() {
        if (mode != Mode.TARGETS || capturePass == CapturePass.NONE) return;
        publishPendingPlayerFrame();
        var minecraft = Minecraft.getInstance();
        var main = minecraft.gameRenderer.mainRenderTarget();
        var source = main.getColorTexture();
        if (source == null) {
            schedulePlayerCapture(minecraft);
            return;
        }
        if (capturePass == CapturePass.PLAYER) {
            if (minecraft.player != null && minecraft.getCameraEntity() == minecraft.player) {
                ensurePlayerFrameSize(main.width, main.height);
                var destination = playerFrameCapture == null ? null : playerFrameCapture.getColorTexture();
                if (destination != null) {
                    copyFrame(source, destination, main.width, main.height);
                    playerFramePending = true;
                }
            }
            if (!scheduleTargetCapture(minecraft)) schedulePlayerCapture(minecraft);
            return;
        }

        var frame = captureTargetUuid == null ? null : VIEW_FRAMES.get(captureTargetUuid);
        if (frame != null && !frame.invalid && minecraft.getCameraEntity() != null
                && minecraft.getCameraEntity().getId() == captureTargetEntityId) {
            frame.ensureSize(main.width, main.height);
            var destination = frame.target == null ? null : frame.target.getColorTexture();
            if (destination != null) {
                copyFrame(source, destination, main.width, main.height);
                frame.captured = true;
            }
        }
        schedulePlayerCapture(minecraft);
    }

    private static void ensurePlayerFrameSize(int width, int height) {
        if (playerFrameDisplay == null || playerFrameCapture == null) {
            destroyPlayerFrames();
            playerFrameDisplay = new TextureTarget(
                    "WideAreaInterference player background display",
                    width, height, false, GpuFormat.RGBA8_UNORM);
            playerFrameCapture = new TextureTarget(
                    "WideAreaInterference player background capture",
                    width, height, false, GpuFormat.RGBA8_UNORM);
            playerFrameCaptured = false;
            playerFramePending = false;
        } else if (playerFrameDisplay.width != width || playerFrameDisplay.height != height
                || playerFrameCapture.width != width || playerFrameCapture.height != height) {
            playerFrameDisplay.resize(width, height);
            playerFrameCapture.resize(width, height);
            playerFrameCaptured = false;
            playerFramePending = false;
        }
    }

    private static void publishPendingPlayerFrame() {
        if (!playerFramePending || playerFrameDisplay == null || playerFrameCapture == null) return;
        var previousDisplay = playerFrameDisplay;
        playerFrameDisplay = playerFrameCapture;
        playerFrameCapture = previousDisplay;
        playerFramePending = false;
        playerFrameCaptured = true;
    }

    private static void destroyPlayerFrames() {
        if (playerFrameDisplay != null) playerFrameDisplay.destroyBuffers();
        if (playerFrameCapture != null && playerFrameCapture != playerFrameDisplay) {
            playerFrameCapture.destroyBuffers();
        }
        playerFrameDisplay = null;
        playerFrameCapture = null;
    }

    private static void copyFrame(
            com.mojang.blaze3d.textures.GpuTexture source,
            com.mojang.blaze3d.textures.GpuTexture destination,
            int width,
            int height
    ) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                source, destination, 0, 0, 0, 0, 0, width, height);
    }

    private static void rebuildHiddenBounds() {
        rebuild(hiddenBounds);
    }

    private static void rebuild(@Nullable Bounds bounds) {
        var level = Minecraft.getInstance().level;
        if (level == null || bounds == null) return;
        level.setSectionRangeDirty(
                SectionPos.blockToSectionCoord(bounds.minimum.getX()),
                SectionPos.blockToSectionCoord(bounds.minimum.getY()),
                SectionPos.blockToSectionCoord(bounds.minimum.getZ()),
                SectionPos.blockToSectionCoord(bounds.maximum.getX()),
                SectionPos.blockToSectionCoord(bounds.maximum.getY()),
                SectionPos.blockToSectionCoord(bounds.maximum.getZ()));
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLevelRender(LevelRenderEvent event) {
            var bounds = workPreviewBounds;
            if (mode != Mode.RTS || bounds == null) return;
            var matrixStack = event.getMatrixStack();
            var camera = event.getCameraPosition();
            var box = new AABB(
                    bounds.minimum.getX(), bounds.minimum.getY(), bounds.minimum.getZ(),
                    bounds.maximum.getX() + 1.0,
                    bounds.maximum.getY() + 1.0,
                    bounds.maximum.getZ() + 1.0
            ).inflate(0.003);
            matrixStack.pushPose();
            matrixStack.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
            event.submitCustomGeometry(Render.RenderTypes.MINE_DETECT_LINES,
                    (snapshot, consumer) -> LineBoxRenderer.renderWireframeBox(
                            snapshot, consumer, box, 1.0f, 1.0f, 1.0f, 1.0f));
            matrixStack.popPose();
        }

        @SubscribeEvent
        public static void onLevelRendered(RenderLevelStageEvent.AfterLevel event) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof WideAreaInterferenceScreen) captureCurrentFrame();
        }

        @SubscribeEvent
        public static void onResizeDisplay(ResizeDisplayEvent event) {
            for (var frame : VIEW_FRAMES.values()) frame.resize(event.getWidth(), event.getHeight());
            if (playerFrameDisplay != null && playerFrameCapture != null) {
                playerFrameDisplay.resize(event.getWidth(), event.getHeight());
                playerFrameCapture.resize(event.getWidth(), event.getHeight());
                playerFrameCaptured = false;
                playerFramePending = false;
            }
        }
    }

    private static final class ViewFrame {
        private int entityId;
        private @Nullable TextureTarget target;
        private boolean captured;
        private boolean invalid;

        private ViewFrame(int entityId) {
            this.entityId = entityId;
        }

        private void ensureSize(int width, int height) {
            if (target == null) {
                target = new TextureTarget(
                        "WideAreaInterference target feed", width, height, false, GpuFormat.RGBA8_UNORM);
            } else if (target.width != width || target.height != height) {
                target.resize(width, height);
                captured = false;
            }
        }

        private void resize(int width, int height) {
            if (target != null) {
                target.resize(width, height);
                captured = false;
            }
        }

        private void close() {
            if (target != null) target.destroyBuffers();
            target = null;
            captured = false;
        }
    }

    private record Bounds(BlockPos minimum, BlockPos maximum) {
        private Bounds union(BlockPos first, BlockPos second) {
            return new Bounds(
                    new BlockPos(
                            Math.min(minimum.getX(), first.getX()),
                            Math.min(minimum.getY(), first.getY()),
                            Math.min(minimum.getZ(), first.getZ())),
                    new BlockPos(
                            Math.max(maximum.getX(), second.getX()),
                            Math.max(maximum.getY(), second.getY()),
                            Math.max(maximum.getZ(), second.getZ())));
        }
    }

    public record ScreenProjection(double x, double y, double depth) {
    }

    private enum Mode {
        NONE,
        RTS,
        TARGETS
    }

    private enum CapturePass {
        NONE,
        PLAYER,
        TARGET
    }
}
