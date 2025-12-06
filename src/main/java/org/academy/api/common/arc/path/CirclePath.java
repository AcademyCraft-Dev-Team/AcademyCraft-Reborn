package org.academy.api.common.arc.path;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.BasePath;
import org.academy.api.common.arc.PathType;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PathFrame;
import org.academy.api.common.arc.data.PropertyType;
import org.academy.internal.common.arc.PathTypes;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CirclePath(float radius) implements BasePath {
    public static final StreamCodec<ByteBuf, CirclePath> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CirclePath::radius,
            CirclePath::new
    );

    @Override
    public PathData generate(float resolution) {
        var circumference = 2.0f * (float) Math.PI * radius;
        var segments = Math.max(3, (int) (circumference * resolution));
        var pointCount = segments + 1;
        List<PathFrame> frames = new ArrayList<>(pointCount);

        for (var i = 0; i <= segments; i++) {
            var t = (float) i / segments;
            var angle = t * 2.0f * (float) Math.PI;
            var x = (float) Math.cos(angle) * radius;
            var z = (float) Math.sin(angle) * radius;

            var position = new Vector3f(x, 0, z);
            var tangent = new Vector3f(-z, 0, x).normalize();
            var normal = new Vector3f(0, 1, 0);

            frames.add(new PathFrame(position, tangent, normal));
        }

        var data = new PathData(frames);
        data.setProperty(PropertyType.THICKNESS, new ArrayList<>(Collections.nCopies(pointCount, 1.0f)));
        return data;
    }

    @Override
    public PathType<? extends BasePath> getType() {
        return PathTypes.CIRCLE.get();
    }

    @Override
    public BasePath transform(Matrix4f transform) {
        var center = new Vector3f(0, 0, 0).mulPosition(transform);
        var p1 = new Vector3f(radius, 0, 0).mulPosition(transform);
        var p2 = new Vector3f(0, 1, 0).mulDirection(transform);
        var transformedRadius = center.distance(p1);
        var transformedNormal = new Vector3f(p2).normalize();

        return new TransformedCirclePath(center, transformedNormal, transformedRadius);
    }

    private record TransformedCirclePath(Vector3fc center, Vector3fc normal, float radius) implements BasePath {
        @Override
        public PathData generate(float resolution) {
            var circumference = 2.0f * (float) Math.PI * radius;
            var segments = Math.max(3, (int) (circumference * resolution));
            var pointCount = segments + 1;
            List<PathFrame> frames = new ArrayList<>(pointCount);

            var rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), normal);

            for (var i = 0; i <= segments; i++) {
                var t = (float) i / segments;
                var angle = t * 2.0f * (float) Math.PI;
                var x = (float) Math.cos(angle) * radius;
                var z = (float) Math.sin(angle) * radius;

                var localPos = new Vector3f(x, 0, z);
                var localTangent = new Vector3f(-z, 0, x).normalize();

                var position = new Vector3f(localPos).rotate(rotation).add(center);
                var tangent = new Vector3f(localTangent).rotate(rotation);

                frames.add(new PathFrame(position, tangent, new Vector3f(normal)));
            }
            var data = new PathData(frames);
            data.setProperty(PropertyType.THICKNESS, new ArrayList<>(Collections.nCopies(pointCount, 1.0f)));
            return data;
        }

        @Override
        public PathType<? extends BasePath> getType() {
            return PathTypes.CIRCLE.get();
        }

        @Override
        public BasePath transform(Matrix4f transform) {
            return this;
        }
    }
}