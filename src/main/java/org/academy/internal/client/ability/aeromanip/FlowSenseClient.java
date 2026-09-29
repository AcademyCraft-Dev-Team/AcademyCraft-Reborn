package org.academy.internal.client.ability.aeromanip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.network.FlowSensePacket;
import org.joml.Matrix4fc;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class FlowSenseClient {
    private static final FlowObservationCache CACHE = new FlowObservationCache();
    private static final FlowSenseRenderer RENDERER = new FlowSenseRenderer();
    private static ClientLevel level;
    private static long ticks;
    public static void init() { }
    public static int markerCount() { return CACHE.size(); }
    public static FlowSenseRenderer renderer() { return RENDERER; }
    private static void world() {
        var current = Minecraft.getInstance().level;
        if (level != current) { CACHE.reset(); RENDERER.close(); level = current; }
    }
    public static void receive(FlowSensePacket packet) {
        world();
        if (level != null && level.dimension().identifier().equals(packet.dimension)) CACHE.accept(packet, ticks);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        world(); ticks++;
        if (level == null) return;
        // Ability data can be temporarily unavailable during a resource/world transition. Only an
        // explicit server clear closes a generation; a local presentation gap must allow its heartbeat.
        if (!AbilitySystemClient.canUseSkillSilently(Skills.FLOW_SENSE.get())) CACHE.clearMarkers();
        CACHE.expire(ticks);
    }
    public static void render(Vec3 camera, Matrix4fc view, Matrix4fc projection) {
        if (level == null || CACHE.size() == 0 || !AbilitySystemClient.canUseSkillSilently(Skills.FLOW_SENSE.get())) return;
        RENDERER.render(CACHE, ticks, camera, view, projection);
    }
    private static void reset() { CACHE.reset(); RENDERER.close(); level = null; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    @SubscribeEvent public static void stopped(ClientStoppedEvent event) { reset(); }
    @SubscribeEvent public static void resources(ClientResourceLoadFinishedEvent event) { CACHE.clearMarkers(); RENDERER.close(); }
}
