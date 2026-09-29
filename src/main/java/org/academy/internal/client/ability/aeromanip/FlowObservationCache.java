package org.academy.internal.client.ability.aeromanip;

import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.aeromanip.network.FlowSensePacket;
import java.util.Collection;
import java.util.LinkedHashMap;

/** Tick-based leases, stable identities, and bounded incremental expiry without per-frame map copies. */
public final class FlowObservationCache {
    public static final int MAX_MARKERS = 8192;
    private final LinkedHashMap<Integer, Marker> markers = new LinkedHashMap<>();
    private long session = -1, lastServerTick = Long.MIN_VALUE;
    private boolean closed;
    private float range;
    public static final class Marker {
        public FlowSensePacket.Sample sample;
        public Vec3 from, to;
        public long updatedAt, expiresAt, bornAt;
        public Vec3 position(double tick) { return from.lerp(to, Math.clamp((tick - updatedAt) / 3.0, 0, 1)); }
    }
    public Collection<Marker> markers() { return markers.values(); }
    public float range() { return range; }
    public int size() { return markers.size(); }
    public void accept(FlowSensePacket packet, long tick) {
        if (packet.session < session || packet.session == session && (closed || packet.tick < lastServerTick)) return;
        if (packet.session > session) { markers.clear(); session = packet.session; closed = false; lastServerTick = Long.MIN_VALUE; }
        lastServerTick = packet.tick; range = packet.range;
        if (packet.clear) { markers.clear(); closed = true; return; }
        for (int id : packet.removed) markers.remove(id);
        for (var sample : packet.updates) {
            var marker = markers.remove(sample.id());
            if (marker == null || !marker.sample.uuid().equals(sample.uuid())) {
                marker = new Marker(); marker.from = marker.to = sample.position(); marker.bornAt = tick;
            } else { marker.from = marker.position(tick); marker.to = sample.position(); }
            marker.sample = sample; marker.updatedAt = tick; marker.expiresAt = tick + packet.leaseTicks;
            markers.put(sample.id(), marker);
            if (markers.size() > MAX_MARKERS) markers.pollFirstEntry();
        }
    }
    public void expire(long tick) {
        int count = Math.min(256, markers.size());
        for (int i = 0; i < count; i++) {
            var entry = markers.pollFirstEntry();
            if (entry.getValue().expiresAt > tick) markers.put(entry.getKey(), entry.getValue());
        }
    }
    /** Resource reload retains the generation so the next authoritative heartbeat can refill it. */
    public void clearMarkers() { markers.clear(); }
    public void reset() { markers.clear(); session = -1; lastServerTick = Long.MIN_VALUE; closed = false; }
}
