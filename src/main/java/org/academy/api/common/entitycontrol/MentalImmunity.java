package org.academy.api.common.entitycontrol;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Per-instance immunity sources supplement data-pack entity-type immunity tags. Server thread only. */
public final class MentalImmunity {
    private static final Map<LivingEntity, Map<Identifier, Component>> SOURCES = new WeakHashMap<>();
    private MentalImmunity() {}

    public static void set(LivingEntity subject, Identifier source, Component feedback, boolean enabled) {
        if (subject.level().isClientSide()) return;
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
        return subject.getType().builtInRegistryHolder().is(MentalControlTags.IMMUNE)
                || feedback(subject).isPresent();
    }

    public static Optional<Component> feedback(LivingEntity subject) {
        var values = SOURCES.get(subject);
        return values == null ? Optional.empty() : values.values().stream().findFirst().map(Component::copy);
    }
}
