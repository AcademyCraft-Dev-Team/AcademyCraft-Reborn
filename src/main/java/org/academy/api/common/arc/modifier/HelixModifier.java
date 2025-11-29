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
    public PathData apply(PathData data, int tick) {
        List<PathFrame> originalFrames = data.getFrames();
        if (originalFrames.size() < 2 || radius <= 1.0E-6f || Math.abs(turns) <= 1.0E-6f) {
            return data;
        }

        float totalLength = 0.0f;
        for (int i = 0; i < originalFrames.size() - 1; i++) {
            totalLength += originalFrames.get(i).position().distance(originalFrames.get(i + 1).position());
        }
        if (totalLength <= 1.0E-6f) {
            return data;
        }

        List<Vector3f> newPositions = new ArrayList<>(originalFrames.size());
        float distanceTraveled = 0.0f;

        for (int i = 0; i < originalFrames.size(); i++) {
            PathFrame frame = originalFrames.get(i);
            float progress = distanceTraveled / totalLength;
            float angle = this.phaseOffset + this.turns * 2.0f * (float) Math.PI * progress;

            Vector3f binormal = new Vector3f(frame.tangent()).cross(frame.normal());
            Vector3f offset = new Vector3f(frame.normal()).mul((float) Math.cos(angle) * this.radius)
                    .add(binormal.mul((float) Math.sin(angle) * this.radius));

            newPositions.add(new Vector3f(frame.position()).add(offset));

            if (i < originalFrames.size() - 1) {
                distanceTraveled += frame.position().distance(originalFrames.get(i + 1).position());
            }
        }

        List<PathFrame> newFrames = new ArrayList<>(originalFrames.size());
        for (int i = 0; i < newPositions.size(); i++) {
            Vector3f pos = newPositions.get(i);
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

            Vector3f radialDir = new Vector3f(pos).sub(originalFrames.get(i).position());
            if (radialDir.lengthSquared() < 1.0E-6f) {
                radialDir.set(originalFrames.get(i).normal());
            }

            Vector3f normal = new Vector3f(tangent).cross(radialDir).cross(tangent).normalize();
            if (normal.lengthSquared() < 1.0E-6f) {
                normal.set(originalFrames.get(i).normal());
            }

            newFrames.add(new PathFrame(pos, tangent, normal));
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
        return PathModifierTypes.HELIX.get();
    }
}