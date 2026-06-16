package org.academy.internal.common.ability.level0.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

public class Level0PassiveLv3 extends Skill {
    public Level0PassiveLv3() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .level(AbilityLevel.LEVEL3)
                .passive()
                .maintenanceCost(0)
        );
    }

    @Override
    public void initServer(MinecraftServerContext c) {
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onTick(PlayerTickEvent.Post e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            if (!Skills.LEVEL0_PASSIVE_LV3.get().isEnabled(p)) return;

            var armor = p.getAttribute(Attributes.ARMOR);
            if (armor != null && !armor.hasModifier(Modifier.ARMOR_ID)) {
                armor.addPermanentModifier(new AttributeModifier(Modifier.ARMOR_ID, 2,
                        AttributeModifier.Operation.ADD_VALUE));
            }

            var toughness = p.getAttribute(Attributes.ARMOR_TOUGHNESS);
            if (toughness != null && !toughness.hasModifier(Modifier.TOUGHNESS_ID)) {
                toughness.addPermanentModifier(new AttributeModifier(Modifier.TOUGHNESS_ID, 2,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }

        static class Modifier {
            static final net.minecraft.resources.Identifier ARMOR_ID =
                    AcademyCraft.academy("phase_science_armor");
            static final net.minecraft.resources.Identifier TOUGHNESS_ID =
                    AcademyCraft.academy("phase_science_toughness");
        }
    }
}
