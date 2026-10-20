package org.academy.internal.common.structure.space;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsAttachment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureRecoveryCommitTest {
    @TempDir Path directory;
    private static final UUID REGION = new UUID(0, 100);
    private static final AABB BOUNDS = new AABB(0, 64, 0, 24, 88, 24);

    @Test
    void durableWholeCohortReplacementPreservesPosesIdentitiesSlotsAndOtherRegions() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = failed(1, 0, REGION, BOUNDS);
        var second = failed(2, 1, REGION, BOUNDS);
        var other = failed(3, 2, new UUID(0, 200), BOUNDS.move(25, 0, 0));
        manifest.putAll(List.of(first, second, other));
        for (var entry : manifest.entries()) manifest.claimSlot(entry.slot(), entry.reference().id());
        manifest.persist();

        var replacement = List.of(recovered(first), recovered(second));
        manifest.replaceAllAtomically(List.of(first, second), replacement);
        var expected = List.of(replacement.get(0), replacement.get(1), other);
        assertEquals(expected, manifest.entries());
        var reopened = new NativeStructureManifest(directory);
        assertEquals(expected.size(), reopened.entries().size());
        for (var entry : expected) {
            assertReloadedEntry(entry, reopened.get(entry.reference().id()).orElseThrow());
        }
        for (var original : List.of(first, second, other)) {
            var current = reopened.get(original.reference().id()).orElseThrow();
            assertEquals(original.pose(), manifest.get(original.reference().id()).orElseThrow().pose());
            assertEquals(original.reference(), current.reference());
            assertEquals(original.slot(), current.slot());
            assertTrue(reopened.ownsSlot(current.slot(), current.reference().id()));
        }
    }

    @Test
    void atomicMoveFailurePreservesOriginalDiskAndMemoryWithoutDestructiveFallback() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = failed(1, 0, REGION, BOUNDS);
        var second = failed(2, 1, REGION, BOUNDS);
        var old = List.of(first, second);
        manifest.putAll(old);
        manifest.persist();
        var original = Files.readAllBytes(directory.resolve("metadata.dat"));
        var originalReloaded = new NativeStructureManifest(directory).entries();
        var attempted = new AtomicInteger();

        assertThrows(IOException.class, () -> manifest.replaceAllAtomically(old,
                List.of(recovered(first), recovered(second)), (source, target, atomic) -> {
                    assertTrue(atomic, "Recovery must not call the delete-and-move fallback");
                    attempted.incrementAndGet();
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected");
                }));

        assertEquals(1, attempted.get());
        assertEquals(old, manifest.entries());
        assertEquals(originalReloaded, new NativeStructureManifest(directory).entries());
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("metadata.dat")));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void rejectsPartialDuplicateOrChangedCheckpointBeforeAnyWrite() throws IOException {
        var manifest = new NativeStructureManifest(directory);
        var first = failed(1, 0, REGION, BOUNDS);
        var second = failed(2, 1, REGION, BOUNDS);
        var expected = List.of(first, second);
        manifest.putAll(expected);
        manifest.persist();
        var original = Files.readAllBytes(directory.resolve("metadata.dat"));
        assertThrows(IllegalArgumentException.class,
                () -> manifest.replaceAllAtomically(expected, List.of(recovered(first))));
        assertThrows(IllegalArgumentException.class,
                () -> manifest.replaceAllAtomically(expected, List.of(recovered(first), recovered(first))));
        manifest.put(first.withAvailability(false, false));
        assertThrows(IllegalStateException.class,
                () -> manifest.replaceAllAtomically(expected, List.of(recovered(first), recovered(second))));
        assertFalse(manifest.get(first.reference().id()).orElseThrow().active());
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("metadata.dat")));
    }

    private static void assertReloadedEntry(NativeStructureManifest.Entry expected,
                                            NativeStructureManifest.Entry actual) {
        assertEquals(expected.withPose(actual.pose()), actual);
        assertEquals(expected.pose().worldOrigin(), actual.pose().worldOrigin());
        // Loading reconstructs StructurePose and normalizes its quaternion again.
        assertEquals(expected.pose().rotationX(), actual.pose().rotationX(), 1.0e-15);
        assertEquals(expected.pose().rotationY(), actual.pose().rotationY(), 1.0e-15);
        assertEquals(expected.pose().rotationZ(), actual.pose().rotationZ(), 1.0e-15);
        assertEquals(expected.pose().rotationW(), actual.pose().rotationW(), 1.0e-15);
    }

    private static NativeStructureManifest.Entry failed(long id, int slot, UUID region, AABB bounds) {
        var reference = new StructureRef(new UUID(0, id), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:native_level"), 0);
        var motion = new NativeStructurePhysicsAttachment.Motion(new Vec3(8, 16, 17), new Vec3(12, -1, -22),
                new Vec3(0.5, 0.5, 0.5), false, "a".repeat(64));
        return new NativeStructureManifest.Entry(reference, slot,
                new StructurePose(new Vec3(5 + slot, 70, 5), 0.2, 0, -0.3, 0.9),
                true, false, new NativeStructurePhysicsAttachment(region, bounds, motion, false));
    }

    private static NativeStructureManifest.Entry recovered(NativeStructureManifest.Entry entry) {
        var original = entry.physicsAttachment();
        var motion = new NativeStructurePhysicsAttachment.Motion(Vec3.ZERO, Vec3.ZERO,
                original.motion().localCenterOfMass(), false, "b".repeat(64));
        return entry.withPhysicsAttachment(new NativeStructurePhysicsAttachment(
                original.regionId(), original.bounds(), motion, true));
    }
}
