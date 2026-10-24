package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.BlockStructureOrientedBox;

import java.util.Objects;

/** Runtime description of a plot-backed structure; physics state lives in the universe. */
public final class SubLevel {
    private final SubLevelId id;
    private PlotPos plot;
    private AABB localBounds;
    private StructurePose pose;
    private StructurePose previousPose;
    private StructurePose carryPose;
    private SubLevelState state = SubLevelState.ALLOCATED;
    private long revision;
    private long geometryRevision;

    public SubLevel(SubLevelId id, PlotPos plot, AABB localBounds, StructurePose pose) {
        this.id = Objects.requireNonNull(id, "id");
        this.plot = Objects.requireNonNull(plot, "plot");
        this.localBounds = Objects.requireNonNull(localBounds, "localBounds");
        this.pose = Objects.requireNonNull(pose, "pose");
        this.previousPose = pose;
        this.carryPose = pose;
    }

    public SubLevelId id() { return id; }
    public PlotPos plot() { return plot; }
    public AABB localBounds() { return localBounds; }
    public StructurePose pose() { return pose; }
    public StructurePose previousPose() { return previousPose; }
    public StructurePose carryPose() { return carryPose; }
    public SubLevelState state() { return state; }
    public long revision() { return revision; }
    public long geometryRevision() { return geometryRevision; }

    void setLocalBounds(AABB bounds) { localBounds = Objects.requireNonNull(bounds); }
    void relocateStorage(PlotPos destination) { plot = Objects.requireNonNull(destination); }

    public void geometryChanged() {
        geometryRevision = Math.incrementExact(geometryRevision);
        revision = Math.incrementExact(revision);
    }

    public void setPose(StructurePose pose) {
        this.previousPose = this.pose;
        this.pose = Objects.requireNonNull(pose, "pose");
        revision = Math.incrementExact(revision);
    }

    /** Consumes only the carry delta; previousPose remains available to delayed interaction validation. */
    public void consumeCarryPose() {
        carryPose = pose;
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
