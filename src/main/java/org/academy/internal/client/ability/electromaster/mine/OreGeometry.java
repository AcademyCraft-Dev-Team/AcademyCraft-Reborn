package org.academy.internal.client.ability.electromaster.mine;

/** Immutable, world-independent geometry captured on the client thread. */
public record OreGeometry(int color, java.util.List<Box> boxes) {
    public static final Box CUBE = new Box(0, 0, 0, 1, 1, 1);

    public OreGeometry {
        boxes = java.util.List.copyOf(boxes);
    }

    public boolean fullCube() {
        return boxes.size() == 1 && boxes.getFirst().equals(CUBE);
    }

    public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) { }
}
