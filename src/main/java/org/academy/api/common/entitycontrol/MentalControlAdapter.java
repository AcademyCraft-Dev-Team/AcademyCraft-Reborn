package org.academy.api.common.entitycontrol;

import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;
import java.util.Set;

public interface MentalControlAdapter {
    boolean matches(LivingEntity subject);

    ControlSupport support(LivingEntity subject, ControlCapability capability);

    default ControlRejectionReason rejectionReason(
            LivingEntity subject,
            ControlCapability capability
    ) {
        return support(subject, capability).isSupported()
                ? ControlRejectionReason.SUPPORTED
                : ControlRejectionReason.UNSUPPORTED_CAPABILITY;
    }

    ControlBinding activate(ControlContext context, ControlDirective directive);

    default boolean supports(LivingEntity subject) {
        return matches(subject);
    }

    default boolean supports(LivingEntity subject, ControlCapability capability) {
        return matches(subject) && support(subject, capability).isSupported();
    }

    default Set<ControlCapability> capabilities(LivingEntity subject) {
        if (!matches(subject)) return Set.of();
        var capabilities = EnumSet.noneOf(ControlCapability.class);
        for (var capability : ControlCapability.values()) {
            if (support(subject, capability).isSupported()) capabilities.add(capability);
        }
        return Set.copyOf(capabilities);
    }
}
