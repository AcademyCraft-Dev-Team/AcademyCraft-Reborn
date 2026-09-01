package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.mentalout.control.GroupControlRuntime;

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

    public static java.util.Optional<GroupControlInspection> inspect(LivingEntity subject) {
        return GroupControlRuntime.inspect(subject);
    }

    /** @deprecated Use {@link MentalControlApi#hasAiTakeover(LivingEntity)}. */
    @Deprecated(forRemoval = true)
    public static boolean hasExclusiveWorkOrder(LivingEntity subject) {
        return MentalControlApi.hasAiTakeover(subject);
    }

    public static void cancelByControllerAndSource(
            java.util.UUID controllerId,
            Identifier source
    ) {
        GroupControlRuntime.cancelByControllerAndSource(controllerId, source);
    }

    public static void cancelSubjects(
            java.util.UUID controllerId,
            Identifier source,
            java.util.Set<java.util.UUID> subjectIds
    ) {
        GroupControlRuntime.cancelSubjects(controllerId, source, subjectIds);
    }
}
