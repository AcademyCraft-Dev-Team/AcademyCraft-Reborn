package org.academy.internal.common.structure.physics.validation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class NativePhysicsSmokeSupport {
    private NativePhysicsSmokeSupport() {}

    static Path verifyDirectory(String role, Path actualDirectory) {
        try {
            var expected = Path.of(required("academy.nativePhysicsSmokeDirectory")).toRealPath();
            var runRoot = Path.of(required("academy.nativePhysicsSmokeRunRoot")).toRealPath();
            if (!actualDirectory.toRealPath().equals(expected) || !expected.startsWith(runRoot)
                    || !expected.getFileName().toString().startsWith("native-physics-smoke-" + role)) {
                throw new IllegalStateException("Physics probe requires its explicitly configured isolated run directory");
            }
            if (Files.exists(expected.resolve("native-physics-" + role + "-result.txt"))) {
                throw new IllegalStateException("Physics probe requires a fresh result directory");
            }
            return expected;
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot verify isolated physics probe directory", failure);
        }
    }

    static void runAndRecord(String role, Path directory) {
        var result = directory.resolve("native-physics-" + role + "-result.txt");
        try {
            final String details;
            try {
                details = NativePhysicsSmokeChecks.run();
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot load the configured native physics artifact", failure);
            }
            write(result, "PASSED\nrole=" + role + "\nprocess=" + ProcessHandle.current().pid() + "\n" + details);
            System.out.println("NATIVE_PHYSICS_" + role.toUpperCase(java.util.Locale.ROOT)
                    + "_PASSED " + details.replace('\n', ' '));
        } catch (RuntimeException | Error failure) {
            try {
                write(result, "FAILED\nrole=" + role + "\nfailure=" + failure + "\n");
            } catch (RuntimeException writingFailure) {
                failure.addSuppressed(writingFailure);
            }
            System.err.println("NATIVE_PHYSICS_" + role.toUpperCase(java.util.Locale.ROOT) + "_FAILED " + failure);
            throw failure;
        }
    }

    private static String required(String name) {
        var value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing opt-in physics property: " + name);
        return value;
    }

    private static void write(Path path, String text) {
        try {
            Files.writeString(path, text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot record native physics probe result", failure);
        }
    }
}
