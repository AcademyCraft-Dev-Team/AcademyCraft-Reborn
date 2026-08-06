package org.academy.api.common.entitycontrol;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public sealed interface ControlDirective permits ControlDirective.ForceTarget,
        ControlDirective.FreezeAi, ControlDirective.ImpressionAlliance, ControlDirective.MoveTo, ControlDirective.LookAt {
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

    record MoveTo(UUID targetUuid) implements ControlDirective {
        public MoveTo {
            Objects.requireNonNull(targetUuid, "targetUuid");
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
}
