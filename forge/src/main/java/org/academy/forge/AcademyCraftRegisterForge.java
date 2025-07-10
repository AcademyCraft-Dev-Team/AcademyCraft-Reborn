package org.academy.forge;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.forge.internal.client.renderer.blockentity.forge.AcademyCraftBlockEntityRenderersForge;
import org.academy.forge.internal.common.world.item.forge.AcademyCraftItemsForge;
import org.academy.forge.internal.common.world.level.block.entity.forge.AcademyCraftBlockEntityTypesForge;
import org.academy.forge.internal.common.world.level.block.forge.AcademyCraftBlocksForge;
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

import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = AcademyCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AcademyCraftRegisterForge {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB_DEFERRED_REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AcademyCraft.MOD_ID);
    @SuppressWarnings("unused")
    public static final RegistryObject<CreativeModeTab> BASE_CREATIVE_TAB = CREATIVE_MODE_TAB_DEFERRED_REGISTER.register("all", () -> CreativeModeTab.builder().icon(() -> new ItemStack(Items.ICON)).displayItems((itemDisplayParameters, output) -> {
        for (String key : Items.ITEMS.keySet()) {
            Item item = Items.ITEMS.get(key);
            if (!(item == Items.ICON)) {
                output.accept(item);
            }
        }
    }).title(Component.literal(AcademyCraft.MOD_NAME)).build());

    public static void init(IEventBus bus) {
        CREATIVE_MODE_TAB_DEFERRED_REGISTER.register(bus);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey() == ForgeRegistries.Keys.FLUIDS) {
            registerFluid(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.BLOCKS) {
            registerBlock(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.ITEMS) {
            registerItem(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.FLUID_TYPES) {
            registerFluidType(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.BLOCK_ENTITY_TYPES) {
            registerBlockEntityType(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.SOUND_EVENTS) {
            registerSoundEvent(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.ENTITY_TYPES) {
            registerEntityType(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.PARTICLE_TYPES) {
            registerParticleType(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.MENU_TYPES) {
            registerMenuType(event);
        } else if (event.getRegistryKey() == ForgeRegistries.Keys.FEATURES) {
            registerFeature(event);
        }
    }

    private static void registerItem(RegisterEvent event) {
        AcademyCraftItemsForge.init();
        for (String key : Items.ITEMS.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            event.register(ForgeRegistries.Keys.ITEMS, resourceLocation, () -> Items.ITEMS.get(key));
        }
    }

    private static void registerBlock(RegisterEvent event) {
        AcademyCraftBlocksForge.init();
        for (String key : Blocks.BLOCKS.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            event.register(ForgeRegistries.Keys.BLOCKS, resourceLocation, () -> Blocks.BLOCKS.get(key));
        }
    }

    private static void registerBlockEntityType(RegisterEvent event) {
        AcademyCraftBlockEntityTypesForge.init();
        for (ResourceLocation resourceLocation : BlockEntityTypes.BLOCK_ENTITY_TYPES.keySet()) {
            event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, resourceLocation, () -> BlockEntityTypes.BLOCK_ENTITY_TYPES.get(resourceLocation));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerBlockEntityRenderer() {
        AcademyCraftBlockEntityRenderersForge.init();
        for (BlockEntityType<?> blockEntityType : BlockEntityRenderers.BLOCK_ENTITY_RENDERERS.keySet()) {
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(blockEntityType, context -> (BlockEntityRenderer) BlockEntityRenderers.BLOCK_ENTITY_RENDERERS.get(blockEntityType));
        }
    }

    private static void registerEntityType(RegisterEvent event) {
        for (EntityTypes.Type<?> type : EntityTypes.TYPE_LIST) {
            event.register(ForgeRegistries.Keys.ENTITY_TYPES, new ResourceLocation(AcademyCraft.MOD_ID, type.name()), type::entityType);
        }
    }

    private static void registerSoundEvent(RegisterEvent event) {
        for (SoundEvent soundEvent : AcademyCraftSoundEvents.SOUND_EVENT_LIST) {
            event.register(ForgeRegistries.Keys.SOUND_EVENTS,
                    soundEvent.getLocation(), () -> soundEvent);
        }
    }

    private static void registerAbilityCategory() {
        for (AbilityCategory abilityCategory : AbilityCategories.ABILITY_CATEGORY_LIST) {
            AbilitySystem.registerAbilityCategory(abilityCategory);
        }
    }

    private static void registerMenuType(RegisterEvent event) {
        for (String name : MenuTypes.MENU_TYPES.keySet()) {
            MenuType<?> menuType = MenuTypes.MENU_TYPES.get(name);
            event.register(ForgeRegistries.Keys.MENU_TYPES,
                    new ResourceLocation(AcademyCraft.MOD_ID, name), () -> menuType);
        }
    }

    // Fuck forge
    private static void registerFluid(RegisterEvent event) {
        for (String key : Fluids.FLUIDS.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            event.register(ForgeRegistries.Keys.FLUIDS, resourceLocation, () -> Fluids.FLUIDS.get(key));
        }
    }

    private static void registerFluidType(RegisterEvent event) {
        ResourceLocation imagTexture = new ResourceLocation(AcademyCraft.MOD_ID, "block/black");
        event.register(ForgeRegistries.Keys.FLUID_TYPES, fluidTypeRegisterHelper -> {
            Fluids.IMAGIPHASE_PLASMA.forgeFluidType = Fluids.FLOWING_IMAGIPHASE_PLASMA.forgeFluidType =
                    new FluidType(FluidType.Properties.create()
                            .motionScale(0)
                            .canPushEntity(false)
                            .canSwim(false)
                            .canDrown(false)
                            .fallDistanceModifier(1F)
                            .pathType(null)
                            .adjacentPathType(null)
                            .density(0)
                            .temperature(0)
                            .viscosity(0)
                    ) {
                        @Override
                        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                            consumer.accept(new IClientFluidTypeExtensions() {
                                @Override
                                public ResourceLocation getFlowingTexture() {
                                    return imagTexture;
                                }

                                @Override
                                public ResourceLocation getStillTexture() {
                                    return imagTexture;
                                }
                            });
                        }
                    };
            fluidTypeRegisterHelper.register(new ResourceLocation(AcademyCraft.MOD_ID, "imag_phase_type"),
                    Fluids.IMAGIPHASE_PLASMA.forgeFluidType);
        });
    }

    private static void registerParticleType(RegisterEvent event) {
        for (String key : ParticleTypes.PARTICLE_TYPES.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            event.register(ForgeRegistries.Keys.PARTICLE_TYPES, resourceLocation,
                    () -> ParticleTypes.PARTICLE_TYPES.get(key));
        }
    }

    private static void registerFeature(RegisterEvent event) {
        for (String key : Features.FEATURES.keySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, key);
            event.register(ForgeRegistries.Keys.FEATURES, resourceLocation, () -> Features.FEATURES.get(key));
        }
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        registerAbilityCategory();
        registerBlockEntityRenderer();
    }
}