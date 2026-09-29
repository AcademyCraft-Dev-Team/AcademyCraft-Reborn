package org.academy.internal.common.ability.aeromanip;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.AreaQueryService;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.network.FlowSensePacket;
import java.util.*;

/** Owns each observer's query and delta history, with no client-side independent entity discovery. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class FlowSenseRuntime {
    private static final Map<ServerPlayer, Observation> ACTIVE = new IdentityHashMap<>();
    private static long nextSession, packets, records;
    public static long packetsSent() { return packets; }
    public static long recordsSent() { return records; }
    public static void tick(ServerPlayer player, int milestone, double range, int interval, int maximum) {
        var observation = ACTIVE.get(player);
        if (observation != null && (observation.query.isClosed() || observation.dimension != player.level())) {
            stop(player); observation = null;
        }
        if (observation == null) { observation = new Observation(player); ACTIVE.put(player, observation); }
        observation.milestone = milestone; observation.range = range;
        observation.interval = interval; observation.maximum = maximum;
        observation.query.update(player.position(), range, interval, maximum);
    }
    public static void stop(ServerPlayer player) {
        var observation = ACTIVE.remove(player);
        if (observation != null) observation.query.close();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
    private record Sent(FlowSensePacket.Sample sample, long tick) { }
    private static final class Observation implements AreaQueryService.Effect {
        final ServerPlayer owner;
        final net.minecraft.server.level.ServerLevel dimension;
        final long session = ++nextSession;
        final AreaQueryService.Session query;
        final Int2ObjectOpenHashMap<Sent> sent = new Int2ObjectOpenHashMap<>();
        final List<FlowSensePacket.Sample> updates = new ArrayList<>();
        final LinkedHashSet<Integer> removals = new LinkedHashSet<>();
        int milestone, interval = 10, maximum = 64;
        double range = 24;
        long lastActivity;
        Observation(ServerPlayer owner) {
            this.owner = owner; dimension = owner.level();
            query = AreaQueryService.open(dimension, owner, AreaQueryService.Point.ORIGIN, this);
        }
        @Override public boolean matches(LivingEntity target) {
            return target != owner && !target.isSpectator()
                    && (!(target instanceof Player player) || !player.isShiftKeyDown() || milestone >= 3);
        }
        @Override public void apply(LivingEntity target, long tick) {
            var sample = FlowSensePacket.Sample.of(target);
            var previous = sent.get(target.getId());
            if (previous == null || !previous.sample.equals(sample) || tick - previous.tick >= 20) {
                updates.add(sample); sent.put(target.getId(), new Sent(sample, tick)); removals.remove(target.getId());
            }
            if (tick - lastActivity >= interval) { Skills.FLOW_SENSE.get().reportActivity(owner, true); lastActivity = tick; }
        }
        @Override public void removed(LivingEntity target) {
            if (sent.remove(target.getId()) != null) removals.add(target.getId());
        }
        @Override public void afterTick(long tick) {
            if (updates.isEmpty() && removals.isEmpty()) return;
            var removed = new ArrayList<Integer>();
            var cursor = removals.iterator();
            while (cursor.hasNext() && removed.size() < FlowSensePacket.MAX_BATCH) { removed.add(cursor.next()); cursor.remove(); }
            int lease = (int) Math.clamp(Math.max(60, query.maximumDelayTicks() * 2 + 40
                    + (long) query.trackedCount() * interval * 2 / maximum), 60, 600);
            new FlowSensePacket(dimension.dimension().identifier(), session, tick, lease, (float) range,
                    false, updates, removed).sendTo(owner);
            packets++; records += updates.size(); updates.clear();
        }
        @Override public void closed() {
            sent.clear(); updates.clear(); removals.clear();
            if (!owner.hasDisconnected()) new FlowSensePacket(dimension.dimension().identifier(), session,
                    dimension.getGameTime(), 1, (float) range, true, List.of(), List.of()).sendTo(owner);
        }
    }
}
