package org.academy.internal.common.ability.level0.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

public class Level0PassiveLv2 extends Skill {
    public Level0PassiveLv2() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .level(AbilityLevel.LEVEL2)
                .passive()
                .maintenanceCost(0)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.AnySkillOfLevelCondition(3))
        );
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.LEVEL0.get(),
                new AbilitySystemClient.SkillInfo(Skills.LEVEL0_PASSIVE_LV2.get(), List.of(Level0PassiveLv1.Client.SKILL_INFO), R.textures.level0_passive_lv2_icon, 72, 110)
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
            if (!Skills.LEVEL0_PASSIVE_LV2.get().isEnabled(p)) return;

            if (p.level().getGameTime() % 200 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.HASTE, 220, 0,
                        false, false, true));
            }
        }
    }
}
