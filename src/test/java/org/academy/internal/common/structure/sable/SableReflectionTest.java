package org.academy.internal.common.structure.sable;

import org.academy.internal.common.structure.sable.reflect.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SableReflectionTest {
    @SableType("org.academy.internal.common.structure.sable.SableReflectionTest$Backend")
    public interface View extends SableView {
        static View create(int value) { return SableReflection.construct(View.class, new Class<?>[]{int.class}, value); }
        static View lookup(Backend value) { return SableReflection.callStatic(View.class, "lookup", new Class<?>[]{Backend.class}, value); }
        int value();
        View self();
        @SableField Map<UUID, View> owners();
        @SableField List<View> queued();
        @SableField Set<View> reserved();
        Iterable<View> loaded();
        void subscribe(Listener listener);
        void persist() throws IOException;
        void missing();
    }

    @SableType("org.academy.internal.common.structure.sable.SableReflectionTest$BackendListener")
    public interface Listener extends SableView { void changed(View source); }

    public interface BackendListener { void changed(Backend source); }

    public static final class Backend {
        private final Map<UUID, Backend> owners = new HashMap<>();
        private final List<Backend> queued = new ArrayList<>();
        private final Set<Backend> reserved = new HashSet<>();
        private final int value;
        public Backend(int value) { this.value = value; }
        public static Backend lookup(Backend value) { return value; }
        public int value() { return value; }
        public Backend self() { return this; }
        public Iterable<Backend> loaded() { return queued; }
        public void subscribe(BackendListener listener) { listener.changed(this); }
        private void persist() throws IOException { throw new IOException("durable write failed"); }
    }

    @Test void identitySurvivesFactoryReturnAndRepeatedLookups() {
        var raw = new Backend(37);
        var view = SableReflection.wrap(View.class, raw);
        assertSame(view, SableReflection.wrap(View.class, raw));
        assertSame(view, view.self());
        assertSame(view, View.lookup(raw));
        assertSame(raw, SableReflection.unwrap(view));
        assertEquals(37, view.value());
        assertEquals(19, View.create(19).value());
    }

    @Test void ownershipCollectionsStayLiveAndUnwrapInsertedValues() {
        var raw = new Backend(1);
        var id = UUID.randomUUID();
        raw.owners.put(id, raw);
        var view = SableReflection.wrap(View.class, raw);
        assertSame(view, view.owners().get(id));
        assertSame(view, view.owners().values().iterator().next());
        assertSame(view, view.owners().remove(id));
        assertTrue(raw.owners.isEmpty());
        view.queued().add(view);
        assertSame(raw, raw.queued.getFirst());
        assertSame(view, view.loaded().iterator().next());
        assertTrue(view.queued().contains(view));
        assertTrue(view.queued().remove(view));
        assertTrue(raw.queued.isEmpty());
        view.reserved().add(view);
        assertSame(raw, raw.reserved.iterator().next());
        assertTrue(view.reserved().remove(view));
        assertTrue(raw.reserved.isEmpty());
    }

    @Test void callbacksUseTheSameObservedActivation() {
        var view = View.create(2);
        var seen = new ArrayList<View>();
        view.subscribe(seen::add);
        assertEquals(List.of(view), seen);
        assertSame(view, seen.getFirst());
    }

    @Test void storageFailuresRetainTheirCheckedCause() {
        var view = View.create(1);
        assertEquals("durable write failed", assertThrows(IOException.class, view::persist).getMessage());
        assertTrue(assertThrows(IllegalStateException.class, view::missing).getMessage().contains("Unsupported Sable API member"));
    }

    @Test void optionalViewsLoadWithoutInstallingTheirTargets() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.ryanhcode.sable.Sable"));
        var loader = getClass().getClassLoader();
        Class.forName("org.academy.internal.common.structure.sable.SableStructureAccess", true, loader);
        Class.forName("org.academy.internal.common.structure.sable.SableStructureTransactions", true, loader);
        Class.forName("org.academy.internal.common.structure.sable.SableStructureTraction", true, loader);
        Class.forName("org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d", true, loader);
    }

    @Test void compiledBridgeAndMixinsContainOnlyStringReferencesToOptionalTypes() throws IOException {
        var root = java.nio.file.Path.of("build/classes/java/main/org/academy");
        assertTrue(java.nio.file.Files.isDirectory(root));
        int inspected = 0;
        try (var files = java.nio.file.Files.walk(root)) {
            for (var path : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                var relative = root.relativize(path).toString().replace('\\', '/');
                if (!relative.contains("/sable/") && !relative.contains("/compat/MixinSable")) continue;
                inspected++;
                var remapper = new org.objectweb.asm.commons.Remapper(org.objectweb.asm.Opcodes.ASM9) {
                    @Override public String map(String name) {
                        assertFalse(name.startsWith("dev/ryanhcode/") || name.startsWith("foundry/"),
                                "Hard optional linkage in " + relative + ": " + name);
                        return name;
                    }
                };
                new org.objectweb.asm.ClassReader(java.nio.file.Files.readAllBytes(path)).accept(
                        new org.objectweb.asm.commons.ClassRemapper(new org.objectweb.asm.ClassWriter(0), remapper), 0);
            }
        }
        assertTrue(inspected > 50);
    }
}
