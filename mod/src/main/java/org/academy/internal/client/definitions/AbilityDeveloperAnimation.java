package org.academy.internal.client.definitions;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public final class AbilityDeveloperAnimation {
    public static final AnimationDefinition OPENING = AnimationDefinition.Builder.withLength(0.75F)
            .addAnimation("L1", scale(
                    keyframe(0.5F, 1.0F, 1.0F, 1.0F),
                    keyframe(0.75F, 1.0F, 0.0F, 1.0F)
            ))
            .addAnimation("R1", scale(
                    keyframe(0.5F, 1.0F, 1.0F, 1.0F),
                    keyframe(0.75F, 1.0F, 0.0F, 1.0F)
            ))
            .addAnimation("L2", scale(
                    keyframe(0.25F, 1.0F, 1.0F, 1.0F),
                    keyframe(0.5F, 0.0F, 0.0F, 1.0F)
            ))
            .addAnimation("L3", scale(
                    keyframe(0.0F, 1.0F, 1.0F, 1.0F),
                    keyframe(0.25F, 0.0F, 1.0F, 1.0F)
            ))
            .addAnimation("R2", scale(
                    keyframe(0.25F, 1.0F, 1.0F, 1.0F),
                    keyframe(0.5F, 0.0F, 0.0F, 1.0F)
            ))
            .addAnimation("R3", scale(
                    keyframe(0.0F, 1.0F, 1.0F, 1.0F),
                    keyframe(0.25F, 0.0F, 1.0F, 1.0F)
            ))
            .build();

    public static final AnimationDefinition CLOSING = AnimationDefinition.Builder.withLength(0.75F)
            .addAnimation("L1", scale(
                    keyframe(0.0F, 1.0F, 0.0F, 1.0F),
                    keyframe(0.25F, 1.0F, 1.0F, 1.0F)
            ))
            .addAnimation("R1", scale(
                    keyframe(0.0F, 1.0F, 0.0F, 1.0F),
                    keyframe(0.25F, 1.0F, 1.0F, 1.0F)
            ))
            .addAnimation("L2", scale(
                    keyframe(0.25F, 0.0F, 0.0F, 1.0F),
                    keyframe(0.5F, 1.0F, 1.0F, 1.0F)
            ))
            .addAnimation("L3", scale(
                    keyframe(0.5F, 0.0F, 1.0F, 1.0F),
                    keyframe(0.75F, 1.0F, 1.0F, 1.0F)
            ))
            .addAnimation("R2", scale(
                    keyframe(0.25F, 0.0F, 0.0F, 1.0F),
                    keyframe(0.5F, 1.0F, 1.0F, 1.0F)
            ))
            .addAnimation("R3", scale(
                    keyframe(0.5F, 0.0F, 1.0F, 1.0F),
                    keyframe(0.75F, 1.0F, 1.0F, 1.0F)
            ))
            .build();

    public static final AnimationDefinition STANDING = AnimationDefinition.Builder.withLength(1.5F)
            .addAnimation("poles", rotation(
                    rotationKeyframe(0.0F, 0.0F, 0.0F, 0.0F),
                    rotationKeyframe(0.625F, -18.83F, 0.0F, 0.0F),
                    rotationKeyframe(1.0417F, -39.72F, 0.0F, 0.0F),
                    rotationKeyframe(1.2917F, -47.83F, 0.0F, 0.0F),
                    rotationKeyframe(1.5F, -50.0F, 0.0F, 0.0F)
            ))
            .addAnimation("poles", scale(
                    keyframe(0.0F, 1.0F, 1.0F, 0.5F),
                    keyframe(0.375F, 1.0F, 1.0F, 0.525F),
                    keyframe(0.5F, 1.0F, 1.0F, 0.4F),
                    keyframe(0.75F, 1.0F, 1.0F, 0.75F),
                    keyframe(1.5F, 1.0F, 1.0F, 1.2F)
            ))
            .addAnimation("up", rotation(
                    rotationKeyframe(0.0F, 0.0F, 0.0F, 0.0F),
                    rotationKeyframe(0.5F, 0.0F, 0.0F, 0.0F),
                    rotationKeyframe(1.5F, 60.0F, 0.0F, 0.0F)
            ))
            .addAnimation("up", position(
                    positionKeyframe(0.0F, 0.0F, 0.0F, 0.0F),
                    positionKeyframe(0.2083F, 0.0F, 1.0F, 0.0F),
                    positionKeyframe(0.5F, 0.0F, 1.0F, 0.0F),
                    positionKeyframe(1.5F, 0.0F, 1.0F, 2.0F)
            ))
            .build();

    public static final AnimationDefinition LYING_DOWN = AnimationDefinition.Builder.withLength(1.5F)
            .addAnimation("poles", rotation(
                    rotationKeyframe(0.0F, -50.0F, 0.0F, 0.0F),
                    rotationKeyframe(0.2083F, -47.83F, 0.0F, 0.0F),
                    rotationKeyframe(0.4583F, -39.72F, 0.0F, 0.0F),
                    rotationKeyframe(0.875F, -18.83F, 0.0F, 0.0F),
                    rotationKeyframe(1.5F, 0.0F, 0.0F, 0.0F)
            ))
            .addAnimation("poles", scale(
                    keyframe(0.0F, 1.0F, 1.0F, 1.2F),
                    keyframe(0.75F, 1.0F, 1.0F, 0.75F),
                    keyframe(1.0F, 1.0F, 1.0F, 0.4F),
                    keyframe(1.125F, 1.0F, 1.0F, 0.525F),
                    keyframe(1.5F, 1.0F, 1.0F, 0.5F)
            ))
            .addAnimation("up", rotation(
                    rotationKeyframe(0.0F, 60.0F, 0.0F, 0.0F),
                    rotationKeyframe(1.0F, 0.0F, 0.0F, 0.0F),
                    rotationKeyframe(1.5F, 0.0F, 0.0F, 0.0F)
            ))
            .addAnimation("up", position(
                    positionKeyframe(0.0F, 0.0F, 1.0F, 2.0F),
                    positionKeyframe(1.0F, 0.0F, 1.0F, 0.0F),
                    positionKeyframe(1.2917F, 0.0F, 1.0F, 0.0F),
                    positionKeyframe(1.5F, 0.0F, 0.0F, 0.0F)
            ))
            .build();

    private static AnimationChannel scale(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.SCALE, keyframes);
    }

    private static AnimationChannel rotation(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, keyframes);
    }

    private static AnimationChannel position(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.POSITION, keyframes);
    }

    private static Keyframe keyframe(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe rotationKeyframe(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe positionKeyframe(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private AbilityDeveloperAnimation() {
    }
}
