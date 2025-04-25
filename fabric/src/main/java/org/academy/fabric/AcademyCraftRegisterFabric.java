package org.academy.fabric;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.util.GameUtil;
import org.academy.fabric.internal.common.world.item.fabric.AcademyCraftItemsFabric;
import org.academy.fabric.internal.common.world.level.block.entity.fabric.BlockEntityTypesFabric;
import org.academy.fabric.internal.common.world.level.block.fabric.AcademyCraftBlocksFabric;
import org.academy.internal.client.renderer.blockentity.BlockEntityRenderers;
import org.academy.internal.client.renderer.entity.EntityRenderers;
import org.academy.internal.common.ability.builtin.AbilityCategories;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.item.AcademyCraftIconItem;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;

public class AcademyCraftRegisterFabric {
    private AcademyCraftRegisterFabric() {
    }

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
        for (ResourceLocation resourceLocation : Items.ITEMS.keySet()) {
            Registry.register(BuiltInRegistries.ITEM, resourceLocation, Items.ITEMS.get(resourceLocation));
        }
    }

    private static void registerBlock() {
        AcademyCraftBlocksFabric.init();
        for (ResourceLocation resourceLocation : Blocks.BLOCKS.keySet()) {
            Registry.register(BuiltInRegistries.BLOCK, resourceLocation, Blocks.BLOCKS.get(resourceLocation));
        }
    }

    private static void registerBlockEntityType() {
        BlockEntityTypesFabric.init();
        for (ResourceLocation resourceLocation : BlockEntityTypes.BLOCK_ENTITY_TYPES.keySet()) {
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, resourceLocation, BlockEntityTypes.BLOCK_ENTITY_TYPES.get(resourceLocation));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerBlockEntityRenderer() {
        for (BlockEntityType<?> blockEntityType : BlockEntityRenderers.BLOCK_ENTITY_RENDERERS.keySet()) {
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(blockEntityType, context -> (BlockEntityRenderer) BlockEntityRenderers.BLOCK_ENTITY_RENDERERS.get(blockEntityType));
        }
    }

    private static void registerCreativeModeTab() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(AcademyCraft.MOD_ID, "item_group"), CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0).icon(() -> new ItemStack(Items.ACADEMY_CRAFT_ICON_ITEM)).displayItems((itemDisplayParameters, output) -> {
            for (ResourceLocation resourceLocation : Items.ITEMS.keySet()) {
                Item item = Items.ITEMS.get(resourceLocation);
                if (!(item instanceof AcademyCraftIconItem)) {
                    output.accept(item);
                }
            }
        }).title(Component.literal(AcademyCraft.MOD_NAME)).build());
    }

    private static void registerEntityType() {
        for (EntityTypes.Type<?> type : EntityTypes.TYPE_LIST) {
            Registry.register(BuiltInRegistries.ENTITY_TYPE, new ResourceLocation(AcademyCraft.MOD_ID, type.name()), type.entityType());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerEntityRenderer() {
        for (EntityRenderers.Renderer renderer : EntityRenderers.RENDERER_LIST) {
            net.minecraft.client.renderer.entity.EntityRenderers.register(renderer.entityType(), renderer.entityRenderer());
        }
    }

    private static void registerSoundEvent() {
        for (SoundEvent soundEvent : AcademyCraftSoundEvents.SOUND_EVENT_LIST) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, soundEvent.getLocation(), soundEvent);
        }
    }

    private static void registerAbilityCategory() {
        for (AbilityCategory abilityCategory : AbilityCategories.ABILITY_CATEGORY_LIST) {
            AbilitySystem.registerAbilityCategory(abilityCategory);
        }
    }
}