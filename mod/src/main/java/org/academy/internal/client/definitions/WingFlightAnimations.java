package org.academy.internal.client.definitions;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Player animation definitions authored by the user in a GeckoLib Blockbench model.
 * Source keyframe values stay unchanged; the shared keyframe helpers perform only
 * GeckoLib's standard coordinate conversion for vanilla model parts.
 */
public final class WingFlightAnimations {
    public static final AnimationDefinition START_FLYING_SLOW =
            AnimationDefinition.Builder.withLength(0.5f)
                    .addAnimation("head", rotation(
                            keyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            keyframe(0.5f, 5.0f, 0.0f, 0.0f)))
                    .addAnimation("right_arm", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            positionKeyframe(0.5f, 0.25f, -0.25f, 0.0f)))
                    .addAnimation("right_arm", rotation(
                            keyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            keyframe(0.5f, 7.1171f, -2.3518f, 24.8632f)))
                    .addAnimation("left_arm", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            positionKeyframe(0.5f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_arm", rotation(
                            keyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            keyframe(0.5f, 10.0121f, 0.544f, -17.4166f)))
                    .addAnimation("right_leg", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            positionKeyframe(0.5f, 0.25f, 1.0f, -1.0f)))
                    .addAnimation("right_leg", rotation(
                            keyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            keyframe(0.5f, -25.0f, 0.0f, 5.0f)))
                    .addAnimation("left_leg", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            positionKeyframe(0.5f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_leg", rotation(
                            keyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            keyframe(0.5f, -7.4929f, -0.3262f, -4.9786f)))
                    .addAnimation("root", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            positionKeyframe(0.5f, 0.0f, 0.0f, 1.0f)))
                    .addAnimation("root", rotation(
                            keyframe(0.0f, 0.0f, 0.0f, 0.0f),
                            keyframe(0.5f, -5.0f, 0.0f, 0.0f)))
                    .build();

    public static final AnimationDefinition FLYING_SLOW =
            AnimationDefinition.Builder.withLength(1.0f)
                    .looping()
                    .addAnimation("head", rotation(
                            keyframe(0.0f, 20.0f, 0.0f, 0.0f),
                            keyframe(1.0f, 20.0f, 0.0f, 0.0f)))
                    .addAnimation("right_arm", position(
                            positionKeyframe(0.0f, 0.25f, -0.25f, 0.0f),
                            positionKeyframe(1.0f, 0.25f, -0.25f, 0.0f)))
                    .addAnimation("right_arm", rotation(
                            keyframe(0.0f, 7.2122f, -2.0399f, 22.3809f),
                            keyframe(0.2083f, 4.7122f, -2.0399f, 22.3809f),
                            keyframe(0.4583f, 4.7964f, -1.8327f, 19.8881f),
                            keyframe(0.7083f, 9.7065f, -1.8236f, 22.4083f),
                            keyframe(1.0f, 7.2122f, -2.0399f, 22.3809f)))
                    .addAnimation("left_arm", position(
                            positionKeyframe(0.0f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(1.0f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_arm", rotation(
                            keyframe(0.0f, 10.0121f, 0.544f, -17.4166f),
                            keyframe(0.3333f, 9.9794f, 0.978f, -19.8789f),
                            keyframe(0.7083f, 15.0158f, 0.0653f, -15.9594f),
                            keyframe(1.0f, 10.0121f, 0.544f, -17.4166f)))
                    .addAnimation("right_leg", position(
                            positionKeyframe(0.0f, 0.25f, 1.0f, -1.0f),
                            positionKeyframe(0.5f, 0.25f, 1.0f, -0.5f),
                            positionKeyframe(1.0f, 0.25f, 1.0f, -1.0f)))
                    .addAnimation("right_leg", rotation(
                            keyframe(0.0f, -25.0f, 0.0f, 5.0f),
                            keyframe(0.5f, -24.9791f, 1.0563f, 7.266f),
                            keyframe(1.0f, -25.0f, 0.0f, 5.0f)))
                    .addAnimation("left_leg", position(
                            positionKeyframe(0.0f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(0.4583f, -0.25f, 0.0f, 0.25f),
                            positionKeyframe(1.0f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_leg", rotation(
                            keyframe(0.0f, -7.4929f, -0.3262f, -4.9786f),
                            keyframe(0.4583f, -9.9696f, -0.7596f, -7.4409f),
                            keyframe(1.0f, -7.4929f, -0.3262f, -4.9786f)))
                    .addAnimation("root", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 1.0f)))
                    .addAnimation("root", rotation(
                            keyframe(0.0f, -20.0f, 0.0f, 0.0f)))
                    .build();

    public static final AnimationDefinition START_FLYING_FAST =
            AnimationDefinition.Builder.withLength(1.0f)
                    .addAnimation("head", position(
                            positionKeyframe(0.0f, 0.0f, 0.25f, -0.5f)))
                    .addAnimation("head", rotation(
                            keyframe(0.0f, 5.0f, 0.0f, 0.0f),
                            keyframe(0.58333f, 2.5f, 0.0f, 0.0f),
                            keyframe(0.75f, 65.0f, 0.0f, 0.0f)))
                    .addAnimation("right_arm", position(
                            positionKeyframe(0.0f, 0.25f, -0.25f, 0.0f),
                            positionKeyframe(0.2083f, 0.25f, -0.25f, 0.0f),
                            positionKeyframe(1.0f, 0.25f, -0.25f, 0.0f)))
                    .addAnimation("right_arm", rotation(
                            keyframe(0.0f, 7.2122f, -2.0399f, 22.3809f),
                            keyframe(0.2083f, 9.6171f, -2.3518f, 24.8632f),
                            keyframe(0.5833f, 71.4879f, 15.7969f, 10.0618f),
                            keyframe(0.75f, -7.5814f, -2.1688f, 23.4155f),
                            keyframe(1.0f, 7.2937f, -1.7242f, 19.8996f)))
                    .addAnimation("left_arm", position(
                            positionKeyframe(0.0f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(0.2083f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(1.0f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_arm", rotation(
                            keyframe(0.0f, 10.0121f, 0.544f, -17.4166f),
                            keyframe(0.2083f, 12.5121f, 0.544f, -17.4166f),
                            keyframe(0.5833f, 81.1259f, -4.0183f, -9.828f),
                            keyframe(0.75f, -8.636f, -1.9916f, -16.2384f),
                            keyframe(1.0f, 0.0121f, 0.544f, -17.4166f)))
                    .addAnimation("right_leg", position(
                            positionKeyframe(0.0f, 0.25f, 1.0f, -1.0f),
                            positionKeyframe(0.2083f, 0.25f, 1.0f, -1.0f),
                            positionKeyframe(1.0f, 0.25f, 0.0f, -0.25f)))
                    .addAnimation("right_leg", rotation(
                            keyframe(0.0f, -25.0f, 0.0f, 5.0f),
                            keyframe(0.2083f, -25.0f, 0.0f, 5.0f),
                            keyframe(1.0f, -25.0f, 0.0f, 5.0f)))
                    .addAnimation("left_leg", position(
                            positionKeyframe(0.0f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(0.1667f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(1.0f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_leg", rotation(
                            keyframe(0.0f, -7.4929f, -0.3262f, -4.9786f),
                            keyframe(0.1667f, -7.4929f, -0.3262f, -4.9786f),
                            keyframe(1.0f, -12.4675f, -0.8665f, -7.4197f)))
                    .addAnimation("root", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 1.0f),
                            positionKeyframe(0.58333f, 0.0f, 0.0f, 0.0f),
                            positionKeyframe(0.70833f, 0.0f, 0.0f, 13.0f)))
                    .addAnimation("root", rotation(
                            keyframe(0.0f, -5.0f, 0.0f, 0.0f),
                            keyframe(0.58333f, -5.0f, 0.0f, 0.0f),
                            keyframe(0.75f, -72.5f, 0.0f, 0.0f)))
                    .build();

    public static final AnimationDefinition FLYING_FAST =
            AnimationDefinition.Builder.withLength(1.0f)
                    .looping()
                    .addAnimation("head", position(
                            positionKeyframe(0.0f, 0.0f, 0.25f, -0.5f),
                            positionKeyframe(1.0f, 0.0f, 0.25f, -0.5f)))
                    .addAnimation("head", rotation(
                            keyframe(0.0f, 65.0f, 0.0f, 0.0f),
                            keyframe(1.0f, 65.0f, 0.0f, 0.0f)))
                    .addAnimation("right_arm", position(
                            positionKeyframe(0.0f, 0.25f, -0.25f, 0.0f),
                            positionKeyframe(1.0f, 0.25f, -0.25f, 0.0f)))
                    .addAnimation("right_arm", rotation(
                            keyframe(0.0f, 7.2937f, -1.7242f, 19.8996f),
                            keyframe(0.2917f, 3.7172f, -1.7606f, 20.9301f),
                            keyframe(0.625f, 4.9953f, -1.508f, 17.408f),
                            keyframe(1.0f, 7.2937f, -1.7242f, 19.8996f)))
                    .addAnimation("left_arm", position(
                            positionKeyframe(0.0f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(1.0f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_arm", rotation(
                            keyframe(0.0f, 0.0121f, 0.544f, -17.4166f),
                            keyframe(0.25f, -4.9595f, 0.7608f, -14.9259f),
                            keyframe(0.7083f, -2.128f, 0.7321f, -13.831f),
                            keyframe(1.0f, 0.0121f, 0.544f, -17.4166f)))
                    .addAnimation("right_leg", position(
                            positionKeyframe(0.0f, 0.25f, 0.0f, -0.25f),
                            positionKeyframe(1.0f, 0.25f, 0.0f, -0.25f)))
                    .addAnimation("right_leg", rotation(
                            keyframe(0.0f, -25.0f, 0.0f, 5.0f),
                            keyframe(0.2917f, -24.9791f, 1.0563f, 7.266f),
                            keyframe(0.7083f, -27.4914f, 0.4349f, 5.9331f),
                            keyframe(1.0f, -25.0f, 0.0f, 5.0f)))
                    .addAnimation("left_leg", position(
                            positionKeyframe(0.0f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(1.0f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_leg", rotation(
                            keyframe(0.0f, -12.4675f, -0.8665f, -7.4197f),
                            keyframe(0.2917f, -7.4174f, -1.5112f, -9.8357f),
                            keyframe(0.6667f, -13.6575f, -0.5803f, -6.1269f),
                            keyframe(1.0f, -12.4675f, -0.8665f, -7.4197f)))
                    .addAnimation("root", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 13.0f),
                            positionKeyframe(1.0f, 0.0f, 0.0f, 13.0f)))
                    .addAnimation("root", rotation(
                            keyframe(0.0f, -72.5f, 0.0f, 0.0f),
                            keyframe(1.0f, -72.5f, 0.0f, 0.0f)))
                    .build();

    public static final AnimationDefinition QUIT_FLYING_FAST =
            AnimationDefinition.Builder.withLength(0.5f)
                    .addAnimation("head", position(
                            positionKeyframe(0.0f, 0.0f, 0.25f, -0.5f)))
                    .addAnimation("head", rotation(
                            keyframe(0.0f, 65.0f, 0.0f, 0.0f),
                            keyframe(0.16667f, 10.0f, 0.0f, 0.0f),
                            keyframe(0.5f, 20.0f, 0.0f, 0.0f)))
                    .addAnimation("right_arm", position(
                            positionKeyframe(0.0f, 0.25f, -0.25f, 0.0f),
                            positionKeyframe(0.16667f, 0.25f, -0.25f, 0.0f),
                            positionKeyframe(0.5f, 0.25f, -0.25f, 0.0f)))
                    .addAnimation("right_arm", rotation(
                            keyframe(0.0f, 7.2937f, -1.7242f, 19.8996f),
                            keyframe(0.16667f, 7.2122f, -2.0399f, 22.3809f),
                            keyframe(0.5f, 7.2122f, -2.0399f, 22.3809f)))
                    .addAnimation("left_arm", position(
                            positionKeyframe(0.0f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(0.16667f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(0.5f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_arm", rotation(
                            keyframe(0.0f, 0.0121f, 0.544f, -17.4166f),
                            keyframe(0.16667f, 10.0121f, 0.544f, -17.4166f),
                            keyframe(0.5f, 10.0121f, 0.544f, -17.4166f)))
                    .addAnimation("right_leg", position(
                            positionKeyframe(0.0f, 0.25f, 0.0f, -0.25f),
                            positionKeyframe(0.16667f, 0.25f, 1.0f, -1.0f),
                            positionKeyframe(0.5f, 0.25f, 1.0f, -1.0f)))
                    .addAnimation("right_leg", rotation(
                            keyframe(0.0f, -25.0f, 0.0f, 5.0f),
                            keyframe(0.16667f, -25.0f, 0.0f, 5.0f),
                            keyframe(0.5f, -25.0f, 0.0f, 5.0f)))
                    .addAnimation("left_leg", position(
                            positionKeyframe(0.0f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(0.16667f, -0.25f, 0.0f, 0.0f),
                            positionKeyframe(0.5f, -0.25f, 0.0f, 0.0f)))
                    .addAnimation("left_leg", rotation(
                            keyframe(0.0f, -12.4675f, -0.8665f, -7.4197f),
                            keyframe(0.16667f, -7.4929f, -0.3262f, -4.9786f),
                            keyframe(0.5f, -7.4929f, -0.3262f, -4.9786f)))
                    .addAnimation("root", position(
                            positionKeyframe(0.0f, 0.0f, 0.0f, 13.0f),
                            positionKeyframe(0.16667f, 0.0f, 0.0f, 1.0f),
                            positionKeyframe(0.5f, 0.0f, 0.0f, 1.0f)))
                    .addAnimation("root", rotation(
                            keyframe(0.0f, -72.5f, 0.0f, 0.0f),
                            keyframe(0.16667f, -10.0f, 0.0f, 0.0f),
                            keyframe(0.5f, -20.0f, 0.0f, 0.0f)))
                    .build();

    private WingFlightAnimations() {
    }

    private static AnimationChannel rotation(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, keyframes);
    }

    private static AnimationChannel position(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.POSITION, keyframes);
    }

    private static Keyframe keyframe(float time, float x, float y, float z) {
        // The supplied bbmodel uses GeckoLib animation coordinates. GeckoLib converts
        // Blockbench rotations as (-x, -y, z) before applying them to model parts.
        return new Keyframe(time, KeyframeAnimations.degreeVec(-x, -y, z),
                AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe positionKeyframe(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.posVec(x, y, z),
                AnimationChannel.Interpolations.LINEAR);
    }
}
