package org.academy.internal.common.ability.teleport;

import net.minecraft.server.level.ChunkResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTicketLeaseManagerTest {
    @Test
    void waitsForEveryChunkBeforeContinuing() {
        var first = new CompletableFuture<Object>();
        var second = new CompletableFuture<Object>();
        var committed = new AtomicBoolean();
        var loading = ChunkTicketLeaseManager.awaitLoads(first, second)
                .thenRun(() -> committed.set(true));

        first.complete(ChunkResult.of(List.of("first chunk")));
        assertFalse(loading.isDone());
        assertFalse(committed.get());
        second.complete(ChunkResult.of(List.of("second chunk")));
        loading.join();
        assertTrue(committed.get());
    }

    @Test
    void exceptionalLoadNeverRunsTheCommitAndPreservesItsCause() {
        var cause = new NullPointerException("feature placement failed");
        var committed = new AtomicBoolean();
        var loading = ChunkTicketLeaseManager.awaitLoads(
                        CompletableFuture.completedFuture(ChunkResult.of(List.of("loaded"))),
                        CompletableFuture.failedFuture(cause))
                .thenRun(() -> committed.set(true));

        var failure = assertThrows(CompletionException.class, loading::join);
        assertSame(cause, failure.getCause());
        assertFalse(committed.get());
    }

    @Test
    void normallyCompletedUnloadedResultIsStillAFailure() {
        var committed = new AtomicBoolean();
        var loading = ChunkTicketLeaseManager.awaitLoads(
                        CompletableFuture.completedFuture(ChunkResult.error("Unloaded chunk")))
                .thenRun(() -> committed.set(true));

        var failure = assertThrows(CompletionException.class, loading::join);
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("Unloaded chunk"));
        assertFalse(committed.get());
    }

    @Test
    void cancelledLoadNeverRunsTheCommit() {
        var chunk = new CompletableFuture<Object>();
        var committed = new AtomicBoolean();
        var loading = ChunkTicketLeaseManager.awaitLoads(chunk).thenRun(() -> committed.set(true));
        chunk.cancel(false);

        assertThrows(CompletionException.class, loading::join);
        assertFalse(committed.get());
    }
}
