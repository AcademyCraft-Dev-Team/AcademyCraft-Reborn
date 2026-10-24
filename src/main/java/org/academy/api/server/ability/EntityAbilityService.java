package org.academy.api.server.ability;

import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.common.ability.AbilityActorSnapshot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.academy.api.common.ability.AbilityActorProfile;
import org.academy.api.common.ability.AbilityActorState;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.api.server.vanilla.MinecraftServerContext;
import java.util.*;

/** Server-thread API for non-player abilities. Player entry points keep their existing cost/event contract. */
public final class EntityAbilityService {
    private static final Map<Identifier, AbilitySkillExecutor> EXECUTORS = new HashMap<>();
    private static final Map<EntityType<?>, AbilityActorProvider> PROVIDERS = new HashMap<>();
    private static final Map<LivingEntity, Map<Identifier, Running>> RUNNING = new IdentityHashMap<>();
    private record Running(AbilityExecutionHandle handle, String reservation, AbilityActorState state, boolean permanent) {}
    private static final AbilityActorProvider ATTACHED = new AbilityActorProvider() {
        public AbilityActorState state(LivingEntity actor) {
            return actor.hasData(AttachmentTypes.ABILITY_ACTOR)
                    ? actor.getData(AttachmentTypes.ABILITY_ACTOR).orElse(null) : null;
        }
        public void setState(LivingEntity actor, AbilityActorState state) {
            actor.setData(AttachmentTypes.ABILITY_ACTOR, Optional.of(state));
        }
    };
    private EntityAbilityService() {}
    public static void register(AbilitySkillExecutor executor) { EXECUTORS.put(executor.skill().getKey(), executor); }
    public static void registerProvider(EntityType<?> type, AbilityActorProvider provider) { PROVIDERS.put(type, provider); }
    private static AbilityActorProvider provider(LivingEntity actor) { return PROVIDERS.getOrDefault(actor.getType(), ATTACHED); }
    private static void requireServer(LivingEntity actor) {
        if (!(actor.level() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Entity abilities require the server thread");
    }
    public static Optional<AbilityActorState> inspect(LivingEntity actor) {
        requireServer(actor);
        return actor instanceof Player ? Optional.empty() : Optional.ofNullable(provider(actor).state(actor));
    }
    public static void assign(LivingEntity actor, AbilityActorProfile profile) {
        requireServer(actor);
        if (actor instanceof Player) throw new IllegalArgumentException("Use the player ability system for players");
        stopAll(actor);
        provider(actor).setState(actor, new AbilityActorState(profile));
        provider(actor).changed(actor);
    }
    public static AbilityActorSnapshot snapshot(LivingEntity actor) {
        var state = inspect(actor).orElse(null);
        if (state == null) return AbilityActorSnapshot.EMPTY;
        return new AbilityActorSnapshot(state.profile().level(), state.cp().current(),
                state.cp().capacity(), canAct(actor), state.profile().skills().stream().map(id ->
                new AbilityActorSnapshot.SkillStatus(id, state.enabled(id),
                        Math.max(state.cooldown(id), state.globalCooldown()), isRunning(actor, id))).toList());
    }
    public static AbilityConfig config(LivingEntity actor) {
        return ((MinecraftServerContext) ((ServerLevel) actor.level()).getServer()).getAcademyCraftServer().getAbilityConfig();
    }
    public static boolean canAct(LivingEntity actor) { return !actor.isRemoved() && provider(actor).canAct(actor); }
    public static boolean mayTarget(LivingEntity actor, LivingEntity target) {
        return actor != target && target.isAlive() && !target.isSpectator() && actor.level() == target.level()
                && !(target instanceof Player player && player.isCreative())
                && !TeamRelations.areAllied(actor, target) && !TeamRelations.areAllied(target, actor)
                && provider(actor).mayTarget(actor, target);
    }
    private static boolean permitted(LivingEntity actor, LivingEntity target, AbilityCastRequest request) {
        return mayTarget(actor, target) && (request.controller() == null
                || request.controller().isAlive() && request.controller().level() == actor.level()
                && !FriendlyFireSetting.shouldPrevent(request.controller(), target));
    }
    public static boolean isRunning(LivingEntity actor, Identifier skill) {
        return RUNNING.getOrDefault(actor, Map.of()).containsKey(skill);
    }
    public static AbilityCastResult checkCast(LivingEntity actor, AbilityCastRequest request) {
        var state = inspect(actor).orElse(null);
        if (state == null || !state.knows(request.skill())) return AbilityCastResult.NO_ABILITY;
        var executor = EXECUTORS.get(request.skill());
        if (executor == null) return AbilityCastResult.UNSUPPORTED;
        if (!state.profile().category().equals(executor.skill().getCategory().getKey())) return AbilityCastResult.NO_ABILITY;
        if (!state.enabled(request.skill()) || !SkillTuning.isSkillEnabled(config(actor), executor.skill()))
            return AbilityCastResult.DISABLED;
        if (state.profile().level() < executor.minimumLevel()) return AbilityCastResult.LEVEL_TOO_LOW;
        if (!canAct(actor)) return AbilityCastResult.INACTIVE;
        if (request.controller() != null && (!request.controller().isAlive()
                || request.controller().hasDisconnected() || request.controller().level() != actor.level())) return AbilityCastResult.INACTIVE;
        if (isRunning(actor, request.skill())) return AbilityCastResult.BUSY;
        if (state.cooldown(request.skill()) > 0 || !executor.permanent() && state.globalCooldown() > 0)
            return AbilityCastResult.COOLDOWN;
        if (executor.requiresTarget()) {
            var target = request.target();
            double range = executor.range() * SkillTuning.rangeMultiplier(actor.level(), executor.skill());
            if (target == null || !permitted(actor, target, request) || actor.distanceToSqr(target) > range * range)
                return AbilityCastResult.INVALID_TARGET;
            if (!StructureQueries.clearSightline((ServerLevel) actor.level(), actor.getEyePosition(), target.getBoundingBox().getCenter()))
                return AbilityCastResult.BLOCKED;
        }
        return state.cp().current() < cost(actor, executor) ? AbilityCastResult.INSUFFICIENT_CP : AbilityCastResult.ACCEPTED;
    }
    private static double cost(LivingEntity actor, AbilitySkillExecutor executor) {
        return (executor.permanent() ? executor.skill().getMaintenanceCost(1) : executor.skill().getCpCost(1))
                * SkillTuning.costMultiplier(config(actor), executor.skill());
    }
    public static AbilityCastResult tryCast(LivingEntity actor, AbilityCastRequest request) {
        var result = checkCast(actor, request);
        if (result != AbilityCastResult.ACCEPTED) return result;
        if (NeoForge.EVENT_BUS.post(new EntityAbilityCastEvent(actor, request)).isCanceled())
            return AbilityCastResult.CANCELED;
        result = checkCast(actor, request);
        if (result != AbilityCastResult.ACCEPTED) return result;
        var executor = EXECUTORS.get(request.skill());
        var state = inspect(actor).orElseThrow();
        int points = SkillTuning.iterationTicks(config(actor), executor.skill(), executor.skill().getIterationTicks(1));
        int duration = Math.max(1, points > 0 ? points : (int) Math.ceil(cost(actor, executor) * .5)) * 20;
        var reservation = state.cp().reserve(cost(actor, executor), executor.permanent() ? -1 : duration);
        if (reservation == null) return AbilityCastResult.INSUFFICIENT_CP;
        var context = new AbilityActorContext(actor, executor.skill(), state.profile().abilityPower(),
                state.profile().damageMultiplier(), state.milestone(request.skill()), state.cp());
        try {
            var handle = executor.start(context, request, target -> permitted(actor, target, request));
            if (handle != null) RUNNING.computeIfAbsent(actor, ignored -> new HashMap<>()).put(request.skill(),
                    new Running(handle, reservation, state, executor.permanent()));
            else if (executor.permanent()) state.cp().release(reservation);
            if (!executor.permanent()) state.cast(request.skill(), executor.cooldownTicks());
            provider(actor).changed(actor);
            return AbilityCastResult.ACCEPTED;
        } catch (RuntimeException failure) {
            state.cp().release(reservation);
            provider(actor).changed(actor);
            throw failure;
        }
    }
    public static void setEnabled(LivingEntity actor, Identifier skill, boolean enabled) {
        var state = inspect(actor).orElseThrow();
        state.setEnabled(skill, enabled);
        if (!enabled) stop(actor, skill);
        provider(actor).changed(actor);
    }
    public static void stop(LivingEntity actor, Identifier skill) {
        requireServer(actor);
        var sessions = RUNNING.get(actor);
        var running = sessions == null ? null : sessions.remove(skill);
        if (running != null) {
            running.handle().stop();
            if (running.permanent()) running.state().cp().release(running.reservation());
            provider(actor).changed(actor);
        }
        if (sessions != null && sessions.isEmpty()) RUNNING.remove(actor);
    }
    public static void stopAll(LivingEntity actor) {
        for (var skill : List.copyOf(RUNNING.getOrDefault(actor, Map.of()).keySet())) stop(actor, skill);
    }
    /** Called once per loaded entity tick; unloaded cooldowns and occupations remain frozen. */
    public static void tick(LivingEntity actor) {
        var state = inspect(actor).orElse(null);
        if (state == null) return;
        boolean active = canAct(actor);
        if (state.tick(active)) provider(actor).changed(actor);
        for (var skill : List.copyOf(RUNNING.getOrDefault(actor, Map.of()).keySet())) {
            var running = RUNNING.getOrDefault(actor, Map.of()).get(skill);
            if (running != null && (!active || !state.enabled(skill)
                    || !SkillTuning.isSkillEnabled(config(actor), EXECUTORS.get(skill).skill()) || !running.handle().tick())) stop(actor, skill);
        }
    }
}
