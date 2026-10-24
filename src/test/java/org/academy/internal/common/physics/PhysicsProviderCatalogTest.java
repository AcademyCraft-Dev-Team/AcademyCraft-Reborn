package org.academy.internal.common.physics;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.physics.PhysicsProvider;
import org.academy.api.common.physics.PhysicsResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsProviderCatalogTest {
    @Test
    void defaultDiscoveryCannotOpenEitherUnwiredProviderOrAdvertiseRuntimeCapabilities() {
        var catalog = PhysicsProviderCatalog.defaults();
        assertEquals(2, catalog.descriptors().size());
        assertTrue(catalog.find("sable").isEmpty(), "Sable owns a separate structure backend");
        for (var descriptor : catalog.descriptors()) {
            assertNotEquals(PhysicsProvider.Readiness.READY, descriptor.readiness());
            assertTrue(descriptor.available().isEmpty());
            var provider = catalog.find(descriptor.identity().id()).orElseThrow();
            var result = provider.openWorld(request());
            assertEquals(PhysicsResult.Status.UNSUPPORTED, result.status());
            assertTrue(result.value().isEmpty(), "Discovery must not hand out an executable world");
            assertFalse(result.reason().isBlank());
        }
        assertEquals(PhysicsProvider.Readiness.LIVE_EXTERNAL_UNWIRED,
                catalog.find(PhysicsProviderCatalog.ACADEMY_RAPIER).orElseThrow().descriptor().readiness());
        var mps = catalog.find(ScaffoldMpsPhysicsProvider.ID).orElseThrow().descriptor();
        assertEquals(PhysicsProvider.Readiness.SCAFFOLD_UNAVAILABLE, mps.readiness());
        assertTrue(mps.planned().containsAll(Set.of(PhysicsProvider.Capability.FLUID_SPH,
                PhysicsProvider.Capability.SOFT_XPBD, PhysicsProvider.Capability.RIGID_FLUID_COUPLING)));
    }

    @Test
    void injectionDoesNotSelectOrOpenProviderAndCannotBeChangedByCallerCollection() {
        var calls = new int[1];
        PhysicsProvider injected = new PhysicsProvider() {
            @Override public Descriptor descriptor() {
                return new Descriptor(new Identity("test:isolated", "test", "1", "test-revision", 1),
                        Readiness.SCAFFOLD_UNAVAILABLE, Set.of(), Set.of(), "test_only");
            }

            @Override public PhysicsResult<World> openWorld(WorldRequest request) {
                calls[0]++;
                return PhysicsResult.rejected(PhysicsResult.Status.NOT_READY, "test_only");
            }
        };
        var input = new ArrayList<>(List.of(injected));
        var catalog = new PhysicsProviderCatalog(input);
        input.clear();
        assertSame(injected, catalog.find("test:isolated").orElseThrow());
        assertEquals(1, catalog.descriptors().size());
        assertEquals(0, calls[0]);
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicsProviderCatalog(List.of(injected, injected)));
    }

    private static PhysicsProvider.WorldRequest request() {
        return new PhysicsProvider.WorldRequest(UUID.randomUUID(), 1, new Vec3(0, -9.81, 0),
                new PhysicsProvider.Budget(64, 4096, 32, 128, 512, 128, 2048, 512, 128, 65536),
                Set.of(PhysicsProvider.Capability.RIGID_BODY));
    }
}
