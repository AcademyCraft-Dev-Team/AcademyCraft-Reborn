package org.academy.internal.client.renderer.blockentity;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public class BlockEntityRenderers {
    public static final Map<BlockEntityType<?>, BlockEntityRenderer<?>> BLOCK_ENTITY_RENDERERS = new HashMap<>();

    public static void add() {
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.WIND_GEN_BASE.get(), WindGenBaseBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.WIND_GEN_TOP.get(), WindGenTopBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.ABILITY_DEVELOPER.get(), AbilityDeveloperBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.WIND_GEN_PILLAR.get(), WindGenPillarBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.WIRELESS_NODE.get(), WirelessNodeBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.OMNI_CRAFTING_TABLE.get(), OmniCraftingTableBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.CAT_ENGINE.get(), CatEngineBlockEntityRenderer.INSTANCE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @SubscribeEvent
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        add();
        for (var blockEntityType : BlockEntityRenderers.BLOCK_ENTITY_RENDERERS.keySet()) {
            event.registerBlockEntityRenderer(blockEntityType, context -> (BlockEntityRenderer) BlockEntityRenderers.BLOCK_ENTITY_RENDERERS.get(blockEntityType));
        }
    }

    private BlockEntityRenderers() {
    }
}