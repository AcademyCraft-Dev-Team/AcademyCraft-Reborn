package org.academy.internal.common.ability.teleport;

/**
 * Single source of truth for 区块跃迁 pricing, shared by the client map screen (estimate/preview)
 * and the server (authoritative charge).
 *
 * <p>Cost grows linearly with the exchanged volume and the number of relocated entities so that
 * large-scale terrain rearrangement is self-limiting. All arithmetic saturates: a client-supplied
 * (and therefore untrusted) count must never wrap the cost to a negative number and turn a huge
 * swap into a refund.
 */
public final class ChunkLeapCost {
    public static final int BASE = 100;
    public static final int PER_CHUNK = 4;
    public static final int PER_ENTITY = 30;
    public static final int ENTITY_TELEPORT = 30;

    private ChunkLeapCost() {
    }

    public static int forSwap(long chunkCount, long entityCount) {
        var chunks = Math.max(0L, chunkCount);
        var entities = Math.max(0L, entityCount);
        var chunkCost = saturatingMultiply(chunks, PER_CHUNK);
        var entityCost = saturatingMultiply(entities, PER_ENTITY);
        var total = (long) chunkCost + entityCost;
        return (int) Math.min(Integer.MAX_VALUE, BASE + total);
    }

    public static int forEntityTeleport() {
        return ENTITY_TELEPORT;
    }

    private static long saturatingMultiply(long value, long factor) {
        if (value != 0 && Long.MAX_VALUE / value < factor) return Integer.MAX_VALUE;
        var product = value * factor;
        return Math.min(Integer.MAX_VALUE, product == 0L ? 0L : product);
    }
}
