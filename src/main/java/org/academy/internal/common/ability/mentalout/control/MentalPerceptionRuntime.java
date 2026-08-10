package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import org.academy.api.common.entitycontrol.PerceptionDecision;
import org.academy.internal.common.ability.mentalout.MentalIntrusionManager;
import org.academy.internal.common.ability.Skills;

import java.util.*;

/**
 * Server-authoritative, source-aware perception masks.
 */
public final class MentalPerceptionRuntime {
    private static final Map<PairKey, Relation> RELATIONS = new HashMap<>();
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static final Map<UUID, Set<UUID>> BY_ENTITY = new HashMap<>();
    private static final Map<UUID, Set<UUID>> BY_CONTROLLER = new HashMap<>();

    private MentalPerceptionRuntime() {
    }

    public static Handle apply(
            ServerPlayer controller,
            LivingEntity observer,
            LivingEntity hidden,
            Identifier source,
            int priority,
            long expiresAt
    ) {
        if (controller == null || observer == null || hidden == null || source == null) {
            throw new IllegalArgumentException("Perception mask arguments must not be null");
        }
        if (observer == hidden || !observer.isAlive() || !hidden.isAlive()
                || observer.level() != hidden.level() || controller.level() != observer.level()) {
            throw new IllegalArgumentException("Perception mask entities must be alive in one level");
        }
        if (expiresAt < 0L || MentalControlRuntime.isProtectedTarget(observer)) {
            throw new IllegalArgumentException("Perception observer is protected or expiry is invalid");
        }

        var id = UUID.randomUUID();
        var key = new PairKey(observer.getUUID(), hidden.getUUID());
        var relation = RELATIONS.computeIfAbsent(key, _ -> new Relation(observer, hidden));
        var wasHidden = relation.isHidden();
        var suppressedAmbient = relation.suppressesAmbient();
        var lease = new Lease(
                id,
                controller.getUUID(),
                key,
                source,
                priority,
                expiresAt,
                relation.nextOrder++,
                Skills.SENSORY_DISTORTION.get().hasProficiencyMilestone(controller, 2)
        );
        relation.leases.put(id, lease);
        LEASES.put(id, lease);
        index(BY_CONTROLLER, controller.getUUID(), id);
        index(BY_ENTITY, observer.getUUID(), id);
        index(BY_ENTITY, hidden.getUUID(), id);
        if (observer instanceof ServerPlayer player
                && (!wasHidden || suppressedAmbient != relation.suppressesAmbient())) {
            MentalIntrusionManager.sendPerception(
                    player, hidden, true, relation.suppressesAmbient());
        }
        clearNaturalTarget(observer, hidden);
        return new Handle(id);
    }

    public static PerceptionDecision decision(LivingEntity observer, LivingEntity target) {
        if (observer == null || target == null || observer == target) return PerceptionDecision.PASS;
        if (observer instanceof Mob mob && MentalControlRuntime.getForcedTarget(mob) == target) {
            return PerceptionDecision.PASS;
        }
        var relation = RELATIONS.get(new PairKey(observer.getUUID(), target.getUUID()));
        return relation != null && relation.isHidden()
                ? PerceptionDecision.HIDDEN
                : PerceptionDecision.PASS;
    }

    public static void tick(MinecraftServer server) {
        if (LEASES.isEmpty()) return;
        var now = server.overworld().getGameTime();
        var expired = new ArrayList<UUID>();
        for (var lease : List.copyOf(LEASES.values())) {
            var relation = RELATIONS.get(lease.key);
            if (relation == null
                    || lease.expiresAt != Long.MAX_VALUE && now >= lease.expiresAt
                    || !relation.observer.isAlive()
                    || !relation.hidden.isAlive()
                    || relation.observer.isRemoved()
                    || relation.hidden.isRemoved()
                    || relation.observer.level() != relation.hidden.level()
                    || MentalControlRuntime.isProtectedTarget(relation.observer)) {
                expired.add(lease.id);
            } else {
                clearNaturalTarget(relation.observer, relation.hidden);
            }
        }
        expired.forEach(MentalPerceptionRuntime::close);
    }


    public static boolean isAffected(LivingEntity entity) {
        if (entity == null) return false;
        return RELATIONS.values().stream()
                .anyMatch(relation -> relation.observer == entity && !relation.leases.isEmpty());
    }

    public static void releaseController(UUID controllerId) {
        closeIndexed(BY_CONTROLLER, controllerId);
    }

    public static void releaseEntity(UUID entityId) {
        closeIndexed(BY_ENTITY, entityId);
    }

    public static void clear() {
        List.copyOf(LEASES.keySet()).forEach(MentalPerceptionRuntime::close);
        RELATIONS.clear();
        LEASES.clear();
        BY_ENTITY.clear();
        BY_CONTROLLER.clear();
    }

