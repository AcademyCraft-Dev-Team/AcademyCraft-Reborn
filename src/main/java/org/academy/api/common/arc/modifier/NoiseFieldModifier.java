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
import org.academy.api.common.util.ImprovedNoise;
import org.academy.internal.common.arc.PathModifierTypes;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public record NoiseFieldModifier(AttributeCurve strength, float scale, float speed, long seed) implements PathModifier {
    public static final StreamCodec<ByteBuf, NoiseFieldModifier> CODEC = StreamCodec.composite(
            AttributeCurve.CODEC, NoiseFieldModifier::strength,
            ByteBufCodecs.FLOAT, NoiseFieldModifier::scale,
            ByteBufCodecs.FLOAT, NoiseFieldModifier::speed,
            ByteBufCodecs.LONG, NoiseFieldModifier::seed,
            NoiseFieldModifier::new
    );

    @Override
    public PathData apply(PathData data, float time) {
        var originalFrames = data.getFrames();
        if (originalFrames.size() < 2) {
            return data;
        }

        var frameCount = originalFrames.size();
        List<PathFrame> newFrames = new ArrayList<>(frameCount);
        var noiseTime = (time + seed) * speed;

        for (var i = 0; i < frameCount; i++) {
            var frame = originalFrames.get(i);
            var progress = (float) i / (frameCount - 1);
            var currentStrength = strength.evaluate(progress);

            if (currentStrength <= 1.0E-6f) {
                newFrames.add(frame);
                continue;
            }

            var pos = frame.position();
            var offsetX = ImprovedNoise.noise(pos.x() * scale, pos.y() * scale, pos.z() * scale + noiseTime);
            var offsetY = ImprovedNoise.noise(pos.x() * scale + 100, pos.y() * scale, pos.z() * scale + noiseTime);
            var offsetZ = ImprovedNoise.noise(pos.x() * scale, pos.y() * scale + 100, pos.z() * scale + noiseTime);

            var offset = new Vector3f((float) offsetX, (float) offsetY, (float) offsetZ).mul(currentStrength);
            var newPosition = new Vector3f(pos).add(offset);
            newFrames.add(new PathFrame(newPosition, frame.tangent(), frame.normal()));
        }

        var newData = new PathData(newFrames);
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
        return PathModifierTypes.NOISE_FIELD.get();
    }
}