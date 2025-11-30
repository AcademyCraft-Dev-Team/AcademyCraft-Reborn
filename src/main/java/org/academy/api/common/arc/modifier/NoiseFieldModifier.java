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
import org.joml.Vector3fc;

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
        List<PathFrame> originalFrames = data.getFrames();
        if (originalFrames.size() < 2) {
            return data;
        }

        int frameCount = originalFrames.size();
        List<PathFrame> newFrames = new ArrayList<>(frameCount);
        float noiseTime = (time + this.seed) * this.speed;

        for (int i = 0; i < frameCount; i++) {
            PathFrame frame = originalFrames.get(i);
            float progress = (float) i / (frameCount - 1);
            float currentStrength = this.strength.evaluate(progress);

            if (currentStrength <= 1.0E-6f) {
                newFrames.add(frame);
                continue;
            }

            Vector3fc pos = frame.position();
            double offsetX = ImprovedNoise.noise(pos.x() * this.scale, pos.y() * this.scale, pos.z() * this.scale + noiseTime);
            double offsetY = ImprovedNoise.noise(pos.x() * this.scale + 100, pos.y() * this.scale, pos.z() * this.scale + noiseTime);
            double offsetZ = ImprovedNoise.noise(pos.x() * this.scale, pos.y() * this.scale + 100, pos.z() * this.scale + noiseTime);

            Vector3f offset = new Vector3f((float) offsetX, (float) offsetY, (float) offsetZ).mul(currentStrength);
            Vector3f newPosition = new Vector3f(pos).add(offset);
            newFrames.add(new PathFrame(newPosition, frame.tangent(), frame.normal()));
        }

        PathData newData = new PathData(newFrames);
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