    static void close(UUID id) {
        var lease = LEASES.remove(id);
        if (lease == null) return;
        unindex(BY_CONTROLLER, lease.controllerId, id);
        unindex(BY_ENTITY, lease.key.observerId, id);
        unindex(BY_ENTITY, lease.key.hiddenId, id);
        var relation = RELATIONS.get(lease.key);
        if (relation == null) return;
        var suppressedAmbient = relation.suppressesAmbient();
        relation.leases.remove(id);
        if (!relation.isHidden()) {
            RELATIONS.remove(lease.key);
            if (relation.observer instanceof ServerPlayer player) {
                MentalIntrusionManager.sendPerception(player, relation.hidden, false, false);
            }
        } else if (suppressedAmbient != relation.suppressesAmbient()
                && relation.observer instanceof ServerPlayer player) {
            MentalIntrusionManager.sendPerception(
                    player, relation.hidden, true, relation.suppressesAmbient());
        }
    }

    private static void closeIndexed(Map<UUID, Set<UUID>> index, UUID key) {
        var ids = index.get(key);
        if (ids == null) return;
        List.copyOf(ids).forEach(MentalPerceptionRuntime::close);
    }

    private static void clearNaturalTarget(LivingEntity observer, LivingEntity hidden) {
        if (!(observer instanceof Mob mob) || MentalControlRuntime.getForcedTarget(mob) == hidden) return;
        if (mob.getTarget() == hidden) mob.setTarget(null);
        var brain = mob.getBrain();
        clearIfEqual(brain, MemoryModuleType.ATTACK_TARGET, hidden);
        clearIfEqual(brain, MemoryModuleType.NEAREST_ATTACKABLE, hidden);
        clearIfEqual(brain, MemoryModuleType.ROAR_TARGET, hidden);
        var angry = brain.getMemoryInternal(MemoryModuleType.ANGRY_AT);
        if (angry != null && angry.isPresent() && angry.get().equals(hidden.getUUID())) {
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }
        var nearest = brain.getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
        if (nearest != null && nearest.isPresent() && nearest.get() == hidden) {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
        }
        var nemesis = brain.getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
        if (nemesis != null && nemesis.isPresent() && nemesis.get() == hidden) {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
        }
        var visible = brain.getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (visible != null && visible.isPresent() && visible.get().contains(hidden)
                && mob.level() instanceof ServerLevel level) {
            var filtered = visible.get().nearbyEntities().stream()
                    .filter(entity -> entity != hidden)
                    .toList();
            brain.setMemory(
                    MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    filtered.isEmpty()
                            ? NearestVisibleLivingEntities.empty()
                            : new NearestVisibleLivingEntities(level, mob, filtered)
            );
        }
        var players = brain.getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS);
        if (players != null && players.isPresent() && players.get().contains(hidden)) {
            brain.setMemory(
                    MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS,
                    players.get().stream().filter(entity -> entity != hidden).toList()
            );
        }
    }

    private static <T> void clearIfEqual(
            Brain<?> brain,
            MemoryModuleType<T> type,
            T value
    ) {
        var memory = brain.getMemoryInternal(type);
        if (memory != null && memory.isPresent() && memory.get() == value) brain.eraseMemory(type);
    }

    private static void index(Map<UUID, Set<UUID>> index, UUID key, UUID id) {
        index.computeIfAbsent(key, _ -> new HashSet<>()).add(id);
    }

    private static void unindex(Map<UUID, Set<UUID>> index, UUID key, UUID id) {
        var values = index.get(key);
        if (values == null) return;
        values.remove(id);
        if (values.isEmpty()) index.remove(key);
    }

    public static final class Handle implements AutoCloseable {
        private final UUID id;

        private Handle(UUID id) {
            this.id = id;
        }

        public UUID id() {
            return id;
        }

        public boolean isClosed() {
            return !LEASES.containsKey(id);
        }

        @Override
        public void close() {
            MentalPerceptionRuntime.close(id);
        }
    }

    private record PairKey(UUID observerId, UUID hiddenId) {
    }

    private record Lease(
            UUID id,
            UUID controllerId,
            PairKey key,
            Identifier source,
            int priority,
            long expiresAt,
            long order,
            boolean suppressAmbient
    ) {
    }

    private static final class Relation {
        private final LivingEntity observer;
        private final LivingEntity hidden;
        private final LinkedHashMap<UUID, Lease> leases = new LinkedHashMap<>();
        private long nextOrder;

        private Relation(LivingEntity observer, LivingEntity hidden) {
            this.observer = observer;
            this.hidden = hidden;
        }

        private boolean isHidden() {
            return !leases.isEmpty();
        }

        private boolean suppressesAmbient() {
            return leases.values().stream().anyMatch(Lease::suppressAmbient);
        }
    }
}
