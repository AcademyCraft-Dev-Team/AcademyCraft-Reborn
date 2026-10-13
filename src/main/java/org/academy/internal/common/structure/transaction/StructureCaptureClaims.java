package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-server source-cell exclusivity shared by live captures and pending recovery. A claim is
 * all-or-nothing and survives failed stages so a retained transaction keeps blocking duplicates
 * until its journal outcome is resolved.
 */
public final class StructureCaptureClaims {
    private final Map<Identifier, Map<BlockPos, UUID>> claimed = new HashMap<>();
    private final Map<UUID, Claim> byTransaction = new HashMap<>();

    /** Claims every position or nothing; a position may belong to at most one transaction. */
    public synchronized boolean tryClaim(Identifier dimension, List<BlockPos> positions, UUID transactionId) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(transactionId, "transactionId");
        if (positions.isEmpty() || byTransaction.containsKey(transactionId)) return false;
        var within = claimed.computeIfAbsent(dimension, ignored -> new HashMap<>());
        for (var position : positions) {
            Objects.requireNonNull(position, "position");
            if (within.containsKey(position)) return false;
        }
        var inserted = new ArrayList<BlockPos>(positions.size());
        for (var position : positions) {
            var immutable = position.immutable();
            within.put(immutable, transactionId);
            inserted.add(immutable);
        }
        byTransaction.put(transactionId, new Claim(dimension, List.copyOf(inserted)));
        return true;
    }

    public synchronized boolean isClaimed(Identifier dimension, BlockPos position) {
        return claimedBy(dimension, position) != null;
    }

    @Nullable
    public synchronized UUID claimedBy(Identifier dimension, BlockPos position) {
        var within = claimed.get(dimension);
        return within == null ? null : within.get(position);
    }

    /** Releases only this transaction's cells; other transactions are untouched. */
    public synchronized void release(UUID transactionId) {
        var claim = byTransaction.remove(transactionId);
        if (claim == null) return;
        var within = claimed.get(claim.dimension());
        if (within == null) return;
        for (var position : claim.positions()) {
            if (transactionId.equals(within.get(position))) within.remove(position);
        }
        if (within.isEmpty()) claimed.remove(claim.dimension());
    }

    public synchronized void clear() {
        claimed.clear();
        byTransaction.clear();
    }

    public synchronized int claimedTransactions() {
        return byTransaction.size();
    }

    private record Claim(Identifier dimension, List<BlockPos> positions) {}
}
