package org.academy.internal.client.structure;

import org.academy.internal.common.network.NativeStructureMiningProgressPacket;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded remote watermarks plus one reserved local connection survive visual expiration. */
final class NativeStructureMiningUpdates {
    static final int MAX_BREAKERS = 256;
    static final long VISUAL_TTL = 100;
    private final Map<Integer, Entry> entries = new HashMap<>();
    private final Entry own = new Entry();

    boolean accept(NativeStructureMiningProgressPacket packet, long tick, boolean updateVisual) {
        var entry = entries.get(packet.breakerEntityId());
        if (entry != null && packet.updateSequence() <= entry.sequence) return false;
        if (entry == null) {
            if (entries.size() >= MAX_BREAKERS) return false;
            entry = new Entry();
            entries.put(packet.breakerEntityId(), entry);
        }
        update(entry, packet, tick, updateVisual);
        return true;
    }

    /** Local progress must remain usable after the remote budget fills or the player respawns. */
    boolean acceptOwn(NativeStructureMiningProgressPacket packet, long tick, boolean updateVisual) {
        if (packet.updateSequence() <= own.sequence) return false;
        update(own, packet, tick, updateVisual);
        return true;
    }

    private static void update(Entry entry, NativeStructureMiningProgressPacket packet, long tick, boolean updateVisual) {
        entry.sequence = packet.updateSequence();
        if (updateVisual) {
            entry.visual = active(packet) && packet.stage() >= 0 ? packet : null;
            entry.updatedTick = tick;
        }
    }

    void clearOwnVisual() { own.visual = null; }

    void clearVisual(int breaker) {
        var entry = entries.get(breaker);
        if (entry != null) entry.visual = null;
    }

    List<NativeStructureMiningProgressPacket> active(long tick) {
        var result = new ArrayList<NativeStructureMiningProgressPacket>();
        appendActive(result, own, tick);
        for (var entry : entries.values()) {
            appendActive(result, entry, tick);
        }
        return result;
    }

    private static void appendActive(List<NativeStructureMiningProgressPacket> result, Entry entry, long tick) {
        if (tick - entry.updatedTick > VISUAL_TTL) entry.visual = null;
        if (entry.visual != null) result.add(entry.visual);
    }

    void clear() {
        entries.clear();
        own.sequence = -1;
        own.visual = null;
    }

    private static boolean active(NativeStructureMiningProgressPacket packet) {
        return switch (packet.status()) {
            case STARTED, PROGRESS, READY -> true;
            case COMPLETED, ABORTED, REJECTED -> false;
        };
    }

    private static final class Entry {
        long sequence = -1;
        long updatedTick;
        @Nullable NativeStructureMiningProgressPacket visual;
    }
}
