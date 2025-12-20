package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PropertyType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

import static org.academy.api.client.Render.RenderTypes;

public final class ArcFactory {
    private static final Vector3f DEFAULT_COLOR = new Vector3f(1.0f, 1.0f, 1.0f);

    static {
        PostEffect.addFixedBuffer(RenderTypes.ARC);
    }

    public static void render(PoseStack ps, ArcRenderData data) {
        render(ps, data, 1, 1, 1, 1);
    }

    public static void render(PoseStack ps, ArcRenderData data, float r, float g, float b, float a) {
        var vc = PostEffect.BUFFER_SOURCE_PRE.getBuffer(RenderTypes.ARC);
        renderRecursive(ps.last(), vc, data, r, g, b, a);
    }

    private static void renderRecursive(PoseStack.Pose pose, VertexConsumer vc, ArcRenderData data, float r, float g, float b, float a) {
        for (var quad : data.quads) {
            var v1 = quad.v1();
            var v2 = quad.v2();
            var v3 = quad.v3();
            var v4 = quad.v4();
            addVertex(pose, vc, v1, r, g, b, a);
            addVertex(pose, vc, v2, r, g, b, a);
            addVertex(pose, vc, v3, r, g, b, a);
            addVertex(pose, vc, v4, r, g, b, a);
        }

        for (var branch : data.branches) {
            renderRecursive(pose, vc, branch, r, g, b, a);
        }
    }

    private static void addVertex(
            PoseStack.Pose pose,
            VertexConsumer vc,
            Vertex vertex,
            float r, float g, float b, float a
    ) {
        vc.addVertex(pose, vertex.pos.x(), vertex.pos.y(), vertex.pos.z())
                .setUv(vertex.u, vertex.v)
                .setColor(vertex.color.x() * r, vertex.color.y() * g, vertex.color.z() * b, a);
    }

    public static class Vertex {
        public final Vector3f pos;
        public final float u, v;
        public final Vector3f color;

        Vertex(Vector3f pos, float u, float v, Vector3f color) {
            this.pos = pos;
            this.u = u;
            this.v = v;
            this.color = color;
        }
    }

    public record Quad(Vertex v1, Vertex v2, Vertex v3, Vertex v4) {
    }

    public static class ArcRenderData {
        public final List<Quad> quads = new ArrayList<>();
        public final List<ArcRenderData> branches = new ArrayList<>();
    }

    public static class Generator {
        public static ArcRenderData generate(PathData data, float baseThickness, Vector3fc cameraPos) {
            var renderData = new ArcRenderData();
            var frames = data.getFrames();
            var frameCount = frames.size();
            if (frameCount < 2) {
                return renderData;
            }

            var thicknessTrack = data.getProperty(PropertyType.THICKNESS);
            var colorTrack = data.getProperty(PropertyType.COLOR);
            var hasThickness = thicknessTrack != null && thicknessTrack.size() == frameCount;
            var hasColor = colorTrack != null && colorTrack.size() == frameCount;

            List<Vector3f> sideVectors = new ArrayList<>(frameCount);
            var firstFrame = frames.getFirst();
            var viewVec = new Vector3f(cameraPos).sub(firstFrame.position()).normalize();
            var firstSideVec = new Vector3f(firstFrame.tangent()).cross(viewVec).normalize();
            if (firstSideVec.lengthSquared() < 1.0E-6f) {
                firstSideVec.set(firstFrame.normal());
            }
            sideVectors.add(firstSideVec);

            for (var i = 1; i < frameCount; i++) {
                var prevTangent = frames.get(i - 1).tangent();
                var currentTangent = frames.get(i).tangent();
                var prevSideVec = sideVectors.get(i - 1);

                var rotation = new Quaternionf().rotationTo(prevTangent, currentTangent);
                var currentSideVec = new Vector3f(prevSideVec).rotate(rotation);
                sideVectors.add(currentSideVec);
            }

            for (var i = 0; i < frameCount - 1; i++) {
                var frame1 = frames.get(i);
                var frame2 = frames.get(i + 1);

                var sideVec1 = sideVectors.get(i);
                var sideVec2 = sideVectors.get(i + 1);

                var scale1 = hasThickness ? thicknessTrack.get(i) : 1.0f;
                var scale2 = hasThickness ? thicknessTrack.get(i + 1) : 1.0f;
                var color1 = hasColor ? colorTrack.get(i) : DEFAULT_COLOR;
                var color2 = hasColor ? colorTrack.get(i + 1) : DEFAULT_COLOR;

                var halfThick1 = baseThickness * scale1 * 0.5f;
                var halfThick2 = baseThickness * scale2 * 0.5f;

                var v1Pos = new Vector3f(frame1.position()).sub(new Vector3f(sideVec1).mul(halfThick1));
                var v2Pos = new Vector3f(frame1.position()).add(new Vector3f(sideVec1).mul(halfThick1));
                var v3Pos = new Vector3f(frame2.position()).add(new Vector3f(sideVec2).mul(halfThick2));
                var v4Pos = new Vector3f(frame2.position()).sub(new Vector3f(sideVec2).mul(halfThick2));

                var u0 = (float) i / (frameCount - 1);
                var u1 = (float) (i + 1) / (frameCount - 1);

                var quad = new Quad(
                        new Vertex(v1Pos, u0, 0, color1),
                        new Vertex(v2Pos, u0, 1, color1),
                        new Vertex(v3Pos, u1, 1, color2),
                        new Vertex(v4Pos, u1, 0, color2)
                );
                renderData.quads.add(quad);
            }

            return renderData;
        }
    }
}