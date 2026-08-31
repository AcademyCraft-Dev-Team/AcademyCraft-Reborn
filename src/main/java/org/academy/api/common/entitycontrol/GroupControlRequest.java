package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Objects;

/** Server-authoritative request for applying one high-level order to several living entities. */
public record GroupControlRequest(
        ServerPlayer controller,
        Identifier source,
        List<LivingEntity> subjects,
        GroupControlCommand command,
        int priority,
        GroupControlObserver observer
) {
    public GroupControlRequest(
            ServerPlayer controller,
            Identifier source,
            List<LivingEntity> subjects,
            GroupControlCommand command,
            int priority
    ) {
        this(controller, source, subjects, command, priority, GroupControlObserver.NONE);
    }

    public GroupControlRequest {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(source, "source");
        subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        Objects.requireNonNull(command, "command");
        observer = observer == null ? GroupControlObserver.NONE : observer;
        if (subjects.isEmpty() || subjects.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one non-null subject is required");
        }
    }
}
