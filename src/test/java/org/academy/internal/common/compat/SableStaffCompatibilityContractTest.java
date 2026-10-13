package org.academy.internal.common.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Runs without Sable: inspects class bytes without loading either optional mixin target. */
class SableStaffCompatibilityContractTest {
    @TempDir Path directory;

    @Test
    void fingerprintDoesNotAcceptVersionAloneOrMissingArtifacts() throws IOException {
        var impostor = directory.resolve("same-version.jar");
        Files.writeString(impostor, "not the audited nested Simulated artifact");
        assertFalse(SableStaffMixinGate.matches(impostor, SableStaffMixinGate.VERSION));
        assertFalse(SableStaffMixinGate.matches(impostor, "1.3.2"));
        assertFalse(SableStaffMixinGate.matches(directory.resolve("missing.jar"), SableStaffMixinGate.VERSION));
        assertFalse(SableStaffMixinGate.matches(directory, SableStaffMixinGate.VERSION));
    }

    @Test
    void knownStaffCannotEnableMixinsAgainstUnknownSableOrCompanion() {
        for (var fingerprint : new String[]{SableStaffMixinGate.SHA256,
                SableStaffMixinGate.SABLE_SHA256, SableStaffMixinGate.COMPANION_SHA256}) {
            assertTrue(fingerprint.matches("[0-9a-f]{64}"), "Artifact SHA-256 must contain exactly 64 hexadecimal digits");
        }
        var staff = new SableStaffMixinGate.Fingerprint(SableStaffMixinGate.VERSION, SableStaffMixinGate.SHA256);
        var sable = new SableStaffMixinGate.Fingerprint(SableStaffMixinGate.SABLE_VERSION, SableStaffMixinGate.SABLE_SHA256);
        var companion = new SableStaffMixinGate.Fingerprint(SableStaffMixinGate.COMPANION_VERSION,
                SableStaffMixinGate.COMPANION_SHA256);
        assertTrue(SableStaffMixinGate.compatible(staff, sable, companion));
        assertFalse(SableStaffMixinGate.compatible(staff, null, companion));
        assertFalse(SableStaffMixinGate.compatible(staff, sable, null));
        assertFalse(SableStaffMixinGate.compatible(staff,
                new SableStaffMixinGate.Fingerprint("unknown", sable.sha256()), companion));
        assertFalse(SableStaffMixinGate.compatible(staff,
                new SableStaffMixinGate.Fingerprint(sable.version(), "0".repeat(64)), companion));
        assertFalse(SableStaffMixinGate.compatible(staff, sable,
                new SableStaffMixinGate.Fingerprint("unknown", companion.sha256())));
        assertFalse(SableStaffMixinGate.compatible(staff, sable,
                new SableStaffMixinGate.Fingerprint(companion.version(), "0".repeat(64))));
    }

    @Test
    void earlyLoaderAndNeutralViewsContainNoOptionalModTypeLinkage() throws IOException {
        for (var name : new String[]{"org.academy.internal.common.compat.SableStaffMixinGate",
                "org.academy.internal.common.compat.PhysicsStaffHandlerAccess",
                "org.academy.internal.common.compat.PhysicsStaffDragAccess",
                "org.academy.mixin.plugin.MixinPlugin"}) {
            var bytes = bytes(name);
            var pool = new String(bytes, StandardCharsets.ISO_8859_1);
            assertFalse(pool.contains("dev/ryanhcode/"), name);
            assertFalse(pool.contains("dev/simulated_team/"), name);
            assertFalse(pool.contains("org/academy/internal/common/structure/StructureBackendRouter"), name);
        }
    }

    @Test
    void staffAdaptersNeverWriteShadowedStateOrWeakenRequiredInjectionMatching() throws IOException {
        var expected = Set.of(
                "get(Lnet/minecraft/server/level/ServerLevel;)Ldev/simulated_team/simulated/content/physics_staff/PhysicsStaffServerHandler;",
                "toggleLock(Ljava/util/UUID;)V",
                "applyLockIfNeeded(Ldev/ryanhcode/sable/sublevel/SubLevel;)V",
                "drag(Ljava/util/UUID;Ljava/util/UUID;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V");
        var actual = new HashSet<String>();
        for (var suffix : new String[]{"Handler", "DragSession"}) {
            var node = new ClassNode();
            new ClassReader(bytes("org.academy.mixin.common.compat.MixinSablePhysicsStaff" + suffix)).accept(node, 0);
            for (var method : node.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                        assertFalse(Set.of("locks", "draggingSessions", "level", "subLevel").contains(field.name),
                                "Staff arbitration must not mutate the target's fields: " + field.name);
                    }
                }
                if (method.visibleAnnotations == null) continue;
                for (var annotation : method.visibleAnnotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                    boolean required = false;
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        if (annotation.values.get(i).equals("method")) {
                            for (var selector : (java.util.List<?>) annotation.values.get(i + 1)) actual.add((String) selector);
                        }
                        if (annotation.values.get(i).equals("require")) {
                            required = Integer.valueOf(1).equals(annotation.values.get(i + 1));
                        }
                    }
                    assertTrue(required, "Pinned compatibility must fail if its injection stops matching");
                }
            }
        }
        assertEquals(expected, actual);
    }

    private static byte[] bytes(String name) throws IOException {
        try (var input = SableStaffCompatibilityContractTest.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(input, name);
            return input.readAllBytes();
        }
    }
}
