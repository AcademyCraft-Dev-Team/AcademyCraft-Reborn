package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Direct block-state collision capture for one 16x16x16 terrain section. */
public final class TerrainSectionGeometry {
    private TerrainSectionGeometry() {
    }

    /**
     * Boxes are relative to the section origin. Empty/air states are skipped; states with an
     * empty collision shape (grass, flowers) contribute nothing and do not fail the section.
     */
    public static SectionGeometry capture(ServerLevel host, int sectionX, int sectionY, int sectionZ) {
        Objects.requireNonNull(host, "host");
        if (host.getChunkSource().getChunkNow(sectionX, sectionZ) == null) {
            throw new IllegalStateException("Terrain section chunk is not loaded");
        }
        int minX = sectionX << 4, minY = sectionY << 4, minZ = sectionZ << 4;
        int maxY = Math.min(host.getMaxY(), minY + 16);
        var boxes = new ArrayList<PhysicsBox>();
        var context = CollisionContext.empty();
        var position = new BlockPos.MutableBlockPos();
        int blocks = 0;
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int y = Math.max(host.getMinY(), minY); y < maxY; y++) {
                    position.set(x, y, z);
                    var state = host.getBlockState(position);
                    if (state.isAir()) continue;
                    var shape = state.getCollisionShape(host, position, context);
                    if (shape.isEmpty()) continue;
                    blocks++;
                    for (var box : shape.toAabbs()) {
                        boxes.add(box(box.move(x, y, z), minX, minY, minZ));
                    }
                }
            }
        }
        return new SectionGeometry(List.copyOf(merge(boxes)), blocks, new AABB(minX, minY, minZ,
                minX + 16, maxY, minZ + 16));
    }

    /** Merges exactly adjacent, identically sized cuboids to keep terrain inside the native budget. */
    private static List<PhysicsBox> merge(List<PhysicsBox> input) {
        var boxes = new ArrayList<AABB>(input.size());
        for (var box : input) {
            var center = box.center();
            var half = box.halfExtents();
            boxes.add(new AABB(center.x() - half.x(), center.y() - half.y(), center.z() - half.z(),
                    center.x() + half.x(), center.y() + half.y(), center.z() + half.z()));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    var merged = mergeOne(boxes.get(i), boxes.get(j));
                    if (merged != null) {
                        boxes.set(i, merged);
                        boxes.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        var result = new ArrayList<PhysicsBox>(boxes.size());
        for (var box : boxes) {
            result.add(new PhysicsBox(new PhysicsVector(box.getCenter().x, box.getCenter().y, box.getCenter().z),
                    PhysicsQuaternion.IDENTITY, new PhysicsVector(box.getXsize() / 2.0,
                    box.getYsize() / 2.0, box.getZsize() / 2.0)));
        }
        return result;
    }

    private static AABB mergeOne(AABB first, AABB second) {
        if (first.minY == second.minY && first.maxY == second.maxY
                && first.minZ == second.minZ && first.maxZ == second.maxZ) {
            if (first.maxX == second.minX) return new AABB(first.minX, first.minY, first.minZ,
                    second.maxX, first.maxY, first.maxZ);
            if (second.maxX == first.minX) return new AABB(second.minX, first.minY, first.minZ,
                    first.maxX, first.maxY, first.maxZ);
        }
        if (first.minX == second.minX && first.maxX == second.maxX
                && first.minY == second.minY && first.maxY == second.maxY) {
            if (first.maxZ == second.minZ) return new AABB(first.minX, first.minY, first.minZ,
                    first.maxX, first.maxY, second.maxZ);
            if (second.maxZ == first.minZ) return new AABB(first.minX, first.minY, second.minZ,
                    first.maxX, first.maxY, first.maxZ);
        }
        if (first.minX == second.minX && first.maxX == second.maxX
                && first.minZ == second.minZ && first.maxZ == second.maxZ) {
            if (first.maxY == second.minY) return new AABB(first.minX, first.minY, first.minZ,
                    first.maxX, second.maxY, first.maxZ);
            if (second.maxY == first.minY) return new AABB(first.minX, second.minY, first.minZ,
                    first.maxX, first.maxY, first.maxZ);
        }
        return null;
    }

    private static PhysicsBox box(AABB box, int minX, int minY, int minZ) {
        return new PhysicsBox(new PhysicsVector(box.getCenter().x - minX, box.getCenter().y - minY,
                box.getCenter().z - minZ), PhysicsQuaternion.IDENTITY,
                new PhysicsVector(box.getXsize() / 2.0, box.getYsize() / 2.0, box.getZsize() / 2.0));
    }

    public record SectionGeometry(List<PhysicsBox> boxes, int blocks, AABB bounds) {
        public SectionGeometry {
            boxes = List.copyOf(boxes);
            if (blocks < 0) throw new IllegalArgumentException("Block count cannot be negative");
        }
    }
}
