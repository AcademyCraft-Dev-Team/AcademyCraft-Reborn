package org.academy.api.client.render.vfxgraph.shape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerModelType;
import org.academy.api.client.render.vfxgraph.arc.MeshDistance;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class HumanoidSurfaceSampler implements SurfaceSampler {
    private static final int PARTS = 6;
    private static final int FACES = 6;
    public static final int SLOTS = PARTS * FACES;
    private static final int ITEM_SLOTS = 18;

    private static final Vector3f[] WIDE_MIN = {
            vector(-4, 0, -2), vector(-4, -8, -4), vector(-3, -2, -2),
            vector(-1, -2, -2), vector(-2, 0, -2), vector(-2, 0, -2)
    };
    private static final Vector3f[] WIDE_MAX = {
            vector(4, 12, 2), vector(4, 0, 4), vector(1, 10, 2),
            vector(3, 10, 2), vector(2, 12, 2), vector(2, 12, 2)
    };
    private static final Vector3f[] SLIM_MIN = {
            vector(-4, 0, -2), vector(-4, -8, -4), vector(-2, -2, -2),
            vector(-1, -2, -2), vector(-2, 0, -2), vector(-2, 0, -2)
    };
    private static final Vector3f[] SLIM_MAX = {
            vector(4, 12, 2), vector(4, 0, 4), vector(1, 10, 2),
            vector(2, 10, 2), vector(2, 12, 2), vector(2, 12, 2)
    };

    private final int entityId;
    private final boolean slim;
    private final Matrix4f adjustedRoot = new Matrix4f();
    private final Matrix4f transform = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final Vector3f local = new Vector3f();
    private final Vector3f candidate = new Vector3f();

    public HumanoidSurfaceSampler(int entityId) {
        this.entityId = entityId;
        var level = Minecraft.getInstance().level;
        this.slim = level != null && level.getEntity(entityId) instanceof AbstractClientPlayer player
                && player.getSkin().model() == PlayerModelType.SLIM;
    }

    @Override
    public int slotCount() {
        var items = itemTriangleCount();
        return items == 0 ? SLOTS : SLOTS + Math.min(ITEM_SLOTS, items);
    }

    @Override
    public boolean sample(int index, float u, float v, Vector3f position, Vector3f normal) {
        if (firstPersonCamera()) return false;
        if (index >= SLOTS) {
            var total = itemTriangleCount();
            if (total == 0) return false;
            var triangle = Math.floorMod(index - SLOTS, total);
            for (var triangles : ThirdPersonItemSurface.captures(entityId)) {
                var count = TriangleSurface.count(triangles);
                if (triangle < count) {
                    return TriangleSurface.sample(triangles, triangle, u, v, position, normal);
                }
                triangle -= count;
            }
            return false;
        }
        var bones = HumanoidSurfacePose.get(entityId);
        if (bones == null || !resolveRoot()) return false;
        var part = Math.floorMod(index, PARTS);
        var face = Math.floorMod(index / PARTS, FACES);
        var min = (slim ? SLIM_MIN : WIDE_MIN)[part];
        var max = (slim ? SLIM_MAX : WIDE_MAX)[part];
        transform.set(adjustedRoot).mul(bones[part]);
        SurfaceSampler.box(face, Math.clamp(u, 0f, 1f), Math.clamp(v, 0f, 1f), min, max, transform, position, normal);
        return true;
    }

    @Override
    public boolean nearestPoint(float x, float y, float z, Vector3f destination) {
        if (firstPersonCamera()) return false;
        var bones = HumanoidSurfacePose.get(entityId);
        if (bones == null || !resolveRoot()) return false;
        var min = slim ? SLIM_MIN : WIDE_MIN;
        var max = slim ? SLIM_MAX : WIDE_MAX;
        var best = Float.MAX_VALUE;
        var found = false;
        for (var part = 0; part < PARTS; part++) {
            transform.set(adjustedRoot).mul(bones[part]);
            inverse.set(transform).invert();
            local.set(x, y, z);
            inverse.transformPosition(local);
            nearestOnBox(local, min[part], max[part], candidate);
            transform.transformPosition(candidate);
            var dx = candidate.x - x;
            var dy = candidate.y - y;
            var dz = candidate.z - z;
            var distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                best = distance;
                destination.set(candidate);
                found = true;
            }
        }
        for (var triangles : ThirdPersonItemSurface.captures(entityId)) {
            if (triangles.length == 0) continue;
            var nearest = MeshDistance.nearestPoint(triangles, x, y, z);
            var dx = nearest[0] - x;
            var dy = nearest[1] - y;
            var dz = nearest[2] - z;
            var distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                best = distance;
                destination.set(nearest[0], nearest[1], nearest[2]);
                found = true;
            }
        }
        return found;
    }

    private int itemTriangleCount() {
        var count = 0;
        for (var triangles : ThirdPersonItemSurface.captures(entityId)) {
            count += TriangleSurface.count(triangles);
        }
        return count;
    }

    private boolean firstPersonCamera() {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson()) return false;
        var level = minecraft.level;
        return level != null && level.getEntity(entityId) == minecraft.getCameraEntity();
    }

    private boolean resolveRoot() {
        var root = HumanoidSurfacePose.root(entityId);
        if (root == null) return false;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return false;
        var entity = level.getEntity(entityId);
        if (entity == null) return false;
        var partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var position = entity.getPosition(partialTick);
        var camera = minecraft.gameRenderer.mainCamera().position();
        adjustedRoot.set(root);
        adjustedRoot.m30(adjustedRoot.m30() + (float) (camera.x - position.x));
        adjustedRoot.m31(adjustedRoot.m31() + (float) (camera.y - position.y));
        adjustedRoot.m32(adjustedRoot.m32() + (float) (camera.z - position.z));
        return true;
    }

    static void nearestOnBox(Vector3f point, Vector3f min, Vector3f max, Vector3f out) {
        var x = Math.clamp(point.x, min.x, max.x);
        var y = Math.clamp(point.y, min.y, max.y);
        var z = Math.clamp(point.z, min.z, max.z);
        if (x != point.x || y != point.y || z != point.z) {
            out.set(x, y, z);
            return;
        }
        out.set(min.x, y, z);
        var best = point.x - min.x;
        var distance = max.x - point.x;
        if (distance < best) {
            best = distance;
            out.set(max.x, y, z);
        }
        distance = point.y - min.y;
        if (distance < best) {
            best = distance;
            out.set(x, min.y, z);
        }
        distance = max.y - point.y;
        if (distance < best) {
            best = distance;
            out.set(x, max.y, z);
        }
        distance = point.z - min.z;
        if (distance < best) {
            best = distance;
            out.set(x, y, min.z);
        }
        distance = max.z - point.z;
        if (distance < best) {
            out.set(x, y, max.z);
        }
    }

    static void armBounds(boolean right, boolean slim, Vector3f min, Vector3f max) {
        var sourceMin = slim ? SLIM_MIN : WIDE_MIN;
        var sourceMax = slim ? SLIM_MAX : WIDE_MAX;
        var part = right ? HumanoidSurfacePose.RIGHT_ARM : HumanoidSurfacePose.LEFT_ARM;
        min.set(sourceMin[part]);
        max.set(sourceMax[part]);
    }

    private static Vector3f vector(float x, float y, float z) {
        return new Vector3f(x, y, z).div(16f);
    }
}
