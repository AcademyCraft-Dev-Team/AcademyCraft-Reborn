package org.academy.internal.common.ability.teleport.skills.lv1;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;

import java.util.List;

public final class SpaceFoldingTheorem extends Skill {
    public static final float DAMAGE_MULTIPLIER = 1.25f;

    public SpaceFoldingTheorem() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .passive()
                .iterationTicks(40)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.THREATENING_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
                .devCondition(new DevCondition.DependencyCondition(
                        "Threatening Teleport",
                        "academy:threatening_teleport"
                ))
        );
    }

    public static float damageMultiplier(ServerPlayer player) {
        var skill = Skills.SPACE_FOLDING_THEOREM.get();
        if (!skill.isEnabled(player)) return 1.0f;
        return switch (skill.getEffectiveProficiencyMilestone(player)) {
            case 1 -> 1.30f;
            case 2 -> 1.35f;
            case 3 -> 1.40f;
            default -> DAMAGE_MULTIPLIER;
        };
    }

    public static void refundKillCost(ServerPlayer player, float actualCost) {
        var skill = Skills.SPACE_FOLDING_THEOREM.get();
        if (!skill.isEnabled(player) || !skill.hasProficiencyMilestone(player, 3)
                || !(actualCost > 0.0f) || !Float.isFinite(actualCost)) return;
        var now = player.level().getGameTime();
        if (TimedSkillEffectRuntime.get(player.getUUID(), player.getUUID(), skill,
                "kill_refund_cooldown", now).isPresent()) return;
        TimedSkillEffectRuntime.put(player, player.getUUID(), skill,
                "kill_refund_cooldown", 60, 1.0f);
        var system = AbilitySystemServer.getSystem(player);
        var id = player.getUUID();
        system.setPlayerAvailableCP(id, Math.min(
                system.getPlayerMaxCP(id),
                system.getPlayerAvailableCP(id) + actualCost * 0.2f
        ));
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
