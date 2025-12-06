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
    public PathData apply(PathData data, float time) {
        var originalFrames = data.getFrames();
        if (originalFrames.size() < 2) {
            return data;
        }

        var frameCount = originalFrames.size();
        List<PathFrame> displacedFrames = new ArrayList<>(frameCount);
        var random = new Random(seed + (int) time);

        displacedFrames.add(originalFrames.getFirst());

        for (var i = 1; i < frameCount - 1; ++i) {
            var currentFrame = originalFrames.get(i);
            var progress = (float) i / (frameCount - 1);

            var currentStrength = strength.evaluate(progress);
            if (currentStrength <= 1.0E-6f) {
                displacedFrames.add(currentFrame);
                continue;
            }

            var currentFrequency = frequency.evaluate(progress);

            var binormal = new Vector3f(currentFrame.tangent()).cross(currentFrame.normal()).normalize();

            var noiseFactor = 1.0f - (random.nextFloat() * (1.0f - currentFrequency));
            var magnitude = currentStrength * noiseFactor;

            var angle = random.nextFloat() * 2.0f * (float) Math.PI;
            var cosAngle = (float) Math.cos(angle);
            var sinAngle = (float) Math.sin(angle);

            var offset = new Vector3f(currentFrame.normal()).mul(cosAngle)
                    .add(binormal.mul(sinAngle))
                    .mul(magnitude);

            var newPosition = new Vector3f(currentFrame.position()).add(offset);
            displacedFrames.add(new PathFrame(newPosition, currentFrame.tangent(), currentFrame.normal()));
        }

        displacedFrames.add(originalFrames.getLast());

        var newData = new PathData(displacedFrames);
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