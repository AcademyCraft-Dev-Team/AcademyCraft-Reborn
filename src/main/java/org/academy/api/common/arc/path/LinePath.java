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
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LinePath(Vector3fc start, Vector3fc end) implements BasePath {
    public static final StreamCodec<ByteBuf, LinePath> CODEC = StreamCodec.composite(
            ByteBufCodecs.VECTOR3F, LinePath::start,
            ByteBufCodecs.VECTOR3F, LinePath::end,
            LinePath::new
    );

    @Override
    public PathData generate(float resolution) {
        var distance = start.distance(end);
        var segments = Math.max(1, (int) (distance * resolution));
        var pointCount = segments + 1;

        List<PathFrame> frames = new ArrayList<>(pointCount);
        if (distance < 1.0E-6f) {
            frames.add(new PathFrame(start, new Vector3f(0, 1, 0), new Vector3f(1, 0, 0)));
            var data = new PathData(frames);
            data.setProperty(PropertyType.THICKNESS, Collections.singletonList(1.0f));
            return data;
        }

        var tangent = new Vector3f(end).sub(start).normalize();
        var globalUp = new Vector3f(0, 1, 0);
        var normal = new Vector3f(tangent).cross(globalUp);

        if (normal.lengthSquared() < 1.0E-6f) {
            var globalRight = new Vector3f(1, 0, 0);
            normal.set(tangent).cross(globalRight);
        }
        normal.normalize();

        for (var i = 0; i <= segments; ++i) {
            var t = (float) i / segments;
            var position = start.lerp(end, t, new Vector3f());
            frames.add(new PathFrame(position, new Vector3f(tangent), new Vector3f(normal)));
        }

        var data = new PathData(frames);
        List<Float> thickness = new ArrayList<>(Collections.nCopies(pointCount, 1.0f));
        data.setProperty(PropertyType.THICKNESS, thickness);

        return data;
    }

    @Override
    public PathType<? extends BasePath> getType() {
        return PathTypes.LINE.get();
    }

    @Override
    public BasePath transform(Matrix4f transform) {
        var newStart = new Vector3f(start).mulPosition(transform);
        var newEnd = new Vector3f(end).mulPosition(transform);
        return new LinePath(newStart, newEnd);
    }
}