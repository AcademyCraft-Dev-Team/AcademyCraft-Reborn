package org.academy.internal.common.structure.upgrade;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.upgrade.StructureCapabilities;
import org.academy.api.common.structure.upgrade.StructureUpgradeServices;
import org.academy.internal.common.physics.PhysicsProviderCatalog;

import java.util.EnumMap;

/** Discovery only. No tick listeners, world writes, node actions or native library loading. */
public final class StructureUpgradeBootstrap {
    public static final boolean GAMEPLAY_ENABLED = false;
    public static final StructureUpgradeServices DRAFT = createScaffold();
    public static final PhysicsProviderCatalog PROVIDERS = PhysicsProviderCatalog.defaults();

    private StructureUpgradeBootstrap() {}

    public static StructureUpgradeServices createScaffold() {
        var controls = new ScaffoldStructureControls();
        var lifecycle = new ScaffoldStructureLifecycle();
        var combat = new ScaffoldStructureCombat();
        var entries = new EnumMap<StructureCapabilities.Capability, StructureCapabilities.Support>(
                StructureCapabilities.Capability.class);
        for (var capability : StructureCapabilities.Capability.values()) {
            entries.put(capability, new StructureCapabilities.Support(
                    StructureCapabilities.Readiness.SCAFFOLD, false, "implementation_unavailable"));
        }
        return new StructureUpgradeServices(new StructureCapabilities(entries), controls, controls,
                lifecycle, lifecycle, combat, combat, combat);
    }

    public static void register(IEventBus bus) {
        bus.addListener(StructureUpgradeBootstrap::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> AcademyCraft.LOGGER.info(
                "Structure upgrade: SCAFFOLD_REGISTERED; gameplay disabled, {} explicit capabilities, {} provider descriptors",
                DRAFT.capabilities().entries().size(), PROVIDERS.descriptors().size()));
    }
}
