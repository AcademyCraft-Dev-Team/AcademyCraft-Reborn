package org.academy.internal.common.structure.upgrade.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadedChunkViewTest {
    @Test
    void shapeCallbacksKeepUnloadedAndBudgetSignalsDistinctFromBrokenProviders() {
        var unloaded = new LoadedChunkView.Unavailable();
        var exhausted = new LoadedChunkView.Unavailable(true);
        assertSame(unloaded, assertThrows(LoadedChunkView.Unavailable.class,
                () -> LoadedChunkView.inspectShape(() -> { throw unloaded; })));
        assertSame(exhausted, assertThrows(LoadedChunkView.Unavailable.class,
                () -> LoadedChunkView.inspectShape(() -> { throw exhausted; })));
        var providerFailure = new IllegalStateException("Third-party shape provider failed");
        assertSame(providerFailure, assertThrows(LoadedChunkView.ShapeFailure.class,
                () -> LoadedChunkView.inspectShape(() -> { throw providerFailure; })).getCause());
        var linkageFailure = new NoClassDefFoundError("OptionalShapeProvider");
        assertSame(linkageFailure, assertThrows(LoadedChunkView.ShapeFailure.class,
                () -> LoadedChunkView.inspectShape(() -> { throw linkageFailure; })).getCause());
    }
}
