package org.academy.internal.common.ability.aeromanip;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AirflowFieldTest {
    private static final ResourceKey<Level> DIMENSION = ResourceKey.create(
            Registries.DIMENSION, Identifier.fromNamespaceAndPath("test", "airflow_field"));

    private static AirflowField field(AirflowField.Shape shape, Vec3 center, Vec3 direction, double radius, double length) {
        return new AirflowField(UUID.randomUUID(), UUID.randomUUID(), DIMENSION,
                AirflowField.Type.VORTEX, shape, center, direction, radius, length, 1, 80);
    }

    @Test
    void normalizesDirectionAndRejectsNonFinitePoints() {
        var field = field(AirflowField.Shape.SPHERE, new Vec3(0, 0, 0), new Vec3(10, 0, 0), 2, 0);
        assertEquals(1.0, field.direction().length(), 1.0e-9);
        assertFalse(field.contains(new Vec3(Double.NaN, 0, 0), 0));
    }

    @Test
    void sphereBoundaryIsIncluded() {
        var field = field(AirflowField.Shape.SPHERE, Vec3.ZERO, Vec3.ZERO, 3, 0);
        assertTrue(field.contains(new Vec3(3, 0, 0), 0));
        assertTrue(field.contains(new Vec3(3.5, 0, 0), 0.5));
        assertFalse(field.contains(new Vec3(3.01, 0, 0), 0));
    }

    @Test
    void capsuleUsesTheWholeSegment() {
        var field = field(AirflowField.Shape.CAPSULE, Vec3.ZERO, new Vec3(0, 0, 1), 1, 10);
        assertTrue(field.contains(new Vec3(0, 0, 5), 0));
        assertTrue(field.contains(new Vec3(1, 0, 10), 0));
        assertFalse(field.contains(new Vec3(1.01, 0, 5), 0));
    }

    @Test
    void coneExpandsFromTheApex() {
        var field = field(AirflowField.Shape.CONE, Vec3.ZERO, new Vec3(0, 0, 1), 4, 8);
        assertTrue(field.contains(new Vec3(0, 0, 0), 0));
        assertTrue(field.contains(new Vec3(1.9, 0, 4), 0));
        assertFalse(field.contains(new Vec3(3, 0, 4), 0));
        assertFalse(field.contains(new Vec3(0, 0, -0.1), 0));
    }
}
