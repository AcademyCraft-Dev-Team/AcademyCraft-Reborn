package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;
import java.util.UUID;

public record ControlContext(
        MinecraftServer server,
        UUID leaseId,
        ServerPlayer controller,
        LivingEntity subject,
        Identifier source,
        UUID scopeId,
        int priority,
        long expiresAt
) {
    public ControlContext {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(leaseId, "leaseId");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scopeId, "scopeId");
    }

    public ControlContext(
            MinecraftServer server,
            UUID leaseId,
            ServerPlayer controller,
            LivingEntity subject,
            Identifier source,
            int priority,
            long expiresAt
    ) {
        this(
                server,
                leaseId,
                controller,
                subject,
                source,
                ControlRequest.DEFAULT_SCOPE,
                priority,
                expiresAt
        );
    }
}
