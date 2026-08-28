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

public class AbsoluteSelfControl extends Skill {
    public AbsoluteSelfControl() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .common()
                .level(AbilityLevel.LEVEL5)
                .passive()
                .maintenanceCost(0)
                .iterationTicks(40)
                .energyCost(100000)
                .dependsOn(Skills.COMPLETE_CONSCIOUSNESS_ANALYSIS)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
        );
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    @Override
    public void initServer(MinecraftServerContext c) {
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addCommonSkillInfo(
                new AbilitySystemClient.SkillInfo(Skills.ABSOLUTE_SELF_CONTROL.get(), List.of(CompleteConsciousnessAnalysis.Client.SKILL_INFO), R.textures.ability.level0.skill.absolute_self_control.icon, 210, 62)
        );

        private static void initialize() {
        }
    }
}
