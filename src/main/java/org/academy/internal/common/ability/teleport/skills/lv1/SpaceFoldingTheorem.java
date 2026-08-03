package org.academy.internal.common.ability.teleport.skills.lv1;

import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

import java.util.List;

public final class SpaceFoldingTheorem extends Skill {
    public static final float DAMAGE_MULTIPLIER = 1.25f;

    public SpaceFoldingTheorem() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .passive()
                .dependsOn(Skills.THREATENING_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
                .devCondition(new DevCondition.DependencyCondition(
                        "Threatening Teleport",
                        "academy:threatening_teleport"
                ))
        );
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.SPACE_FOLDING_THEOREM.get(),
                        List.of(ThreateningTeleport.Client.SKILL_INFO),
                        R.textures.space_folding_theorem_icon,
                        60,
                        17.5f
                )
        );

        private Client() {
        }

        private static void initialize() {
        }
    }
}
