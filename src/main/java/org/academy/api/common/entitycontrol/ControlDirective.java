package org.academy.api.common.entitycontrol;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public sealed interface ControlDirective permits ControlDirective.ForceTarget,
        ControlDirective.FreezeAi, ControlDirective.ImpressionAlliance, ControlDirective.MoveTo,
        ControlDirective.LookAt, ControlDirective.DirectControl, ControlDirective.Guard {
    ControlCapability capability();

    default Set<ControlDomain> domains() {
        return capability().domains();
    }

    record ForceTarget(UUID targetUuid) implements ControlDirective {
        public ForceTarget {
            Objects.requireNonNull(targetUuid, "targetUuid");
        }

        @Override
        public ControlCapability capability() {
            return ControlCapability.FORCE_TARGET;
        }
    }

    record FreezeAi() implements ControlDirective {
        @Override
        public ControlCapability capability() {
            return ControlCapability.FREEZE_AI;
        }
    }

    record ImpressionAlliance() implements ControlDirective {
        @Override
        public ControlCapability capability() {
            return ControlCapability.RELATION_CONTROL;
        }
    }

    record MoveTo(ControlDestination destination, double arrivalRadius) implements ControlDirective {
        public MoveTo {
            Objects.requireNonNull(destination, "destination");
            if (!Double.isFinite(arrivalRadius) || arrivalRadius <= 0.0) {
                throw new IllegalArgumentException("arrivalRadius must be positive and finite");
            }
        }

        public MoveTo(ControlDestination destination) {
            this(destination, 1.0);
        }

        public MoveTo(UUID targetUuid) {
            this(new ControlDestination.Entity(targetUuid), 1.0);
        }

        public UUID targetUuid() {
            return destination instanceof ControlDestination.Entity(UUID uuid) ? uuid : null;
        }

        @Override
        public ControlCapability capability() {
            return ControlCapability.PATH_CONTROL;
        }
    }

    record LookAt(UUID targetUuid) implements ControlDirective {
        public LookAt {
            Objects.requireNonNull(targetUuid, "targetUuid");
        }

        @Override
        public ControlCapability capability() {
            return ControlCapability.VIEW_CONTROL;
        }
    }

    record DirectControl() implements ControlDirective {
        @Override
        public ControlCapability capability() {
            return ControlCapability.DIRECT_CONTROL;
        }
    }

    record Guard(ControlDestination destination, double detectionRadius, double arrivalRadius)
            implements ControlDirective {
        public Guard {
            Objects.requireNonNull(destination, "destination");
            if (!Double.isFinite(detectionRadius) || detectionRadius <= 0.0
                    || !Double.isFinite(arrivalRadius) || arrivalRadius <= 0.0) {
                throw new IllegalArgumentException("Guard radii must be positive and finite");
            }
        }

        public Guard(ControlDestination destination) {
            this(destination, 16.0, 1.0);
        }

        @Override
        public ControlCapability capability() {
            return ControlCapability.GUARD_CONTROL;
        }
    }
}
