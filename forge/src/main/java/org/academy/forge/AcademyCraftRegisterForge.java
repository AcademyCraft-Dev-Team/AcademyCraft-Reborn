package org.academy.forge;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.util.GameUtil;
import org.academy.forge.internal.client.renderer.blockentity.forge.AcademyCraftBlockEntityRenderersForge;
import org.academy.forge.internal.common.world.item.forge.AcademyCraftItemsForge;
import org.academy.forge.internal.common.world.level.block.entity.forge.AcademyCraftBlockEntityTypesForge;
import org.academy.forge.internal.common.world.level.block.forge.AcademyCraftBlocksForge;
import org.academy.internal.client.renderer.blockentity.AcademyCraftBlockEntityRenderers;
import org.academy.internal.client.renderer.entity.AcademyCraftEntityRenderers;
import org.academy.internal.client.ui.hud.AcademyCraftHUDSystem;
import org.academy.internal.common.ability.builtin.AcademyCraftAbilityCategories;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.item.AcademyCraftIconItem;
import org.academy.internal.common.world.item.AcademyCraftItems;
import org.academy.internal.common.world.level.block.AcademyCraftBlocks;
import org.academy.internal.common.world.level.block.entity.AcademyCraftBlockEntityTypes;

@Mod.EventBusSubscriber(modid = AcademyCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AcademyCraftRegisterForge {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB_DEFERRED_REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AcademyCraft.MOD_ID);
    public static final RegistryObject<CreativeModeTab> BASE_CREATIVE_TAB = CREATIVE_MODE_TAB_DEFERRED_REGISTER.register("all", () -> CreativeModeTab.builder().icon(() -> new ItemStack(AcademyCraftItems.ACADEMY_CRAFT_ICON_ITEM)).displayItems((itemDisplayParameters, output) -> {
        for (ResourceLocation resourceLocation : AcademyCraftItems.ITEMS.keySet()) {
            Item item = AcademyCraftItems.ITEMS.get(resourceLocation);
            if (!(item instanceof AcademyCraftIconItem)) {
                output.accept(item);
            }
        }
    }).title(Component.literal(AcademyCraft.MOD_NAME)).build());

    public static void init(IEventBus bus) {
        CREATIVE_MODE_TAB_DEFERRED_REGISTER.register(bus);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        registerItem(event);
        registerBlock(event);
        registerBlockEntityType(event);
        if (GameUtil.getEnvType() == GameUtil.EnvType.CLIENT) {
            registerEntityRenderer();
            registerBlockEntityRenderer();
        }
        registerEntityType(event);
        registerSoundEvent(event);
        registerAbilityCategory();
    }

    private static void registerItem(RegisterEvent event) {
        AcademyCraftItemsForge.init();
        for (ResourceLocation resourceLocation : AcademyCraftItems.ITEMS.keySet()) {
            event.register(ForgeRegistries.Keys.ITEMS, resourceLocation, () -> AcademyCraftItems.ITEMS.get(resourceLocation));
        }
    }

    private static void registerBlock(RegisterEvent event) {
        AcademyCraftBlocksForge.init();
        for (ResourceLocation resourceLocation : AcademyCraftBlocks.BLOCKS.keySet()) {
            event.register(ForgeRegistries.Keys.BLOCKS, resourceLocation, () -> AcademyCraftBlocks.BLOCKS.get(resourceLocation));
        }
    }

    private static void registerBlockEntityType(RegisterEvent event) {
        AcademyCraftBlockEntityTypesForge.init();
        for (ResourceLocation resourceLocation : AcademyCraftBlockEntityTypes.BLOCK_ENTITY_TYPES.keySet()) {
            event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, resourceLocation, () -> AcademyCraftBlockEntityTypes.BLOCK_ENTITY_TYPES.get(resourceLocation));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerBlockEntityRenderer() {
        AcademyCraftBlockEntityRenderersForge.init();
        for (BlockEntityType<?> blockEntityType : AcademyCraftBlockEntityRenderers.BLOCK_ENTITY_RENDERERS.keySet()) {
            BlockEntityRenderers.register(blockEntityType, context -> (BlockEntityRenderer) AcademyCraftBlockEntityRenderers.BLOCK_ENTITY_RENDERERS.get(blockEntityType));
        }
    }

    private static void registerEntityType(RegisterEvent event) {
        for (AcademyCraftEntityTypes.Type<?> type : AcademyCraftEntityTypes.TYPE_LIST) {
            event.register(ForgeRegistries.Keys.ENTITY_TYPES, new ResourceLocation(AcademyCraft.MOD_ID, type.name()), type::entityType);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerEntityRenderer() {
        for (AcademyCraftEntityRenderers.Renderer renderer : AcademyCraftEntityRenderers.RENDERER_LIST) {
            EntityRenderers.register(renderer.entityType(), renderer.entityRenderer());
        }
    }

    private static void registerSoundEvent(RegisterEvent event) {
        for (SoundEvent soundEvent : AcademyCraftSoundEvents.SOUND_EVENT_LIST) {
            event.register(ForgeRegistries.Keys.SOUND_EVENTS, soundEvent.getLocation(), () -> soundEvent);
        }
    }

    private static void registerAbilityCategory() {
        for (AbilityCategory abilityCategory : AcademyCraftAbilityCategories.ABILITY_CATEGORY_LIST) {
            AbilitySystem.registerAbilityCategory(abilityCategory);
        }
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "ability_hud", (forgeGui, arg, f, i, j) -> AcademyCraftHUDSystem.render(arg, f));
    }
}