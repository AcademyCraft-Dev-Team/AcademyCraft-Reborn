package org.academy.internal.common.sublevel;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelContactResolverTest {
    private static SubLevelContactResolver.Obstacle obstacle(AABB bounds) {
        var reference = new StructureRef(new UUID(0, 1), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:sublevel"), 0);
        return new SubLevelContactResolver.Obstacle(
                SubLevelOrientedCollision.Box.of(bounds, StructurePose.identity(Vec3.ZERO)),
                reference, 1, 1, bounds.getCenter());
    }

    @Test
    void stepCandidatesIncludeTheApproachingSurface() {
        var entity = new AABB(0.1, 0, 0.2, 0.7, 1.8, 0.8);
        var step = obstacle(new AABB(1, 0, 0, 2, 0.5, 1));
        var result = SubLevelContactResolver.clipMovement(entity, new Vec3(0.8, 0, 0),
                List.of(step), List.of(), 0.6, true, 0, true);
        assertEquals(0.8, result.movement().x, 1.0E-6);
        assertEquals(0.5, result.movement().y, 1.0E-6);
    }

    @Test
    void structureStepCannotRaiseAnEntityThroughAHostCeiling() {
        var entity = new AABB(0.1, 0, 0.2, 0.7, 1.8, 0.8);
        var step = obstacle(new AABB(1, 0, 0, 2, 0.5, 1));
        var ceiling = Shapes.create(new AABB(0, 1.9, 0, 3, 2.9, 1));
        var result = SubLevelContactResolver.clipMovement(entity, new Vec3(0.8, 0, 0),
                List.of(step), List.of(ceiling), 0.6, true, 0, true);
        assertTrue(result.movement().x <= 0.3 + 1.0E-6);
        assertTrue(entity.maxY + result.movement().y <= 1.9 + 1.0E-6);
    }

    @Test
    void depenetrationCannotPushAnEntityThroughAHostWall() {
        var entity = new AABB(0.9, 0.2, 0.2, 1.5, 2, 0.8);
        var body = obstacle(new AABB(0, 0, 0, 1, 1, 1));
        var wall = Shapes.create(new AABB(1.55, 0, 0, 2.55, 3, 1));
        var result = SubLevelContactResolver.clipMovement(entity, Vec3.ZERO,
                List.of(body), List.of(wall), 0, true, 0, false);
        assertEquals(0.05, result.movement().x, 1.0E-6);
    }

    @Test
    void hostWallIsRecheckedAfterStructureChangesTheVerticalPath() {
        var entity = new AABB(0.1, 1.1, 0.2, 0.7, 2.9, 0.8);
        var floor = obstacle(new AABB(0, 0, 0, 3, 1, 1));
        var wall = Shapes.create(new AABB(1, 2, 0, 2, 3, 1));
        var result = SubLevelContactResolver.clipMovement(entity, new Vec3(0.8, -1.1, 0),
                List.of(floor), List.of(wall), 0, false, 0, false);
        assertEquals(-0.1, result.movement().y, 2.0E-6);
        assertTrue(result.movement().x <= 0.3 + 1.0E-6);
    }
}
