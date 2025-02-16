package org.academy.internal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.academy.AbilitySystem;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.builtin.AcademyCraftAbilityCategoryList;
import org.academy.internal.client.renderer.entity.AcademyCraftEntityRenderers;
import org.academy.internal.common.commands.AcademyCraftCommand;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.item.AcademyCraftIconItem;
import org.academy.internal.common.world.item.AcademyCraftItems;

public class AcademyCraftRegister {
    public static void init() {
        registerItem();
        registerCommand();
        registerAuxGui();
        registerCreativeModeTab();
        registerEntityType();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerEntityRenderer();
        }
        registerSoundEvent();
        registerAbilityCategory();
    }

    private static void registerItem() {
        for (Item item : AcademyCraftItems.ITEM_LIST) {
            String name = item.getClass().getSimpleName().replace("Item", "").replaceAll("([a-z])([A-Z])", "$1_$2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").toLowerCase();
            Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(AcademyCraft.MOD_ID, name), item);
        }
    }

    private static void registerBlock() {
    }

    private static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AcademyCraftCommand.register(dispatcher));
    }

    private static void registerAuxGui() {
    }

    private static void registerCreativeModeTab() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(AcademyCraft.MOD_ID, "item_group"), FabricItemGroup.builder().icon(() -> new ItemStack(AcademyCraftItems.ACADEMY_CRAFT_ICON_ITEM)).displayItems((itemDisplayParameters, output) -> {
            for (Item item : AcademyCraftItems.ITEM_LIST) {
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
        for (AbilityCategory abilityCategory : AcademyCraftAbilityCategoryList.ABILITY_CATEGORY_LIST) {
            AbilitySystem.registerAbilityCategory(abilityCategory);
        }
    }

    private AcademyCraftRegister() {
    }
}