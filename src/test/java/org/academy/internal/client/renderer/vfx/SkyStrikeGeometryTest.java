package org.academy.internal.client.renderer.vfx;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.path.LinePath;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyStrikeGeometryTest {
    private static void assertFinite(SkyStrikeGeometry geometry) {
        for (var path : geometry.paths()) {
            var line = (LinePath) path.path();
            assertTrue(Float.isFinite(line.start().x()));
            assertTrue(Float.isFinite(line.start().y()));
            assertTrue(Float.isFinite(line.start().z()));
            assertTrue(Float.isFinite(line.end().x()));
            assertTrue(Float.isFinite(line.end().y()));
            assertTrue(Float.isFinite(line.end().z()));
        }
    }

    @Test
    void thunderclapBuildsTheFullDeterministicLayerSet() {
        var impact = new Vec3(4.5, 72.0, -8.5);
        var first = SkyStrikeGeometry.build(
                SkyStrikeProfile.THUNDERCLAP,
                impact,
                123456789L,
                SkyStrikeGeometry.Detail.FULL
        );
        var second = SkyStrikeGeometry.build(
                SkyStrikeProfile.THUNDERCLAP,
                impact,
                123456789L,
                SkyStrikeGeometry.Detail.FULL
        );

        assertEquals(12, first.aerialArcCount());
        assertEquals(20, first.inwardArcCount());
        assertEquals(24, first.groundArcCount());
        assertEquals(56, first.paths().size());
        assertEquals(first.paths(), second.paths());
        assertFinite(first);
    }

    @Test
    void distanceLodReducesBranchesAndRemovesGroundArcs() {
        var reduced = SkyStrikeGeometry.build(
                SkyStrikeProfile.THUNDERCLAP,
                Vec3.ZERO,
                42L,
                SkyStrikeGeometry.Detail.REDUCED
        );
        var far = SkyStrikeGeometry.build(
                SkyStrikeProfile.LIGHTNING_STORM,
                Vec3.ZERO,
                42L,
                SkyStrikeGeometry.Detail.COLUMN_ONLY
        );

        assertEquals(6, reduced.aerialArcCount());
        assertEquals(10, reduced.inwardArcCount());
        assertEquals(0, reduced.groundArcCount());
        assertEquals(16, reduced.paths().size());
        assertTrue(far.paths().isEmpty());
    }
}
