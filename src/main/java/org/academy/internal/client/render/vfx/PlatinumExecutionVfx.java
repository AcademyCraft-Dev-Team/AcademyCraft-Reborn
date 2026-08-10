package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlatinumExecutionVfx implements Vfx {
    public static final PlatinumExecutionVfx INSTANCE = new PlatinumExecutionVfx();
    public static final int DURATION_TICKS = 40;
    private static final int MAX_VERTICES = 4096;
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final Map<UUID, DeathState> DEATHS = new ConcurrentHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ByteBuffer vertexData = BufferUtils.createByteBuffer(MAX_VERTICES * ColorMeshData.VERTEX_STRIDE);

    private PlatinumExecutionVfx() {
    }

    public static void enqueue(UUID executionId, int entityId, double x, double y, double z,
                               float yRot, float width, float height, int durationTicks) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || executionId == null) return;
        var target = minecraft.level.getEntity(entityId);
        if (target != null) target.setInvisible(true);
        DEATHS.put(executionId, new DeathState(
                new Vec3(x, y, z),
                yRot,
                Math.max(0.3f, width),
                Math.max(0.5f, height),
                minecraft.level.getGameTime(),
                Math.max(1, durationTicks)
        ));
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (DEATHS.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            DEATHS.clear();
            return;
        }

        var camera = ctx.camera().pos();
        var currentTick = (double) minecraft.level.getGameTime() + ctx.partialTick();
        var activeDeaths = DEATHS.entrySet().size();
        ensureCapacity(activeDeaths * 12 * 24);
        vertexData.clear();
        var vertexCount = 0;

        for (var iterator = DEATHS.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var state = entry.getValue();
            float progress = Mth.clamp(
                    (float) ((currentTick - state.startTick) / state.durationTicks), 0.0f, 1.0f);
            if (progress >= 1.0f) {
                DEATHS.remove(entry.getKey(), state);
                continue;
            }
            if (!state.burstSpawned && progress >= 0.45f) {
                state.burstSpawned = true;
                spawnBurst(state);
            }

            var collapseProgress = Mth.clamp(progress / 0.72f, 0.0f, 1.0f);
            var inverse = 1.0f - collapseProgress;
            var collapse = 81.0f * (1.0f - inverse * inverse * inverse);
            float flash = progress < 0.08f ? 1.0f - progress / 0.08f : 0.0f;
            float alpha = progress > 0.70f
                    ? Mth.clamp(1.0f - (progress - 0.70f) / 0.30f, 0.0f, 1.0f)
                    : 1.0f;
            float red = Mth.clamp(0.80f + flash * 0.20f, 0.0f, 1.0f);
            float green = Mth.clamp(0.85f + flash * 0.15f, 0.0f, 1.0f);
            var halfWidth = Math.max(0.15, state.width * 0.5);
            var localBounds = new AABB(
                    -halfWidth, 0.0, -halfWidth,
                    halfWidth, state.height, halfWidth
            );

            var box = localVertices(localBounds);
            var yaw = (float) ((180.0f - state.yRot) * Mth.DEG_TO_RAD);
            var pitch = (float) (collapse * Mth.DEG_TO_RAD);
            var sx = (float) (state.position.x - camera.x);
            var sy = (float) (state.position.y - camera.y);
            var sz = (float) (state.position.z - camera.z);
            var transformed = transformBox(box, sx, sy, sz, yaw, pitch);

            for (var edge : EDGES) {
                var v1 = transformed[edge[0]];
                var v2 = transformed[edge[1]];
                vertexCount = appendEdge(vertexData, vertexCount,
                        v1, v2, red, green, alpha);
            }
        }

        if (vertexCount == 0) return;
        vertexData.flip();
        sink.push(new ColorMeshData(vertexData, vertexCount));
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    private void ensureCapacity(int requiredVertices) {
        var requiredBytes = (long) requiredVertices * ColorMeshData.VERTEX_STRIDE;
        if (vertexData.capacity() >= requiredBytes) return;
        var newCapacity = Math.max(requiredVertices,
                (int) (vertexData.capacity() / ColorMeshData.VERTEX_STRIDE) * 2);
        vertexData = BufferUtils.createByteBuffer(Math.toIntExact((long) newCapacity * ColorMeshData.VERTEX_STRIDE));
    }

    private static Vector3f[] localVertices(AABB box) {
        return new Vector3f[]{
                new Vector3f((float) box.minX, (float) box.minY, (float) box.minZ),
                new Vector3f((float) box.maxX, (float) box.minY, (float) box.minZ),
                new Vector3f((float) box.maxX, (float) box.minY, (float) box.maxZ),
                new Vector3f((float) box.minX, (float) box.minY, (float) box.maxZ),
                new Vector3f((float) box.minX, (float) box.maxY, (float) box.minZ),
                new Vector3f((float) box.maxX, (float) box.maxY, (float) box.minZ),
                new Vector3f((float) box.maxX, (float) box.maxY, (float) box.maxZ),
                new Vector3f((float) box.minX, (float) box.maxY, (float) box.maxZ)
        };
    }

    private static Vector3f[] transformBox(Vector3f[] vertices, float tx, float ty, float tz,
                                           float yaw, float pitch) {
        var cosY = Mth.cos(yaw);
        var sinY = Mth.sin(yaw);
        var cosX = Mth.cos(pitch);
        var sinX = Mth.sin(pitch);
        var out = new Vector3f[vertices.length];
        for (var i = 0; i < vertices.length; i++) {
            var v = vertices[i];
            var x = v.x;
            var y = v.y;
            var z = v.z;
            var ry = x * cosY - z * sinY;
            var rz = x * sinY + z * cosY;
            var ryy = y * cosX - rz * sinX;
            var rzz = y * sinX + rz * cosX;
            out[i] = new Vector3f(ry + tx, ryy + ty, rzz + tz);
        }
        return out;
    }

    private static int appendEdge(ByteBuffer target, int count,
                                  Vector3f v1, Vector3f v2,
                                  float r, float g, float alpha) {
        var dx = v2.x - v1.x;
        var dy = v2.y - v1.y;
        var dz = v2.z - v1.z;
        var length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1.0e-4f) return count;
        var nx = dx / length;
        var ny = dy / length;
        var nz = dz / length;
        var rightX = -nz;
        var rightY = 0.0f;
        var rightZ = nx;
        if (rightX * rightX + rightZ * rightZ < 1.0e-6f) {
            rightX = 0.0f;
            rightY = 1.0f;
            rightZ = 0.0f;
        }
        var upX = ny * rightZ - nz * rightY;
        var upY = nz * rightX - nx * rightZ;
        var upZ = nx * rightY - ny * rightX;
        var halfWidth = 0.02f;
        var a1 = new Vector3f(
                v1.x + (rightX + upX) * halfWidth,
                v1.y + (rightY + upY) * halfWidth,
                v1.z + (rightZ + upZ) * halfWidth);
        var a2 = new Vector3f(
                v1.x + (-rightX + upX) * halfWidth,
                v1.y + (-rightY + upY) * halfWidth,
                v1.z + (-rightZ + upZ) * halfWidth);
        var a3 = new Vector3f(
                v1.x + (-rightX - upX) * halfWidth,
                v1.y + (-rightY - upY) * halfWidth,
                v1.z + (-rightZ - upZ) * halfWidth);
        var a4 = new Vector3f(
                v1.x + (rightX - upX) * halfWidth,
                v1.y + (rightY - upY) * halfWidth,
                v1.z + (rightZ - upZ) * halfWidth);
        var b1 = new Vector3f(a1).add(dx, dy, dz);
        var b2 = new Vector3f(a2).add(dx, dy, dz);
        var b3 = new Vector3f(a3).add(dx, dy, dz);
        var b4 = new Vector3f(a4).add(dx, dy, dz);
        putVertex(target, a1, r, g, alpha);
        putVertex(target, a2, r, g, alpha);
        putVertex(target, b1, r, g, alpha);
        putVertex(target, a2, r, g, alpha);
        putVertex(target, b2, r, g, alpha);
        putVertex(target, b1, r, g, alpha);
        putVertex(target, a2, r, g, alpha);
        putVertex(target, a3, r, g, alpha);
        putVertex(target, b2, r, g, alpha);
        putVertex(target, a3, r, g, alpha);
        putVertex(target, b3, r, g, alpha);
        putVertex(target, b2, r, g, alpha);
        putVertex(target, a3, r, g, alpha);
        putVertex(target, a4, r, g, alpha);
        putVertex(target, b3, r, g, alpha);
        putVertex(target, a4, r, g, alpha);
        putVertex(target, b4, r, g, alpha);
        putVertex(target, b3, r, g, alpha);
        putVertex(target, a4, r, g, alpha);
        putVertex(target, a1, r, g, alpha);
        putVertex(target, b4, r, g, alpha);
        putVertex(target, a1, r, g, alpha);
        putVertex(target, b1, r, g, alpha);
        putVertex(target, b4, r, g, alpha);
        return count + 24;
    }

    private static void putVertex(ByteBuffer target, Vector3f pos,
                                  float r, float g, float alpha) {
        target.putFloat(pos.x);
        target.putFloat(pos.y);
        target.putFloat(pos.z);
        target.putFloat(r).putFloat(g).putFloat(1.0f).putFloat(alpha);
    }

    private static void spawnBurst(DeathState state) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var random = RANDOM;
        for (var i = 0; i < 36; i++) {
            var x = state.position.x + (random.nextDouble() - 0.5) * state.width;
            var y = state.position.y + random.nextDouble() * state.height;
            var z = state.position.z + (random.nextDouble() - 0.5) * state.width;
            var vx = (random.nextDouble() - 0.5) * 0.22;
            var vy = 0.02 + random.nextDouble() * 0.18;
            var vz = (random.nextDouble() - 0.5) * 0.22;
            level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.PORTAL,
                    x, y, z, vx, vy, vz);
        }
    }

    private static final class DeathState {
        private final Vec3 position;
        private final float yRot;
        private final float width;
        private final float height;
        private final long startTick;
        private final int durationTicks;
        private boolean burstSpawned;

        private DeathState(Vec3 position, float yRot, float width, float height,
                           long startTick, int durationTicks) {
            this.position = position;
            this.yRot = yRot;
            this.width = width;
            this.height = height;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }
}
