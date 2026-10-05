package org.academy.internal.common.ability.electromaster;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.path.LinePath;
import org.academy.internal.common.ability.electromaster.skystrike.SkyStrikeProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyStrikeGeometryTest {
    private static void assertFinite(Electromaster.Client.SkyStrikeGeometry geometry) {
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
        var first = Electromaster.Client.SkyStrikeGeometry.build(
                SkyStrikeProfile.THUNDERCLAP,
                impact,
                123456789L,
                Electromaster.Client.SkyStrikeGeometry.Detail.FULL
        );
        var second = Electromaster.Client.SkyStrikeGeometry.build(
                SkyStrikeProfile.THUNDERCLAP,
                impact,
                123456789L,
                Electromaster.Client.SkyStrikeGeometry.Detail.FULL
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
        var reduced = Electromaster.Client.SkyStrikeGeometry.build(
                SkyStrikeProfile.THUNDERCLAP,
                Vec3.ZERO,
                42L,
                Electromaster.Client.SkyStrikeGeometry.Detail.REDUCED
        );
        var far = Electromaster.Client.SkyStrikeGeometry.build(
                SkyStrikeProfile.LIGHTNING_STORM,
                Vec3.ZERO,
                42L,
                Electromaster.Client.SkyStrikeGeometry.Detail.COLUMN_ONLY
        );

        assertEquals(6, reduced.aerialArcCount());
        assertEquals(10, reduced.inwardArcCount());
        assertEquals(0, reduced.groundArcCount());
        assertEquals(16, reduced.paths().size());
        assertTrue(far.paths().isEmpty());
    }
}
