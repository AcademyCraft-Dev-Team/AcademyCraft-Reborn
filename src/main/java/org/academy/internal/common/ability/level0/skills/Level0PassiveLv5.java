package org.academy.internal.common.ability.level0.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

import java.util.List;

public class Level0PassiveLv5 extends Skill {
    public Level0PassiveLv5() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .level(AbilityLevel.LEVEL5)
                .passive()
                .maintenanceCost(0)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.AnySkillOfLevelCondition(5))
        );
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.LEVEL0.get(),
                new AbilitySystemClient.SkillInfo(Skills.LEVEL0_PASSIVE_LV5.get(), List.of(Level0PassiveLv4.Client.SKILL_INFO), R.textures.level0_passive_lv5_icon, 205, 110)
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
            if (!Skills.LEVEL0_PASSIVE_LV5.get().isEnabled(p)) return;

            var maxHp = p.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null && !maxHp.hasModifier(Modifier.HP_ID)) {
                maxHp.addPermanentModifier(new AttributeModifier(Modifier.HP_ID, 2,
                        AttributeModifier.Operation.ADD_VALUE));
            }

            var armor = p.getAttribute(Attributes.ARMOR);
            if (armor != null && !armor.hasModifier(Modifier.ARMOR_ID)) {
                armor.addPermanentModifier(new AttributeModifier(Modifier.ARMOR_ID, 2,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }

        static class Modifier {
            static final net.minecraft.resources.Identifier HP_ID =
                    AcademyCraft.academy("bandwidth_hp");
            static final net.minecraft.resources.Identifier ARMOR_ID =
                    AcademyCraft.academy("bandwidth_armor");
        }
    }
}
