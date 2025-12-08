package org.academy.api.common.arc.modifier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.PathModifierType;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PathFrame;
import org.academy.api.common.arc.data.PropertyType;
import org.academy.internal.common.arc.PathModifierTypes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public record TransformModifier(Matrix4f transform) implements PathModifier {
    public static final StreamCodec<ByteBuf, Matrix4f> MATRIX4F_CODEC = new StreamCodec<>() {
        @Override
        public Matrix4f decode(ByteBuf buf) {
            return new Matrix4f().set(
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()
            );
        }

        @Override
        public void encode(ByteBuf buf, Matrix4f value) {
            buf.writeFloat(value.m00());
            buf.writeFloat(value.m10());
            buf.writeFloat(value.m20());
            buf.writeFloat(value.m30());
            buf.writeFloat(value.m01());
            buf.writeFloat(value.m11());
            buf.writeFloat(value.m21());
            buf.writeFloat(value.m31());
            buf.writeFloat(value.m02());
            buf.writeFloat(value.m12());
            buf.writeFloat(value.m22());
            buf.writeFloat(value.m32());
            buf.writeFloat(value.m03());
            buf.writeFloat(value.m13());
            buf.writeFloat(value.m23());
            buf.writeFloat(value.m33());
        }
    };

    public static final StreamCodec<ByteBuf, TransformModifier> CODEC = MATRIX4F_CODEC
            .map(TransformModifier::new, TransformModifier::transform);

    @Override
    public PathData apply(PathData data, float time) {
        var originalFrames = data.getFrames();
        if (originalFrames.isEmpty()) {
            return data;
        }

        List<PathFrame> newFrames = new ArrayList<>(originalFrames.size());
        for (var frame : originalFrames) {
            var newPosition = new Vector3f(frame.position()).mulPosition(transform);
            var newTangent = new Vector3f(frame.tangent()).mulDirection(transform).normalize();
            var newNormal = new Vector3f(frame.normal()).mulDirection(transform).normalize();

            newFrames.add(new PathFrame(newPosition, newTangent, newNormal));
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
        return PathModifierTypes.TRANSFORM.get();
    }
}