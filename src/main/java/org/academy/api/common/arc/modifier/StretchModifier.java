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
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public record StretchModifier(AttributeCurve strength, Vector3fc direction, float frequency) implements PathModifier {
    public static final StreamCodec<ByteBuf, StretchModifier> CODEC = StreamCodec.composite(
            AttributeCurve.CODEC, StretchModifier::strength,
            ByteBufCodecs.VECTOR3F, StretchModifier::direction,
            ByteBufCodecs.FLOAT, StretchModifier::frequency,
            StretchModifier::new
    );

    @Override
    public PathData apply(PathData data, float time) {
        List<PathFrame> originalFrames = data.getFrames();
        int frameCount = originalFrames.size();
        if (frameCount < 2) {
            return data;
        }

        float subTickProgress = time % 1.0f;
        float cycleMultiplier = (float) Math.sin(subTickProgress * 2.0 * Math.PI * this.frequency);

        if (Math.abs(cycleMultiplier) < 1.0E-6f) {
            return data;
        }

        List<PathFrame> newFrames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            PathFrame frame = originalFrames.get(i);
            float progress = (float) i / (frameCount - 1);
            float currentStrength = this.strength.evaluate(progress);

            if (currentStrength <= 1.0E-6f) {
                newFrames.add(frame);
                continue;
            }

            Vector3f binormal = new Vector3f(frame.tangent()).cross(frame.normal());
            Vector3f worldOffsetDir = new Vector3f(frame.normal()).mul(this.direction.x())
                    .add(binormal.mul(this.direction.y()))
                    .add(new Vector3f(frame.tangent()).mul(this.direction.z()));

            Vector3f finalOffset = worldOffsetDir.mul(currentStrength * cycleMultiplier);
            Vector3f newPosition = new Vector3f(frame.position()).add(finalOffset);

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
        return PathModifierTypes.STRETCH.get();
    }
}