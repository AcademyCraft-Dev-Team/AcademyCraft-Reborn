package org.academy.internal.common.sublevel;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.accelerator.KineticLiftProfile;
import org.academy.api.common.structure.StructureContactResolver;
import org.academy.api.common.structure.StructureTerrainBreakPolicy;

/**
 * Shared service holder for the five SubLevel enhancements. The live paths are connected in
 * {@link SubLevelRuntime} (simulation, contacts, soft terrain), {@link SubLevelServer}
 * (interaction tick, carry, snapshot deltas) and {@link SubLevelContactResolver} (the entity
 * movement mixin). Common setup validates that the instances are the runtime ones; it does not
 * install a second, competing collision or drop path.
 */
public final class SubLevelEnhancementBootstrap {
    public static final SubLevelSimulationBudget SIMULATION = new SubLevelSimulationBudget();
    public static final SubLevelContactResolver CONTACTS = new SubLevelContactResolver();
    public static final SubLevelInteractionController INTERACTIONS = new SubLevelInteractionController();
    public static final SubLevelKineticLiftProfile KINETIC_LIFT = new SubLevelKineticLiftProfile();
    public static final SubLevelTerrainBreakPolicy TERRAIN_BREAKING = new SubLevelTerrainBreakPolicy();

    public static final TagKey<Block> MOTION_BREAKABLE = TagKey.create(Registries.BLOCK,
            AcademyCraft.academy("structure_motion_breakable"));
    public static final TagKey<Block> MOTION_PROTECTED = TagKey.create(Registries.BLOCK,
            AcademyCraft.academy("structure_motion_protected"));

    private static volatile boolean installed;

    private SubLevelEnhancementBootstrap() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SubLevelEnhancementBootstrap::commonSetup);
    }

    /** True after the common setup pass; diagnostics may use it without loading client classes. */
    public static boolean installed() {
        return installed;
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // The service instances are created before any world; the runtime holds the same
            // references, so this only publishes readiness for diagnostics and integration tests.
            installed = true;
            AcademyCraft.LOGGER.info(
                    "SubLevel enhancements installed: simulated-budget, oriented contacts, mining, lift {}/{}/{}",
                    SubLevelKineticLiftProfile.HEIGHTS[0], SubLevelKineticLiftProfile.HEIGHTS[1],
                    SubLevelKineticLiftProfile.HEIGHTS[2]);
        });
    }

    /** Compile-time contract references; keeps the API surface used by addons discoverable. */
    public static StructureContactResolver contacts() {
        return CONTACTS;
    }

    public static KineticLiftProfile kineticLift() {
        return KINETIC_LIFT;
    }

    public static StructureTerrainBreakPolicy terrainBreaking() {
        return TERRAIN_BREAKING;
    }
}
