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

public record PolylinePath(List<Vector3fc> vertices) implements BasePath {
    public static final StreamCodec<ByteBuf, PolylinePath> CODEC = StreamCodec.composite(
            ByteBufCodecs.VECTOR3F.apply(ByteBufCodecs.list()), PolylinePath::vertices,
            PolylinePath::new
    );

    @Override
    public PathData generate(float resolution) {
        List<PathFrame> frames = new ArrayList<>();
        if (this.vertices.size() < 2) {
            if (!this.vertices.isEmpty()) {
                frames.add(new PathFrame(this.vertices.getFirst(), new Vector3f(0, 1, 0), new Vector3f(1, 0, 0)));
            }
            PathData data = new PathData(frames);
            if (!frames.isEmpty()) {
                data.setProperty(PropertyType.THICKNESS, Collections.singletonList(1.0f));
            }
            return data;
        }

        for (int i = 0; i < this.vertices.size() - 1; ++i) {
            Vector3fc start = this.vertices.get(i);
            Vector3fc end = this.vertices.get(i + 1);

            float distance = start.distance(end);
            int segments = Math.max(1, (int) (distance * resolution));

            Vector3f tangent = new Vector3f(end).sub(start).normalize();
            Vector3f globalUp = new Vector3f(0, 1, 0);
            Vector3f normal = new Vector3f(tangent).cross(globalUp);
            if (normal.lengthSquared() < 1.0E-6f) {
                normal.set(tangent).cross(new Vector3f(1, 0, 0));
            }
            normal.normalize();

            int loopEnd = (i == this.vertices.size() - 2) ? segments : segments - 1;
            for (int j = 0; j <= loopEnd; ++j) {
                float t = (float) j / segments;
                Vector3f position = start.lerp(end, t, new Vector3f());
                frames.add(new PathFrame(position, new Vector3f(tangent), new Vector3f(normal)));
            }
        }

        PathData data = new PathData(frames);
        if (!frames.isEmpty()) {
            List<Float> thickness = new ArrayList<>(Collections.nCopies(frames.size(), 1.0f));
            data.setProperty(PropertyType.THICKNESS, thickness);
        }
        return data;
    }

    @Override
    public PathType<? extends BasePath> getType() {
        return PathTypes.POLYLINE.get();
    }

    @Override
    public BasePath transform(Matrix4f transform) {
        List<Vector3fc> newVertices = new ArrayList<>(this.vertices.size());
        for (Vector3fc vertex : this.vertices) {
            newVertices.add(new Vector3f(vertex).mulPosition(transform));
        }
        return new PolylinePath(newVertices);
    }
}