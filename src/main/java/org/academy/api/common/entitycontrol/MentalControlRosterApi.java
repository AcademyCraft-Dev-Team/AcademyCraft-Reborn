package org.academy.api.common.entitycontrol;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.mentalout.MentaloutControlContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stable public access to the Mentalout controller's retained subject roster. */
public final class MentalControlRosterApi {
    private MentalControlRosterApi() {
    }

    public static List<LivingEntity> subjects(ServerPlayer controller) {
        return MentaloutControlContext.subjects(controller);
    }

    public static EnrollmentBatch enroll(
            ServerPlayer controller,
            List<? extends LivingEntity> candidates
    ) {
        var added = 0;
        var alreadyControlled = 0;
        var rejected = 0;
        var results = new ArrayList<MentaloutControlContext.ToggleResult>();
        var existing = MentaloutControlContext.get(controller);
        for (var candidate : List.copyOf(candidates)) {
            if (candidate == null) {
                rejected++;
                continue;
            }
            if (existing != null && existing.contains(candidate.getUUID())) {
                alreadyControlled++;
                continue;
            }
            var result = MentaloutControlContext.addTarget(controller, candidate);
            results.add(result);
            if (result == MentaloutControlContext.ToggleResult.ADDED) {
                added++;
                existing = MentaloutControlContext.get(controller);
            } else {
                rejected++;
            }
        }
        return new EnrollmentBatch(added, alreadyControlled, rejected, results);
    }

    /** Releases the requested subjects through the same path used by Mental Intervention. */
    public static int release(ServerPlayer controller, Set<UUID> subjectIds) {
        if (controller == null || subjectIds == null || subjectIds.isEmpty()) return 0;
        var controlled = MentaloutControlContext.subjects(controller).stream()
                .map(LivingEntity::getUUID)
                .filter(subjectIds::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        MentaloutControlContext.releaseInterventionSubjects(controller, controlled);
        return controlled.size();
    }

    public record EnrollmentBatch(
            int added,
            int alreadyControlled,
            int rejected,
            List<MentaloutControlContext.ToggleResult> results
    ) {
        public EnrollmentBatch {
            results = List.copyOf(results);
        }
    }
}
