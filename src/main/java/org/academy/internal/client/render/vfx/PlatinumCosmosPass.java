package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.academy.AcademyCraft;
import org.academy.api.client.compatibility.IrisCompat;
import org.joml.Matrix4fc;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PlatinumCosmosPass {
    private static final PerFrameRenderQueue<ThirdPersonInstance> WORLD_QUEUE = new PerFrameRenderQueue<>();
    private static final SubmitNodeStorage WORLD_STORAGE = new SubmitNodeStorage();
    private static final SubmitNodeStorage HAND_STORAGE = new SubmitNodeStorage();
    private static final SubmitNodeStorage HIDDEN_HUD_STORAGE = new SubmitNodeStorage();
    private static final AtomicBoolean WORLD_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean HIDDEN_HUD_FAILURE_LOGGED = new AtomicBoolean();
    private static boolean worldPassAvailable = true;
    private static boolean hiddenHudPassAvailable = true;

    private PlatinumCosmosPass() {
    }

    public static void beginFrame(ClientLevel level) {
        WORLD_QUEUE.beginFrame(level);
    }

    public static void clear() {
        WORLD_QUEUE.clear();
        drain(WORLD_STORAGE);
        drain(HAND_STORAGE);
        drain(HIDDEN_HUD_STORAGE);
    }

    public static PlatinumCosmosRenderMode worldMode() {
        return PlatinumCosmosRenderMode.select(IrisCompat.isShaderPackInUse(), worldPassAvailable);
    }

    public static PlatinumCosmosRenderMode handMode() {
        return PlatinumCosmosRenderMode.select(
                IrisCompat.isShaderPackInUse(), IrisCompat.isHandBridgeMounted()
        );
    }

    static PlatinumCosmosRenderMode hiddenHudMode() {
        return PlatinumCosmosRenderMode.select(
                IrisCompat.isShaderPackInUse(), hiddenHudPassAvailable
        );
    }

    public static void enqueueThirdPerson(PoseStack poseStack, int entityId, double currentTick, float effectTime) {
        var snapshot = new PoseStack();
        snapshot.last().set(poseStack.last());
        WORLD_QUEUE.add(new ThirdPersonInstance(snapshot, entityId, currentTick, effectTime));
    }

    public static void renderWorld(FeatureRenderDispatcher dispatcher, Matrix4fc modelViewMatrix) {
        var minecraft = Minecraft.getInstance();
        var instances = WORLD_QUEUE.consume(minecraft.level);
        if (instances.isEmpty() || worldMode() != PlatinumCosmosRenderMode.EXACT) return;

        try {
            for (var instance : instances) {
                WingVfx.submitThirdPersonCosmos(
                        instance.poseStack(), WORLD_STORAGE, instance.entityId(),
                        instance.currentTick(), instance.effectTime()
                );
            }
            withModelView(modelViewMatrix,
                    () -> dispatcher.renderAllFeatures(WORLD_STORAGE));
        } catch (Throwable throwable) {
            worldPassAvailable = false;
            if (WORLD_FAILURE_LOGGED.compareAndSet(false, true)) {
                AcademyCraft.getLogger().warn(
                        "Platinum-wing world cosmos pass failed; using the visible textured fallback.",
                        throwable
                );
            }
        } finally {
            drain(WORLD_STORAGE);
        }
    }

    public static void renderFirstPersonHand(FeatureRenderDispatcher dispatcher, float partialTick) {
        if (!IrisCompat.isShaderPackInUse()) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;

        try {
            var submitted = WingVfx.submitFirstPersonCosmos(
                    new PoseStack(), HAND_STORAGE, player,
                    minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick),
                    partialTick
            );
            if (submitted) {
                dispatcher.renderAllFeatures(HAND_STORAGE);
            }
        } catch (Throwable throwable) {
            IrisCompat.markHandBridgeFailed(throwable);
        } finally {
            drain(HAND_STORAGE);
        }
    }

    public static void renderFirstPersonWithHiddenHud(FeatureRenderDispatcher dispatcher, float partialTick) {
        var mode = hiddenHudMode();
        if (mode == PlatinumCosmosRenderMode.NORMAL) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;

        try {
            var poseStack = new PoseStack();
            var packedLight = minecraft.getEntityRenderDispatcher()
                    .getPackedLightCoords(player, partialTick);
            var submitted = mode == PlatinumCosmosRenderMode.EXACT
                    ? WingVfx.submitFirstPersonCosmos(
                    poseStack, HIDDEN_HUD_STORAGE, player, packedLight, partialTick
            )
                    : WingVfx.submitFirstPersonFallback(
                    poseStack, HIDDEN_HUD_STORAGE, player, packedLight, partialTick
            );
            if (submitted) {
                dispatcher.renderAllFeatures(HIDDEN_HUD_STORAGE);
            }
        } catch (Throwable throwable) {
            hiddenHudPassAvailable = false;
            if (HIDDEN_HUD_FAILURE_LOGGED.compareAndSet(false, true)) {
                AcademyCraft.getLogger().warn(
                        "Platinum-wing hidden-HUD cosmos pass failed; using the visible textured fallback.",
                        throwable
                );
            }
        } finally {
            drain(HIDDEN_HUD_STORAGE);
        }
    }

    private static void drain(SubmitNodeStorage storage) {
        storage.drainPhases(ignored -> {
        });
    }

    private static void withModelView(Matrix4fc modelViewMatrix, Runnable action) {
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            modelViewStack.set(modelViewMatrix);
            action.run();
        } finally {
            modelViewStack.popMatrix();
        }
    }

    private record ThirdPersonInstance(
            PoseStack poseStack, int entityId, double currentTick, float effectTime
    ) {
    }
}
