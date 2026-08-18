package org.academy.internal.client.ability.mentalout;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.LongConsumer;

public final class MentaloutRosterClientState {
    public static final byte SUPPORT_FULL = 0;
    public static final byte SUPPORT_BEST_EFFORT = 1;
    public static final byte SUPPORT_UNSUPPORTED = 2;

    public static final byte FLAG_STUPOR = 1;
    public static final byte FLAG_IMPRESSION = 1 << 1;
    public static final byte FLAG_MISIDENTIFICATION = 1 << 2;
    public static final byte FLAG_OVERRIDDEN = 1 << 3;
    public static final byte FLAG_PROTECTED = 1 << 4;
    public static final byte FLAG_RESISTANT = 1 << 5;

    public static final byte DELTA_UPSERT = 0;
    public static final byte DELTA_REMOVE = 1;
    static final int FULL_TRANSFER_TIMEOUT_TICKS = 40;
    static final int RESYNC_RETRY_TICKS = 40;
    private static final int MAX_FULL_CHUNK_ENTRIES = 64;
    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static @Nullable FullTransfer pendingFull;
    private static @Nullable LongConsumer resyncRequester;
    private static boolean resyncRequested;
    private static long requiredRevision;
    private static long clientTick;
    private static long lastResyncRequestTick;

