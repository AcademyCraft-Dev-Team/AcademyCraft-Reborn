package org.academy.internal.common.structure.physics;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

/** Defines only our embedded bridge, so FFM permission belongs to its explicit unnamed module. */
final class NativePhysicsBridgeLoader extends ClassLoader {
    private static final String BRIDGE_NAME = "org.academy.internal.common.structure.physics.FfmPhysicsAccess";
    private static final String RESOURCE = "/academy/native_physics/FfmPhysicsAccess.bin";
    private static final int MAX_CLASS_BYTES = 1024 * 1024;

    private NativePhysicsBridgeLoader() {
        super(PhysicsNativeAccess.class.getClassLoader());
    }

    static PhysicsNativeAccess open(Path library) throws IOException {
        var type = defineBridge();
        try {
            return PhysicsNativeAccess.class.cast(type.getMethod("open", Path.class).invoke(null, library));
        } catch (InvocationTargetException failure) {
            var cause = failure.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Cannot open native physics bridge", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Invalid embedded native physics bridge entry point", failure);
        }
    }

    // Package-private for the ordinary JVM test: defining the class must not load native code.
    static Class<? extends PhysicsNativeAccess> defineBridge() throws IOException {
        byte[] content;
        try (var stream = NativePhysicsBridgeLoader.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Missing embedded native physics bridge: " + RESOURCE);
            content = stream.readNBytes(MAX_CLASS_BYTES + 1);
        }
        if (content.length == 0 || content.length > MAX_CLASS_BYTES) {
            throw new IOException("Invalid embedded native physics bridge size");
        }
        return new NativePhysicsBridgeLoader().defineClass(BRIDGE_NAME, content, 0, content.length)
                .asSubclass(PhysicsNativeAccess.class);
    }
}
