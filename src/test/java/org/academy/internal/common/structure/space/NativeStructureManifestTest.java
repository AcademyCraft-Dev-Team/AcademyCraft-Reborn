package org.academy.internal.common.structure.space;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureManifestTest {
    @TempDir
    Path directory;

    @Test
    void reopensIndependentIdentitiesPosesAndQuarantineWithoutExposingMutableEntries() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = entry(1, 0, true, false);
        var second = entry(2, 3, false, true);
        manifest.claimSlot(0, first.reference().id());
        manifest.claimSlot(3, second.reference().id());
        manifest.put(first);
        manifest.put(second);
        manifest.persist();

        var reopened = new NativeStructureManifest(directory);
        assertTrue(reopened.healthy());
        assertEquals(List.of(first, second), reopened.entries());
        assertEquals(first, reopened.get(first.reference().id()).orElseThrow());
        assertEquals(second, reopened.get(second.reference().id()).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> reopened.entries().clear());
        assertTrue(reopened.ownsSlot(0, first.reference().id()));
        assertTrue(reopened.ownsSlot(3, second.reference().id()));
        assertFalse(reopened.slotClaimed(1));
        assertFalse(reopened.hasRecovery(1));
    }

    @Test
    void updatesPoseAndAvailabilityWithoutChangingExistingIdentityOrSlot() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var original = entry(1, 0, true, false);
        manifest.put(original);
        var replacement = new NativeStructureManifest.Entry(original.reference(), 0,
                new StructurePose(new Vec3(-100.5, 85.25, 90.125), 0, 2, 0, 0), false, true);
        manifest.put(replacement);
        assertThrows(IllegalArgumentException.class, () -> manifest.put(entry(2, 0, false, false)));
        assertThrows(IllegalArgumentException.class, () -> manifest.put(entry(1, 1, false, false)));
        var changedRef = new StructureRef(original.reference().id(), original.reference().dimension(),
                original.reference().backend(), 9);
        assertThrows(IllegalArgumentException.class, () -> manifest.put(new NativeStructureManifest.Entry(
                changedRef, 0, original.pose(), false, false)));
        manifest.persist();

        assertEquals(List.of(replacement), new NativeStructureManifest(directory).entries());
    }

    @Test
    void slotMarkersSurviveMissingMetadataAndNeverGrantOwnershipToAnotherIdentity() throws IOException {
        var first = new UUID(0, 1);
        var second = new UUID(0, 2);
        var manifest = new NativeStructureManifest(directory);
        manifest.claimSlot(2, first);
        manifest.claimSlot(2, first);
        assertFalse(Files.exists(directory.resolve("metadata.dat")));

        var reopened = new NativeStructureManifest(directory);
        assertTrue(reopened.healthy());
        assertTrue(reopened.entries().isEmpty());
        assertTrue(reopened.slotClaimed(2));
        assertTrue(reopened.ownsSlot(2, first));
        assertFalse(reopened.ownsSlot(2, second));
        assertThrows(IOException.class, () -> reopened.claimSlot(2, second));
        assertEquals(first.toString(), Files.readString(directory.resolve("owner_2.txt")));
    }

    @Test
    void malformedMarkersReserveCapacityWithoutImpersonatingAnOwner() throws IOException {
        var owner = new UUID(0, 1);
        Files.writeString(directory.resolve("owner_0.txt"), "interrupted owner marker");
        Files.createDirectory(directory.resolve("owner_1.txt"));
        var manifest = new NativeStructureManifest(directory);
        for (var slot : List.of(0, 1)) {
            assertTrue(manifest.slotClaimed(slot));
            assertFalse(manifest.ownsSlot(slot, owner));
            assertThrows(IOException.class, () -> manifest.claimSlot(slot, owner));
        }
        assertEquals("interrupted owner marker", Files.readString(directory.resolve("owner_0.txt")));
    }

    @Test
    void corruptedCompressionDisablesAllWritesAndPreservesTheOriginalFile() throws IOException {
        var path = directory.resolve("metadata.dat");
        var original = new byte[]{1, 2, 3, 4, 5};
        Files.write(path, original);
        var manifest = new NativeStructureManifest(directory);

        assertUnhealthyAndUnwritable(manifest);
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test
    void duplicateSavedIdentitiesOrSlotsRejectTheWholeIndexWithoutPartialRecovery() throws IOException {
        for (var duplicateSlot : List.of(false, true)) {
            var location = directory.resolve(duplicateSlot ? "slot" : "uuid");
            var manifest = new NativeStructureManifest(location);
            manifest.put(entry(1, 0, true, false));
            manifest.put(entry(2, 1, true, false));
            manifest.persist();
            var root = read(location.resolve("metadata.dat"));
            var entries = root.getList("entries").orElseThrow();
            var first = entries.getCompound(0).orElseThrow();
            var second = entries.getCompound(1).orElseThrow();
            if (duplicateSlot) second.putInt("slot", 0);
            else second.putString("id", first.getString("id").orElseThrow());
            NbtIo.writeCompressed(root, location.resolve("metadata.dat"));
            var original = Files.readAllBytes(location.resolve("metadata.dat"));

            assertUnhealthyAndUnwritable(new NativeStructureManifest(location));
            assertArrayEquals(original, Files.readAllBytes(location.resolve("metadata.dat")));
        }
    }

    @Test
    void rejectsMissingFieldsInvalidIdentityBoundsAndCoercedNumericTypesOnDisk() throws IOException {
        var mutations = List.<Consumer<CompoundTag>>of(
                root -> root.putInt("version", 2),
                root -> root.putDouble("version", 1.0),
                root -> savedEntry(root).remove("active"),
                root -> savedEntry(root).putByte("quarantined", (byte) 2),
                root -> savedEntry(root).putInt("slot", -1),
                root -> savedEntry(root).putInt("slot", 4),
                root -> savedEntry(root).putDouble("slot", 0.5),
                root -> savedEntry(root).putLong("generation", -1),
                root -> savedEntry(root).putInt("generation", 0),
                root -> savedEntry(root).putString("id", "not a uuid"),
                root -> savedEntry(root).putString("backend", "academy:legacy_entity"),
                root -> savedEntry(root).putString("dimension", "academy:structure_space_0"),
                root -> savedEntry(root).getCompound("pose").orElseThrow().putDouble("x", Double.NaN),
                root -> savedEntry(root).getCompound("pose").orElseThrow().putDouble("rotation_w", 0)
        );
        for (var index = 0; index < mutations.size(); index++) {
            var location = directory.resolve("invalid_" + index);
            var manifest = new NativeStructureManifest(location);
            manifest.put(entry(1, 0, true, false));
            manifest.persist();
            var path = location.resolve("metadata.dat");
            var root = read(path);
            mutations.get(index).accept(root);
            NbtIo.writeCompressed(root, path);
            var original = Files.readAllBytes(path);
            var reopened = new NativeStructureManifest(location);
            assertFalse(reopened.healthy(), "invalid scenario " + index);
            assertTrue(reopened.entries().isEmpty());
            assertThrows(IOException.class, reopened::persist);
            assertArrayEquals(original, Files.readAllBytes(path));
        }
    }

    @Test
    void rejectsInvalidSlotsAndStorageHostsBeforeChangingFiles() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var owner = new UUID(0, 1);
        for (var slot : List.of(-1, 4, Integer.MAX_VALUE)) {
            assertThrows(IllegalArgumentException.class, () -> manifest.claimSlot(slot, owner));
            assertThrows(IllegalArgumentException.class, () -> manifest.ownsSlot(slot, owner));
            assertThrows(IllegalArgumentException.class, () -> manifest.slotClaimed(slot));
            assertThrows(IllegalArgumentException.class, () -> manifest.hasRecovery(slot));
            assertThrows(IllegalArgumentException.class, () -> manifest.persistRecovery(slot, new CompoundTag()));
            assertThrows(IllegalArgumentException.class, () -> entry(1, slot, false, false));
        }
        for (var slot = 0; slot < 4; slot++) {
            var reference = new StructureRef(owner, Identifier.parse("academy:structure_space_" + slot),
                    Identifier.parse("academy:native_level"), 0);
            assertThrows(IllegalArgumentException.class, () -> new NativeStructureManifest.Entry(
                    reference, 0, StructurePose.identity(Vec3.ZERO), false, false));
        }
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void compressedPayloadCannotBypassTheDecodedMemoryLimit() throws IOException {
        var root = new CompoundTag();
        root.putInt("version", 1);
        root.putByteArray("compressed_padding", new byte[NativeStructureManifest.MAX_PAYLOAD_BYTES + 1]);
        var path = directory.resolve("metadata.dat");
        NbtIo.writeCompressed(root, path);
        assertTrue(Files.size(path) < 10_000);
        var original = Files.readAllBytes(path);

        assertUnhealthyAndUnwritable(new NativeStructureManifest(directory));
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test
    void oversizedPhysicalFileIsAlsoRejectedBeforeReadingIt() throws IOException {
        var path = directory.resolve("metadata.dat");
        Files.write(path, new byte[NativeStructureManifest.MAX_PAYLOAD_BYTES + 1]);
        assertUnhealthyAndUnwritable(new NativeStructureManifest(directory));
        assertEquals(NativeStructureManifest.MAX_PAYLOAD_BYTES + 1L, Files.size(path));
    }

    @Test
    void recoveryEvidenceIsIndependentAndOversizedReplacementPreservesThePreviousRecord() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        manifest.put(entry(1, 0, false, true));
        manifest.persist();
        var recovery = new CompoundTag();
        recovery.putString("reason", "unresolved after-image");
        recovery.putInt("pending_cells", 3);
        manifest.persistRecovery(0, recovery);
        var recoveryPath = directory.resolve("recovery_0.dat");
        assertTrue(manifest.hasRecovery(0));
        assertEquals(recovery, read(recoveryPath));
        var previous = Files.readAllBytes(recoveryPath);
        var oversized = new CompoundTag();
        oversized.putByteArray("payload", new byte[NativeStructureManifest.MAX_PAYLOAD_BYTES]);

        assertThrows(IOException.class, () -> manifest.persistRecovery(0, oversized));
        assertArrayEquals(previous, Files.readAllBytes(recoveryPath));
        assertEquals(List.of(entry(1, 0, false, true)), new NativeStructureManifest(directory).entries());
    }

    @Test
    void recoveryEvidenceBlocksActivationIndependentlyOfTheLastSavedIndexFlag() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var active = entry(1, 0, true, false);
        manifest.put(active);
        manifest.persist();
        var recovery = new CompoundTag();
        recovery.putString("reason", "recovery write committed before the index update");
        manifest.persistRecovery(0, recovery);

        var reopened = new NativeStructureManifest(directory);
        assertTrue(reopened.healthy());
        assertEquals(active, reopened.entries().getFirst());
        assertTrue(reopened.hasRecovery(0));
        assertFalse(reopened.hasRecovery(1));
        Files.createDirectory(directory.resolve("recovery_1.dat"));
        assertTrue(reopened.hasRecovery(1));
        Files.writeString(directory.resolve("recovery_2.dat"), "malformed recovery must not reactivate contents");
        assertTrue(reopened.hasRecovery(2));
    }

    @Test
    void oversizedNbtStringFailsInsteadOfSilentlyReplacingRecoveryContentsWithAnEmptyString() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var recovery = new CompoundTag();
        recovery.putString("reason", "preserve this record");
        manifest.persistRecovery(0, recovery);
        var path = directory.resolve("recovery_0.dat");
        var previous = Files.readAllBytes(path);
        var invalid = new CompoundTag();
        invalid.putString("oversized_string", "x".repeat(65_536));
        assertTrue(invalid.sizeInBytes() < NativeStructureManifest.MAX_PAYLOAD_BYTES);

        assertThrows(IOException.class, () -> manifest.persistRecovery(0, invalid));
        assertArrayEquals(previous, Files.readAllBytes(path));
    }

    @Test
    void failedAtomicReplacementReportsTheErrorAndKeepsTheInMemoryRecord() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var entry = entry(1, 0, true, false);
        manifest.put(entry);
        Files.createDirectory(directory.resolve("metadata.dat"));
        Files.writeString(directory.resolve("metadata.dat/obstruction.txt"), "keep");

        assertThrows(IOException.class, manifest::persist);
        assertEquals(List.of(entry), manifest.entries());
        assertEquals("keep", Files.readString(directory.resolve("metadata.dat/obstruction.txt")));
        try (var files = Files.list(directory)) {
            assertEquals(List.of("metadata.dat"), files.map(path -> path.getFileName().toString()).toList());
        }
    }

    private static NativeStructureManifest.Entry entry(long id, int slot, boolean active, boolean quarantined) {
        return new NativeStructureManifest.Entry(new StructureRef(new UUID(0, id),
                Identifier.parse("minecraft:overworld"), Identifier.parse("academy:native_level"), 2),
                slot, StructurePose.identity(new Vec3(-id * 0.5, 80, id * 1.25)), active, quarantined);
    }

    private static CompoundTag read(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.create(NativeStructureManifest.MAX_PAYLOAD_BYTES));
    }

    private static CompoundTag savedEntry(CompoundTag root) {
        return root.getList("entries").orElseThrow().getCompound(0).orElseThrow();
    }

    private static void assertUnhealthyAndUnwritable(NativeStructureManifest manifest) {
        assertFalse(manifest.healthy());
        assertTrue(manifest.entries().isEmpty());
        assertTrue(manifest.get(new UUID(0, 1)).isEmpty());
        assertTrue(manifest.slotClaimed(0));
        assertTrue(manifest.hasRecovery(0));
        assertFalse(manifest.ownsSlot(0, new UUID(0, 1)));
        assertThrows(IllegalStateException.class, () -> manifest.put(entry(1, 0, false, false)));
        assertThrows(IOException.class, manifest::persist);
        assertThrows(IOException.class, () -> manifest.claimSlot(0, new UUID(0, 1)));
        assertThrows(IOException.class, () -> manifest.persistRecovery(0, new CompoundTag()));
    }
}
