package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.BlockStructureOrientedBox;

import java.util.Objects;

/** Runtime description of a plot-backed structure; physics state lives in the universe. */
public final class SubLevel {
    private final SubLevelId id;
    private final PlotPos plot;
    private final AABB localBounds;
    private StructurePose pose;
    private StructurePose previousPose;
    private SubLevelState state = SubLevelState.ALLOCATED;
    private long revision;

    public SubLevel(SubLevelId id, PlotPos plot, AABB localBounds, StructurePose pose) {
        this.id = Objects.requireNonNull(id, "id");
        this.plot = Objects.requireNonNull(plot, "plot");
        this.localBounds = Objects.requireNonNull(localBounds, "localBounds");
        this.pose = Objects.requireNonNull(pose, "pose");
        this.previousPose = pose;
    }

    public SubLevelId id() { return id; }
    public PlotPos plot() { return plot; }
    public AABB localBounds() { return localBounds; }
    public StructurePose pose() { return pose; }
    public StructurePose previousPose() { return previousPose; }
    public SubLevelState state() { return state; }
    public long revision() { return revision; }

    public void setPose(StructurePose pose) {
        this.previousPose = this.pose;
        this.pose = Objects.requireNonNull(pose, "pose");
        revision = Math.incrementExact(revision);
    }

    public void setState(SubLevelState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public AABB worldBounds() {
        return BlockStructureOrientedBox.transform(localBounds, pose).bounds();
    }

    public Vec3 worldCenter() {
        return pose.localToWorldPoint(localBounds.getCenter());
    }
}
