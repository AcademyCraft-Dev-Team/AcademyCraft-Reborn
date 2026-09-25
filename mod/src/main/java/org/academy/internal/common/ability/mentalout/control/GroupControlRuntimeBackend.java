package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.entitycontrol.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Binds the public {@link GroupControlApi} facade to the internal group control runtime.
 */
public final class GroupControlRuntimeBackend implements GroupControlApi.Backend {
    @Override
    public void registerAdapter(Identifier id, int priority, GroupControlAdapter adapter) {
        GroupControlRuntime.registerAdapter(id, priority, adapter);
    }

    @Override
    public GroupControlResult dispatch(GroupControlRequest request) {
        return GroupControlRuntime.dispatch(request);
    }

    @Override
    public void setWorkPaused(MinecraftServer server, UUID controller, Set<UUID> subjects, boolean paused) {
        GroupControlRuntime.setWorkPaused(server, controller, subjects, paused);
    }

    @Override
    public void cancelWork(MinecraftServer server, UUID controller, Set<UUID> subjects) {
        GroupControlRuntime.cancelWork(server, controller, subjects);
    }

    @Override
    public Optional<GroupControlInspection> inspect(LivingEntity subject) {
        return GroupControlRuntime.inspect(subject);
    }

    @Override
    public void cancelByControllerAndSource(UUID controllerId, Identifier source) {
        GroupControlRuntime.cancelByControllerAndSource(controllerId, source);
    }

    @Override
    public void cancelSubjects(UUID controllerId, Identifier source, Set<UUID> subjectIds) {
        GroupControlRuntime.cancelSubjects(controllerId, source, subjectIds);
    }
}
