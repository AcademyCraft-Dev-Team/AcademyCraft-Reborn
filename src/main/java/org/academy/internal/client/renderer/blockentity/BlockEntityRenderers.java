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
                context -> WindGenTopBlockEntityRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.ABILITY_DEVELOPER.get(),
                context -> AbilityDeveloperBlockEntityRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.WIRELESS_NODE.get(),
                context -> WirelessNodeBlockEntityRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.OMNI_CRAFTING_TABLE.get(),
                context -> OmniCraftingTableBlockEntityRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.CAT_ENGINE.get(),
                context -> CatEngineBlockEntityRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.WIND_GEN_BASE.get(),
                context -> WindGenBaseBlockEntityRenderer.INSTANCE);
        event.registerBlockEntityRenderer(BlockEntityTypes.WIND_GEN_PILLAR.get(),
                context -> WindGenPillarBlockEntityRenderer.INSTANCE);
    }

    private BlockEntityRenderers() {
    }
}