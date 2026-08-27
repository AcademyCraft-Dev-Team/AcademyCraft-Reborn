package org.academy.internal.client.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.academy.api.client.input.InputSystem;
import org.academy.internal.common.ability.accelerator.skills.WingFlightPose;
import org.academy.internal.common.attachment.AttachmentTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Resolves synchronized wing controls into per-avatar animation playback.
 */
public final class WingFlightAnimationClient {
    private static final Map<UUID, WingFlightAnimationTimeline> TIMELINES = new HashMap<>();
    private static final Map<Integer, WingFlightAnimationTimeline.Playback> PLAYBACKS =
            new HashMap<>();

    private WingFlightAnimationClient() {
    }

    public static void prepareRenderState(
            Avatar avatar,
            AvatarRenderState state,
            float partialTick
    ) {
        sample(avatar, partialTick);
        if (WingFlightPose.usesCompactCollision(avatar)) {
            suppressVanillaCompactPoseAnimation(state);
        }
    }

    static void suppressVanillaCompactPoseAnimation(AvatarRenderState state) {
        // FALL_FLYING supplies the one-block collision box. Without vanilla's fall-flying
        // flag, LivingEntity treats that pose as visual swimming and AvatarRenderer adds
        // another -90-degree body rotation on top of the authored Gecko animation.
        state.isVisuallySwimming = false;
        state.swimAmount = 0.0f;
    }

    public static WingFlightAnimationTimeline.Playback sample(
            Avatar avatar,
            float partialTick
    ) {
        var requested = requestedPose(avatar);
        var id = avatar.getUUID();
        var timeline = TIMELINES.computeIfAbsent(id, _ -> new WingFlightAnimationTimeline());
        var playback = timeline.update(requested, avatar.tickCount + partialTick);
        if (requested == WingFlightPose.Pose.IDLE
                && playback.phase() == WingFlightAnimationTimeline.Phase.IDLE) {
            TIMELINES.remove(id);
            PLAYBACKS.remove(avatar.getId());
        } else {
            PLAYBACKS.put(avatar.getId(), playback);
        }
        return playback;
    }

    public static WingFlightAnimationTimeline.Playback playback(int entityId) {
        return PLAYBACKS.get(entityId);
    }

    private static WingFlightPose.Pose requestedPose(Avatar avatar) {
        var minecraft = Minecraft.getInstance();
        if (avatar == minecraft.player && WingFlightPose.hasActiveWing(avatar)) {
            if (minecraft.gui.screen() != null) return WingFlightPose.Pose.IDLE;
            if (down(GLFW_KEY_SPACE)) return WingFlightPose.Pose.FAST;
            var forward = down(GLFW_KEY_W);
            var backward = down(GLFW_KEY_S);
            var left = down(GLFW_KEY_A);
            var right = down(GLFW_KEY_D);
            return forward != backward || left != right
                    ? WingFlightPose.Pose.SLOW : WingFlightPose.coastingPose(avatar);
        }
        return avatar.getData(AttachmentTypes.WING_FLIGHT_POSE.get());
    }

    private static boolean down(int key) {
        return InputSystem.isDown(InputSystem.InputType.KEYBOARD, key);
    }
}
