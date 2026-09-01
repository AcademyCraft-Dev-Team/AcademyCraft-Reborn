package org.academy.api.common.entitycontrol;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable view of all effective control capabilities for one entity at one server tick. */
public record ControlSnapshot(
        UUID subjectId,
        long gameTime,
        Map<ControlCapability, ControlInspection> controls
) {
    public ControlSnapshot {
        Objects.requireNonNull(subjectId, "subjectId");
        var copy = new EnumMap<ControlCapability, ControlInspection>(ControlCapability.class);
        copy.putAll(Objects.requireNonNull(controls, "controls"));
        controls = Collections.unmodifiableMap(copy);
    }

    public Optional<ControlInspection> control(ControlCapability capability) {
        return Optional.ofNullable(controls.get(capability));
    }
}
