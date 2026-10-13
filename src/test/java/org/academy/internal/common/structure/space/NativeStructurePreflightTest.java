package org.academy.internal.common.structure.space;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.NativeStructureRuntime;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructurePreflightTest {
    @TempDir Path directory;

    @Test void absentOrEmptyIndexDoesNotCreateAFalseMigrationOrWriteAnyFiles() throws Exception {
        var missing = directory.resolve("not-created");
        assertFalse(NativeStructureManifest.inspect(missing).requiresMigration());
        assertFalse(Files.exists(missing));
        var manifest = new NativeStructureManifest(directory);
        manifest.persist();
        var before = Files.readAllBytes(directory.resolve("metadata.dat"));
        assertFalse(NativeStructureManifest.inspect(directory).requiresMigration());
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("metadata.dat")));
    }

    @Test void activeAndUnloadedSavedContentAreBothRetainedByteForByte() throws Exception {
        var manifest = new NativeStructureManifest(directory);
        for (int slot = 0; slot < 2; slot++) {
            var ref = new StructureRef(UUID.randomUUID(), Identifier.withDefaultNamespace("overworld"),
                    NativeStructureRuntime.ID, 0);
            manifest.claimSlot(slot, ref.id());
            manifest.put(new NativeStructureManifest.Entry(ref, slot, StructurePose.identity(Vec3.ZERO), slot == 0, false));
        }
        manifest.persist();
        var before = Files.readAllBytes(directory.resolve("metadata.dat"));
        var result = NativeStructureManifest.inspect(directory);
        assertTrue(result.requiresMigration());
        assertTrue(result.healthy());
        assertEquals(2, result.structures());
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("metadata.dat")));
    }

    @Test void interruptedAllocationAndRecoveryWithoutIndexCannotLookEmpty() throws Exception {
        var claims = directory.resolve("claims");
        new NativeStructureManifest(claims).claimSlot(1, UUID.randomUUID());
        assertTrue(NativeStructureManifest.inspect(claims).requiresMigration());
        var recovery = directory.resolve("recovery");
        new NativeStructureManifest(recovery).persistRecovery(3, new CompoundTag());
        assertTrue(NativeStructureManifest.inspect(recovery).requiresMigration());
    }

    @Test void malformedIndexAndNonDirectoryAreNeverOverwrittenOrTreatedAsEmpty() throws Exception {
        var metadata = directory.resolve("metadata.dat");
        byte[] bad = {1, 2, 3};
        Files.write(metadata, bad);
        assertFalse(NativeStructureManifest.inspect(directory).healthy());
        assertTrue(NativeStructureManifest.inspect(directory).requiresMigration());
        assertTrue(NativeStructureManifest.inspect(metadata).requiresMigration());
        assertArrayEquals(bad, Files.readAllBytes(metadata));
    }
}
