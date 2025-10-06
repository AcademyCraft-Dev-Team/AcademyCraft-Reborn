package org.academy.internal.client.renderer.blockentity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class BlockEntityRenderers {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntityTypes.WIND_GEN_TOP.get(),
                context -> WindGenTopRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.ABILITY_DEVELOPER.get(),
                context -> AbilityDeveloperRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.WIRELESS_NODE.get(),
                context -> WirelessNodeRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.OMNI_CRAFTING_TABLE.get(),
                context -> OmniCraftingTableRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.CAT_ENGINE.get(),
                context -> CatEngineRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.WIND_GEN_BASE.get(),
                context -> WindGenBaseRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.WIND_GEN_PILLAR.get(),
                context -> WindGenPillarRenderer.INSTANCE);
    }

    private BlockEntityRenderers() {
    }
}