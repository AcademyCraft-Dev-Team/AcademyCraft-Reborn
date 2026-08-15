package org.academy;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.event.AbilitySystemFinalizedEvent;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.api.common.entitycontrol.MentalControlApi;
import org.academy.api.common.entitycontrol.PlayerNavigationApi;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.AbilityRegistrationValidator;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.program.AbilityProgramNodeTypes;
import org.academy.internal.common.ability.mentalout.control.*;
import org.academy.internal.common.arc.PathModifierTypes;
import org.academy.internal.common.arc.PathTypes;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.network.syncher.EntityDataSerializers;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.sync.DataTypes;
import org.academy.internal.common.sync.SyncKeys;
import org.academy.internal.common.world.effect.StatusEffects;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;
import org.academy.internal.common.world.inventory.MenuTypes;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.item.crafting.RecipeSerializers;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.material.Fluids;

import static org.academy.AcademyCraft.MODID;
import static org.academy.AcademyCraft.MOD_NAME;
import static org.academy.api.common.registries.Registries.*;

public final class AcademyCraftRegister {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_MODE_TAB =
            CREATIVE_MODE_TABS.register("all", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(Items.ICON))
                    .displayItems((_, output) -> {
                        for (var key : Items.ITEMS.getEntries()) {
                            var item = key.get();
                            if (!(item == Items.ICON.get())) {
                                output.accept(item);
                            }
                        }
                    }).title(Component.literal(MOD_NAME)).build());

    private AcademyCraftRegister() {
    }

    public static void register(IEventBus modEventBus) {
        Fluids.FLUID_TYPES.register(modEventBus);
        Fluids.FLUIDS.register(modEventBus);
        Blocks.BLOCKS.register(modEventBus);
        Items.ITEMS.register(modEventBus);
        RecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        BlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);
        EntityTypes.ENTITY_TYPES.register(modEventBus);
        EntityDataSerializers.ENTITY_DATA_SERIALIZERS.register(modEventBus);
        SoundEvents.SOUND_EVENTS.register(modEventBus);
        MenuTypes.MENU_TYPES.register(modEventBus);
        ParticleTypes.PARTICLE_TYPES.register(modEventBus);
        StatusEffects.MOB_EFFECTS.register(modEventBus);
        PlayerAttributes.ATTRIBUTES.register(modEventBus);

        CREATIVE_MODE_TABS.register(modEventBus);

        AttachmentTypes.REGISTER.register(modEventBus);

        AbilityCategories.ABILITY_CATEGORIES.register(modEventBus);
        PacketTypes.PACKET_TYPES.register(modEventBus);
        Skills.SKILLS.register(modEventBus);
        AbilityProgramNodeTypes.REGISTER.register(modEventBus);

        DataTypes.SYNC_DATA_TYPES.register(modEventBus);
        SyncKeys.SYNC_KEYS.register(modEventBus);
        PathTypes.PATH_TYPES.register(modEventBus);
        PathModifierTypes.PATH_MODIFIER_TYPES.register(modEventBus);

        modEventBus.addListener(AcademyCraftRegister::onNewRegistry);
        modEventBus.addListener(AcademyCraftRegister::onCommonSetup);
        modEventBus.addListener(AcademyCraftRegister::onEntityAttributes);
        modEventBus.addListener(AcademyCraftRegister::onEntityAttributeModification);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.register(ABILITY_CATEGORIES);
        event.register(SKILLS);
        event.register(PROGRAM_NODE_TYPES);
        event.register(SYNC_KEYS);
        event.register(DATA_TYPES);
        event.register(PATH_TYPES);
        event.register(PATH_MODIFIER_TYPES);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MentalControlApi.registerAdapter(
                    Identifier.fromNamespaceAndPath(MODID, "warden"),
                    100,
                    new WardenMentalControlAdapter()
            );
            MentalControlApi.registerAdapter(
                    Identifier.fromNamespaceAndPath(MODID, "wither"),
                    100,
                    new WitherMentalControlAdapter()
            );
            MentalControlApi.registerAdapter(
                    Identifier.fromNamespaceAndPath(MODID, "ender_dragon"),
                    100,
                    new EnderDragonMentalControlAdapter()
            );
            MentalControlApi.registerAdapter(
                    Identifier.fromNamespaceAndPath(MODID, "vanilla_mob"),
                    Integer.MIN_VALUE,
                    new VanillaMobMentalControlAdapter()
            );
            MentalControlApi.registerAdapter(
                    Identifier.fromNamespaceAndPath(MODID, "server_player"),
                    200,
                    new ServerPlayerMentalControlAdapter()
            );
            PlayerNavigationApi.registerAdapter(
                    Identifier.fromNamespaceAndPath(MODID, "vanilla_player"),
                    0,
                    new DefaultPlayerNavigationAdapter()
            );
            NeoForge.EVENT_BUS.post(new AbilitySystemFinalizedEvent());
            AbilityRegistrationValidator.validate();
            ABILITY_CATEGORIES.forEach(AbilityCategory::seal);
        });
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(EntityTypes.DARKMATTER_BEETLE.get(), DarkmatterBeetle.createAttributes().build());
    }

    private static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        event.add(net.minecraft.world.entity.EntityTypes.PLAYER, PlayerAttributes.MUSCLE_STRENGTH);
        event.add(net.minecraft.world.entity.EntityTypes.PLAYER, PlayerAttributes.ENDURANCE);
        event.add(net.minecraft.world.entity.EntityTypes.PLAYER, PlayerAttributes.DEXTERITY);
        event.add(net.minecraft.world.entity.EntityTypes.PLAYER, PlayerAttributes.PERCEPTION);
        event.add(net.minecraft.world.entity.EntityTypes.PLAYER, PlayerAttributes.NEURAL_ACTIVITY);
        event.add(net.minecraft.world.entity.EntityTypes.PLAYER, PlayerAttributes.TRUE_RESISTANCE);
    }
}
