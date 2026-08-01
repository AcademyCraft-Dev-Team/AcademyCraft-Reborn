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

public class Level0PassiveLv4 extends Skill {
    public Level0PassiveLv4() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .level(AbilityLevel.LEVEL4)
                .passive()
                .maintenanceCost(0)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.AnySkillOfLevelCondition(4))
        );
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.LEVEL0.get(),
                new AbilitySystemClient.SkillInfo(Skills.LEVEL0_PASSIVE_LV4.get(), List.of(Level0PassiveLv3.Client.SKILL_INFO), R.textures.ability.level0.skill.level0_passive_lv4.icon, 160, 110)
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
            if (!Skills.LEVEL0_PASSIVE_LV4.get().isEnabled(p)) return;

            var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(Modifier.SPEED_ID)) {
                speed.addPermanentModifier(new AttributeModifier(Modifier.SPEED_ID, 0.02,
                        AttributeModifier.Operation.ADD_VALUE));
            }

            var atkSpeed = p.getAttribute(Attributes.ATTACK_SPEED);
            if (atkSpeed != null && !atkSpeed.hasModifier(Modifier.ATK_SPEED_ID)) {
                atkSpeed.addPermanentModifier(new AttributeModifier(Modifier.ATK_SPEED_ID, 0.5,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }

        static class Modifier {
            static final net.minecraft.resources.Identifier SPEED_ID =
                    AcademyCraft.academy("frequency_boost_speed");
            static final net.minecraft.resources.Identifier ATK_SPEED_ID =
                    AcademyCraft.academy("frequency_boost_atk_speed");
        }
    }
}
