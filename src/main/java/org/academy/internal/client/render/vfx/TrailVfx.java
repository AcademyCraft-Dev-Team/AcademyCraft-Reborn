package org.academy.internal.client.render.vfx;

import net.minecraft.util.Mth;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class TrailVfx implements Vfx {
    public static final TrailVfx INSTANCE = new TrailVfx();
    private static final int INITIAL_VERTICES = 4096;

    private final List<Trail> activeTrails = new ArrayList<>();
    private ByteBuffer vertexData = BufferUtils.createByteBuffer(INITIAL_VERTICES * ColorMeshData.VERTEX_STRIDE);

    private TrailVfx() {
    }

    public Trail createTrail(float maxAge, float width, float r, float g, float b) {
        var trail = new Trail(maxAge, width, r, g, b);
        activeTrails.add(trail);
        return trail;
    }

    @Override
    public void update(float dt, VfxFrameContext ctx) {
        if (dt <= 0.0f) return;
        activeTrails.removeIf(trail -> {
            trail.update(dt);
            return trail.isEmpty();
        });
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (activeTrails.isEmpty()) return;
        var camera = ctx.camera();
        var cameraX = camera.pos().x;
        var cameraY = camera.pos().y;
        var cameraZ = camera.pos().z;

        vertexData.clear();
        var vertexCount = 0;
        for (var trail : activeTrails) {
            var count = trail.appendVertices(vertexData, cameraX, cameraY, cameraZ);
            if (count > 0 && vertexData.remaining() < (long) count * ColorMeshData.VERTEX_STRIDE) {
                grow(count);
                vertexData.clear();
                vertexCount = 0;
                for (var retry : activeTrails) {
                    vertexCount += retry.appendVertices(vertexData, cameraX, cameraY, cameraZ);
                }
                break;
            }
            vertexCount += count;
        }
        if (vertexCount == 0) return;
        vertexData.flip();
        sink.push(new ColorMeshData(vertexData, vertexCount));
    }

    private void grow(int requiredVertices) {
        var requiredBytes = (long) requiredVertices * ColorMeshData.VERTEX_STRIDE;
        var newCapacity = Math.max(requiredVertices,
                (int) (vertexData.capacity() / ColorMeshData.VERTEX_STRIDE) * 2);
        vertexData = BufferUtils.createByteBuffer(Math.toIntExact(Math.max(requiredBytes, (long) newCapacity * ColorMeshData.VERTEX_STRIDE)));
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    public static final class Trail {
        private final Deque<TrailPoint> points = new ArrayDeque<>();
        private final float maxAge;
        private final float width;
        private final float r;
        private final float g;
        private final float b;

        private Trail(float maxAge, float width, float r, float g, float b) {
            this.maxAge = maxAge;
            this.width = width;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        public void addPoint(float x, float y, float z) {
            points.addFirst(new TrailPoint(x, y, z, 0));
        }

        public void addPoint(Vector3f pos) {
            addPoint(pos.x, pos.y, pos.z);
        }

        private void update(float deltaTime) {
            for (var p : points) {
                p.age += deltaTime;
            }
            while (!points.isEmpty() && points.getLast().age > maxAge) {
                points.removeLast();
            }
        }

        private boolean isEmpty() {
            return points.size() < 2;
        }

        private int appendVertices(ByteBuffer target, float camX, float camY, float camZ) {
            if (points.size() < 2) return 0;
            var it = points.iterator();
            var prev = it.next();
            var pointCount = points.size();
            var count = 0;

            var prevPos = new Vector3f(prev.x - camX, prev.y - camY, prev.z - camZ);
            while (it.hasNext()) {
                var curr = it.next();
                var currPos = new Vector3f(curr.x - camX, curr.y - camY, curr.z - camZ);
                var prevLife = Mth.clamp(1.0f - prev.age / maxAge, 0.0f, 1.0f);
                var currLife = Mth.clamp(1.0f - curr.age / maxAge, 0.0f, 1.0f);
                var prevW = width * prevLife;
                var currW = width * currLife;

                var dir = new Vector3f(currPos).sub(prevPos).normalize();
                var sideVec = new Vector3f(dir).cross(new Vector3f(prevPos).normalize());
                if (sideVec.length() < 0.0001f) {
                    prev = curr;
                    prevPos = currPos;
                    continue;
                }
                sideVec.normalize().mul(prevW * 0.5f);
                var v1 = new Vector3f(prevPos).sub(sideVec);
                var v2 = new Vector3f(prevPos).add(sideVec);

                sideVec = new Vector3f(dir).cross(new Vector3f(currPos).normalize());
                if (sideVec.length() < 0.0001f) {
                    prev = curr;
                    prevPos = currPos;
                    continue;
                }
                sideVec.normalize().mul(currW * 0.5f);
                var v3 = new Vector3f(currPos).add(sideVec);
                var v4 = new Vector3f(currPos).sub(sideVec);

                putVertex(target, v1, prevLife);
                putVertex(target, v2, prevLife);
                putVertex(target, v3, currLife);
                putVertex(target, v2, prevLife);
                putVertex(target, v3, currLife);
                putVertex(target, v4, currLife);
                count += 6;

                prev = curr;
                prevPos = currPos;
            }
            return count;
        }

        private void putVertex(ByteBuffer target, Vector3f pos, float alpha) {
            target.putFloat(pos.x);
            target.putFloat(pos.y);
            target.putFloat(pos.z);
            target.putFloat(r).putFloat(g).putFloat(b).putFloat(alpha);
        }
    }

    private static final class TrailPoint {
        private final float x;
        private final float y;
        private final float z;
        private float age;

        private TrailPoint(float x, float y, float z, float age) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.age = age;
        }
    }
}
