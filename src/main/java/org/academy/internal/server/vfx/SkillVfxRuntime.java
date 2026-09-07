package org.academy.internal.server.vfx;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.vfx.EffectVisibility;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.common.network.SkillVfxPacket;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.misaka.MisakaNetworkServer;

/** Samples once per effect; shares each immutable packet among interested observers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SkillVfxRuntime {
    private static final Map<Entity, Entry> ACTIVE = new IdentityHashMap<>();
    private static final Map<ServerPlayer, org.academy.api.common.vfx.EffectUpdateQueue<SkillVfxPacket>> UPDATES = new IdentityHashMap<>();
    private static long nextId;
    private static long physicalTick;
    private static long sentPackets;
    private static long snapshots;
    private SkillVfxRuntime() {}

    public record Statistics(int activeEffects, long snapshots, long sentPackets, int pendingUpdates, long droppedUpdates) {}
    public static Statistics statistics() {
        int pending = 0;
        long dropped = 0;
        for (var queue : UPDATES.values()) { pending += queue.size(); dropped += queue.dropped(); }
        return new Statistics(ACTIVE.size(), snapshots, sentPackets, pending, dropped);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel)) return;
        var entity = event.getEntity();
        if (entity instanceof HighSpeedElectronBeam || entity instanceof Plasma) {
            ACTIVE.put(entity, new Entry(entity, ++nextId));
        }
    }

    public static void emit(ServerLevel level, SkillVfxState.Burst state) {
        var packet = new SkillVfxPacket(level.dimension().identifier(), ++nextId, 1, state);
        for (var observer : level.players()) {
            double range = 96 + (state.plasmaImpact() ? Math.max(96, state.radius()) : state.radius());
            if (observer.distanceToSqr(state.position()) <= range * range) send(observer, packet);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        physicalTick++;
        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var pair = iterator.next();
            var entity = pair.getKey();
            var entry = pair.getValue();
            var level = (ServerLevel) entity.level();
            int phase = entity instanceof HighSpeedElectronBeam beam
                    ? (beam.hasFired() ? 1 : 0) | (beam.isContinuous() ? 2 : 0) | (beam.isHeldCharge() ? 4 : 0)
                    : (((Plasma) entity).isLaunched() ? 1 : 0);
            boolean transition = phase != entry.phase;
            if (entry.last == null || transition || physicalTick - entry.sampledAt >= 5 || entity.isRemoved()) {
                long elapsed = Math.max(1, physicalTick - entry.sampledAt);
                float rate = entry.last == null ? 1f
                        : Math.clamp((entity.tickCount - entry.entityAge) / (float) elapsed, 0f, 64f);
                var state = capture(entity, entry, rate, elapsed);
                boolean changed = entry.last == null || transition || meaningfulChange(entry.last, state);
                boolean heartbeat = physicalTick - entry.sentAt >= 20;
                var packet = new SkillVfxPacket(level.dimension().identifier(), entry.id, ++entry.revision, state);
                // A short-lived beam may fire and disappear between ordinary samples.
                for (var observer : level.players()) {
                    boolean wasWatching = entry.observers.containsKey(observer.getUUID());
                    double range = wasWatching ? 112 : 96;
                    boolean visible = distanceSquared(state, observer.position()) <= range * range;
                    if (visible) {
                        entry.observers.put(observer.getUUID(), observer);
                        if (!wasWatching || transition) send(observer, packet);
                        else if (changed || heartbeat) {
                            UPDATES.computeIfAbsent(observer, ignored -> new org.academy.api.common.vfx.EffectUpdateQueue<>(256))
                                    .offer(entry.id, packet, physicalTick);
                        }
                    } else if (wasWatching) {
                        send(observer, new SkillVfxPacket(packet.dimension, entry.id, packet.revision,
                                new SkillVfxState.End(entity.position(), true)));
                        entry.observers.remove(observer.getUUID());
                    }
                }
                entry.observers.values().removeIf(player -> player.hasDisconnected() || player.level() != level);
                entry.entityAge = entity.tickCount;
                entry.sampledAt = physicalTick;
                entry.phase = phase;
                entry.last = state;
                snapshots++;
                if (changed || heartbeat) entry.sentAt = physicalTick;
            }
            if (entity.isRemoved()) {
                var end = new SkillVfxPacket(level.dimension().identifier(), entry.id, ++entry.revision,
                        new SkillVfxState.End(entity.position(), false));
                for (var observer : entry.observers.values()) send(observer, end);
                iterator.remove();
            }
        }
        var queues = UPDATES.entrySet().iterator();
        while (queues.hasNext()) {
            var queued = queues.next();
            var observer = queued.getKey();
            if (observer.hasDisconnected()) { queues.remove(); continue; }
            queued.getValue().drain(physicalTick, 10, 8, packet -> {
                if (observer.level().dimension().identifier().equals(packet.dimension)) {
                    MisakaNetworkServer.send(observer, packet);
                    sentPackets++;
                }
            });
        }
    }

    private static SkillVfxState capture(Entity entity, Entry entry, float rate, long elapsed) {
        if (entity instanceof HighSpeedElectronBeam b) {
            int flags = (b.hasFired() ? 1 : 0) | (b.isContinuous() ? 2 : 0)
                    | (b.isHeldCharge() ? 4 : 0) | (b.isReflectionActive() ? 8 : 0);
            return new SkillVfxState.Beam(b.position(), b.getXRot(), b.getYRot(), b.getBeamLength(),
                    b.getBeamScale(), b.getVisualSideOffset(), Math.max(0, b.currentChargerTicks),
                    b.getAttackDelayTicks(), Math.max(0, b.currentRayLifeTicks), flags,
                    b.getReflectionDistance(), b.getReflectionReturnLength(), b.getReflectionReturnDirection(), rate);
        }
        var p = (Plasma) entity;
        float chargeRate = entry.last instanceof SkillVfxState.Plasma old
                ? Math.clamp((p.getGatherProgress() - old.progress()) / elapsed, 0f, 1f)
                : 1f / org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration.MAX_CHARGE_TICKS;
        return new SkillVfxState.Plasma(p.position(), entry.chargeOrigin, p.visualTarget(),
                p.getGatherProgress(), p.visualSpeed(), p.visualLaunchDelay(), p.isLaunched(), rate, chargeRate);
    }

    private static boolean meaningfulChange(SkillVfxState old, SkillVfxState next) {
        if (old instanceof SkillVfxState.Beam a && next instanceof SkillVfxState.Beam b) {
            return a.flags() != b.flags() || a.position().distanceToSqr(b.position()) > 0.0025
                    || Math.abs(a.xRot() - b.xRot()) > 0.25f || Math.abs(a.yRot() - b.yRot()) > 0.25f
                    || a.length() != b.length() || a.scale() != b.scale() || a.sideOffset() != b.sideOffset()
                    || a.delayTicks() != b.delayTicks() || a.reflectionDistance() != b.reflectionDistance()
                    || a.returnLength() != b.returnLength() || !a.returnDirection().equals(b.returnDirection())
                    || Math.abs(a.tickRate() - b.tickRate()) > 0.01f;
        }
        if (old instanceof SkillVfxState.Plasma a && next instanceof SkillVfxState.Plasma b) {
            return a.launched() != b.launched() || !a.target().equals(b.target()) || a.speed() != b.speed()
                    || Math.abs(a.tickRate() - b.tickRate()) > 0.01f
                    || Math.abs(a.chargeRate() - b.chargeRate()) > 0.0001f;
        }
        return true;
    }

    /** Tests the entire beam, including reflected return, rather than just the shooter. */
    public static double distanceSquared(SkillVfxState state, Vec3 observer) {
        if (state instanceof SkillVfxState.Beam beam) {
            var end = beam.position().add(Vec3.directionFromRotation(beam.xRot(), beam.yRot())
                    .scale(beam.reflected() ? beam.reflectionDistance() : beam.length()));
            double result = segmentDistance(observer, beam.position(), end);
            if (beam.reflected()) result = Math.min(result,
                    segmentDistance(observer, end, end.add(beam.returnDirection().scale(beam.returnLength()))));
            return result;
        }
        if (state instanceof SkillVfxState.Plasma plasma) {
            var center = plasma.launched() ? plasma.position() : plasma.chargeOrigin().add(0, 64, 0);
            double distance = Math.max(0, observer.distanceTo(center) - (plasma.launched() ? 128 : 152));
            return distance * distance;
        }
        return observer.distanceToSqr(state.position());
    }

    private static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        return EffectVisibility.distanceToSegmentSquared(p.x, p.y, p.z, a.x, a.y, a.z, b.x, b.y, b.z);
    }

    private static void send(ServerPlayer observer, SkillVfxPacket packet) {
        var queue = UPDATES.get(observer);
        if (queue != null) queue.cancel(packet.id);
        MisakaNetworkServer.send(observer, packet);
        sentPackets++;
    }

    @SubscribeEvent
    public static void clear(ServerStoppedEvent event) {
        ACTIVE.clear();
        UPDATES.clear();
        // IDs never repeat during this JVM session, including integrated-server restarts.
        physicalTick = 0;
        snapshots = 0;
        sentPackets = 0;
    }

    private static final class Entry {
        final long id;
        final Vec3 chargeOrigin;
        final Map<UUID, ServerPlayer> observers = new HashMap<>();
        SkillVfxState last;
        long revision, sampledAt, sentAt;
        int entityAge, phase = -1;
        Entry(Entity entity, long id) {
            this.id = id;
            sampledAt = physicalTick;
            entityAge = entity.tickCount;
            var owner = entity instanceof Plasma p ? entity.level().getEntity(p.getOwnerEntityId()) : null;
            chargeOrigin = owner == null ? entity.position().add(0, -31, 0) : owner.position();
        }
    }
}
