package org.academy.internal.common.sublevel;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelHostManifestTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");
    @TempDir Path root;

    @Test
    void oldOverworldPathAndAbsentHostOwnershipSurviveDiscovery() throws Exception {
        var overworld = new SubLevelManifest(root);
        var oldEntry = entry(new PlotPos(0, 0));
        overworld.put(oldEntry);
        overworld.persist();
        var absent = new SubLevelManifest(SubLevelManifest.directoryForHost(root, NETHER));
        var retained = entry(new PlotPos(1, 0));
        absent.put(retained);
        absent.persist();

        var hosts = SubLevelManifest.loadHosts(root, List.of(OVERWORLD));
        assertEquals(root, SubLevelManifest.directoryForHost(root, OVERWORLD));
        assertEquals(oldEntry, hosts.get(OVERWORLD).get(oldEntry.id()).orElseThrow());
        assertEquals(retained, hosts.get(NETHER).get(retained.id()).orElseThrow());
    }

    @Test
    void duplicatePlotOrIdentityAcrossHostsFailsBeforeAllocation() throws Exception {
        var first = new SubLevelManifest(root);
        var second = new SubLevelManifest(SubLevelManifest.directoryForHost(root, NETHER));
        var original = entry(new PlotPos(0, 0));
        first.put(original);
        second.put(entry(original.plot()));
        assertThrows(IOException.class, () -> SubLevelManifest.validateOwnership(List.of(first, second)));
        var duplicateIdentity = new SubLevelManifest(root.resolve("other"));
        duplicateIdentity.put(new SubLevelManifest.Entry(original.id(), new PlotPos(1, 0), original.localBounds(),
                original.pose(), original.state(), 0));
        assertThrows(IOException.class, () -> SubLevelManifest.validateOwnership(List.of(first, duplicateIdentity)));
    }

    @Test
    void dimensionPathsAreDistinctAndCannotTraverseTheRoot() throws Exception {
        var slash = SubLevelManifest.directoryForHost(root, Identifier.parse("example:moon/base"));
        var underscore = SubLevelManifest.directoryForHost(root, Identifier.parse("example:moon_base"));
        assertNotEquals(slash, underscore);
        assertEquals(root.resolve("hosts"), slash.getParent());
        var discovered = SubLevelManifest.loadHosts(root, List.of(Identifier.parse("example:moon/base")));
        assertTrue(discovered.containsKey(Identifier.parse("example:moon/base")));
    }

    private static SubLevelManifest.Entry entry(PlotPos plot) {
        return new SubLevelManifest.Entry(SubLevelId.random(), plot, new AABB(0, 0, 0, 1, 1, 1),
                StructurePose.identity(Vec3.ZERO), SubLevelState.ALLOCATED, 0);
    }
}
