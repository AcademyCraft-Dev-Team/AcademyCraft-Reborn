package org.academy.internal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.AbilitySystem;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.client.renderer.blockentity.AcademyCraftBlockEntityRenderers;
import org.academy.internal.client.renderer.entity.AcademyCraftEntityRenderers;
import org.academy.internal.common.ability.builtin.AcademyCraftAbilityCategories;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.item.AcademyCraftIconItem;
import org.academy.internal.common.world.item.AcademyCraftItems;
import org.academy.internal.common.world.level.block.AcademyCraftBlocks;
import org.academy.internal.common.world.level.block.entity.AcademyCraftBlockEntityTypes;

public class AcademyCraftRegister {
    public static void init() {
        registerItem();
        registerBlock();
        registerBlockEntityType();
        registerBlockEntityRenderer();
        registerCreativeModeTab();
        registerEntityType();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerEntityRenderer();
        }
        registerSoundEvent();
        registerAbilityCategory();
    }

    private static void registerItem() {
        for (ResourceLocation resourceLocation : AcademyCraftItems.ITEMS.keySet()) {
            Registry.register(BuiltInRegistries.ITEM, resourceLocation, AcademyCraftItems.ITEMS.get(resourceLocation));
        }
    }

    private static void registerBlock() {
        for (ResourceLocation resourceLocation : AcademyCraftBlocks.BLOCKS.keySet()) {
            Registry.register(BuiltInRegistries.BLOCK, resourceLocation, AcademyCraftBlocks.BLOCKS.get(resourceLocation));
        }
    }

    private static void registerBlockEntityType() {
        for (ResourceLocation resourceLocation : AcademyCraftBlockEntityTypes.BLOCK_ENTITY_TYPES.keySet()) {
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, resourceLocation, AcademyCraftBlockEntityTypes.BLOCK_ENTITY_TYPES.get(resourceLocation));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerBlockEntityRenderer() {
        for (BlockEntityType<?> blockEntityType : AcademyCraftBlockEntityRenderers.BLOCK_ENTITY_RENDERERS.keySet()) {
            BlockEntityRenderers.register(blockEntityType, context -> (BlockEntityRenderer) AcademyCraftBlockEntityRenderers.BLOCK_ENTITY_RENDERERS.get(blockEntityType));
        }
    }

    private static void registerCreativeModeTab() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(AcademyCraft.MOD_ID, "item_group"), FabricItemGroup.builder().icon(() -> new ItemStack(AcademyCraftItems.ACADEMY_CRAFT_ICON_ITEM)).displayItems((itemDisplayParameters, output) -> {
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
    @Environment(EnvType.CLIENT)
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

    private AcademyCraftRegister() {
    }
}