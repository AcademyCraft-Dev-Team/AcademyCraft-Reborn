package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record ControlInspection(
        ControlCapability capability,
        UUID leaseId,
        UUID controllerId,
        Identifier source,
        UUID scopeId,
        int priority,
        long expiresAt,
        ControlDirective directive
) {
    public ControlInspection {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(leaseId, "leaseId");
        Objects.requireNonNull(controllerId, "controllerId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(directive, "directive");
        if (directive.capability() != capability) {
            throw new IllegalArgumentException("Directive does not match the inspected capability");
        }
    }

    public ControlInspection(
            ControlCapability capability,
            UUID leaseId,
            UUID controllerId,
            Identifier source,
            int priority,
            long expiresAt,
            ControlDirective directive
    ) {
        this(
                capability,
                leaseId,
                controllerId,
                source,
                ControlRequest.DEFAULT_SCOPE,
                priority,
                expiresAt,
                directive
        );
    }
}
