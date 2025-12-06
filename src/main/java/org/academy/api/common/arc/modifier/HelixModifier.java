package org.academy.api.common.arc.modifier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.PathModifierType;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PathFrame;
import org.academy.api.common.arc.data.PropertyType;
import org.academy.internal.common.arc.PathModifierTypes;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public record HelixModifier(float radius, float turns, float phaseOffset) implements PathModifier {
    public static final StreamCodec<ByteBuf, HelixModifier> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, HelixModifier::radius,
            ByteBufCodecs.FLOAT, HelixModifier::turns,
            ByteBufCodecs.FLOAT, HelixModifier::phaseOffset,
            HelixModifier::new
    );

    @Override
    public PathData apply(PathData data, float time) {
        var originalFrames = data.getFrames();
        if (originalFrames.size() < 2 || radius <= 1.0E-6f || Math.abs(turns) <= 1.0E-6f) {
            return data;
        }

        var totalLength = 0.0f;
        for (var i = 0; i < originalFrames.size() - 1; i++) {
            totalLength += originalFrames.get(i).position().distance(originalFrames.get(i + 1).position());
        }
        if (totalLength <= 1.0E-6f) {
            return data;
        }

        List<Vector3f> newPositions = new ArrayList<>(originalFrames.size());
        var distanceTraveled = 0.0f;

        for (var i = 0; i < originalFrames.size(); i++) {
            var frame = originalFrames.get(i);
            var progress = distanceTraveled / totalLength;
            var angle = phaseOffset + turns * 2.0f * (float) Math.PI * progress;

            var binormal = new Vector3f(frame.tangent()).cross(frame.normal());
            var offset = new Vector3f(frame.normal()).mul((float) Math.cos(angle) * radius)
                    .add(binormal.mul((float) Math.sin(angle) * radius));

            newPositions.add(new Vector3f(frame.position()).add(offset));

            if (i < originalFrames.size() - 1) {
                distanceTraveled += frame.position().distance(originalFrames.get(i + 1).position());
            }
        }

        List<PathFrame> newFrames = new ArrayList<>(originalFrames.size());
        for (var i = 0; i < newPositions.size(); i++) {
            var pos = newPositions.get(i);
            Vector3f tangent;
            if (i < newPositions.size() - 1) {
                tangent = new Vector3f(newPositions.get(i + 1)).sub(pos);
                if (tangent.lengthSquared() < 1.0E-6f && i > 0) {
                    tangent.set(newFrames.get(i - 1).tangent());
                } else {
                    tangent.normalize();
                }
            } else {
                tangent = new Vector3f(newFrames.get(i - 1).tangent());
            }

            var radialDir = new Vector3f(pos).sub(originalFrames.get(i).position());
            if (radialDir.lengthSquared() < 1.0E-6f) {
                radialDir.set(originalFrames.get(i).normal());
            }

            var normal = new Vector3f(tangent).cross(radialDir).cross(tangent).normalize();
            if (normal.lengthSquared() < 1.0E-6f) {
                normal.set(originalFrames.get(i).normal());
            }

            newFrames.add(new PathFrame(pos, tangent, normal));
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
        return PathModifierTypes.HELIX.get();
    }
}