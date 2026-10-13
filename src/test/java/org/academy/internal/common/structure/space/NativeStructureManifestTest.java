package org.academy.internal.common.structure.space;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsAttachment;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsRegionLayout;
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
                root -> root.putInt("version", 4),
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

    @Test
    void physicsCheckpointsRoundTripWithPoseWithoutPersistingRuntimeIdentity() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var moving = entry(1, 0, true, false).withPhysicsAttachment(attachment(true));
        var blocked = entry(2, 1, false, true).withPhysicsAttachment(attachment(false));
        var awaiting = entry(3, 2, true, false).withPhysicsAttachment(
                new NativeStructurePhysicsAttachment(attachment(true).regionId(), attachment(true).bounds(), null, true));
        manifest.putAll(List.of(moving, blocked, awaiting));
        manifest.persist();
        var root = read(directory.resolve("metadata.dat"));
        assertEquals(3, root.getInt("version").orElseThrow());
        var physics = savedEntry(root).getCompound("physics").orElseThrow();
        assertEquals(java.util.Set.of("region_id", "bounds", "motion", "resume_allowed"), physics.keySet());
        assertEquals(moving.physicsAttachment().regionId().toString(), physics.getString("region_id").orElseThrow());
        assertEquals(java.util.Set.of("linear_velocity", "angular_velocity", "local_center_of_mass", "sleeping", "geometry_digest"),
                physics.getCompound("motion").orElseThrow().keySet());
        assertEquals(List.of(moving, blocked, awaiting), new NativeStructureManifest(directory).entries());
        assertEquals(moving.physicsAttachment(), moving.withPose(StructurePose.identity(Vec3.ZERO)).physicsAttachment());
        assertEquals(moving.physicsAttachment(), moving.withAvailability(false, true).physicsAttachment());
    }

    @Test
    void legacyVersionOneLoadsStationaryAndUpgradesWithoutInventingPhysics() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var original = entry(1, 0, true, false);
        manifest.put(original);
        manifest.persist();
        var path = directory.resolve("metadata.dat");
        var root = read(path);
        root.putInt("version", 1);
        NbtIo.writeCompressed(root, path);
        var reopened = new NativeStructureManifest(directory);
        assertTrue(reopened.healthy());
        assertEquals(List.of(original), reopened.entries());
        assertNull(reopened.entries().getFirst().physicsAttachment());
        reopened.persist();
        var upgraded = read(path);
        assertEquals(3, upgraded.getInt("version").orElseThrow());
        assertNull(savedEntry(upgraded).get("physics"));
    }

    @Test
    void legacyVersionCannotSmuggleAnOptionalPhysicsCheckpoint() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        manifest.put(entry(1, 0, true, false).withPhysicsAttachment(attachment(true)));
        manifest.persist();
        var path = directory.resolve("metadata.dat");
        var root = read(path);
        root.putInt("version", 1);
        NbtIo.writeCompressed(root, path);
        var previous = Files.readAllBytes(path);
        assertUnhealthyAndUnwritable(new NativeStructureManifest(directory));
        assertArrayEquals(previous, Files.readAllBytes(path));
    }

    @Test
    void malformedPhysicsFieldsDisableWritesWithoutDiscardingSavedBytes() throws IOException {
        var mutations = List.<Consumer<CompoundTag>>of(
                tag -> tag.putString("physics", "wrong type"),
                tag -> physics(tag).remove("region_id"),
                tag -> physics(tag).putInt("region_id", 7),
                tag -> physics(tag).putString("region_id", "1-1-1-1-1"),
                tag -> physics(tag).putString("region_id", "ABCDEFAB-CDEF-ABCD-EFAB-CDEFABCDEFAB"),
                tag -> physics(tag).putString("region_id", "00000000-0000-0000-0000-000000000001 "),
                tag -> physics(tag).putString("region_id", "not-a-uuid"),
                tag -> physics(tag).remove("bounds"),
                tag -> physics(tag).putInt("resume_allowed", 1),
                tag -> physics(tag).putByte("resume_allowed", (byte) -1),
                tag -> physics(tag).putLong("runtime_revision", 7),
                tag -> physics(tag).putString("motion", "wrong type"),
                tag -> savedBounds(tag).putDouble("min_x", 13),
                tag -> savedBounds(tag).putInt("max_y", 88),
                tag -> savedBounds(tag).putDouble("max_z", Double.NaN),
                tag -> savedBounds(tag).putDouble("max_z", 12.001),
                tag -> savedMotion(tag).putInt("sleeping", 1),
                tag -> savedMotion(tag).putByte("sleeping", (byte) 2),
                tag -> savedMotion(tag).remove("geometry_digest"),
                tag -> savedMotion(tag).putString("geometry_digest", "F".repeat(64)),
                tag -> savedMotion(tag).putString("geometry_digest", "0".repeat(63)),
                tag -> savedMotion(tag).getCompound("linear_velocity").orElseThrow().putFloat("x", 1),
                tag -> savedMotion(tag).getCompound("angular_velocity").orElseThrow().putDouble("y", 10_000.1),
                tag -> savedMotion(tag).getCompound("local_center_of_mass").orElseThrow().putDouble("z", Double.NEGATIVE_INFINITY),
                tag -> savedMotion(tag).getCompound("local_center_of_mass").orElseThrow().putDouble("z", 1_000_000.1),
                tag -> savedMotion(tag).getCompound("linear_velocity").orElseThrow().putDouble("w", 0)
        );
        for (int index = 0; index < mutations.size(); index++) {
            var location = directory.resolve("physics_invalid_" + index);
            var manifest = new NativeStructureManifest(location);
            manifest.put(entry(1, 0, true, false).withPhysicsAttachment(attachment(true)));
            manifest.persist();
            var path = location.resolve("metadata.dat");
            var root = read(path);
            mutations.get(index).accept(savedEntry(root));
            NbtIo.writeCompressed(root, path);
            var previous = Files.readAllBytes(path);
            assertUnhealthyAndUnwritable(new NativeStructureManifest(location));
            assertArrayEquals(previous, Files.readAllBytes(path), "scenario " + index);
        }
    }

    @Test
    void checkpointBatchIsAtomicAcrossRegionConsistencyIdentityAndSlotFailures() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = entry(1, 0, true, false).withPhysicsAttachment(attachment(true));
        var second = entry(2, 1, true, false).withPhysicsAttachment(attachment(true));
        manifest.putAll(List.of(first, second));
        manifest.persist();
        var previous = Files.readAllBytes(directory.resolve("metadata.dat"));
        var moved = first.withPose(StructurePose.identity(new Vec3(1, 80, 1)));
        var otherBounds = new NativeStructurePhysicsAttachment(first.physicsAttachment().regionId(),
                new AABB(-11, 64, -12, 13, 88, 12), null, true);
        for (var invalid : List.of(second.withPhysicsAttachment(otherBounds), entry(2, 2, true, false),
                entry(3, 0, true, false))) {
            assertThrows(IllegalArgumentException.class, () -> manifest.putAll(List.of(moved, invalid)));
            assertEquals(List.of(first, second), manifest.entries());
            assertArrayEquals(previous, Files.readAllBytes(directory.resolve("metadata.dat")));
        }
        assertThrows(IllegalArgumentException.class, () -> manifest.putAll(List.of(moved, moved)));
        assertEquals(List.of(first, second), manifest.entries());
        assertThrows(IllegalArgumentException.class, () -> manifest.put(first.withPhysicsAttachment(otherBounds)));
        manifest.putAll(List.of(moved.withPhysicsAttachment(otherBounds), second.withPhysicsAttachment(otherBounds)));
        manifest.persist();
        assertEquals(manifest.entries(), new NativeStructureManifest(directory).entries());
    }

    @Test
    void savedIndexRejectsConflictingRegionsAndMoreThanThreeRegionIdentities() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = entry(1, 0, true, false).withPhysicsAttachment(attachment(true));
        var second = entry(2, 1, true, false).withPhysicsAttachment(attachment(true));
        manifest.putAll(List.of(first, second));
        manifest.persist();
        var path = directory.resolve("metadata.dat");
        var root = read(path);
        savedBounds(root.getList("entries").orElseThrow().getCompound(1).orElseThrow()).putDouble("min_x", -11);
        NbtIo.writeCompressed(root, path);
        assertUnhealthyAndUnwritable(new NativeStructureManifest(directory));

        var location = directory.resolve("hosts");
        var hosts = new NativeStructureManifest(location);
        for (int slot = 0; slot < 3; slot++) hosts.put(hostEntry(slot).withPhysicsAttachment(attachment(new UUID(2, slot), true)));
        var before = hosts.entries();
        assertThrows(IllegalArgumentException.class, () -> hosts.put(hostEntry(3).withPhysicsAttachment(attachment(new UUID(2, 3), true))));
        assertEquals(before, hosts.entries());
        hosts.put(hostEntry(3));
        hosts.persist();
        var hostPath = location.resolve("metadata.dat");
        var hostRoot = read(hostPath);
        var entries = hostRoot.getList("entries").orElseThrow();
        entries.getCompound(3).orElseThrow().put("physics", entries.getCompound(0).orElseThrow().getCompound("physics").orElseThrow().copy());
        physics(entries.getCompound(3).orElseThrow()).putString("region_id", new UUID(2, 3).toString());
        NbtIo.writeCompressed(hostRoot, hostPath);
        assertUnhealthyAndUnwritable(new NativeStructureManifest(location));
    }

    @Test
    void versionTwoGetsStableRegionIdentitiesWithoutChangingItsCheckpointOrRewritingOnRead() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = entry(1, 0, true, false).withPhysicsAttachment(attachment(true));
        var second = entry(2, 1, false, true).withPhysicsAttachment(attachment(false));
        var otherHost = hostEntry(2).withPhysicsAttachment(new NativeStructurePhysicsAttachment(
                new UUID(2, 9), first.physicsAttachment().bounds(), null, true));
        var originals = List.of(first, second, otherHost);
        manifest.putAll(originals);
        manifest.persist();
        var path = directory.resolve("metadata.dat");
        var root = read(path);
        makeLegacyV2(root);
        NbtIo.writeCompressed(root, path);
        var legacyBytes = Files.readAllBytes(path);
        var expected = originals.stream().map(entry -> {
            var previous = entry.physicsAttachment();
            return entry.withPhysicsAttachment(new NativeStructurePhysicsAttachment(
                    NativeStructurePhysicsRegionLayout.legacyId(entry.reference().dimension(), previous.bounds()),
                    previous.bounds(), previous.motion(), previous.resumeAllowed()));
        }).toList();

        var reopened = new NativeStructureManifest(directory);
        assertTrue(reopened.healthy());
        assertEquals(expected, reopened.entries());
        assertEquals(expected, new NativeStructureManifest(directory).entries());
        assertArrayEquals(legacyBytes, Files.readAllBytes(path));
        assertEquals(expected.get(0).physicsAttachment().regionId(), expected.get(1).physicsAttachment().regionId());
        assertNotEquals(expected.get(0).physicsAttachment().regionId(), expected.get(2).physicsAttachment().regionId());

        var values = root.getList("entries").orElseThrow();
        var reversed = new ListTag();
        for (int index = values.size() - 1; index >= 0; index--) reversed.add(values.get(index).copy());
        root.put("entries", reversed);
        NbtIo.writeCompressed(root, path);
        var reordered = new NativeStructureManifest(directory);
        assertTrue(reordered.healthy());
        for (var entry : expected) assertEquals(entry, reordered.get(entry.reference().id()).orElseThrow());
        reordered.persist();
        var upgraded = read(path);
        assertEquals(3, upgraded.getInt("version").orElseThrow());
        assertEquals(reordered.entries(), new NativeStructureManifest(directory).entries());
        for (var value : upgraded.getList("entries").orElseThrow()) {
            var physics = physics((CompoundTag) value);
            var id = physics.getString("region_id").orElseThrow();
            assertEquals(id, UUID.fromString(id).toString());
        }
    }

    @Test
    void versionTwoRejectsNewFieldsAndConflictingOldHostBoundsInsteadOfInventingPartitions() throws IOException {
        for (boolean conflictingBounds : List.of(false, true)) {
            var location = directory.resolve(conflictingBounds ? "legacy_conflict" : "legacy_new_field");
            var manifest = new NativeStructureManifest(location);
            var first = entry(1, 0, true, false).withPhysicsAttachment(attachment(true));
            manifest.putAll(List.of(first, entry(2, 1, true, false).withPhysicsAttachment(attachment(true))));
            manifest.persist();
            var path = location.resolve("metadata.dat");
            var root = read(path);
            if (conflictingBounds) {
                makeLegacyV2(root);
                var secondBounds = savedBounds(root.getList("entries").orElseThrow().getCompound(1).orElseThrow());
                secondBounds.putDouble("min_x", 13);
                secondBounds.putDouble("max_x", 37);
            } else {
                // A v2 record must not silently consume a v3 identity field.
                root.putInt("version", 2);
            }
            NbtIo.writeCompressed(root, path);
            var bytes = Files.readAllBytes(path);
            assertUnhealthyAndUnwritable(new NativeStructureManifest(location));
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }
    }

    @Test
    void independentRegionsPersistAndClearWithoutChangingTheOtherCheckpoint() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = entry(1, 0, true, false).withPhysicsAttachment(attachment(true));
        var other = attachment(new UUID(2, 2), false);
        var second = entry(2, 1, false, true).withPhysicsAttachment(new NativeStructurePhysicsAttachment(
                other.regionId(), other.bounds().move(25, 0, 0), other.motion(), other.resumeAllowed()));
        manifest.putAll(List.of(first, second));
        manifest.persist();
        assertEquals(List.of(first, second), new NativeStructureManifest(directory).entries());
        var original = Files.readAllBytes(directory.resolve("metadata.dat"));
        var moved = first.withPose(StructurePose.identity(new Vec3(3, 80, 4)));
        var near = second.withPhysicsAttachment(new NativeStructurePhysicsAttachment(other.regionId(),
                first.physicsAttachment().bounds().move(24.999, 0, 0), other.motion(), false));
        assertThrows(IllegalArgumentException.class, () -> manifest.putAll(List.of(moved, near)));
        assertEquals(List.of(first, second), manifest.entries());
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("metadata.dat")));

        var cleared = first.withPhysicsAttachment(null);
        manifest.put(cleared);
        manifest.persist();
        assertEquals(List.of(cleared, second), new NativeStructureManifest(directory).entries());
    }

    @Test
    void malformedSavedRegionLayoutDisablesTheIndexAndRetainsItsBytes() throws IOException {
        for (int scenario = 0; scenario < 2; scenario++) {
            var location = directory.resolve("invalid_layout_" + scenario);
            var manifest = new NativeStructureManifest(location);
            manifest.putAll(List.of(entry(1, 0, true, false).withPhysicsAttachment(attachment(true)),
                    entry(2, 1, true, false).withPhysicsAttachment(attachment(true))));
            manifest.persist();
            var path = location.resolve("metadata.dat");
            var root = read(path);
            var second = root.getList("entries").orElseThrow().getCompound(1).orElseThrow();
            if (scenario == 0) {
                // Two distinct worlds may not occupy the same host bounds.
                physics(second).putString("region_id", new UUID(2, 2).toString());
            } else {
                // One UUID may not designate different dimensions.
                second.putString("dimension", "minecraft:the_nether");
            }
            NbtIo.writeCompressed(root, path);
            var bytes = Files.readAllBytes(path);
            assertUnhealthyAndUnwritable(new NativeStructureManifest(location));
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }
    }

    @Test
    void removingAnEntryPersistsBeforeTheSlotMarkerIsReleased() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = entry(1, 0, true, false);
        var second = entry(2, 1, false, false);
        manifest.claimSlot(0, first.reference().id());
        manifest.claimSlot(1, second.reference().id());
        manifest.put(first);
        manifest.put(second);
        manifest.persist();

        manifest.removeEntry(first.reference().id());
        assertEquals(List.of(second), manifest.entries());
        assertEquals(List.of(second), new NativeStructureManifest(directory).entries());
        assertTrue(manifest.slotClaimed(0), "The marker must survive until an explicit release");
        assertTrue(manifest.ownsSlot(0, first.reference().id()));
        assertThrows(IOException.class, () -> manifest.releaseSlot(0, new UUID(9, 9)));
        assertThrows(IOException.class, () -> manifest.releaseSlot(1, second.reference().id()),
                "An indexed structure must block releasing its slot");
        manifest.releaseSlot(0, first.reference().id());
        assertFalse(manifest.slotClaimed(0));
        assertFalse(new NativeStructureManifest(directory).slotClaimed(0));
        manifest.removeEntry(new UUID(9, 9));
        manifest.removeEntry(second.reference().id());
        assertEquals(List.of(), new NativeStructureManifest(directory).entries());
    }

    @Test
    void removalAndReleaseAreBlockedByRecoveryEvidence() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = entry(1, 0, true, false);
        manifest.claimSlot(0, first.reference().id());
        manifest.put(first);
        manifest.persist();
        manifest.persistRecovery(0, new CompoundTag());
        assertThrows(IOException.class, () -> manifest.removeEntry(first.reference().id()));
        assertEquals(List.of(first), manifest.entries());
        assertThrows(IOException.class, () -> manifest.releaseSlot(0, first.reference().id()));
        assertTrue(manifest.slotClaimed(0));
        Files.delete(directory.resolve("recovery_0.dat"));
        manifest.removeEntry(first.reference().id());
        manifest.releaseSlot(0, first.reference().id());
        assertFalse(manifest.slotClaimed(0));
    }

    private static void makeLegacyV2(CompoundTag root) {
        root.putInt("version", 2);
        for (var value : root.getList("entries").orElseThrow()) {
            var entry = (CompoundTag) value;
            if (entry.get("physics") != null) physics(entry).remove("region_id");
        }
    }

    private static NativeStructureManifest.Entry hostEntry(int slot) {
        var source = entry(slot + 1, slot, true, false);
        return new NativeStructureManifest.Entry(new StructureRef(source.reference().id(),
                Identifier.parse("test:host_" + slot), source.reference().backend(), source.reference().generation()),
                slot, source.pose(), true, false);
    }

    private static NativeStructurePhysicsAttachment attachment(boolean allowed) {
        return attachment(new UUID(2, 1), allowed);
    }

    private static NativeStructurePhysicsAttachment attachment(UUID regionId, boolean allowed) {
        return new NativeStructurePhysicsAttachment(regionId, new AABB(-12, 64, -12, 12, 88, 12),
                new NativeStructurePhysicsAttachment.Motion(new Vec3(1.25, -0.5, 0), new Vec3(0, 0.2, 0.1),
                        new Vec3(-0.5, 2.25, 0.75), true, "0123456789abcdef".repeat(4)), allowed);
    }

    private static CompoundTag physics(CompoundTag entry) { return entry.getCompound("physics").orElseThrow(); }
    private static CompoundTag savedBounds(CompoundTag entry) { return physics(entry).getCompound("bounds").orElseThrow(); }
    private static CompoundTag savedMotion(CompoundTag entry) { return physics(entry).getCompound("motion").orElseThrow(); }

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
