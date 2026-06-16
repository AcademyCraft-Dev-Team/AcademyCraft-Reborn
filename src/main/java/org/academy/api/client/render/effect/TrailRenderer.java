package org.academy.api.client.render.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TrailRenderer {
    private final Deque<TrailPoint> points = new ArrayDeque<>();
    private float maxAge;
    private float width;
    private float r = 1, g = 1, b = 1;
    private float endR = 1, endG = 1, endB = 1;
    private boolean useGradient;
    private boolean useGlow;
    private float glowWidth = 1.5f;
    private float taperRatio = 0.3f;

    public TrailRenderer(float maxAge, float width) {
        this.maxAge = maxAge;
        this.width = width;
    }

    public void setMaxAge(float maxAge) {
        this.maxAge = maxAge;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setColor(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public void setEndColor(float r, float g, float b) {
        endR = r;
        endG = g;
        endB = b;
        useGradient = true;
    }

    public void setGlow(boolean enable, float glowWidthMultiplier) {
        useGlow = enable;
        glowWidth = glowWidthMultiplier;
    }

    public void setTaperRatio(float ratio) {
        taperRatio = Math.clamp(ratio, 0, 1);
    }

    public float getR() { return r; }
    public float getG() { return g; }
    public float getB() { return b; }

    public void addPoint(Vector3f pos) {
        addPoint(pos.x, pos.y, pos.z);
    }

    public void addPoint(float x, float y, float z) {
        points.addFirst(new TrailPoint(x, y, z, 0));
    }

    public void update(float deltaTime) {
        for (var p : points) {
            p.age += deltaTime;
        }
        while (!points.isEmpty() && points.getLast().age > maxAge) {
            points.removeLast();
        }
    }

    public void render(PoseStack poseStack, VertexConsumer vc, Camera camera,
                       float r, float g, float b) {
        if (points.size() < 2) return;

        var camPos = camera.position();
        var pose = poseStack.last();

        // Render glow layer first (wider, more transparent)
        if (useGlow) {
            renderQuadStrip(pose, vc, camPos, r * 0.5f, g * 0.5f, b * 0.5f,
                    width * glowWidth, 0.35f);
        }

        // Render main trail
        renderQuadStrip(pose, vc, camPos, r, g, b, width, 1.0f);
    }

    private void renderQuadStrip(PoseStack.Pose pose, VertexConsumer vc,
                                  net.minecraft.world.phys.Vec3 camPos,
                                  float r, float g, float b, float baseWidth, float alphaMult) {
        var it = points.iterator();
        var prev = it.next();
        var pointCount = points.size();
        var idx = 0;

        var prevPos = new Vector3f(
                (float) (prev.x - camPos.x),
                (float) (prev.y - camPos.y),
                (float) (prev.z - camPos.z)
        );

        while (it.hasNext()) {
            var curr = it.next();
            var currPos = new Vector3f(
                    (float) (curr.x - camPos.x),
                    (float) (curr.y - camPos.y),
                    (float) (curr.z - camPos.z)
            );

            var prevLife = 1.0f - prev.age / maxAge;
            var currLife = 1.0f - curr.age / maxAge;
            if (prevLife < 0) prevLife = 0;
            if (currLife < 0) currLife = 0;

            // Width taper: thinner toward the tail
            var prevTaper = 1.0f - (1.0f - prevLife) * taperRatio;
            var currTaper = 1.0f - (1.0f - currLife) * taperRatio;
            var prevW = baseWidth * prevTaper;
            var currW = baseWidth * currTaper;

            // Color gradient
            var prevProgress = prev.age / maxAge;
            var currProgress = curr.age / maxAge;
            float pr, pg, pb, cr, cg, cb;
            if (useGradient) {
                pr = this.r + (endR - this.r) * prevProgress;
                pg = this.g + (endG - this.g) * prevProgress;
                pb = this.b + (endB - this.b) * prevProgress;
                cr = this.r + (endR - this.r) * currProgress;
                cg = this.g + (endG - this.g) * currProgress;
                cb = this.b + (endB - this.b) * currProgress;
            } else {
                pr = r; pg = g; pb = b;
                cr = r; cg = g; cb = b;
            }

            var prevAlpha = prevLife * alphaMult;
            var currAlpha = currLife * alphaMult;

            var dir = new Vector3f(currPos).sub(prevPos).normalize();
            var cameraToPoint = new Vector3f(prevPos).normalize();
            var sideVec = new Vector3f(dir).cross(cameraToPoint);
            var sideLen = sideVec.length();
            if (sideLen < 0.0001f) {
                prev = curr;
                prevPos = currPos;
                idx++;
                continue;
            }
            sideVec.normalize().mul(prevW * 0.5f);

            var v1 = new Vector3f(prevPos).sub(sideVec);
            var v2 = new Vector3f(prevPos).add(sideVec);

            cameraToPoint = new Vector3f(currPos).normalize();
            sideVec = new Vector3f(dir).cross(cameraToPoint);
            sideLen = sideVec.length();
            if (sideLen < 0.0001f) {
                prev = curr;
                prevPos = currPos;
                idx++;
                continue;
            }
            sideVec.normalize().mul(currW * 0.5f);

            var v3 = new Vector3f(currPos).add(sideVec);
            var v4 = new Vector3f(currPos).sub(sideVec);

            vc.addVertex(pose, v1.x, v1.y, v1.z).setColor(pr, pg, pb, prevAlpha);
            vc.addVertex(pose, v2.x, v2.y, v2.z).setColor(pr, pg, pb, prevAlpha);
            vc.addVertex(pose, v3.x, v3.y, v3.z).setColor(cr, cg, cb, currAlpha);
            vc.addVertex(pose, v4.x, v4.y, v4.z).setColor(cr, cg, cb, currAlpha);

            prev = curr;
            prevPos = currPos;
            idx++;
        }
    }

    public void clear() {
        points.clear();
    }

    public boolean isEmpty() {
        return points.size() < 2;
    }

    private static class TrailPoint {
        final float x, y, z;
        float age;

        TrailPoint(float x, float y, float z, float age) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.age = age;
        }
    }
}