    private MentaloutRosterClientState() {
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static boolean hasControlledTargets() {
        return !snapshot.entries().isEmpty();
    }

    public static boolean isControlledTarget(UUID targetUuid) {
        if (targetUuid == null) return false;
        for (var entry : snapshot.entries()) {
            if (targetUuid.equals(entry.targetUuid())) return true;
        }
        return false;
    }

    public static synchronized void setResyncRequester(@Nullable LongConsumer requester) {
        resyncRequester = requester;
        if (requester != null && resyncRequested) sendResyncRequest();
    }

    public static synchronized void tick() {
        clientTick++;
        var transfer = pendingFull;
        if (transfer != null && !resyncRequested
                && elapsedSince(transfer.lastProgressTick) >= FULL_TRANSFER_TIMEOUT_TICKS) {
            requestResync(transfer.revision);
            return;
        }
        if (resyncRequested && elapsedSince(lastResyncRequestTick) >= RESYNC_RETRY_TICKS) {
            sendResyncRequest();
        }
    }

    public static synchronized void applyFullStart(
            long revision,
            int totalChunks,
            int totalEntries,
            int stuporCp,
            int impressionCp
    ) {
        if (revision < snapshot.revision()) return;
        if (pendingFull != null && revision < pendingFull.revision) return;
        if (revision < requiredRevision) {
            requestResync(requiredRevision);
            return;
        }
        if (!isValidFullShape(totalChunks, totalEntries)) {
            requestResync(revision);
            return;
        }

        pendingFull = new FullTransfer(
                revision,
                totalChunks,
                totalEntries,
                Math.max(0, stuporCp),
                Math.max(0, impressionCp),
                clientTick
        );
        resyncRequested = false;
        if (totalChunks == 0) finishFullTransfer();
    }

    public static synchronized boolean applyFullChunk(long revision, int chunkIndex, List<Entry> entries) {
        var transfer = pendingFull;
        if (transfer == null || transfer.revision != revision) {
            if (revision > snapshot.revision()) {
                requestResync(Math.max(revision, transfer == null ? 0L : transfer.revision));
            }
            return false;
        }
        if (chunkIndex < 0 || chunkIndex >= transfer.totalChunks || entries == null
                || entries.size() != expectedChunkSize(transfer, chunkIndex)) {
            requestResync(transfer.revision);
            return false;
        }

        var safeEntries = List.copyOf(entries);
        var previous = transfer.chunks.putIfAbsent(chunkIndex, safeEntries);
        if (previous != null && !previous.equals(safeEntries)) {
            requestResync(transfer.revision);
            return false;
        }
        if (previous == null) transfer.lastProgressTick = clientTick;
        if (transfer.chunks.size() == transfer.totalChunks) return finishFullTransfer();
        return true;
    }

    public static synchronized boolean applyDelta(
            long revision,
            byte operation,
            @Nullable Entry entry,
            @Nullable UUID targetUuid,
            int stuporCp,
            int impressionCp
    ) {
        var current = snapshot;
        if (revision <= current.revision()) return false;
        if (pendingFull != null) {
            requestResync(Math.max(revision, pendingFull.revision));
            return false;
        }
        if (revision != current.revision() + 1) {
            requestResync(revision);
            return false;
        }

        var entries = new LinkedHashMap<UUID, Entry>();
        current.entries().forEach(currentEntry -> entries.put(currentEntry.targetUuid(), currentEntry));
        if (operation == DELTA_UPSERT && entry != null) {
            entries.put(entry.targetUuid(), entry);
        } else if (operation == DELTA_REMOVE && targetUuid != null) {
            entries.remove(targetUuid);
        } else {
            requestResync(revision);
            return false;
        }

        pendingFull = null;
        snapshot = new Snapshot(
                revision,
                List.copyOf(entries.values()),
                Math.max(0, stuporCp),
                Math.max(0, impressionCp)
        );
        if (revision >= requiredRevision) clearResyncRequirement();
        return true;
    }

    public static synchronized void clear(long revision) {
        if (revision < snapshot.revision()) return;
        if (pendingFull != null && revision < pendingFull.revision) return;
        if (revision < requiredRevision) {
            requestResync(requiredRevision);
            return;
        }
        pendingFull = null;
        clearResyncRequirement();
        snapshot = new Snapshot(revision, List.of(), 0, 0);
    }

    public static synchronized void clearLocal() {
        pendingFull = null;
        resyncRequested = false;
        requiredRevision = 0L;
        snapshot = Snapshot.EMPTY;
        clientTick = 0L;
        lastResyncRequestTick = 0L;
    }

    private static boolean finishFullTransfer() {
        var transfer = pendingFull;
        if (transfer == null || transfer.chunks.size() != transfer.totalChunks) return false;

        var entries = new ArrayList<Entry>(transfer.totalEntries);
        for (var index = 0; index < transfer.totalChunks; index++) {
            var chunk = transfer.chunks.get(index);
            if (chunk == null) {
                requestResync(transfer.revision);
                return false;
            }
            entries.addAll(chunk);
        }
        if (entries.size() != transfer.totalEntries) {
            requestResync(transfer.revision);
            return false;
        }

        var unique = new LinkedHashMap<UUID, Entry>();
        entries.forEach(entry -> unique.put(entry.targetUuid(), entry));
        if (unique.size() != entries.size()) {
            requestResync(transfer.revision);
            return false;
        }

        snapshot = new Snapshot(
                transfer.revision,
                List.copyOf(unique.values()),
                transfer.stuporCp,
                transfer.impressionCp
        );
        pendingFull = null;
        if (transfer.revision >= requiredRevision) clearResyncRequirement();
        return true;
    }

    private static void requestResync(long observedRevision) {
        requiredRevision = Math.max(requiredRevision, Math.max(0L, observedRevision));
        if (resyncRequested) return;
        resyncRequested = true;
        sendResyncRequest();
    }

    private static void clearResyncRequirement() {
        resyncRequested = false;
        requiredRevision = 0L;
    }

    private static void sendResyncRequest() {
        lastResyncRequestTick = clientTick;
        var requester = resyncRequester;
        if (requester != null) requester.accept(snapshot.revision());
    }

    private static long elapsedSince(long earlierTick) {
        return clientTick >= earlierTick ? clientTick - earlierTick : Long.MAX_VALUE;
    }

    private static boolean isValidFullShape(int totalChunks, int totalEntries) {
        if (totalChunks < 0 || totalEntries < 0) return false;
        var expectedChunks = ((long) totalEntries + MAX_FULL_CHUNK_ENTRIES - 1L)
                / MAX_FULL_CHUNK_ENTRIES;
        return totalChunks == expectedChunks;
    }

    private static int expectedChunkSize(FullTransfer transfer, int chunkIndex) {
        if (chunkIndex < transfer.totalChunks - 1) return MAX_FULL_CHUNK_ENTRIES;
        return transfer.totalEntries - (transfer.totalChunks - 1) * MAX_FULL_CHUNK_ENTRIES;
    }

    public record Entry(
            UUID targetUuid,
            int entityId,
            String entityTypeId,
            String displayName,
            float health,
            float maxHealth,
            float distance,
            byte support,
            byte flags,
            int misidentificationTicks
    ) {
        public Entry {
            Objects.requireNonNull(targetUuid, "targetUuid");
            entityTypeId = normalizeText(entityTypeId, 128, "minecraft:unknown");
            displayName = normalizeText(displayName, 96, entityTypeId);
            health = finiteNonNegative(health);
            maxHealth = finiteNonNegative(maxHealth);
            distance = Float.isFinite(distance) && distance >= 0.0f ? distance : Float.MAX_VALUE;
            misidentificationTicks = Math.max(0, misidentificationTicks);
        }

        private static String normalizeText(@Nullable String value, int maxLength, String fallback) {
            if (value == null || value.isBlank()) return fallback;
            return value.length() <= maxLength ? value : value.substring(0, maxLength);
        }

        private static float finiteNonNegative(float value) {
            return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
        }

        public boolean hasFlag(byte flag) {
            return (flags & flag) != 0;
        }
    }

    public record Snapshot(long revision, List<Entry> entries, int stuporCp, int impressionCp) {
        private static final Snapshot EMPTY = new Snapshot(0L, List.of(), 0, 0);

        public Snapshot {
            entries = List.copyOf(entries);
            stuporCp = Math.max(0, stuporCp);
            impressionCp = Math.max(0, impressionCp);
        }
    }

    private static final class FullTransfer {
        private final long revision;
        private final int totalChunks;
        private final int totalEntries;
        private final int stuporCp;
        private final int impressionCp;
        private final Map<Integer, List<Entry>> chunks = new HashMap<>();
        private long lastProgressTick;

        private FullTransfer(
                long revision,
                int totalChunks,
                int totalEntries,
                int stuporCp,
                int impressionCp,
                long lastProgressTick
        ) {
            this.revision = revision;
            this.totalChunks = totalChunks;
            this.totalEntries = totalEntries;
            this.stuporCp = stuporCp;
            this.impressionCp = impressionCp;
            this.lastProgressTick = lastProgressTick;
        }
    }
}
