package org.academy.fabric;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.AbilitySystem;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.util.GameUtil;
import org.academy.internal.client.renderer.entity.AcademyCraftEntityRenderers;
import org.academy.fabric.internal.client.renderer.blockentity.fabric.AcademyCraftBlockEntityRenderersFabric;
import org.academy.fabric.internal.common.world.item.fabric.AcademyCraftItemsFabric;
import org.academy.fabric.internal.common.world.level.block.entity.fabric.AcademyCraftBlockEntityTypesFabric;
import org.academy.fabric.internal.common.world.level.block.fabric.AcademyCraftBlocksFabric;
import org.academy.internal.client.renderer.blockentity.AcademyCraftBlockEntityRenderers;
import org.academy.internal.common.ability.builtin.AcademyCraftAbilityCategories;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.item.AcademyCraftIconItem;
import org.academy.internal.common.world.item.AcademyCraftItems;
import org.academy.internal.common.world.level.block.AcademyCraftBlocks;
import org.academy.internal.common.world.level.block.entity.AcademyCraftBlockEntityTypes;

public class AcademyCraftRegisterFabric {
    public static void register() {
        registerItem();
        registerBlock();
        registerBlockEntityType();

        registerCreativeModeTab();
        registerEntityType();
        if (GameUtil.getEnvType() == GameUtil.EnvType.CLIENT) {
            registerEntityRenderer();
            registerBlockEntityRenderer();
        }
        registerSoundEvent();
        registerAbilityCategory();
    }

    private static void registerItem() {
        AcademyCraftItemsFabric.init();
        for (ResourceLocation resourceLocation : AcademyCraftItems.ITEMS.keySet()) {
            Registry.register(BuiltInRegistries.ITEM, resourceLocation, AcademyCraftItems.ITEMS.get(resourceLocation));
        }
    }

    private static void registerBlock() {
        AcademyCraftBlocksFabric.init();
        for (ResourceLocation resourceLocation : AcademyCraftBlocks.BLOCKS.keySet()) {
            Registry.register(BuiltInRegistries.BLOCK, resourceLocation, AcademyCraftBlocks.BLOCKS.get(resourceLocation));
        }
    }

    private static void registerBlockEntityType() {
        AcademyCraftBlockEntityTypesFabric.init();
        for (ResourceLocation resourceLocation : AcademyCraftBlockEntityTypes.BLOCK_ENTITY_TYPES.keySet()) {
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, resourceLocation, AcademyCraftBlockEntityTypes.BLOCK_ENTITY_TYPES.get(resourceLocation));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerBlockEntityRenderer() {
        AcademyCraftBlockEntityRenderersFabric.init();
        for (BlockEntityType<?> blockEntityType : AcademyCraftBlockEntityRenderers.BLOCK_ENTITY_RENDERERS.keySet()) {
            BlockEntityRenderers.register(blockEntityType, context -> (BlockEntityRenderer) AcademyCraftBlockEntityRenderers.BLOCK_ENTITY_RENDERERS.get(blockEntityType));
        }
    }

    private static void registerCreativeModeTab() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(AcademyCraft.MOD_ID, "item_group"), CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0).icon(() -> new ItemStack(AcademyCraftItems.ACADEMY_CRAFT_ICON_ITEM)).displayItems((itemDisplayParameters, output) -> {
            for (ResourceLocation resourceLocation : AcademyCraftItems.ITEMS.keySet()) {
                Item item = AcademyCraftItems.ITEMS.get(resourceLocation);
                if (!(item instanceof AcademyCraftIconItem)) {
                    output.accept(item);
                }
            }
        }).title(Component.literal(AcademyCraft.MOD_NAME)).build());
    }

    private static void registerEntityType() {
        for (AcademyCraftEntityTypes.Type<?> type : AcademyCraftEntityTypes.TYPE_LIST) {
            Registry.register(BuiltInRegistries.ENTITY_TYPE, new ResourceLocation(AcademyCraft.MOD_ID, type.name()), type.entityType());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerEntityRenderer() {
        for (AcademyCraftEntityRenderers.Renderer renderer : AcademyCraftEntityRenderers.RENDERER_LIST) {
            EntityRenderers.register(renderer.entityType(), renderer.entityRenderer());
        }
    }

    private static void registerSoundEvent() {
        for (SoundEvent soundEvent : AcademyCraftSoundEvents.SOUND_EVENT_LIST) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, soundEvent.getLocation(), soundEvent);
        }
    }

    private static void registerAbilityCategory() {
        for (AbilityCategory abilityCategory : AcademyCraftAbilityCategories.ABILITY_CATEGORY_LIST) {
            AbilitySystem.registerAbilityCategory(abilityCategory);
        }
    }

    private AcademyCraftRegisterFabric() {
    }
}