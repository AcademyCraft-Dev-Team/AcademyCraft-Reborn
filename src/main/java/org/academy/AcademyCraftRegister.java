package org.academy;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.event.AbilitySystemFinalizedEvent;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.arc.PathModifierTypes;
import org.academy.internal.common.arc.PathTypes;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.network.syncher.EntityDataSerializers;
import org.academy.internal.common.skilldata.SkillDataTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.sync.DataTypes;
import org.academy.internal.common.sync.SyncKeys;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.inventory.MenuTypes;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;

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
        Blocks.BLOCKS.register(modEventBus);
        Items.ITEMS.register(modEventBus);
        BlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);
        EntityTypes.ENTITY_TYPES.register(modEventBus);
        EntityDataSerializers.ENTITY_DATA_SERIALIZERS.register(modEventBus);
        SoundEvents.SOUND_EVENTS.register(modEventBus);
        MenuTypes.MENU_TYPES.register(modEventBus);
        ParticleTypes.PARTICLE_TYPES.register(modEventBus);

        CREATIVE_MODE_TABS.register(modEventBus);

        AttachmentTypes.REGISTER.register(modEventBus);

        AbilityCategories.ABILITY_CATEGORIES.register(modEventBus);
        PacketTypes.PACKET_TYPES.register(modEventBus);
        Skills.SKILLS.register(modEventBus);

        DataTypes.SYNC_DATA_TYPES.register(modEventBus);
        SyncKeys.SYNC_KEYS.register(modEventBus);
        PathTypes.PATH_TYPES.register(modEventBus);
        PathModifierTypes.PATH_MODIFIER_TYPES.register(modEventBus);
        SkillDataTypes.SKILL_DATA_TYPES.register(modEventBus);

        modEventBus.addListener(AcademyCraftRegister::onNewRegistry);
        modEventBus.addListener(AcademyCraftRegister::onCommonSetup);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.register(ABILITY_CATEGORIES);
        event.register(SKILLS);
        event.register(SYNC_KEYS);
        event.register(DATA_TYPES);
        event.register(PATH_TYPES);
        event.register(PATH_MODIFIER_TYPES);
        event.register(SKILL_DATA_TYPES);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.post(new AbilitySystemFinalizedEvent());
            ABILITY_CATEGORIES.forEach(AbilityCategory::seal);
        });
    }
}