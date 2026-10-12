package org.academy.internal.common.structure.physics;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NativePhysicsBridgeLoaderTest {
    @Test
    void embeddedBridgeHasItsOwnUnnamedModuleAndSharesOnlyTheParentInterface() throws Exception {
        var first = NativePhysicsBridgeLoader.defineBridge();
        var second = NativePhysicsBridgeLoader.defineBridge();
        assertFalse(first.getModule().isNamed());
        assertNotSame(PhysicsNativeAccess.class.getModule(), first.getModule());
        assertNotSame(first.getClassLoader(), second.getClassLoader());
        assertSame(PhysicsNativeAccess.class.getClassLoader(), first.getClassLoader().getParent());
        assertArrayEquals(new Class<?>[]{PhysicsNativeAccess.class}, first.getInterfaces());
        assertEquals(PhysicsNativeAccess.class, first.getMethod("open", Path.class).getReturnType());
        assertThrows(ClassNotFoundException.class, () -> Class.forName(first.getName(), false,
                PhysicsNativeAccess.class.getClassLoader()));
    }

    @Test
    void missingNativePermissionFailsBeforeTryingAnyLibrary() throws Exception {
        var resources = NativePhysicsBridgeLoader.class.getResource("/academy/native_physics/FfmPhysicsAccess.bin");
        assertNotNull(resources);
        assertEquals("file", resources.getProtocol(), "Ordinary tests use processResources output directly");
        var resourceRoot = Path.of(resources.toURI()).getParent().getParent().getParent();
        var classPath = Stream.of(
                Path.of(NativePhysicsBridgePermissionProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
                Path.of(PhysicsNativeAccess.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
                resourceRoot).map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classPath, NativePhysicsBridgePermissionProbe.class.getName());
        for (var variable : new String[]{"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}) {
            builder.environment().remove(variable);
        }
        var process = builder.redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Permission probe did not exit");
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("NATIVE_PERMISSION_REJECTED"), output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
