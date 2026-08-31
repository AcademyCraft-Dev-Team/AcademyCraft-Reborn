package org.academy.api.common.entitycontrol;

import java.util.Objects;

/** High-level orders that a compatible entity-control adapter may execute autonomously. */
public sealed interface GroupControlCommand permits GroupControlCommand.MoveTo,
        GroupControlCommand.GatherResources, GroupControlCommand.Farm {
    record MoveTo(ControlDestination destination) implements GroupControlCommand {
        public MoveTo {
            Objects.requireNonNull(destination, "destination");
        }
    }

    record GatherResources(BlockWorkRegion region) implements GroupControlCommand {
        public GatherResources {
            Objects.requireNonNull(region, "region");
        }
    }

    record Farm(BlockWorkRegion region) implements GroupControlCommand {
        public Farm {
            Objects.requireNonNull(region, "region");
        }
    }
}
