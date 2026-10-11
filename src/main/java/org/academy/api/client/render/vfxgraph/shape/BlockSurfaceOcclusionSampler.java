package org.academy.api.client.render.vfxgraph.shape;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Entity-neutral visual boundary sampler. Three collision rays cover each surface group.
 * Boundaries remain in world space, so cached planes still follow a moving local anchor correctly.
 * This only changes presentation; it never modifies movement, targeting or block state.
 */
public final class BlockSurfaceOcclusionSampler implements SurfaceOcclusionSampler {
    private static final OcclusionQueryBudget SHARED_BUDGET = new OcclusionQueryBudget(48);
    private final Entity actor;
    private final OcclusionQueryBudget budget;
    private final Group[] groups;
    private final Vector3f origin = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf inverseRotation = new Quaternionf();
    private final Vector3f worldRoot = new Vector3f();
    private final Vector3f worldTip = new Vector3f();
    private final Vector3f side = new Vector3f();
    private final Vector3f worldSide = new Vector3f();
    private final Vector3f[] rayStarts = {new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f[] rayEnds = {new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f planeNormal = new Vector3f();
    private boolean visible;
    private int refreshTicks = 4;
    private long queries;
    private long cacheHits;

    private static final class Group {
        final Vector3f[] previousStarts = {new Vector3f(), new Vector3f(), new Vector3f()};
        final Vector3f[] previousEnds = {new Vector3f(), new Vector3f(), new Vector3f()};
        final Vector4f worldPlane = new Vector4f(0, 0, 0, 1);
        long sampledTick = Long.MIN_VALUE;
        long nextTick = Long.MIN_VALUE;
        boolean blocked;
    }

    public BlockSurfaceOcclusionSampler(Entity actor, int groupCount) {
        this(actor, groupCount, SHARED_BUDGET);
    }

    public BlockSurfaceOcclusionSampler(Entity actor, int groupCount, OcclusionQueryBudget budget) {
        if (groupCount < 1 || groupCount > 32) throw new IllegalArgumentException("Invalid surface groups");
        this.actor = java.util.Objects.requireNonNull(actor);
        this.budget = java.util.Objects.requireNonNull(budget);
        groups = new Group[groupCount];
        for (int i = 0; i < groupCount; i++) groups[i] = new Group();
    }

    /** Supply the same rigid transform as the effect anchor, once per frame. */
    public void updateFrame(Vector3fc origin, Quaternionfc rotation, boolean visible, int refreshTicks) {
        if (!origin.isFinite() || !Float.isFinite(rotation.x()) || !Float.isFinite(rotation.y())
                || !Float.isFinite(rotation.z()) || !Float.isFinite(rotation.w()) || rotation.lengthSquared() < .000001f) {
            this.visible = false;
            return;
        }
        this.origin.set(origin);
        this.rotation.set(rotation).normalize();
        inverseRotation.set(this.rotation).conjugate();
        this.visible = visible && !actor.isRemoved();
        this.refreshTicks = Math.clamp(refreshTicks, 1, 20);
    }

    @Override
    public boolean sample(int groupIndex, Vector3fc localRoot, Vector3fc localTip,
                          Vector3fc surfaceNormal, float halfWidth, Vector4f allowedPlane) {
        if (!visible || groupIndex < 0 || groupIndex >= groups.length || !localRoot.isFinite()
                || !localTip.isFinite() || !surfaceNormal.isFinite() || !Float.isFinite(halfWidth)) return false;
        rotation.transform(localRoot, worldRoot).add(origin);
        rotation.transform(localTip, worldTip).add(origin);
        localTip.sub(localRoot, side).cross(surfaceNormal);
        if (side.lengthSquared() > .000001f) side.normalize();
        else side.set(0, 0, 0);
        rotation.transform(side, worldSide).mul(Math.clamp(halfWidth, 0, 2));
        for (int ray = 0; ray < 3; ray++) {
            rayStarts[ray].set(worldRoot).fma(ray - 1, worldSide);
            rayEnds[ray].set(worldTip).fma(ray - 1, worldSide);
        }
        return samplePrepared(groupIndex, allowedPlane);
    }

    @Override
    public boolean sampleFan(int groupIndex, Vector3fc localRoot, Vector3fc centerTip,
                             Vector3fc edgeTipA, Vector3fc edgeTipB, Vector3fc surfaceNormal,
                             float halfWidth, Vector4f allowedPlane) {
        if (!visible || groupIndex < 0 || groupIndex >= groups.length || !localRoot.isFinite()
                || !centerTip.isFinite() || !edgeTipA.isFinite() || !edgeTipB.isFinite()
                || !surfaceNormal.isFinite() || !Float.isFinite(halfWidth)) return false;
        rotation.transform(localRoot, worldRoot).add(origin);
        for (var start : rayStarts) start.set(worldRoot);
        rotation.transform(centerTip, rayEnds[0]).add(origin);
        rotation.transform(edgeTipA, rayEnds[1]).add(origin);
        rotation.transform(edgeTipB, rayEnds[2]).add(origin);
        return samplePrepared(groupIndex, allowedPlane);
    }

    private boolean samplePrepared(int groupIndex, Vector4f allowedPlane) {
        var group = groups[groupIndex];
        long tick = actor.level().getGameTime();
        boolean moved = false;
        if (group.sampledTick != Long.MIN_VALUE) {
            for (int ray = 0; ray < 3; ray++) {
                if (group.previousStarts[ray].distanceSquared(rayStarts[ray]) > .09f
                        || group.previousEnds[ray].distanceSquared(rayEnds[ray]) > .16f) {
                    moved = true;
                    break;
                }
            }
        }
        boolean due = group.sampledTick == Long.MIN_VALUE || tick >= group.nextTick || moved;
        // A fast turn can invalidate a plane, but still cannot cause multiple queries per world tick.
        if (due && group.sampledTick != tick && budget.tryAcquire(actor.level(), tick, 3, group)) {
            query(group);
            for (int ray = 0; ray < 3; ray++) {
                group.previousStarts[ray].set(rayStarts[ray]);
                group.previousEnds[ray].set(rayEnds[ray]);
            }
            group.sampledTick = tick;
            group.nextTick = tick + refreshTicks + Math.floorMod(actor.getId() + groupIndex, 2);
        } else {
            cacheHits++;
        }
        if (!group.blocked) return false;
        planeNormal.set(group.worldPlane.x, group.worldPlane.y, group.worldPlane.z);
        float localOffset = group.worldPlane.w + planeNormal.dot(origin);
        inverseRotation.transform(planeNormal);
        allowedPlane.set(planeNormal, localOffset);
        return true;
    }

    private void query(Group group) {
        group.blocked = false;
        double nearest = Double.POSITIVE_INFINITY;
        for (int ray = 0; ray < 3; ray++) {
            var start = new Vec3(rayStarts[ray].x, rayStarts[ray].y, rayStarts[ray].z);
            var end = new Vec3(rayEnds[ray].x, rayEnds[ray].y, rayEnds[ray].z);
            var hit = actor.level().clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
            queries++;
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            double distance = start.distanceToSqr(hit.getLocation());
            if (distance >= nearest) continue;
            nearest = distance;
            var face = hit.getDirection();
            var point = hit.getLocation();
            float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
            // The allowed side contains the anchor; a tiny inset leaves a soft contact boundary.
            float offset = -(float) (nx * point.x + ny * point.y + nz * point.z) - .015f;
            if (nx * worldRoot.x + ny * worldRoot.y + nz * worldRoot.z + offset < -.02f) {
                nx = -nx; ny = -ny; nz = -nz; offset = -offset;
            }
            group.worldPlane.set(nx, ny, nz, offset);
            group.blocked = true;
        }
    }

    public long queryCount() { return queries; }
    public long cacheHitCount() { return cacheHits; }
    public static int sharedTickQueryCount() { return SHARED_BUDGET.used(); }
    public static int sharedTickQueryLimit() { return SHARED_BUDGET.limit(); }
}
