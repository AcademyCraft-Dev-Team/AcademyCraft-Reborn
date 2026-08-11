package org.academy.internal.client.definitions;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public final class AbilityControlTabletAnimation {
    public static final AnimationDefinition OPEN = AnimationDefinition.Builder.withLength(0.4583F)
            .addAnimation("all", rotation(
                    rotationKeyframe(0.0F, 0.0F, 0.0F, 0.0F),
                    rotationKeyframe(0.0833F, 0.0F, 0.0F, -40.0F),
                    rotationKeyframe(0.2917F, 0.0F, 0.0F, -37.5F),
                    rotationKeyframe(0.375F, 0.0F, 0.0F, 0.0F)
            ))
            .addAnimation("all", position(
                    positionKeyframe(0.0F, -1.0F, 0.0F, 0.0F),
                    positionKeyframe(0.1667F, -0.83F, 2.32F, 0.0F),
                    positionKeyframe(0.25F, -0.83F, 2.32F, 0.0F),
                    positionKeyframe(0.2917F, -0.46F, -0.23F, 0.0F),
                    positionKeyframe(0.3333F, -0.23F, -0.14F, 0.0F),
                    positionKeyframe(0.375F, 0.0F, 0.0F, 0.0F)
            ))
            .addAnimation("f1", rotation(
                    rotationKeyframe(0.0F, 0.0F, 0.0F, 45.0F),
                    rotationKeyframe(0.25F, 0.0F, 0.0F, 45.0F),
                    rotationKeyframe(0.4167F, 0.0F, 0.0F, 0.0F)
            ))
            .addAnimation("f2", rotation(
                    rotationKeyframe(0.0F, 0.0F, 0.0F, -45.0F),
                    rotationKeyframe(0.3333F, 0.0F, 0.0F, -45.0F),
                    rotationKeyframe(0.4583F, 0.0F, 0.0F, 0.0F)
            ))
            .addAnimation("screen", scale(
                    scaleKeyframe(0.0F, 0.0F, 0.0F, 1.0F),
                    scaleKeyframe(0.375F, 0.0F, 0.0F, 1.0F),
                    scaleKeyframe(0.4167F, 1.0F, 1.0F, 1.0F)
            ))
            .build();

    private static AnimationChannel rotation(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, keyframes);
    }

    private static AnimationChannel position(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.POSITION, keyframes);
    }

    private static AnimationChannel scale(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.SCALE, keyframes);
    }

    private static Keyframe rotationKeyframe(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe positionKeyframe(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe scaleKeyframe(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private AbilityControlTabletAnimation() {
    }
}
