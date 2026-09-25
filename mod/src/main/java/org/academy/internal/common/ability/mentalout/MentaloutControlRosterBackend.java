package org.academy.internal.common.ability.mentalout;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.entitycontrol.MentalControlRosterApi;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Binds the public {@link MentalControlRosterApi} facade to the internal Mentalout roster.
 */
public final class MentaloutControlRosterBackend implements MentalControlRosterApi.Backend {
    @Override
    public List<LivingEntity> subjects(ServerPlayer controller) {
        return MentaloutControlContext.subjects(controller);
    }

    @Override
    public Set<UUID> subjectIds(ServerPlayer controller) {
        return MentaloutControlContext.subjects(controller).stream()
                .map(LivingEntity::getUUID)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public MentalControlRosterApi.RosterEnrollmentOutcome addTarget(
            ServerPlayer controller,
            LivingEntity target
    ) {
        return switch (MentaloutControlContext.addTarget(controller, target)) {
            case ADDED -> MentalControlRosterApi.RosterEnrollmentOutcome.ADDED;
            case UNSUPPORTED -> MentalControlRosterApi.RosterEnrollmentOutcome.UNSUPPORTED;
            case INSUFFICIENT_CP -> MentalControlRosterApi.RosterEnrollmentOutcome.INSUFFICIENT_CP;
            case INVALID -> MentalControlRosterApi.RosterEnrollmentOutcome.INVALID;
            case REMOVED -> MentalControlRosterApi.RosterEnrollmentOutcome.REJECTED;
        };
    }

    @Override
    public void releaseInterventionSubjects(ServerPlayer controller, Set<UUID> subjectIds) {
        MentaloutControlContext.releaseInterventionSubjects(controller, subjectIds);
    }
}
