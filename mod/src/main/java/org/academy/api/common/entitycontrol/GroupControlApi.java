package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public entry point for reusable, adapter-backed group orders.
 */
public final class GroupControlApi {
    /**
     * Implementation-provided backend; registered once during common setup.
     */
    public interface Backend {
        void registerAdapter(Identifier id, int priority, GroupControlAdapter adapter);

        GroupControlResult dispatch(GroupControlRequest request);

        void setWorkPaused(MinecraftServer server, UUID controller, Set<UUID> subjects, boolean paused);

        void cancelWork(MinecraftServer server, UUID controller, Set<UUID> subjects);

        Optional<GroupControlInspection> inspect(LivingEntity subject);

        void cancelByControllerAndSource(UUID controllerId, Identifier source);

        void cancelSubjects(UUID controllerId, Identifier source, Set<UUID> subjectIds);
    }

    private static volatile @Nullable Backend backend;

    private GroupControlApi() {
    }

    public static void registerBackend(Backend backend) {
        GroupControlApi.backend = Objects.requireNonNull(backend);
    }

    public static void registerAdapter(Identifier id, int priority, GroupControlAdapter adapter) {
        requireBackend().registerAdapter(id, priority, adapter);
    }

    public static GroupControlResult dispatch(GroupControlRequest request) {
        return requireBackend().dispatch(request);
    }

    public static void pauseWork(MinecraftServer server, UUID controller,
                                 Set<UUID> subjects, boolean paused) {
        requireBackend().setWorkPaused(server, controller, subjects, paused);
    }

    public static void cancelWork(MinecraftServer server, UUID controller,
                                  Set<UUID> subjects) {
        requireBackend().cancelWork(server, controller, subjects);
    }

    public static Optional<GroupControlInspection> inspect(LivingEntity subject) {
        return requireBackend().inspect(subject);
    }

    /**
     * @deprecated Use {@link MentalControlApi#hasAiTakeover(LivingEntity)}.
     */
    @Deprecated(forRemoval = true)
    public static boolean hasExclusiveWorkOrder(LivingEntity subject) {
        return MentalControlApi.hasAiTakeover(subject);
    }

    public static void cancelByControllerAndSource(
            UUID controllerId,
            Identifier source
    ) {
        requireBackend().cancelByControllerAndSource(controllerId, source);
    }

    public static void cancelSubjects(
            UUID controllerId,
            Identifier source,
            Set<UUID> subjectIds
    ) {
        requireBackend().cancelSubjects(controllerId, source, subjectIds);
    }

    private static Backend requireBackend() {
        var current = backend;
        if (current == null) throw new IllegalStateException("Group control backend is not registered");
        return current;
    }
}
