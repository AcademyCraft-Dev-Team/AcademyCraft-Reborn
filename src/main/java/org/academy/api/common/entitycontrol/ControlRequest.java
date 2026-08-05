package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Objects;

public record ControlRequest(
        ServerPlayer controller,
        LivingEntity subject,
        Identifier source,
        int priority,
        long expiresAt,
        List<ControlDirective> directives
) {
    public ControlRequest {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(source, "source");
        directives = List.copyOf(Objects.requireNonNull(directives, "directives"));
        if (expiresAt < 0L) {
            throw new IllegalArgumentException("expiresAt must not be negative");
        }
        if (directives.isEmpty()) {
            throw new IllegalArgumentException("At least one control directive is required");
        }
        if (directives.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Control directives must not contain null values");
        }
    }

    public static ControlRequest permanent(
            ServerPlayer controller,
            LivingEntity subject,
            Identifier source,
            int priority,
            List<ControlDirective> directives
    ) {
        return new ControlRequest(controller, subject, source, priority, Long.MAX_VALUE, directives);
    }

    public static ControlRequest permanent(
            ServerPlayer controller,
            LivingEntity subject,
            Identifier source,
            int priority,
            ControlDirective... directives
    ) {
        return permanent(controller, subject, source, priority, List.of(directives));
    }
}
