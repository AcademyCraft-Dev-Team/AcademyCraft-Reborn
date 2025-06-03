package org.academy.fabric;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.util.GameUtil;
import org.academy.api.common.vanilla.EnvType;
import org.academy.fabric.internal.common.world.item.fabric.AcademyCraftItemsFabric;
import org.academy.fabric.internal.common.world.level.block.entity.fabric.BlockEntityTypesFabric;
import org.academy.fabric.internal.common.world.level.block.fabric.AcademyCraftBlocksFabric;
import org.academy.internal.client.renderer.blockentity.BlockEntityRenderers;
import org.academy.internal.common.ability.builtin.AbilityCategories;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.inventory.MenuTypes;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.levelgen.feature.Features;
import org.academy.internal.common.world.level.material.Fluids;

public class AcademyCraftRegisterFabric {
    private AcademyCraftRegisterFabric() {
    }

    public static void register() {
        registerFluid();
        registerBlock();
        registerItem();
        registerFeature();

        registerParticleType();
        registerBlockEntityType();

        registerCreativeModeTab();
        registerEntityType();

        registerSoundEvent();
        registerAbilityCategory();
        registerMenuType();

        if (GameUtil.getEnvType() == EnvType.CLIENT) {
            registerBlockEntityRenderer();
        }
    }

    private static void registerItem() {
        AcademyCraftItemsFabric.init();
        for (String key : Items.ITEMS.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            Registry.register(BuiltInRegistries.ITEM, resourceLocation, Items.ITEMS.get(key));
        }
    }

    private static void registerBlock() {
        AcademyCraftBlocksFabric.init();
        for (String key : Blocks.BLOCKS.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            Registry.register(BuiltInRegistries.BLOCK, resourceLocation, Blocks.BLOCKS.get(key));
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
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(AcademyCraft.MOD_ID, "item_group"), CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0).icon(() -> new ItemStack(Items.ICON)).displayItems((itemDisplayParameters, output) -> {
            for (String key : Items.ITEMS.keySet()) {
                Item item = Items.ITEMS.get(key);
                if (!(item == Items.ICON)) {
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

    private static void registerMenuType() {
        for (String name : MenuTypes.MENU_TYPES.keySet()) {
            MenuType<?> menuType = MenuTypes.MENU_TYPES.get(name);
            Registry.register(BuiltInRegistries.MENU, new ResourceLocation(AcademyCraft.MOD_ID, name), menuType);
        }
    }

    private static void registerFluid() {
        for (String key : Fluids.FLUIDS.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            Registry.register(BuiltInRegistries.FLUID, resourceLocation, Fluids.FLUIDS.get(key));
        }
        ResourceLocation imagTexture = new ResourceLocation(AcademyCraft.MOD_ID, "block/black");
        FluidRenderHandlerRegistry.INSTANCE.register(Fluids.IMAG_PHASE, Fluids.FLOWING_IMAG_PHASE,
                new SimpleFluidRenderHandler(imagTexture, imagTexture));
        BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), Fluids.IMAG_PHASE, Fluids.FLOWING_IMAG_PHASE);
    }

    private static void registerParticleType() {
        for (String key : ParticleTypes.PARTICLE_TYPES.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            Registry.register(BuiltInRegistries.PARTICLE_TYPE, resourceLocation, ParticleTypes.PARTICLE_TYPES.get(key));
        }
    }

    private static void registerFeature() {
        for (String key : Features.FEATURES.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            Registry.register(BuiltInRegistries.FEATURE, resourceLocation, Features.FEATURES.get(key));
        }
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LAKES,
                ResourceKey.create(Registries.PLACED_FEATURE,
                        new ResourceLocation(AcademyCraft.MOD_ID, "lake_imag_phase_placed")));
    }
}