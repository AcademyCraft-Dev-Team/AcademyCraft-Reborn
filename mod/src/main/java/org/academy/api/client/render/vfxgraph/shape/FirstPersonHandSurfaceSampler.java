package org.academy.api.client.render.vfxgraph.shape;

import net.minecraft.client.Minecraft;
import org.academy.api.client.render.vfxgraph.arc.MeshDistance;
import org.joml.Vector3f;

public final class FirstPersonHandSurfaceSampler implements SurfaceSampler {
    private final int entityId;

    public FirstPersonHandSurfaceSampler(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public int slotCount() {
        return Math.max(1, triangleCount());
    }

    @Override
    public boolean sample(int index, float u, float v, Vector3f position, Vector3f normal) {
        if (!firstPerson()) return false;
        var captures = FirstPersonHandSurface.captures();
        if (captures.isEmpty()) return false;
        var total = triangleCount();
        if (total == 0) return false;
        var triangle = Math.floorMod(index, total);
        for (var capture : captures) {
            var triangles = capture.triangles();
            var count = TriangleSurface.count(triangles);
            if (triangle < count) {
                return TriangleSurface.sample(triangles, triangle, u, v, position, normal);
            }
            triangle -= count;
        }
        return false;
    }

    @Override
    public boolean nearestPoint(float x, float y, float z, Vector3f destination) {
        if (!firstPerson()) return false;
        var captures = FirstPersonHandSurface.captures();
        if (captures.isEmpty()) return false;
        var best = Float.MAX_VALUE;
        var found = false;
        for (var capture : captures) {
            var triangles = capture.triangles();
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

    private static int triangleCount() {
        var count = 0;
        for (var capture : FirstPersonHandSurface.captures()) {
            count += TriangleSurface.count(capture.triangles());
        }
        return count;
    }

    private boolean firstPerson() {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson()) return false;
        var level = minecraft.level;
        if (level == null) return false;
        var entity = level.getEntity(entityId);
        return entity != null && entity == minecraft.getCameraEntity();
    }
}
