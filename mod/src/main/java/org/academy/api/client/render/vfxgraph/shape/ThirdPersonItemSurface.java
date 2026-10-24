package org.academy.api.client.render.vfxgraph.shape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ThirdPersonItemSurface {
    private static final Map<Integer, List<float[]>> CAPTURES = new HashMap<>();
    private static boolean hasOwner;
    private static int ownerId;
    private static double ownerX;
    private static double ownerY;
    private static double ownerZ;

    private ThirdPersonItemSurface() {
    }

    public static void beginFrame() {
        CAPTURES.clear();
        hasOwner = false;
    }

    public static void beginOwner(int entityId, double x, double y, double z) {
        ownerId = entityId;
        ownerX = x;
        ownerY = y;
        ownerZ = z;
        hasOwner = true;
    }

    public static void endOwner() {
        hasOwner = false;
    }

    public static boolean hasOwner() {
        return hasOwner;
    }

    public static void addItem(Matrix4f cameraRelativeTransform, List<BakedQuad> quads) {
        if (!hasOwner || quads.isEmpty()) return;
        var triangles = FirstPersonHandSurface.fromQuads(effectLocal(cameraRelativeTransform), quads);
        if (triangles.length == 0) return;
        CAPTURES.computeIfAbsent(ownerId, _ -> new ArrayList<>()).add(triangles);
    }

    public static void addBox(Matrix4f cameraRelativeTransform,
                              float minX, float minY, float minZ,
                              float maxX, float maxY, float maxZ) {
        if (!hasOwner) return;
        CAPTURES.computeIfAbsent(ownerId, _ -> new ArrayList<>()).add(
                FirstPersonHandSurface.boxTriangles(
                        new Vector3f(minX, minY, minZ),
                        new Vector3f(maxX, maxY, maxZ),
                        effectLocal(cameraRelativeTransform)
                )
        );
    }

    public static List<float[]> captures(int entityId) {
        return CAPTURES.getOrDefault(entityId, List.of());
    }

    private static Matrix4f effectLocal(Matrix4f cameraRelative) {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        var transform = new Matrix4f(cameraRelative);
        transform.m30(transform.m30() + (float) (camera.x - ownerX));
        transform.m31(transform.m31() + (float) (camera.y - ownerY));
        transform.m32(transform.m32() + (float) (camera.z - ownerZ));
        return transform;
    }
}
