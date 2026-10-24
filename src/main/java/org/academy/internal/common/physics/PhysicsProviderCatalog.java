package org.academy.internal.common.physics;

import org.academy.api.common.physics.PhysicsProvider;
import org.academy.api.common.physics.PhysicsResult;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Injectable, immutable discovery catalogue. Discovery never selects or opens a provider. */
public final class PhysicsProviderCatalog {
    public static final String ACADEMY_RAPIER = "academy:rapier_existing";
    private final Map<String, PhysicsProvider> providers;

    public PhysicsProviderCatalog(Collection<? extends PhysicsProvider> providers) {
        var entries = new LinkedHashMap<String, PhysicsProvider>();
        for (var provider : providers) {
            Objects.requireNonNull(provider, "provider");
            var id = provider.descriptor().identity().id();
            if (entries.putIfAbsent(id, provider) != null) {
                throw new IllegalArgumentException("Duplicate physics provider: " + id);
            }
        }
        this.providers = Map.copyOf(entries);
    }

    /** Bootstrap may register this catalogue without touching existing physics routing. */
    public static PhysicsProviderCatalog defaults() {
        return new PhysicsProviderCatalog(List.of(new ExistingRapierDescription(), new ScaffoldMpsPhysicsProvider()));
    }

    public List<PhysicsProvider.Descriptor> descriptors() {
        return providers.values().stream().map(PhysicsProvider::descriptor)
                .sorted(java.util.Comparator.comparing(value -> value.identity().id())).toList();
    }

    public Optional<PhysicsProvider> find(String id) {
        return Optional.ofNullable(providers.get(Objects.requireNonNull(id, "id")));
    }

    private static final class ExistingRapierDescription implements PhysicsProvider {
        private static final Descriptor DESCRIPTOR = new Descriptor(
                new Identity(ACADEMY_RAPIER, "rapier3d-f64", "0.36.1", "crates.io:rapier3d-f64:0.36.1", 2),
                Readiness.LIVE_EXTERNAL_UNWIRED, Set.of(),
                Set.of(Capability.RIGID_BODY, Capability.COVERAGE_PAUSE,
                        Capability.ATOMIC_COMMAND_BATCH, Capability.BATCH_SNAPSHOT, Capability.CONTACT_EVENTS),
                "existing_native_path_unchanged;provider_adapter_unimplemented");

        @Override public Descriptor descriptor() { return DESCRIPTOR; }

        @Override public PhysicsResult<World> openWorld(WorldRequest request) {
            Objects.requireNonNull(request, "request");
            // [TODO: Agent B][UPG-ENGINE-02] 将现有 Rapier 接入统一世界契约并验收生命周期；当前描述不冒充已接线。
            return PhysicsResult.rejected(PhysicsResult.Status.UNSUPPORTED, "rapier_provider_adapter_unimplemented");
        }
    }
}
