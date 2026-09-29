package org.academy.internal.client.ability.aeromanip;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.academy.internal.common.ability.aeromanip.network.FlowSensePacket;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FlowObservationCacheTest {
    @Test void oversizedWireBatchIsRejectedBeforeAllocatingItsRecords() {
        var bytes = Unpooled.buffer();
        try {
            Identifier.STREAM_CODEC.encode(bytes, Identifier.fromNamespaceAndPath("minecraft", "overworld"));
            bytes.writeLong(1).writeLong(1).writeBoolean(false);
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(bytes, 60); bytes.writeFloat(32);
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(bytes, Integer.MAX_VALUE);
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> FlowSensePacket.CODEC.decode(bytes));
        } finally { bytes.release(); }
    }
    private static FlowSensePacket packet(long session, long tick, boolean clear, List<FlowSensePacket.Sample> samples, List<Integer> removed) {
        return new FlowSensePacket(Identifier.fromNamespaceAndPath("minecraft", "overworld"), session, tick, 60, 32, clear, samples, removed);
    }
    private static FlowSensePacket.Sample sample(int id) { return new FlowSensePacket.Sample(id, new UUID(0, id), 0, 64 * 32, id * 32, 1); }
    @Test void batchesMergeAndOnlyExplicitRemovalsOrExpiryRemoveMarkers() {
        var cache = new FlowObservationCache();
        cache.accept(packet(1, 0, false, List.of(sample(1)), List.of()), 0);
        cache.accept(packet(1, 1, false, List.of(sample(2)), List.of()), 1);
        assertEquals(2, cache.size());
        cache.accept(packet(1, 2, false, List.of(), List.of(1)), 2);
        assertEquals(1, cache.size());
        cache.expire(61); assertEquals(0, cache.size());
    }
    @Test void clearedAndOldSessionsCannotResurrectMarkersAndReloadRefillsFromHeartbeat() {
        var cache = new FlowObservationCache();
        cache.accept(packet(4, 0, false, List.of(sample(1)), List.of()), 0);
        cache.accept(packet(4, 1, true, List.of(), List.of()), 1);
        cache.accept(packet(4, 2, false, List.of(sample(1)), List.of()), 2);
        cache.accept(packet(3, 3, false, List.of(sample(2)), List.of()), 3);
        assertEquals(0, cache.size());
        cache.accept(packet(5, 4, false, List.of(sample(2)), List.of()), 4);
        cache.clearMarkers();
        cache.accept(packet(5, 5, false, List.of(sample(2)), List.of()), 5);
        assertEquals(1, cache.size());
        cache.reset(); assertEquals(0, cache.size());
    }
    @Test void idReuseSnapsToNewIdentityAndDenseMarkersHaveNoNinetySixTargetDisplayCap() {
        var cache = new FlowObservationCache();
        for (int i = 0; i < 4224; i++) cache.accept(packet(1, i, false, List.of(sample(i)), List.of()), 0);
        assertEquals(4224, cache.size());
        var replacement = new FlowSensePacket.Sample(0, new UUID(10, 10), 320, 0, 0, 1);
        cache.accept(packet(1, 4224, false, List.of(replacement), List.of()), 1);
        var marker = cache.markers().stream().filter(value -> value.sample.id() == 0).findFirst().orElseThrow();
        assertEquals(10, marker.position(1).x);
        for (int i = 0; i < 17; i++) cache.expire(62);
        assertEquals(0, cache.size());
    }
    @Test void wireRoundTripPreservesIdentityQuantizedPositionAndExplicitRemoval() {
        var original = packet(9, 12, false, List.of(sample(4)), List.of(2));
        var bytes = Unpooled.buffer();
        try {
            FlowSensePacket.CODEC.encode(bytes, original);
            var decoded = FlowSensePacket.CODEC.decode(bytes);
            assertEquals(original.updates, decoded.updates); assertEquals(original.removed, decoded.removed);
            assertEquals(original.session, decoded.session); assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }
}
