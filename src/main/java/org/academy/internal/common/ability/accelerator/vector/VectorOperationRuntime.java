package org.academy.internal.common.ability.accelerator.vector;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.vector.*;
import org.academy.api.common.registries.AcademyRegistrationsReadyEvent;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.AbilityExecutionHandle;
import org.academy.api.server.ability.vector.*;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileRedirects;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;

import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded server-instance state; no class analysis, CP settlement or network trust on this path. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class VectorOperationRuntime {
    private static final int MAX_OBSERVATIONS = 8192;
    private static final int MAX_PLANS = 2048;
    private static final int MAX_LEASES = 1024;
    private static final int PLAN_LIFETIME = 20;
    private static final String PROVIDER_CONTROLLED = "academy_vector_provider_controlled";
    private static final List<ExternalEffectProvider> PROVIDERS = new ArrayList<>(
            List.of(new StandardProjectileEffectProvider()));
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    private static boolean frozen;

    private VectorOperationRuntime() {}

    public static synchronized void register(ExternalEffectProvider provider) {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(provider.id());
        if (frozen) throw new IllegalStateException("Vector effect providers are frozen");
        if (PROVIDERS.stream().anyMatch(existing -> existing.id().equals(provider.id()))) {
            throw new IllegalArgumentException("Duplicate vector effect provider " + provider.id());
        }
        PROVIDERS.add(provider);
        PROVIDERS.sort(Comparator.comparingInt(ExternalEffectProvider::priority).reversed()
                .thenComparing(providerEntry -> providerEntry.id().toString()));
    }

    @SubscribeEvent
    public static synchronized void onReady(AcademyRegistrationsReadyEvent event) { frozen = true; }

    private static ServerLevel requireServer(Projectile projectile) {
        Objects.requireNonNull(projectile);
        if (!(projectile.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("Vector operations require a server level");
        }
        requireThread(level.getServer());
        return level;
    }

    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Vector operations require the server thread");
    }

    private static State state(MinecraftServer server) {
        requireThread(server);
        return SERVERS.computeIfAbsent(server, ignored -> new State());
    }

    public static EffectDescriptor describe(Projectile projectile) {
        requireServer(projectile);
        return select(projectile).descriptor;
    }

    private static Selection select(Projectile projectile) {
        Selection selected = null;
        for (var provider : PROVIDERS) {
            if (selected != null && provider.priority() < selected.provider.priority()) break;
            try {
                var descriptor = provider.describe(projectile).orElse(null);
                if (descriptor == null) continue;
                if (!descriptor.providerId().equals(provider.id())) {
                    return unavailable(projectile, "provider_identity_mismatch");
                }
                if (selected != null) return unavailable(projectile, "ambiguous_providers");
                selected = new Selection(provider, descriptor);
            } catch (RuntimeException failure) {
                return unavailable(projectile, "provider_description_failed:" + provider.id());
            }
        }
        return selected == null ? unavailable(projectile, "no_provider") : selected;
    }

    private static Selection unavailable(Projectile projectile, String reason) {
        return new Selection(null, new EffectDescriptor(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()),
                StandardProjectileEffectProvider.ID, Set.of(EffectOperation.OBSERVE),
                EffectFidelity.OBSERVATION_ONLY, reason));
    }

    public static EffectObservation observe(Projectile projectile) {
        var level = requireServer(projectile);
        if (projectile.isRemoved() || level.getEntity(projectile.getUUID()) != projectile) {
            throw new IllegalArgumentException("Only loaded projectiles can be observed");
        }
        var state = state(level.getServer());
        var observed = state.observed.get(projectile.getUUID());
        if (observed == null || observed.reference.get() != projectile) {
            observed = new Observed(new WeakReference<>(projectile), ++state.generation);
            state.observed.put(projectile.getUUID(), observed);
            trim(state.observed, MAX_OBSERVATIONS);
        }
        var handle = new EffectHandle(state.session, level.dimension(), projectile.getUUID(), observed.generation);
        return new EffectObservation(handle, describe(projectile), level.getGameTime(), projectile.position(),
                projectile.getDeltaMovement(), Optional.ofNullable(projectile.getOwner()).map(Entity::getUUID));
    }

    public static VectorOperationPreparation prepare(AbilityActorContext actor, VectorOperationAuthority authority,
                                                      EffectHandle effect, VectorOperationRequest request) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(authority);
        Objects.requireNonNull(effect);
        Objects.requireNonNull(request);
        var level = actor.level();
        var state = state(level.getServer());
        prunePlans(state, level.getServer().getTickCount());
        var projectile = resolve(level, state, effect);
        if (projectile == null) return rejectedPreparation("stale_effect");
        var selected = select(projectile);
        if (selected.provider == null || !selected.descriptor.supports(request.operation())) {
            return rejectedPreparation(selected.descriptor.detail());
        }
        var reason = admission(actor, authority, projectile, request);
        if (!reason.isEmpty()) return rejectedPreparation(reason);
        if (unavailableClaim(state, actor, projectile)) {
            return rejectedPreparation("effect_already_claimed");
        }
        if (state.claims.size() >= MAX_LEASES || state.plans.size() >= MAX_PLANS) {
            return rejectedPreparation("operation_budget_exhausted");
        }
        long bindingGeneration;
        try {
            reason = selected.provider.rejectionReason(actor, projectile, request);
            if (reason == null || !reason.isEmpty()) return rejectedPreparation(reason == null ? "invalid_provider_result" : reason);
            bindingGeneration = selected.provider.bindingGeneration(projectile);
        } catch (RuntimeException failure) {
            return rejectedPreparation("provider_preflight_failed");
        }
        var key = new RequestKey(actor.subject().getUUID(), request.requestId());
        for (var pending : state.plans.values()) {
            if (pending.key.equals(key)) {
                return pending.effect.equals(effect) && pending.request.equals(request)
                        ? new VectorOperationPreparation(VectorOperationResult.Status.PREPARED, "existing_plan", Optional.of(pending.plan))
                        : rejectedPreparation("request_id_conflict");
            }
        }
        var plan = new VectorOperationPlan(state.session, UUID.randomUUID());
        state.plans.put(plan.token(), new Pending(plan, key, actor, authority, effect, request,
                selected.provider, bindingGeneration, level.getServer().getTickCount() + PLAN_LIFETIME));
        return new VectorOperationPreparation(VectorOperationResult.Status.PREPARED, "prepared", Optional.of(plan));
    }

    public static VectorOperationResult apply(AbilityActorContext caller, VectorOperationPlan plan) {
        Objects.requireNonNull(caller);
        Objects.requireNonNull(plan);
        var server = caller.level().getServer();
        var state = state(server);
        var pending = state.plans.get(plan.token());
        if (!state.session.equals(plan.serverSession()) || pending == null
                || pending.actor.subject() != caller.subject() || pending.actor.skill() != caller.skill()) {
            return result(VectorOperationResult.Status.STALE, "unknown_plan");
        }
        // Plans retain the original effect parameters/account; a freshly built caller context
        // proves actor/skill identity but cannot replace those parameters after preparation.
        var actor = pending.actor;
        if (pending.result != null) {
            return pending.result.applied()
                    ? new VectorOperationResult(VectorOperationResult.Status.ALREADY_APPLIED, "request_already_applied", Optional.empty())
                    : pending.result;
        }
        if (server.getTickCount() > pending.expires) {
            state.plans.remove(plan.token());
            return result(VectorOperationResult.Status.STALE, "expired_plan");
        }
        var projectile = resolve(actor.level(), state, pending.effect);
        if (projectile == null) return finish(pending, VectorOperationResult.Status.STALE, "stale_effect");
        var reason = admission(actor, pending.authority, projectile, pending.request);
        if (!reason.isEmpty()) return finish(pending, VectorOperationResult.Status.REJECTED, reason);
        var selection = select(projectile);
        if (selection.provider != pending.provider || !selection.descriptor.supports(pending.request.operation())) {
            return finish(pending, VectorOperationResult.Status.STALE, "provider_binding_changed");
        }
        if (unavailableClaim(state, actor, projectile)) {
            return finish(pending, VectorOperationResult.Status.REJECTED, "effect_already_claimed");
        }
        if (state.claims.size() >= MAX_LEASES) return finish(pending, VectorOperationResult.Status.REJECTED, "operation_budget_exhausted");
        try {
            if (pending.provider.bindingGeneration(projectile) != pending.bindingGeneration) {
                return finish(pending, VectorOperationResult.Status.STALE, "provider_binding_changed");
            }
            reason = pending.provider.rejectionReason(actor, projectile, pending.request);
            if (reason == null || !reason.isEmpty()) {
                return finish(pending, VectorOperationResult.Status.REJECTED, reason == null ? "invalid_provider_result" : reason);
            }
        } catch (RuntimeException failure) {
            return finish(pending, VectorOperationResult.Status.FAILED, "provider_preflight_failed");
        }

        var claim = new Claim(pending, new WeakReference<>(projectile));
        state.claims.put(projectile.getUUID(), claim);
        // Publish the claim before calling foreign code; recursive defense callbacks see one owner.
        try {
            VectorProjectileRedirects.mark(projectile, actor.subject(), VectorRedirectKind.REFLECTION);
            projectile.getPersistentData().putBoolean(PROVIDER_CONTROLLED, true);
            projectile.setOwner(actor.subject());
            projectile.needsSync = true;
            projectile.syncPosition = true;
            projectile.hurtMarked = true;
            var delegate = Objects.requireNonNull(pending.provider.start(actor, projectile, pending.request), "Provider returned no handle");
            claim.lease = new VectorControlLease(server.getTickCount(), pending.request.durationTicks(),
                    () -> { requireThread(server); return server.getTickCount(); },
                    () -> resolve(actor.level(), state, pending.effect) == projectile
                            && admission(actor, pending.authority, projectile, pending.request).isEmpty()
                            && pending.provider.bindingGeneration(projectile) == pending.bindingGeneration
                            && "".equals(pending.provider.rejectionReason(actor, projectile, pending.request))
                            && projectile.getOwner() == actor.subject(),
                    delegate, () -> state.claims.remove(projectile.getUUID(), claim));
            return pending.result = new VectorOperationResult(VectorOperationResult.Status.APPLIED, "applied", Optional.of(claim.lease));
        } catch (RuntimeException failure) {
            state.claims.remove(projectile.getUUID(), claim);
            AcademyCraft.LOGGER.warn("Vector provider {} failed after claiming {}", pending.provider.id(), projectile.getUUID(), failure);
            // Owner/foreign side effects may already have changed. Keep the defense marker and report partial failure.
            return finish(pending, VectorOperationResult.Status.PARTIAL_FAILURE, "provider_start_failed");
        }
    }

    private static String admission(AbilityActorContext actor, VectorOperationAuthority authority,
                                    Projectile projectile, VectorOperationRequest request) {
        var subject = actor.subject();
        if (!subject.isAlive() || subject.isRemoved() || subject.isSpectator()
                || subject instanceof ServerPlayer player && player.hasDisconnected()) return "actor_unavailable";
        if (projectile.isRemoved() || projectile.level() != actor.level()
                || actor.level().getEntity(subject.getUUID()) != subject) return "world_or_instance_changed";
        if (!Double.isFinite(projectile.position().lengthSqr())
                || subject.distanceToSqr(projectile) > request.maximumRange() * request.maximumRange()) return "out_of_range";
        try {
            return authority.mayOperate(actor, projectile, request) ? "" : "authority_denied";
        } catch (RuntimeException failure) {
            return "authority_failed";
        }
    }

    /** Shared by existing collision guards. It never turns a rejected native hit into an accepted one. */
    public static boolean blocksHit(Projectile projectile, Entity target) {
        if (!(projectile.level() instanceof ServerLevel level)) return false;
        requireThread(level.getServer());
        var state = SERVERS.get(level.getServer());
        var denied = state == null ? null : state.deniedImpacts.get(projectile.getUUID());
        if (denied != null && denied.tick == level.getServer().getTickCount()
                && denied.reference.get() == projectile) return true;
        var claim = state == null ? null : state.claims.get(projectile.getUUID());
        if (claim == null || claim.reference.get() != projectile) return false;
        var pending = claim.pending;
        var actor = pending.actor.subject();
        if (!admission(pending.actor, pending.authority, projectile, pending.request).isEmpty()) {
            // A native impact may split into several damage calls. Releasing a revoked lease
            // must not admit the second part of that same tick's impact.
            state.deniedImpacts.put(projectile.getUUID(),
                    new DeniedImpact(new WeakReference<>(projectile), level.getServer().getTickCount()));
            trim(state.deniedImpacts, MAX_LEASES);
            if (claim.lease != null) claim.lease.stop();
            return true;
        }
        if (actor == target || TeamRelations.areAllied(actor, target) || TeamRelations.areAllied(target, actor)) return true;
        if (target instanceof LivingEntity living && actor instanceof ServerPlayer player
                && FriendlyFireSetting.shouldPrevent(player, living)) return true;
        try {
            return !pending.authority.mayHit(pending.actor, projectile, target);
        } catch (RuntimeException failure) {
            return true;
        }
    }

    public static boolean mayAffect(Projectile projectile, Entity target) {
        requireServer(projectile);
        Objects.requireNonNull(target);
        return projectile.level() == target.level() && !blocksHit(projectile, target);
    }

    /** Provider bindings own guidance; legacy member-name guesses must not overwrite their state. */
    public static boolean isProviderControlled(Projectile projectile) {
        return projectile.getPersistentData().getBoolean(PROVIDER_CONTROLLED).orElse(false);
    }

    private static boolean unavailableClaim(State state, AbilityActorContext actor, Projectile projectile) {
        if (state.claims.containsKey(projectile.getUUID())) return true;
        if (!VectorProjectileRedirects.isRedirected(projectile)) return false;
        // The legacy defense marker stays permanent for this flight. A completed provider lease
        // does not permanently lock its owner's effect: a fresh authorized request may steer it again.
        return !isProviderControlled(projectile) || projectile.getOwner() != actor.subject()
                || !actor.subject().getUUID().equals(VectorProjectileRedirects.get(projectile).redirectorId());
    }

    private static Projectile resolve(ServerLevel level, State state, EffectHandle handle) {
        if (!handle.serverSession().equals(state.session) || !handle.dimension().equals(level.dimension())) return null;
        var observed = state.observed.get(handle.entityId());
        var entity = observed == null ? null : observed.reference.get();
        return entity != null && observed.generation == handle.generation() && !entity.isRemoved()
                && entity.level() == level && level.getEntity(handle.entityId()) == entity ? entity : null;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Pre event) {
        var server = event.getServer();
        var state = SERVERS.get(server);
        if (state == null) return;
        state.deniedImpacts.values().removeIf(denied -> denied.tick != server.getTickCount());
        for (var claim : List.copyOf(state.claims.values())) {
            if (claim.lease != null) claim.lease.tick();
        }
        prunePlans(state, server.getTickCount());
        if (server.getTickCount() % 20 != 0) return;
        state.observed.values().removeIf(observed -> {
            var projectile = observed.reference.get();
            return projectile == null || projectile.isRemoved()
                    || !(projectile.level() instanceof ServerLevel level)
                    || level.getEntity(projectile.getUUID()) != projectile;
        });
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        var state = SERVERS.remove(event.getServer());
        if (state == null) return;
        for (var claim : List.copyOf(state.claims.values())) {
            if (claim.lease != null) claim.lease.stop();
        }
        state.plans.clear();
        state.observed.clear();
    }

    private static void prunePlans(State state, long now) {
        state.plans.values().removeIf(pending -> pending.expires < now);
    }

    private static <K, V> void trim(LinkedHashMap<K, V> map, int maximum) {
        while (map.size() > maximum) map.remove(map.firstEntry().getKey());
    }

    private static VectorOperationPreparation rejectedPreparation(String reason) {
        return new VectorOperationPreparation(VectorOperationResult.Status.REJECTED, reason, Optional.empty());
    }

    private static VectorOperationResult result(VectorOperationResult.Status status, String reason) {
        return new VectorOperationResult(status, reason, Optional.empty());
    }

    private static VectorOperationResult finish(Pending pending, VectorOperationResult.Status status, String reason) {
        return pending.result = result(status, reason);
    }

    private static final class State {
        final UUID session = UUID.randomUUID();
        long generation;
        final LinkedHashMap<UUID, Observed> observed = new LinkedHashMap<>();
        final LinkedHashMap<UUID, Pending> plans = new LinkedHashMap<>();
        final Map<UUID, Claim> claims = new HashMap<>();
        final LinkedHashMap<UUID, DeniedImpact> deniedImpacts = new LinkedHashMap<>();
    }

    private record Observed(WeakReference<Projectile> reference, long generation) {}
    private record DeniedImpact(WeakReference<Projectile> reference, long tick) {}
    private record Selection(ExternalEffectProvider provider, EffectDescriptor descriptor) {}
    private record RequestKey(UUID actorId, UUID requestId) {}

    private static final class Pending {
        final VectorOperationPlan plan;
        final RequestKey key;
        final AbilityActorContext actor;
        final VectorOperationAuthority authority;
        final EffectHandle effect;
        final VectorOperationRequest request;
        final ExternalEffectProvider provider;
        final long bindingGeneration;
        final long expires;
        VectorOperationResult result;

        Pending(VectorOperationPlan plan, RequestKey key, AbilityActorContext actor, VectorOperationAuthority authority,
                EffectHandle effect, VectorOperationRequest request, ExternalEffectProvider provider,
                long bindingGeneration, long expires) {
            this.plan = plan;
            this.key = key;
            this.actor = actor;
            this.authority = authority;
            this.effect = effect;
            this.request = request;
            this.provider = provider;
            this.bindingGeneration = bindingGeneration;
            this.expires = expires;
        }
    }

    private static final class Claim {
        final Pending pending;
        final WeakReference<Projectile> reference;
        VectorControlLease lease;
        Claim(Pending pending, WeakReference<Projectile> reference) {
            this.pending = pending;
            this.reference = reference;
        }
    }
}
