package org.academy.api.common.entitycontrol;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class MentalPerceptionApi {
    /**
     * Implementation-provided backend; registered once during common setup.
     */
    public interface Backend {
        PerceptionDecision decision(LivingEntity observer, LivingEntity target);
    }

    private static volatile @Nullable Backend backend;

    private MentalPerceptionApi() {
    }

    public static void registerBackend(Backend backend) {
        MentalPerceptionApi.backend = Objects.requireNonNull(backend);
    }

    public static PerceptionDecision perceptionDecision(LivingEntity observer, LivingEntity target) {
        return requireBackend().decision(observer, target);
    }

    public static boolean canPerceive(LivingEntity observer, LivingEntity target) {
        return perceptionDecision(observer, target) != PerceptionDecision.HIDDEN;
    }

    private static Backend requireBackend() {
        var current = backend;
        if (current == null) throw new IllegalStateException("Mental perception backend is not registered");
        return current;
    }
}
