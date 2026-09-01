package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ControlRequest(
        ServerPlayer controller,
        LivingEntity subject,
        Identifier source,
        UUID scopeId,
        int priority,
        long expiresAt,
        List<ControlDirective> directives
) {
    public static final UUID DEFAULT_SCOPE = new UUID(0L, 0L);

    public ControlRequest {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scopeId, "scopeId");
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

    /**
     * Compatibility constructor. Requests with the same controller, subject and source replace
     * one another just as they did before scoped sessions were introduced.
     */
    public ControlRequest(
            ServerPlayer controller,
            LivingEntity subject,
            Identifier source,
            int priority,
            long expiresAt,
            List<ControlDirective> directives
    ) {
        this(controller, subject, source, DEFAULT_SCOPE, priority, expiresAt, directives);
    }

    public static ControlRequest permanent(
            ServerPlayer controller,
            LivingEntity subject,
            Identifier source,
            int priority,
            List<ControlDirective> directives
    ) {
        return new ControlRequest(
                controller, subject, source, DEFAULT_SCOPE, priority, Long.MAX_VALUE, directives);
    }

    public static ControlRequest scopedPermanent(
            ServerPlayer controller,
            LivingEntity subject,
            Identifier source,
            UUID scopeId,
            int priority,
            List<ControlDirective> directives
    ) {
        return new ControlRequest(
                controller, subject, source, scopeId, priority, Long.MAX_VALUE, directives);
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
