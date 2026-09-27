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

public class CompleteConsciousnessAnalysis extends Skill {
    public CompleteConsciousnessAnalysis() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .common()
                .level(AbilityLevel.LEVEL4)
                .passive()
                .maintenanceCost(0)
                .iterationTicks(40)
                .energyCost(60000)
                .dependsOn(Skills.PARALLEL_THOUGHT_COMPUTATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
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
                new AbilitySystemClient.SkillInfo(Skills.COMPLETE_CONSCIOUSNESS_ANALYSIS.get(), List.of(ParallelThoughtComputation.Client.SKILL_INFO), R.textures.ability.level0.skill.complete_consciousness_analysis.icon, 165, 62)
        );

        private static void initialize() {
        }
    }
}
