package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureContextsTest {
    @Test
    void nestingAcrossStructuresRestoresEachCallerAndClearsTheOutermostScope() {
        var first = new TestHandle();
        var second = new TestHandle();
        assertTrue(StructureContexts.current().isEmpty());

        var result = StructureContexts.call(first, outer -> {
            assertSame(outer, StructureContexts.current().orElseThrow());
            assertSame(first.world(), outer.world());
            StructureContexts.run(second, inner -> {
                assertEquals(second.reference(), inner.reference());
                assertSame(inner, StructureContexts.current().orElseThrow());
                StructureContexts.run(first, nested -> {
                    assertEquals(first.reference(), nested.reference());
                    assertSame(nested, StructureContexts.current().orElseThrow());
                });
                assertSame(inner, StructureContexts.current().orElseThrow());
            });
            assertSame(outer, StructureContexts.current().orElseThrow());
            return "completed";
        });

        assertEquals("completed", result);
        assertTrue(StructureContexts.current().isEmpty());
    }

    @Test
    void callbackFailureRestoresOuterScopeAndDoesNotLeakAfterUnwinding() {
        var first = new TestHandle();
        var second = new TestHandle();
        var failure = new IllegalArgumentException("callback failed");

        var propagated = assertThrows(IllegalArgumentException.class, () ->
                StructureContexts.run(first, outer -> {
                    var nested = assertThrows(IllegalArgumentException.class, () ->
                            StructureContexts.run(second, ignored -> { throw failure; }));
                    assertSame(failure, nested);
                    assertSame(outer, StructureContexts.current().orElseThrow());
                    throw failure;
                }));

        assertSame(failure, propagated);
        assertTrue(StructureContexts.current().isEmpty());
        StructureContexts.run(second, next -> assertSame(next, StructureContexts.current().orElseThrow()));
        assertTrue(StructureContexts.current().isEmpty());
    }

    @Test
    void depthLimitRefusesEntryWithoutDiscardingTheExistingScope() {
        var handle = new TestHandle();

        enterToLimit(handle, 0);

        assertTrue(StructureContexts.current().isEmpty());
        StructureContexts.run(handle, context -> assertSame(context, StructureContexts.current().orElseThrow()));
        assertTrue(StructureContexts.current().isEmpty());
    }

    private static void enterToLimit(TestHandle handle, int depth) {
        if (depth == StructureContexts.MAX_DEPTH) {
            var previous = StructureContexts.current().orElseThrow();
            assertThrows(IllegalStateException.class,
                    () -> StructureContexts.run(handle, ignored -> { throw new AssertionError("entered over limit"); }));
            assertSame(previous, StructureContexts.current().orElseThrow());
            return;
        }
        StructureContexts.run(handle, context -> {
            enterToLimit(handle, depth + 1);
            assertSame(context, StructureContexts.current().orElseThrow());
        });
    }

    @Test
    void workerCannotInheritTheScopeOrEnterWithAnotherThreadsHandle() throws Exception {
        var handle = new TestHandle();
        try (var worker = Executors.newSingleThreadExecutor()) {
            StructureContexts.run(handle, outer -> {
                var pending = worker.submit(() -> {
                    assertTrue(StructureContexts.current().isEmpty());
                    assertThrows(IllegalStateException.class,
                            () -> StructureContexts.run(handle, ignored -> { throw new AssertionError("wrong thread"); }));
                    assertTrue(StructureContexts.current().isEmpty());
                    var workerHandle = new TestHandle();
                    StructureContexts.run(workerHandle,
                            inner -> assertSame(inner, StructureContexts.current().orElseThrow()));
                    assertTrue(StructureContexts.current().isEmpty());
                });
                try {
                    pending.get(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new AssertionError("Worker context isolation failed", failure);
                }
                assertSame(outer, StructureContexts.current().orElseThrow());
            });
        }
        assertTrue(StructureContexts.current().isEmpty());
    }

    @Test
    void inactiveHandleCannotReplaceAnExistingScope() {
        var first = new TestHandle();
        var inactive = new TestHandle();
        inactive.lifecycle = StructureLifecycle.UNLOADED;

        StructureContexts.run(first, outer -> {
            assertThrows(IllegalStateException.class,
                    () -> StructureContexts.run(inactive, ignored -> { throw new AssertionError("inactive scope"); }));
            assertSame(outer, StructureContexts.current().orElseThrow());
        });

        assertTrue(StructureContexts.current().isEmpty());
    }

    @Test
    void eachScopeCapturesItsPoseWhileKeepingAccessBoundToTheHandle() {
        var handle = new TestHandle();
        var original = handle.pose();
        var moved = StructurePose.identity(new Vec3(12.0, 5.0, -3.0));

        StructureContexts.run(handle, outer -> {
            handle.setPose(moved);
            assertEquals(original, outer.pose());
            StructureContexts.run(handle, inner -> {
                assertEquals(moved, inner.pose());
                assertSame(outer.world(), inner.world());
                assertEquals(outer.runtimeId(), inner.runtimeId());
            });
            assertSame(outer, StructureContexts.current().orElseThrow());
            assertEquals(original, StructureContexts.current().orElseThrow().pose());
        });

        assertTrue(StructureContexts.current().isEmpty());
    }

    private static final class TestHandle implements StructureHandle {
        private final Thread thread = Thread.currentThread();
        private final StructureRef reference = new StructureRef(UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                Identifier.fromNamespaceAndPath("academy", "test"), 0);
        private final UUID runtimeId = UUID.randomUUID();
        private final StructureWorldAccess world = new StructureWorldAccess() {
            @Override public StructureRef reference() { checkThread(); return reference; }
            @Override public StructureBlockData read(BlockPos position) { throw new UnsupportedOperationException(); }
            @Override public StructureWriteResult compareAndSet(BlockPos position,
                    StructureBlockData expected, StructureBlockData replacement) {
                throw new UnsupportedOperationException();
            }
        };
        private StructureLifecycle lifecycle = StructureLifecycle.ACTIVE;
        private StructurePose pose = StructurePose.identity(Vec3.ZERO);
        private long revision;

        private void checkThread() {
            if (Thread.currentThread() != thread) throw new IllegalStateException("Wrong thread");
        }

        @Override public StructureRef reference() { checkThread(); return reference; }
        @Override public StructureLifecycle lifecycle() { checkThread(); return lifecycle; }
        @Override public UUID runtimeId() { checkThread(); return runtimeId; }
        @Override public long geometryRevision() { checkThread(); return revision; }
        @Override public StructurePose pose() { checkThread(); return pose; }
        @Override public void setPose(StructurePose pose) { checkThread(); this.pose = pose; revision++; }
        @Override public StructureWorldAccess world() { checkThread(); return world; }
    }
}
