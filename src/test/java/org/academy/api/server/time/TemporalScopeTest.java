package org.academy.api.server.time;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalScopeTest {
    private static final ResourceKey<Level> DIMENSION_A = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("test", "temporal_a")
    );
    private static final ResourceKey<Level> DIMENSION_B = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("test", "temporal_b")
    );

    @Test
    void saveAndDimensionScopesDoNotRequirePosition() {
        assertTrue(TemporalScope.save().contains(DIMENSION_A, null));
        assertTrue(TemporalScope.dimension(DIMENSION_A)
                .contains(DIMENSION_A, null));
        assertFalse(TemporalScope.dimension(DIMENSION_A)
                .contains(DIMENSION_B, null));
    }

    @Test
    void sphereIncludesBoundaryOnlyInItsDimension() {
        var scope = TemporalScope.sphere(
                DIMENSION_A,
                new Vec3(10.0D, 20.0D, 30.0D),
                4.0D
        );

        assertTrue(scope.contains(
                DIMENSION_A,
                new Vec3(14.0D, 20.0D, 30.0D)
        ));
        assertFalse(scope.contains(
                DIMENSION_A,
                new Vec3(14.01D, 20.0D, 30.0D)
        ));
        assertFalse(scope.contains(DIMENSION_B, scope.center()));
        assertFalse(scope.contains(DIMENSION_A, null));
        assertTrue(scope.isSpatial());
    }

    @Test
    void sphereRejectsInvalidGeometry() {
        assertThrows(IllegalArgumentException.class, () -> TemporalScope.sphere(
                DIMENSION_A,
                Vec3.ZERO,
                0.0D
        ));
        assertThrows(IllegalArgumentException.class, () -> TemporalScope.sphere(
                DIMENSION_A,
                new Vec3(Double.NaN, 0.0D, 0.0D),
                1.0D
        ));
    }
}
