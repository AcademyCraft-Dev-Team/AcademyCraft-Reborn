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

public final class EnduranceTraining extends Skill {
    public EnduranceTraining() {
        super(Builder.of(AbilityCategories.LEVEL0.get())
                .common()
                .level(AbilityLevel.LEVEL1)
                .passive()
                .energyCost(5_000)
                .dependsOn(Skills.BRAIN_DOMAIN_DEVELOPMENT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1)));
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    @Override
    public void initServer(MinecraftServerContext context) {
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addCommonSkillInfo(
                new AbilitySystemClient.SkillInfo(
                        Skills.ENDURANCE_TRAINING.get(),
                        List.of(BrainDomainDevelopment.Client.SKILL_INFO),
                        R.textures.ability.level0.skill.multiple_brain_domain_segmentation.icon,
                        75,
                        25
                )
        );

        private static void initialize() {
        }
    }
}
