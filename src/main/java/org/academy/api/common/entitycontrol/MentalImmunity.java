package org.academy.api.common.entitycontrol;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Per-instance immunity sources supplement data-pack entity-type immunity tags. Server thread only. */
public final class MentalImmunity {
    private static final Map<LivingEntity, Map<Identifier, Component>> SOURCES = new WeakHashMap<>();
    private static final Set<UUID> SUPPRESSED = new HashSet<>();
    private MentalImmunity() {}

    public static void set(LivingEntity subject, Identifier source, Component feedback, boolean enabled) {
        if (subject.level().isClientSide() || isSuppressed(subject)) return;
        var values = SOURCES.get(subject);
        if (enabled) {
            if (values == null) values = SOURCES.computeIfAbsent(subject, _ -> new LinkedHashMap<>());
            var first = values.put(source, feedback.copy()) == null;
            if (first) MentalControlRuntime.releaseBySubject(subject.level().getServer(), subject.getUUID());
        } else if (values != null) {
            values.remove(source);
            if (values.isEmpty()) SOURCES.remove(subject);
        }
    }

    public static boolean isImmune(LivingEntity subject) {
        return !isSuppressed(subject)
                && (subject.getType().builtInRegistryHolder().is(MentalControlTags.IMMUNE)
                || feedback(subject).isPresent());
    }

    public static Optional<Component> feedback(LivingEntity subject) {
        if (isSuppressed(subject)) return Optional.empty();
        var values = SOURCES.get(subject);
        return values == null ? Optional.empty() : values.values().stream().findFirst().map(Component::copy);
    }

    /**
     * Permanently (for this life) strips every mental immunity and resistance source from the
     * subject, including data-pack tags, instance sources and skill-provided protection.
     */
    public static void suppress(LivingEntity subject) {
        if (subject == null || subject.level().isClientSide()) return;
        SOURCES.remove(subject);
        SUPPRESSED.add(subject.getUUID());
        MentalControlRuntime.releaseBySubject(subject.level().getServer(), subject.getUUID());
    }

    public static boolean isSuppressed(LivingEntity subject) {
        return subject != null && SUPPRESSED.contains(subject.getUUID());
    }

    /** Restores a suppressed subject, used when players die or respawn. */
    public static void restore(LivingEntity subject) {
        if (subject != null) restore(subject.getUUID());
    }

    public static void restore(UUID subjectId) {
        if (subjectId != null) SUPPRESSED.remove(subjectId);
    }

    public static void clear() {
        SOURCES.clear();
        SUPPRESSED.clear();
    }
}
