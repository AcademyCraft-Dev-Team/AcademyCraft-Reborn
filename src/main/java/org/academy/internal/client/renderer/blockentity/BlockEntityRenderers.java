package org.academy.internal.client.renderer.blockentity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;

@EventBusSubscriber(Dist.CLIENT)
public final class BlockEntityRenderers {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntityTypes.WIND_GEN_TOP.get(),
                _ -> WindGenTopRenderer.INSTANCE);
/*        event.registerBlockEntityRenderer(BlockEntityTypes.ABILITY_DEVELOPER.get(),
                AbilityDeveloperRenderer::new
        );*/
        event.registerBlockEntityRenderer(BlockEntityTypes.WIRELESS_NODE.get(),
                _ -> WirelessNodeRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.OMNI_CRAFTING_TABLE.get(),
                _ -> OmniCraftingTableRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.CAT_ENGINE.get(),
                _ -> CatEngineRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.WIND_GEN_BASE.get(),
                _ -> WindGenBaseRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.WIND_GEN_PILLAR.get(),
                _ -> WindGenPillarRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.SOLAR_GEN.get(),
                _ -> SolarGenRenderer.INSTANCE);
    }

    private BlockEntityRenderers() {
    }
}