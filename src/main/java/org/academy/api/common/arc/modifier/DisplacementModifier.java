package org.academy.api.common.arc.modifier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.PathModifierType;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PathFrame;
import org.academy.api.common.arc.data.PropertyType;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.internal.common.arc.PathModifierTypes;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record DisplacementModifier(AttributeCurve strength, AttributeCurve frequency, long seed) implements PathModifier {
    public static final StreamCodec<ByteBuf, DisplacementModifier> CODEC = StreamCodec.composite(
            AttributeCurve.CODEC, DisplacementModifier::strength,
            AttributeCurve.CODEC, DisplacementModifier::frequency,
            ByteBufCodecs.LONG, DisplacementModifier::seed,
            DisplacementModifier::new
    );

    @Override
    public PathData apply(PathData data, int tick) {
        List<PathFrame> originalFrames = data.getFrames();
        if (originalFrames.size() < 2) {
            return data;
        }

        int frameCount = originalFrames.size();
        List<PathFrame> displacedFrames = new ArrayList<>(frameCount);
        Random random = new Random(this.seed + tick);

        displacedFrames.add(originalFrames.getFirst());

        for (int i = 1; i < frameCount - 1; ++i) {
            PathFrame currentFrame = originalFrames.get(i);
            float progress = (float) i / (frameCount - 1);

            float currentStrength = this.strength.evaluate(progress);
            if (currentStrength <= 1.0E-6f) {
                displacedFrames.add(currentFrame);
                continue;
            }

            float currentFrequency = this.frequency.evaluate(progress);

            Vector3f binormal = new Vector3f(currentFrame.tangent()).cross(currentFrame.normal()).normalize();

            float noiseFactor = 1.0f - (random.nextFloat() * (1.0f - currentFrequency));
            float magnitude = currentStrength * noiseFactor;

            float angle = random.nextFloat() * 2.0f * (float) Math.PI;
            float cosAngle = (float) Math.cos(angle);
            float sinAngle = (float) Math.sin(angle);

            Vector3f offset = new Vector3f(currentFrame.normal()).mul(cosAngle)
                    .add(binormal.mul(sinAngle))
                    .mul(magnitude);

            Vector3f newPosition = new Vector3f(currentFrame.position()).add(offset);

            displacedFrames.add(new PathFrame(newPosition, currentFrame.tangent(), currentFrame.normal()));
        }

        displacedFrames.add(originalFrames.getLast());

        PathData newData = new PathData(displacedFrames);
        if (data.hasProperty(PropertyType.THICKNESS)) {
            newData.setProperty(PropertyType.THICKNESS, new ArrayList<>(data.getProperty(PropertyType.THICKNESS)));
        }
        if (data.hasProperty(PropertyType.COLOR)) {
            newData.setProperty(PropertyType.COLOR, new ArrayList<>(data.getProperty(PropertyType.COLOR)));
        }

        return newData;
    }

    @Override
    public PathModifierType<? extends PathModifier> getType() {
        return PathModifierTypes.DISPLACEMENT.get();
    }
}