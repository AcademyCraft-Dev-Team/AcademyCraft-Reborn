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

public class ParallelThoughtComputation extends Skill {
    public ParallelThoughtComputation() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .common()
                .level(AbilityLevel.LEVEL3)
                .passive()
                .maintenanceCost(0)
                .iterationTicks(40)
                .energyCost(30000)
                .dependsOn(Skills.MULTIPLE_BRAIN_DOMAIN_SEGMENTATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.AnySkillOfLevelCondition(4))
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
                new AbilitySystemClient.SkillInfo(Skills.PARALLEL_THOUGHT_COMPUTATION.get(), List.of(MultipleBrainDomainSegmentation.Client.SKILL_INFO), R.textures.ability.level0.skill.parallel_thought_computation.icon, 120, 62)
        );

        private static void initialize() {
        }
    }
}
