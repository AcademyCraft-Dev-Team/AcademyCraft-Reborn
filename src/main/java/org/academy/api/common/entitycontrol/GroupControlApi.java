package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.mentalout.control.GroupControlRuntime;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public entry point for reusable, adapter-backed group orders. */
public final class GroupControlApi {
    private GroupControlApi() {
    }

    public static void registerAdapter(Identifier id, int priority, GroupControlAdapter adapter) {
        GroupControlRuntime.registerAdapter(id, priority, adapter);
    }

    public static GroupControlResult dispatch(GroupControlRequest request) {
        return GroupControlRuntime.dispatch(request);
    }

    public static void pauseWork(MinecraftServer server, UUID controller,
                                 Set<UUID> subjects, boolean paused) {
        GroupControlRuntime.setWorkPaused(server, controller, subjects, paused);
    }

    public static void cancelWork(MinecraftServer server, UUID controller,
                                  Set<UUID> subjects) {
        GroupControlRuntime.cancelWork(server, controller, subjects);
    }

    public static Optional<GroupControlInspection> inspect(LivingEntity subject) {
        return GroupControlRuntime.inspect(subject);
    }

    /** @deprecated Use {@link MentalControlApi#hasAiTakeover(LivingEntity)}. */
    @Deprecated(forRemoval = true)
    public static boolean hasExclusiveWorkOrder(LivingEntity subject) {
        return MentalControlApi.hasAiTakeover(subject);
    }

    public static void cancelByControllerAndSource(
            UUID controllerId,
            Identifier source
    ) {
        GroupControlRuntime.cancelByControllerAndSource(controllerId, source);
    }

    public static void cancelSubjects(
            UUID controllerId,
            Identifier source,
            Set<UUID> subjectIds
    ) {
        GroupControlRuntime.cancelSubjects(controllerId, source, subjectIds);
    }
}
