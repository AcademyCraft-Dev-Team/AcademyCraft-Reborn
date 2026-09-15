package org.academy.api.server.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;

/** Learned capability, independent of a hybrid skill's individual mode switches. */
public final class SkillAvailability {
    private SkillAvailability() {}

    public static boolean isLearnedAndAvailable(ServerPlayer subject, Skill skill) {
        return SkillTuning.isSkillEnabled(subject, skill)
                && LearningHelper.isSkillAvailableForCategory(AbilitySystemServer.getSystem(subject)
                .getPlayerAbilityCategory(subject.getUUID()), skill)
                && skill.getRuntimeData(subject).map(data -> data.isEnabled()).orElse(false);
    }
    /** Applies the same paralysis and external cancellation gate as ordinary skill casts. */
    public static boolean requestExecution(ServerPlayer subject, Skill skill, boolean continuous) {
        if (!isLearnedAndAvailable(subject, skill) || !subject.isAlive() || subject.isSpectator()) return false;
        var event = new org.academy.api.common.ability.event.SkillExecutionPreEvent(skill, subject, continuous);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }
}
