package org.academy.internal.common.ability.level0.skills;

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
                .common()
                .level(AbilityLevel.LEVEL2)
                .passive()
                .maintenanceCost(0)
                .iterationTicks(40)
                .energyCost(10000)
                .dependsOn(Skills.LEVEL0_PASSIVE_LV1)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.AnySkillOfLevelCondition(3))
        );
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addCommonSkillInfo(
                new AbilitySystemClient.SkillInfo(Skills.LEVEL0_PASSIVE_LV2.get(), List.of(Level0PassiveLv1.Client.SKILL_INFO), R.textures.ability.level0.skill.level0_passive_lv2.icon, 250, 58)
        );

        private static void initialize() {
        }
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    @Override
    public void initServer(MinecraftServerContext c) {
    }
}
