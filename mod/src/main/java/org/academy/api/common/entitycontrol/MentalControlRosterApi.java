package org.academy.api.common.entitycontrol;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Stable public access to the Mentalout controller's retained subject roster.
 */
public final class MentalControlRosterApi {
    /**
     * Public per-candidate outcome of {@link #enroll}, independent of internal toggle states.
     */
    public enum RosterEnrollmentOutcome {
        ADDED,
        ALREADY_CONTROLLED,
        REJECTED,
        UNSUPPORTED,
        INSUFFICIENT_CP,
        INVALID
    }

    /**
     * Implementation-provided backend; registered once during common setup.
     */
    public interface Backend {
        List<LivingEntity> subjects(ServerPlayer controller);

        Set<UUID> subjectIds(ServerPlayer controller);

        RosterEnrollmentOutcome addTarget(ServerPlayer controller, LivingEntity target);

        void releaseInterventionSubjects(ServerPlayer controller, Set<UUID> subjectIds);
    }

    private static volatile @Nullable Backend backend;

    private MentalControlRosterApi() {
    }

    public static void registerBackend(Backend backend) {
        MentalControlRosterApi.backend = Objects.requireNonNull(backend);
    }

    public static List<LivingEntity> subjects(ServerPlayer controller) {
        return requireBackend().subjects(controller);
    }

    public static EnrollmentBatch enroll(
            ServerPlayer controller,
            List<? extends LivingEntity> candidates
    ) {
        var added = 0;
        var alreadyControlled = 0;
        var rejected = 0;
        var results = new ArrayList<RosterEnrollmentOutcome>();
        var existing = new HashSet<>(requireBackend().subjectIds(controller));
        for (var candidate : List.copyOf(candidates)) {
            if (existing.contains(candidate.getUUID())) {
                alreadyControlled++;
                continue;
            }
            var result = requireBackend().addTarget(controller, candidate);
            results.add(result);
            if (result == RosterEnrollmentOutcome.ADDED) {
                added++;
                existing = new HashSet<>(requireBackend().subjectIds(controller));
            } else {
                rejected++;
            }
        }
        return new EnrollmentBatch(added, alreadyControlled, rejected, results);
    }

    /**
     * Releases the requested subjects through the same path used by Mental Intervention.
     */
    public static int release(ServerPlayer controller, Set<UUID> subjectIds) {
        if (subjectIds.isEmpty()) return 0;
        var controlled = new HashSet<UUID>();
        for (var id : requireBackend().subjectIds(controller)) {
            if (subjectIds.contains(id)) controlled.add(id);
        }
        if (controlled.isEmpty()) return 0;
        requireBackend().releaseInterventionSubjects(controller, Set.copyOf(controlled));
        return controlled.size();
    }

    public record EnrollmentBatch(
            int added,
            int alreadyControlled,
            int rejected,
            List<RosterEnrollmentOutcome> results
    ) {
        public EnrollmentBatch {
            results = List.copyOf(results);
        }
    }

    private static Backend requireBackend() {
        var current = backend;
        if (current == null) throw new IllegalStateException("Mental roster backend is not registered");
        return current;
    }
}
