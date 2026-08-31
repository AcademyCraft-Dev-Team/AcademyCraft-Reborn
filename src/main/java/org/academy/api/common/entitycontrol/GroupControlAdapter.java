package org.academy.api.common.entitycontrol;

import net.minecraft.world.entity.LivingEntity;

/**
 * Extension point for entity types that provide specialized movement, harvesting, or farming.
 */
public interface GroupControlAdapter {
    boolean supports(LivingEntity subject, GroupControlCommand command);

    GroupControlHandle start(GroupControlRequest request, LivingEntity subject, int subjectIndex);
}
