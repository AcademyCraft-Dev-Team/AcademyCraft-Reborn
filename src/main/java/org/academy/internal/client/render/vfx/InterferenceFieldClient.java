package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.vfx.DirectionalArea;
import org.academy.internal.common.network.DarkmatterVisualPacket;
import org.academy.api.client.render.post.DirectionalFieldRenderer;
import org.academy.api.client.render.post.WorldSurfaceMasks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-authoritative field snapshots; never infer a second combat region on the client. */
public final class InterferenceFieldClient {
    private record Entry(@org.jspecify.annotations.Nullable LivingEntity actor, DirectionalArea area, long tick) { }
    private static final Map<Integer, Entry> FIELDS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static List<DirectionalArea> frame = List.of();
    private InterferenceFieldClient() { }

    public static void accept(DarkmatterVisualPacket packet) {
        resetWorld();
        if (world == null || !world.dimension().identifier().equals(packet.dimension)) return;
        if (!packet.active) { FIELDS.remove(packet.entityId); return; }
        if (packet.area == null) return;
        // A large field can be visible before its caster enters entity tracking distance.
        var actor = world.getEntity(packet.entityId) instanceof LivingEntity living ? living : null;
        FIELDS.put(packet.entityId, new Entry(actor, packet.area, world.getGameTime()));
    }

    private static void resetWorld() {
        var next = Minecraft.getInstance().level;
        if (world == next) return;
        FIELDS.clear(); frame = List.of(); world = next;
        WorldSurfaceMasks.reset();
        DirectionalFieldRenderer.close();
    }

    public static void beginFrame() {
        tick();
        frame = FIELDS.values().stream().map(Entry::area).toList();
        WorldSurfaceMasks.beginFrame(frame);
    }

    public static void tick() {
        resetWorld();
        if (world != null) FIELDS.values().removeIf(e -> world.getGameTime() - e.tick > 12
                || e.actor != null && (e.actor.isRemoved() || !e.actor.isAlive() || world.getEntity(e.actor.getId()) != e.actor));
    }

    public static List<DirectionalArea> areas() { return frame; }
    public static int activeCount() { return FIELDS.size(); }
    public static void close() { FIELDS.clear(); frame = List.of(); WorldSurfaceMasks.close(); DirectionalFieldRenderer.close(); }
}
