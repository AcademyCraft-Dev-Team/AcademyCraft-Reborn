package org.academy.internal.common.ability.aeromanip.skills.lv2;

import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

import java.util.List;

/** Passive cavitation damage resolved by {@code AeromanipDisplacementTracker}. */
public final class TurbulentCavitation extends Skill {
    public TurbulentCavitation() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .iterationTicks(1)
                .maxStacks(NO_STACK_LIMIT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2)));
    }

    @Override
    public void initClient() {
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.TURBULENT_CAVITATION.get(),
                        List.of(),
                        R.textures.pressure_lock_icon,
                        130,
                        104));
    }

    public static final class Client {
        public static AbilitySystemClient.SkillInfo SKILL_INFO;

        private Client() {
        }
    }
}
