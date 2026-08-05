package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

public record ControlEvaluation(
        ControlCapability capability,
        ControlSupport support,
        ControlRejectionReason reason,
        Optional<Identifier> adapterId,
        int adapterPriority
) {
    public ControlEvaluation {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(reason, "reason");
        adapterId = Objects.requireNonNull(adapterId, "adapterId");
    }

    public boolean supported() {
        return support.isSupported() && reason == ControlRejectionReason.SUPPORTED && adapterId.isPresent();
    }
}
