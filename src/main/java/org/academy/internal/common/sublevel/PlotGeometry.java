package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Captures real collision geometry from a loaded plot into body-local physics boxes. */
public final class PlotGeometry {
    public static final int MAX_BOXES = 16_384;

    private PlotGeometry() {
    }

    /**
     * Boxes retain the structure's stable local origin, including negative coordinates after
     * expansion. Changing content bounds must never translate the body or its collision shapes.
     */
    public static Capture capture(PlotStorage storage, PlotPos plot, AABB localBounds) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(localBounds, "localBounds");
        int minX = (int) Math.floor(localBounds.minX);
        int minY = (int) Math.floor(localBounds.minY);
        int minZ = (int) Math.floor(localBounds.minZ);
        int maxX = (int) Math.ceil(localBounds.maxX);
        int maxY = (int) Math.ceil(localBounds.maxY);
        int maxZ = (int) Math.ceil(localBounds.maxZ);
        if (!storage.layout().containsLocal(plot, minX, minZ)
                || !storage.layout().containsLocal(plot, maxX - 1, maxZ - 1)
                || minY < storage.level().getMinY() || maxY > storage.level().getMaxY()) {
            throw new IllegalArgumentException("Geometry bounds are outside the plot footprint or build height");
        }
        var boxes = new ArrayList<PhysicsBox>();
        int blocks = 0;
        var context = CollisionContext.empty();
        var position = new BlockPos.MutableBlockPos();
        boolean full = true;
        for (int x = minX; x < maxX && full; x++) {
            for (int y = minY; y < maxY && full; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    position.set(x, y, z);
                    var state = storage.blockState(plot, position);
                    if (state.isAir() || !state.isCollisionShapeFullBlock(storage.level(),
                            storage.worldPosition(plot, position))) {
                        full = false;
                        break;
                    }
                }
            }
        }
        if (full) {
            var center = localBounds.getCenter();
            var half = new PhysicsVector(localBounds.getXsize() / 2.0, localBounds.getYsize() / 2.0,
                    localBounds.getZsize() / 2.0);
            return new Capture(List.of(new PhysicsBox(
                    new PhysicsVector(center.x, center.y, center.z),
                    PhysicsQuaternion.IDENTITY, half)), (maxX - minX) * (maxY - minY) * (maxZ - minZ));
        }
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    position.set(x, y, z);
                    var state = storage.blockState(plot, position);
                    if (state.isAir()) continue;
                    blocks++;
                    var world = storage.worldPosition(plot, position);
                    var shape = state.getCollisionShape(storage.level(), world, context);
                    for (var box : shape.toAabbs()) {
                        if (boxes.size() >= MAX_BOXES) {
                            throw new IllegalStateException("Plot geometry exceeds " + MAX_BOXES + " collision boxes");
                        }
                        var local = box.move(x, y, z);
                        boxes.add(new PhysicsBox(
                                new PhysicsVector(local.getCenter().x, local.getCenter().y, local.getCenter().z),
                                PhysicsQuaternion.IDENTITY,
                                new PhysicsVector(local.getXsize() / 2.0, local.getYsize() / 2.0,
                                        local.getZsize() / 2.0)));
                    }
                }
            }
        }
        // Preserve the exact collision-shape union, while removing unnecessary
        // partitions even for small and irregular structures.
        return new Capture(TerrainSectionGeometry.merge(boxes), blocks);
    }

    public record Capture(List<PhysicsBox> boxes, int blocks) {
        public Capture {
            boxes = List.copyOf(boxes);
            if (blocks < 0) throw new IllegalArgumentException("Block count cannot be negative");
        }
    }
}
