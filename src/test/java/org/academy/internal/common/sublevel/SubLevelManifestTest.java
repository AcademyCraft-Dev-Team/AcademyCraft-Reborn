package org.academy.internal.common.sublevel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelManifestTest {
    @TempDir
    Path directory;

    @Test
    void manifestRoundTripsThroughAtomicFile() throws Exception {
        var first = entry(new Vec3(10, 20, 30), SubLevelState.ACTIVE, 4);
        var second = entry(new Vec3(-5, 64, 8), SubLevelState.SLEEPING, 9);
        var manifest = new SubLevelManifest(directory);
        manifest.put(first);
        manifest.put(second);
        manifest.persist();

        var loaded = new SubLevelManifest(directory);
        assertEquals(2, loaded.size());
        assertEquals(first, loaded.get(first.id()).orElseThrow());
        assertEquals(second, loaded.get(second.id()).orElseThrow());

        assertTrue(loaded.remove(first.id()));
        loaded.persist();
        var reloaded = new SubLevelManifest(directory);
        assertEquals(1, reloaded.size());
        assertTrue(reloaded.get(first.id()).isEmpty());
        assertTrue(reloaded.get(second.id()).isPresent());
    }

    @Test
    void twoHundredFiftySixPersistentEntriesRoundTrip() throws Exception {
        var manifest = new SubLevelManifest(directory);
        for (int index = 0; index < 256; index++) {
            manifest.put(new SubLevelManifest.Entry(SubLevelId.random(),
                    new PlotPos(index % 16, index / 16), new AABB(0, 0, 0, 3, 2, 1),
                    StructurePose.identity(new Vec3(index, 64, 0)), SubLevelState.SLEEPING, index));
        }
        manifest.persist();
        assertEquals(256, new SubLevelManifest(directory).size());
    }

    @Test
    void unknownVersionAndDuplicateIdentityAreRejected() {
        var entry = entry(Vec3.ZERO, SubLevelState.INACTIVE, 0);
        var root = SubLevelManifest.encode(List.of(entry));
        var wrong = root.copy();
        wrong.putInt("version", 99);
        assertThrows(Exception.class, () -> SubLevelManifest.decode(wrong));

        var duplicate = SubLevelManifest.encode(List.of(entry)).copy();
        var list = duplicate.getList("entries").orElseThrow();
        list.add(list.get(0).copy());
        duplicate.put("entries", list);
        assertThrows(Exception.class, () -> SubLevelManifest.decode(duplicate));
    }

    private static SubLevelManifest.Entry entry(Vec3 origin, SubLevelState state, long revision) {
        return new SubLevelManifest.Entry(SubLevelId.random(), new PlotPos(2, 3),
                new AABB(0, 0, 0, 3, 2, 1), StructurePose.identity(origin), state, revision);
    }
}
