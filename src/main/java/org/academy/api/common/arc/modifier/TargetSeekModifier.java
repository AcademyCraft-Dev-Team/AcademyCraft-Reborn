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

public record TargetSeekModifier(Vector3fc target, AttributeCurve force) implements PathModifier {
    public static final StreamCodec<ByteBuf, TargetSeekModifier> CODEC = StreamCodec.composite(
            ByteBufCodecs.VECTOR3F, TargetSeekModifier::target,
            AttributeCurve.CODEC, TargetSeekModifier::force,
            TargetSeekModifier::new
    );

    @Override
    public PathData apply(PathData data, float time) {
        var originalFrames = data.getFrames();
        if (originalFrames.size() < 2) {
            return data;
        }

        var frameCount = originalFrames.size();
        List<PathFrame> newFrames = new ArrayList<>(frameCount);

        for (var i = 0; i < frameCount; i++) {
            var frame = originalFrames.get(i);
            var progress = (float) i / (frameCount - 1);
            var currentForce = force.evaluate(progress);

            if (currentForce <= 1.0E-6f) {
                newFrames.add(frame);
                continue;
            }

            var toTarget = new Vector3f(target).sub(frame.position());
            if (toTarget.lengthSquared() > 1.0E-6f) {
                toTarget.normalize().mul(currentForce);
            }

            var newPosition = new Vector3f(frame.position()).add(toTarget);
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
        return PathModifierTypes.TARGET_SEEK.get();
    }
}