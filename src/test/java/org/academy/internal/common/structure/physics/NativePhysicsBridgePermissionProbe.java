package org.academy.internal.common.structure.physics;

import java.nio.file.Path;

/** Child JVM entry point: no JUnit or native artifact is required for the permission rejection. */
public final class NativePhysicsBridgePermissionProbe {
    private NativePhysicsBridgePermissionProbe() {}

    public static void main(String[] args) throws Exception {
        try (var ignored = NativePhysicsBridgeLoader.open(Path.of("no-native-library-is-provided"))) {
            throw new AssertionError("Bridge unexpectedly opened without explicit native permission");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("--enable-native-access=ALL-UNNAMED")) throw expected;
            System.out.println("NATIVE_PERMISSION_REJECTED");
        }
    }
}
