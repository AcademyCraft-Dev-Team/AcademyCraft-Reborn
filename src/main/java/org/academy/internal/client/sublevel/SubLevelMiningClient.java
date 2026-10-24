package org.academy.internal.client.sublevel;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.network.SubLevelMiningProgressPacket;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.structure.space.NativeStructureView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Bounded server-authored crack stages for SubLevel projections; never predicts removal. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelMiningClient {
    private static final int MAX_ENTRIES = 512;
    private static final long VISUAL_TTL = 100L;
    private static final Map<Key, Entry> ENTRIES = new HashMap<>();
    private static long ticks;

    private SubLevelMiningClient() {
    }

    @SubscribeEvent
    public static void beginTick(ClientTickEvent.Pre event) {
        ticks++;
        if (ticks % 20 == 0) purge();
    }

    public static void accept(SubLevelMiningProgressPacket packet) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.getConnection() == null || packet.getPacketListener() != minecraft.getConnection()) return;
        SubLevelClientInteraction.accept(packet);
        if (packet.id() == null) return;
        var key = new Key(packet.actorEntityId(), packet.requestId(), packet.id(), packet.local());
        var entry = ENTRIES.get(key);
        if (entry != null && packet.sequence() <= entry.sequence) return;
        if (entry == null) {
            if (ENTRIES.size() >= MAX_ENTRIES) return;
            entry = new Entry();
            ENTRIES.put(key, entry);
        }
        entry.sequence = packet.sequence();
        entry.stage = packet.stage();
        entry.status = packet.status();
        entry.updatedTick = ticks;
    }

    /** Highest stage per local cell for one projection; empty when the state changed or expired. */
    public static Map<BlockPos, Integer> cracks(SubLevelId id, NativeStructureView view) {
        var result = new HashMap<BlockPos, Integer>();
        if (id == null || view == null) return result;
        for (var entry : ENTRIES.entrySet()) {
            var key = entry.getKey();
            if (!key.id.equals(id)) continue;
            var value = entry.getValue();
            if (value.stage < 0 || value.stage > 9) continue;
            if (ticks - value.updatedTick > VISUAL_TTL) continue;
            if (view.getBlockState(key.local).isAir()) continue;
            result.merge(key.local, value.stage, Math::max);
        }
        return result;
    }

    private static void purge() {
        var expired = new ArrayList<Key>();
        for (var entry : ENTRIES.entrySet()) {
            if (ticks - entry.getValue().updatedTick > VISUAL_TTL) expired.add(entry.getKey());
        }
        for (var key : expired) ENTRIES.remove(key);
    }

    private record Key(int actor, long requestId, SubLevelId id, BlockPos local) {
    }

    private static final class Entry {
        private long sequence = -1;
        private int stage = -1;
        private SubLevelMiningProgressPacket.Status status = SubLevelMiningProgressPacket.Status.STARTED;
        private long updatedTick;
    }
}
