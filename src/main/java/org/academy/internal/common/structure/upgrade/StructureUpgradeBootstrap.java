package org.academy.internal.common.structure.upgrade;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.upgrade.StructureCapabilities;
import org.academy.api.common.structure.upgrade.StructureUpgradeServices;
import org.academy.internal.common.physics.PhysicsProviderCatalog;

import java.util.EnumMap;

/** Draft services stay disabled; live extraction and orientation use the existing solvers. */
public final class StructureUpgradeBootstrap {
    public static final boolean DRAFT_GAMEPLAY_ENABLED = false;
    public static final boolean ORIENTATION_ENABLED = true;
    public static final boolean EXTRACTION_ENABLED =
            org.academy.api.common.structure.upgrade.StructureCaptureExtractions.enabled();
    public static final StructureUpgradeServices DRAFT = createScaffold();
    public static final StructureUpgradeServices LIVE = createLive();
    public static final PhysicsProviderCatalog PROVIDERS = PhysicsProviderCatalog.defaults();

    private StructureUpgradeBootstrap() {}

    private static StructureUpgradeServices createLive() {
        var entries = new EnumMap<StructureCapabilities.Capability, StructureCapabilities.Support>(DRAFT.capabilities().entries());
        entries.put(StructureCapabilities.Capability.ORIENTATION, new StructureCapabilities.Support(
                StructureCapabilities.Readiness.EXPERIMENTAL, true, "sublevel_sable_bounded_angular_control"));
        entries.put(StructureCapabilities.Capability.EXTRACTION, new StructureCapabilities.Support(
                StructureCapabilities.Readiness.EXPERIMENTAL, EXTRACTION_ENABLED, "sublevel_sable_bounded_extraction"));
        entries.put(StructureCapabilities.Capability.PROVENANCE, new StructureCapabilities.Support(
                StructureCapabilities.Readiness.EXPERIMENTAL, true, "durable_skill_capture_provenance"));
        entries.put(StructureCapabilities.Capability.GRID_RESTORATION, new StructureCapabilities.Support(
                StructureCapabilities.Readiness.EXPERIMENTAL, true, "marked_rest_server_policy"));
        return new StructureUpgradeServices(new StructureCapabilities(entries),
                org.academy.api.common.structure.upgrade.StructureExtractions.service(),
                org.academy.api.common.structure.upgrade.StructureOrientations.service(),
                org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance.INSTANCE,
                org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration.INSTANCE,
                DRAFT.damage(), DRAFT.impacts(), DRAFT.fracture());
    }

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
                "Structure upgrade: SCAFFOLD_REGISTERED; draft services disabled; live orientation EXPERIMENTAL; "
                        + "live extraction EXPERIMENTAL gameplay={}; {} capabilities, "
                        + "{} provider descriptors",
                EXTRACTION_ENABLED, DRAFT.capabilities().entries().size(), PROVIDERS.descriptors().size()));
    }
}
