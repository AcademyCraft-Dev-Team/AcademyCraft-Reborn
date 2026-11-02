package org.academy.internal.common.ability.external;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.AbilityExecutionHandle;
import org.academy.api.server.ability.external.*;
import org.academy.api.server.ability.spell.SpellCastGeometry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight runtime only: all third-party discovery and patch compilation belongs to providers. */
public final class ExternalAbilityRuntime {
    private static final Map<Identifier, ExternalAbilityProvider> PROVIDERS = new ConcurrentHashMap<>();

    private ExternalAbilityRuntime() { }

    public static void register(ExternalAbilityProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.putIfAbsent(Objects.requireNonNull(provider.id()), provider) != null)
            throw new IllegalStateException("Duplicate external ability provider: " + provider.id());
    }

    public static Optional<ExternalAbilityDescriptor> describe(AbilityActorContext actor,
                                                              ExternalAbilityRequest request) {
        thread(actor.level());
        var provider = PROVIDERS.get(request.provider());
        return provider == null ? Optional.empty() : provider.describe(actor, request.abilityId());
    }

    public static ExternalAbilityPlan prepare(AbilityActorContext actor, ExternalAbilityAuthority authority,
                                              ExternalAbilityRequest request) {
        Objects.requireNonNull(authority, "authority");
        var level = actor.level();
        thread(level);
        var provider = PROVIDERS.get(request.provider());
        if (provider == null) throw new IllegalArgumentException("External ability provider unavailable");
        var descriptor = provider.describe(actor, request.abilityId())
                .orElseThrow(() -> new IllegalArgumentException("Ability is not prepared or not learned"));
        var problem = admission(actor, level, authority, request, descriptor, provider, true);
        if (!problem.isEmpty()) throw new IllegalArgumentException(problem);
        var prepared = Objects.requireNonNull(provider.prepare(actor, request), "Provider returned no plan");
        if (prepared.generation() != descriptor.bindingGeneration())
            throw new IllegalStateException("Ability binding changed during preparation");
        return new Plan(actor, level, authority, request, descriptor, provider, prepared);
    }

    private static String admission(AbilityActorContext actor, ServerLevel level, ExternalAbilityAuthority authority,
                                    ExternalAbilityRequest request, ExternalAbilityDescriptor descriptor,
                                    ExternalAbilityProvider provider, boolean starting) {
        thread(level);
        if (actor.subject().level() != level || !actor.subject().isAlive() || actor.subject().isRemoved())
            return "Ability actor unavailable or changed dimension";
        if (!descriptor.id().equals(request.abilityId())) return "Ability identity mismatch";
        if (request.durationTicks() > descriptor.maximumDurationTicks()) return "Ability duration is out of range";
        if ((request.spatial().inputMask() & ~descriptor.spatialInputs()) != 0)
            return "Ability does not support the requested spatial inputs";
        var current = provider.describe(actor, request.abilityId());
        if (current.isEmpty() || current.get().bindingGeneration() != descriptor.bindingGeneration())
            return "Ability binding is stale";
        var reason = Objects.requireNonNull(authority.rejectionReason(actor, request), "Missing admission result");
        if (!reason.isEmpty()) return reason;
        if (starting) SpellCastGeometry.resolve(actor.subject(), request.spatial());
        else {
            // Initial aiming constraints have already been validated. A homing target may move
            // away from that initial direction without invalidating the identity of the cast.
            var target = request.spatial().target();
            if (target != null && (target.level() != level || !target.isAlive() || target.isRemoved()
                    || level.getEntity(target.getUUID()) != target)) return "Ability target unavailable";
        }
        return "";
    }

    private static void thread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
    }

    private static final class Plan implements ExternalAbilityPlan {
        final AbilityActorContext actor;
        final ServerLevel level;
        final ExternalAbilityAuthority authority;
        final ExternalAbilityRequest request;
        final ExternalAbilityDescriptor descriptor;
        final ExternalAbilityProvider provider;
        final ExternalAbilityProvider.Prepared prepared;
        boolean used;

        Plan(AbilityActorContext actor, ServerLevel level, ExternalAbilityAuthority authority,
             ExternalAbilityRequest request, ExternalAbilityDescriptor descriptor,
             ExternalAbilityProvider provider, ExternalAbilityProvider.Prepared prepared) {
            this.actor = actor; this.level = level; this.authority = authority; this.request = request;
            this.descriptor = descriptor; this.provider = provider; this.prepared = prepared;
        }

        @Override public ExternalAbilityDescriptor descriptor() { return descriptor; }
        @Override public ExternalAbilityRequest request() { return request; }

        @Override public ExternalAbilityExecution start() {
            thread(level);
            if (used) throw new IllegalStateException("Ability plan already consumed");
            used = true;
            var reason = admission(actor, level, authority, request, descriptor, provider, true);
            if (!reason.isEmpty()) throw new IllegalStateException(reason);
            var handle = Objects.requireNonNull(prepared.start(), "Provider returned no execution");
            return new ExternalAbilityLease(level::getGameTime, () -> thread(level),
                    () -> admission(actor, level, authority, request, descriptor, provider, false), handle, request.durationTicks());
        }
    }
}
