package org.academy.api.common.entitycontrol;

import net.minecraft.util.Mth;

import java.util.Objects;

/**
 * A single authorized input frame for a controlled player. Frames describe input, never a
 * destination position, so vanilla movement and collision remain authoritative.
 */
public record PlayerControlFrame(
        float forward,
        float strafe,
        float yaw,
        float pitch,
        boolean jump,
        boolean sneak,
        boolean sprint,
        boolean attack,
        boolean use,
        PlayerMovementMode mode
) {
    public static final PlayerControlFrame NEUTRAL = new PlayerControlFrame(
            0.0f, 0.0f, 0.0f, 0.0f,
            false, false, false, false, false,
            PlayerMovementMode.WALK
    );

    public PlayerControlFrame {
        if (!Float.isFinite(forward) || !Float.isFinite(strafe)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Player control frame values must be finite");
        }
        forward = Mth.clamp(forward, -1.0f, 1.0f);
        strafe = Mth.clamp(strafe, -1.0f, 1.0f);
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
        mode = Objects.requireNonNull(mode, "mode");
    }

    public PlayerControlFrame withoutActions() {
        return new PlayerControlFrame(
                forward, strafe, yaw, pitch, jump, sneak, sprint,
                false, false, mode
        );
    }
}
