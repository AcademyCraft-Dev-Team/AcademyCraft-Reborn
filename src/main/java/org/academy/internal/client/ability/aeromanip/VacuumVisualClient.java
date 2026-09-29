package org.academy.internal.client.ability.aeromanip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.ability.aeromanip.VacuumVisuals;
import java.util.*;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class VacuumVisualClient {
    private record Visual(ActiveEffect effect, long expires) { }
    private static final Map<UUID, Visual> ACTIVE = new LinkedHashMap<>();
    private static ClientLevel level;
    private static long ticks;
    public static int count() { return ACTIVE.size(); }
    public static void receive(VacuumVisuals.Update packet) {
        world();
        if (level == null || !level.dimension().identifier().equals(packet.dimension)) return;
        var previous = ACTIVE.get(packet.owner);
        if (!packet.active) { if (previous != null) previous.effect.stop(); ACTIVE.remove(packet.owner); return; }
        if (previous == null && ACTIVE.size() >= 64) {
            var oldest = ACTIVE.keySet().iterator().next(); ACTIVE.remove(oldest).effect.stop();
        }
        var effect = previous == null || previous.effect.isStopped() ? VfxGraphManager.INSTANCE.spawn(
                AcademyCraft.academy("vfxgraph/aeromanip_vacuum_sustained"), packet.position.toVector3f()) : previous.effect;
        effect.setScale(packet.radius);
        effect.bindFrame((active, camera, partial) -> {
            if (level == null) return false;
            var owner = level.getEntity(packet.ownerId);
            if (owner != null) {
                if (!owner.getUUID().equals(packet.owner) || !owner.isAlive()) return false;
                active.setPosition(owner.getPosition(partial).toVector3f());
            } else active.setPosition(packet.position.toVector3f());
            active.setCullingSphere(active.position(), packet.radius * 2);
            return true;
        });
        ACTIVE.put(packet.owner, new Visual(effect, ticks + 60));
    }
    private static void world() {
        if (level != Minecraft.getInstance().level) { clear(); level = Minecraft.getInstance().level; }
    }
    private static void clear() { ACTIVE.values().forEach(value -> value.effect.stop()); ACTIVE.clear(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        world(); ticks++;
        ACTIVE.values().removeIf(value -> {
            if (value.expires > ticks) return false;
            value.effect.stop(); return true;
        });
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); level = null; }
    @SubscribeEvent public static void resources(ClientResourceLoadFinishedEvent event) { clear(); }
}
