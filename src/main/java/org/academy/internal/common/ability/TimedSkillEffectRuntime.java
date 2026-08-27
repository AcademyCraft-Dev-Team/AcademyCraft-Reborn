package org.academy.internal.common.ability;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;

import java.util.*;

/**
 * Bounded, session-only marks and cooldowns shared by proficiency effects.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TimedSkillEffectRuntime {
    private static final int MAX_EFFECTS = 4096;
    private static final int MAX_EFFECTS_PER_SOURCE = 96;
    private static final Map<Key, Entry> EFFECTS = new LinkedHashMap<>();
    private static final ArrayDeque<ScheduledAction> SCHEDULED = new ArrayDeque<>();

    private TimedSkillEffectRuntime() {
    }

    public static synchronized boolean put(
            ServerPlayer source,
            UUID target,
            Skill skill,
            String effect,
            int durationTicks,
            float value
    ) {
        if (source == null || target == null || skill == null || effect == null || effect.isBlank()
                || durationTicks <= 0 || !Float.isFinite(value)) return false;
        var now = source.level().getGameTime();
        purgeExpired(now);
        var key = new Key(source.getUUID(), target, skill.getKeyString(), effect);
        if (!EFFECTS.containsKey(key) && countForSource(source.getUUID()) >= MAX_EFFECTS_PER_SOURCE) {
            return false;
        }
        while (!EFFECTS.containsKey(key) && EFFECTS.size() >= MAX_EFFECTS) {
            var iterator = EFFECTS.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        EFFECTS.put(key, new Entry(now + durationTicks, value));
        return true;
    }

    public static synchronized Optional<Entry> get(
            UUID source,
            UUID target,
            Skill skill,
            String effect,
            long gameTime
    ) {
        var key = new Key(source, target, skill.getKeyString(), effect);
        var entry = EFFECTS.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expiresAt() <= gameTime) {
            EFFECTS.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public static synchronized Optional<Entry> consume(
            UUID source,
            UUID target,
            Skill skill,
            String effect,
            long gameTime
    ) {
        var key = new Key(source, target, skill.getKeyString(), effect);
        var entry = EFFECTS.remove(key);
        return entry == null || entry.expiresAt() <= gameTime ? Optional.empty() : Optional.of(entry);
    }

    public static synchronized float maxValueForTarget(
            UUID target,
            Skill skill,
            String effect,
            long gameTime
    ) {
        purgeExpired(gameTime);
        var maximum = 0.0f;
        for (var entry : EFFECTS.entrySet()) {
            var key = entry.getKey();
            if (key.target().equals(target)
                    && key.skill().equals(skill.getKeyString())
                    && key.effect().equals(effect)) {
                maximum = Math.max(maximum, entry.getValue().value());
            }
        }
        return maximum;
    }

    public static synchronized Optional<UUID> sourceForTarget(
            UUID target,
            Skill skill,
            String effect,
            long gameTime
    ) {
        purgeExpired(gameTime);
        return EFFECTS.keySet().stream()
                .filter(key -> key.target().equals(target)
                        && key.skill().equals(skill.getKeyString())
                        && key.effect().equals(effect))
                .map(Key::source)
                .findFirst();
    }

    public static synchronized void clearSource(UUID source) {
        EFFECTS.keySet().removeIf(key -> key.source().equals(source));
        SCHEDULED.removeIf(action -> action.source().equals(source));
    }

    public static synchronized void clearEntity(UUID entity) {
        EFFECTS.keySet().removeIf(key -> key.source().equals(entity) || key.target().equals(entity));
    }

    public static synchronized void clearAll() {
        EFFECTS.clear();
        SCHEDULED.clear();
    }

    static synchronized int size() {
        return EFFECTS.size();
    }

    public static synchronized boolean schedule(ServerPlayer source, int delayTicks, Runnable action) {
        if (source == null || delayTicks < 0 || action == null || SCHEDULED.size() >= MAX_EFFECTS) return false;
        var sourceId = source.getUUID();
        var sourceCount = SCHEDULED.stream().filter(entry -> entry.source().equals(sourceId)).count();
        if (sourceCount >= MAX_EFFECTS_PER_SOURCE) return false;
        SCHEDULED.addLast(new ScheduledAction(
                sourceId,
                source.level().getGameTime() + delayTicks,
                action
        ));
        return true;
    }

    private static long countForSource(UUID source) {
        return EFFECTS.keySet().stream().filter(key -> key.source().equals(source)).count();
    }

    private static void purgeExpired(long now) {
        for (var iterator = EFFECTS.entrySet().iterator(); iterator.hasNext(); ) {
            if (iterator.next().getValue().expiresAt() <= now) iterator.remove();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var ready = new ArrayDeque<Runnable>();
        synchronized (TimedSkillEffectRuntime.class) {
            var now = event.getServer().overworld().getGameTime();
            purgeExpired(now);
            for (var iterator = SCHEDULED.iterator(); iterator.hasNext(); ) {
                var scheduled = iterator.next();
                if (scheduled.executeAt() > now) continue;
                ready.addLast(scheduled.action());
                iterator.remove();
            }
        }
        while (!ready.isEmpty()) {
            try {
                ready.removeFirst().run();
            } catch (RuntimeException ignored) {
                // A target may unload between scheduling and execution; the action is session-only.
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearEntity(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        clearEntity(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onEntityUnload(EntityLeaveLevelEvent event) {
        clearEntity(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    private record Key(UUID source, UUID target, String skill, String effect) {
    }

    public record Entry(long expiresAt, float value) {
    }

    private record ScheduledAction(UUID source, long executeAt, Runnable action) {
    }
}
