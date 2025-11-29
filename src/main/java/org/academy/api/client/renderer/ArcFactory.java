package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PathFrame;
import org.academy.api.common.arc.data.PropertyType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.academy.api.client.Render.RenderTypes;

public final class ArcFactory {
    private static final Vector3f DEFAULT_COLOR = new Vector3f(1.0f, 1.0f, 1.0f);

    static {
        PostEffect.addFixedBuffer(RenderTypes.ARC);
    }

    public static void render(PoseStack ps, SubmitNodeCollector submitNodeCollector, ArcRenderData data) {
        render(ps, submitNodeCollector, data, 1, 1, 1, 1);
    }

    public static void render(PoseStack ps, SubmitNodeCollector submitNodeCollector, ArcRenderData data, float r, float g, float b, float a) {
        submitNodeCollector.submitCustomGeometry(ps, RenderTypes.ARC, (pose, vc) -> renderRecursive(pose, vc, data, r, g, b, a));
    }

    private static void renderRecursive(PoseStack.Pose pose, VertexConsumer vc, ArcRenderData data, float r, float g, float b, float a) {
        for (var quad : data.quads) {
            vc.addVertex(pose, quad.v1.pos.x(), quad.v1.pos.y(), quad.v1.pos.z()).setUv(quad.v1.u, quad.v1.v).setColor(quad.v1.color.x() * r, quad.v1.color.y() * g, quad.v1.color.z() * b, a);
            vc.addVertex(pose, quad.v2.pos.x(), quad.v2.pos.y(), quad.v2.pos.z()).setUv(quad.v2.u, quad.v2.v).setColor(quad.v2.color.x() * r, quad.v2.color.y() * g, quad.v2.color.z() * b, a);
            vc.addVertex(pose, quad.v3.pos.x(), quad.v3.pos.y(), quad.v3.pos.z()).setUv(quad.v3.u, quad.v3.v).setColor(quad.v3.color.x() * r, quad.v3.color.y() * g, quad.v3.color.z() * b, a);
            vc.addVertex(pose, quad.v4.pos.x(), quad.v4.pos.y(), quad.v4.pos.z()).setUv(quad.v4.u, quad.v4.v).setColor(quad.v4.color.x() * r, quad.v4.color.y() * g, quad.v4.color.z() * b, a);
        }

        for (var branch : data.branches) {
            renderRecursive(pose, vc, branch, r, g, b, a);
        }
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

    public static class Quad {
        public Vertex v1, v2, v3, v4;
    }

    public static class ArcRenderData {
        public final List<Quad> quads = new ArrayList<>();
        public final List<ArcRenderData> branches = new ArrayList<>();
    }

    public static class Generator {
        public static ArcRenderData generate(PathData data, float baseThickness) {
            ArcRenderData renderData = new ArcRenderData();
            List<PathFrame> frames = data.getFrames();
            if (frames.size() < 2) {
                return renderData;
            }

            List<Float> thicknessTrack = data.getProperty(PropertyType.THICKNESS);
            List<Vector3f> colorTrack = data.getProperty(PropertyType.COLOR);
            boolean hasThickness = thicknessTrack != null && thicknessTrack.size() == frames.size();
            boolean hasColor = colorTrack != null && colorTrack.size() == frames.size();

            for (int i = 0; i < frames.size() - 1; i++) {
                PathFrame frame1 = frames.get(i);
                PathFrame frame2 = frames.get(i + 1);

                float scale1 = hasThickness ? thicknessTrack.get(i) : 1.0f;
                float scale2 = hasThickness ? thicknessTrack.get(i + 1) : 1.0f;
                Vector3f color1 = hasColor ? colorTrack.get(i) : DEFAULT_COLOR;
                Vector3f color2 = hasColor ? colorTrack.get(i + 1) : DEFAULT_COLOR;

                float halfThick1 = baseThickness * scale1 * 0.5f;
                float halfThick2 = baseThickness * scale2 * 0.5f;

                Vector3f binormal1 = new Vector3f(frame1.tangent()).cross(frame1.normal());
                Vector3f binormal2 = new Vector3f(frame2.tangent()).cross(frame2.normal());

                Vector3f p1 = new Vector3f(frame1.position());
                Vector3f p2 = new Vector3f(frame2.position());

                Vector3f v1Pos = new Vector3f(p1).sub(new Vector3f(binormal1).mul(halfThick1));
                Vector3f v2Pos = new Vector3f(p1).add(new Vector3f(binormal1).mul(halfThick1));
                Vector3f v3Pos = new Vector3f(p2).add(new Vector3f(binormal2).mul(halfThick2));
                Vector3f v4Pos = new Vector3f(p2).sub(new Vector3f(binormal2).mul(halfThick2));

                float u0 = (float) i / (frames.size() - 1);
                float u1 = (float) (i + 1) / (frames.size() - 1);

                Quad quad = new Quad();
                quad.v1 = new Vertex(v1Pos, u0, 0, color1);
                quad.v2 = new Vertex(v2Pos, u0, 1, color1);
                quad.v3 = new Vertex(v3Pos, u1, 1, color2);
                quad.v4 = new Vertex(v4Pos, u1, 0, color2);
                renderData.quads.add(quad);
            }

            return renderData;
        }
    }
}