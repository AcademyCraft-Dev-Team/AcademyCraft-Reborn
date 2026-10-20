package org.academy.internal.common.sublevel.pipeline;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructurePipeline;

/** Registers discovery of the draft, never replaces the current gameplay services. */
public final class SubLevelPipelineBootstrap {
    public static final boolean GAMEPLAY_ENABLED = false;
    public static final StructurePipeline DRAFT = new SubLevelPipelineDraft();

    private SubLevelPipelineBootstrap() {}

    public static void register(IEventBus bus) {
        bus.addListener(SubLevelPipelineBootstrap::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> AcademyCraft.LOGGER.info("Structure pipeline v2: DRAFT_REGISTERED, gameplay disabled"));
    }
}
