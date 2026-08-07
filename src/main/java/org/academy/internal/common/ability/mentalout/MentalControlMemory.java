package org.academy.internal.common.ability.mentalout;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;

import java.util.UUID;

public final class MentalControlMemory {
    public static final String TAG_PREFIX = "academy.mentalout_controlled_by.";

    private MentalControlMemory() {
    }

    public static void remember(ServerPlayer controller, LivingEntity subject) {
        if (controller == null || !(subject instanceof Mob) || MentalControlRuntime.isProtectedTarget(subject)) {
            return;
        }
        subject.addTag(tag(controller.getUUID()));
    }

    public static boolean wasControlledBy(Mob subject, UUID controllerId) {
        return subject != null && controllerId != null && subject.entityTags().contains(tag(controllerId));
    }

    public static String tag(UUID controllerId) {
        return TAG_PREFIX + controllerId;
    }
}
