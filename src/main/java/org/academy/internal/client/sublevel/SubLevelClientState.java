package org.academy.internal.client.sublevel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.network.SubLevelPosePacket;
import org.academy.internal.common.network.SubLevelStatePacket;
import org.academy.internal.common.sublevel.PlotPos;
import org.academy.internal.common.sublevel.SubLevelCollisionIndex;
import org.academy.internal.common.sublevel.SubLevelCollisionRuntime;
import org.academy.internal.common.sublevel.SubLevelContactResolver;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client snapshots, projections and collision share the current client level lifetime.
 * Snapshot boxes are structure-local; the client builds oriented contact data from the pose.
 */
public final class SubLevelClientState {
    private static final boolean COLLISION = Boolean.parseBoolean(
            System.getProperty("academy.sublevel.clientCollision", "true"));
    private static final Map<ClientLevel, Map<SubLevelId, Snapshot>> STATES = new WeakHashMap<>();
    private static final Map<ClientLevel, SubLevelCollisionIndex> INDEXES = new WeakHashMap<>();
    private static final Map<ClientLevel, Map<SubLevelId, NativeStructureProjectionEntity>> ENTITIES = new WeakHashMap<>();
    private static final Map<ClientLevel, Map<SubLevelId, StructurePose>> COLLISION_POSES = new WeakHashMap<>();
    private static final Map<Integer, SubLevelId> PROJECTION_IDS = new HashMap<>();
    private static final boolean TELEPORT_DIAGNOSTICS = Boolean.getBoolean("academy.structureTeleportSmoke")
            || Boolean.getBoolean("academy.multiplayerStructureSmoke");
    private static final Map<ClientLevel, Map<SubLevelId, SnapObservation>> TELEPORT_OBSERVATIONS = new WeakHashMap<>();
    private static final boolean PACKET_DIAGNOSTICS = Boolean.getBoolean("academy.subLevelBatchMultiplayerSmoke");
    private static final Map<ClientLevel, long[]> PACKET_COUNTS = new WeakHashMap<>();
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(-1_000_000);
    private static List<CarryFrame> pendingCarry = List.of();
    private static ClientLevel carryLevel;
    private static ClientLevel retainedCurrentLevel;
    private static long discardedOnLevelChange;

    private SubLevelClientState() {
    }

    public static synchronized void accept(SubLevelStatePacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(packet.dimension())) return;
        if (PACKET_DIAGNOSTICS) {
            var counts = PACKET_COUNTS.computeIfAbsent(level, ignored -> new long[5]);
            counts[packet.removed() ? 4 : 0]++;
        }
        var states = STATES.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        var entities = ENTITIES.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (packet.removed()) {
            states.remove(packet.id());
            var removed = entities.remove(packet.id());
            if (removed != null) {
                PROJECTION_IDS.remove(removed.getId());
                removed.discard();
            }
            var index = INDEXES.get(level);
            if (index != null) index.remove(packet.id());
            var poses = COLLISION_POSES.get(level);
            if (poses != null) poses.remove(packet.id());
            var observations = TELEPORT_OBSERVATIONS.get(level);
            if (observations != null) observations.remove(packet.id());
            return;
        }
        var existing = states.get(packet.id());
        var view = packet.fullView() || existing == null ? packet.view() : existing.view();
        states.put(packet.id(), new Snapshot(packet.plot(), packet.pose(), packet.boxes(), view));
        var projection = entities.get(packet.id());
        if (projection == null) {
            projection = new NativeStructureProjectionEntity(EntityTypes.NATIVE_STRUCTURE_PROJECTION.get(), level);
            projection.markClientOnly();
            projection.setId(ENTITY_IDS.getAndDecrement());
            projection.applyClientSnapshot(view, packet.pose(), 1);
            level.addEntity(projection);
            entities.put(packet.id(), projection);
            PROJECTION_IDS.put(projection.getId(), packet.id());
        } else {
            projection.applyClientSnapshot(view, packet.pose(), projection.viewState().revision() + 1);
        }
        if (!COLLISION) return;
        var index = INDEXES.computeIfAbsent(level, ignored -> {
            var created = new SubLevelCollisionIndex();
            SubLevelCollisionRuntime.register(level, created);
            return created;
        });
        var poses = COLLISION_POSES.computeIfAbsent(level, ignored -> new HashMap<>());
        var contactPose = projection.clientContactPose();
        index.update(packet.id(), packet.boxes(), contactPose,
                poses.getOrDefault(packet.id(), contactPose), 0, projection.viewState().revision());
        poses.put(packet.id(), contactPose);
    }

    /** Pose-only delta; the view and local geometry are unchanged. */
    public static synchronized void acceptPose(SubLevelPosePacket packet) {
        var level = Minecraft.getInstance().level;
        if (PACKET_DIAGNOSTICS && level != null && level.dimension().identifier().equals(packet.dimension())) {
            PACKET_COUNTS.computeIfAbsent(level, ignored -> new long[5])[3]++;
        }
        acceptPose(packet.dimension(), packet.id(), packet.pose(), false);
    }

    public static synchronized void acceptPoses(org.academy.internal.common.network.SubLevelPoseBatchPacket packet) {
        var level = Minecraft.getInstance().level;
        if (PACKET_DIAGNOSTICS && level != null && level.dimension().identifier().equals(packet.dimension())) {
            var counts = PACKET_COUNTS.computeIfAbsent(level, ignored -> new long[5]);
            counts[1]++; counts[2] += packet.entries().size();
        }
        for (var entry : packet.entries()) acceptPose(packet.dimension(), entry.id(), entry.pose(), entry.teleport());
    }

    private static void acceptPose(net.minecraft.resources.Identifier dimension, SubLevelId id, StructurePose pose,
                                   boolean teleport) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(dimension)) return;
        var states = STATES.get(level);
        var previous = states == null ? null : states.get(id);
        if (previous == null) return; // A delta before the full snapshot is ignored.
        var updated = new Snapshot(previous.plot(), pose, previous.boxes(), previous.view());
        states.put(id, updated);
        var projection = ENTITIES.getOrDefault(level, Map.of()).get(id);
        if (projection != null) {
            long revision = projection.viewState().revision() + 1;
            if (teleport) projection.applyClientTeleport(previous.view(), pose, revision);
            else projection.applyClientSnapshot(previous.view(), pose, revision);
        }
        if (teleport) {
            pendingCarry = pendingCarry.stream().filter(frame -> !frame.id().equals(id)).toList();
            COLLISION_POSES.computeIfAbsent(level, ignored -> new HashMap<>()).put(id, pose);
            var index = INDEXES.get(level);
            if (index != null) index.update(id, previous.boxes(), pose, pose, 0, 0);
            if (TELEPORT_DIAGNOSTICS && projection != null) {
                var observations = TELEPORT_OBSERVATIONS.computeIfAbsent(level, ignored -> new HashMap<>());
                var old = observations.get(id);
                observations.put(id, new SnapObservation(pose, projection.clientContactPose(), projection.renderPose(0),
                        projection.renderPose(.5f), projection.renderPose(1), old == null ? 1 : old.count() + 1));
            }
        }
        // The collision pose advances in tick(), sampling the renderer's game-tick timeline;
        // using the packet target directly would leave the visible surface behind the contact.
    }

    /**
     * Called once per actual client game tick, before entity movement. Contact samples the same
     * timeline as rendering, with no frame-dependent exponential approach to the target.
     */
    public static synchronized void tick() {
        pendingCarry = List.of();
        carryLevel = null;
        var minecraft = Minecraft.getInstance();
        retainCurrentLevel(minecraft.level);
        if (minecraft.isPaused()) return;
        var level = minecraft.level;
        if (level == null) return;
        var states = STATES.get(level);
        var index = INDEXES.get(level);
        if (states == null) return;
        var poses = COLLISION_POSES.computeIfAbsent(level, ignored -> new HashMap<>());
        var entities = ENTITIES.getOrDefault(level, Map.of());
        var carryFrames = new ArrayList<CarryFrame>();
        for (var entry : states.entrySet()) {
            var projection = entities.get(entry.getKey());
            var next = projection == null ? entry.getValue().pose() : projection.advanceClientPose();
            var current = poses.getOrDefault(entry.getKey(), next);
            poses.put(entry.getKey(), next);
            if (COLLISION && !current.equals(next)) {
                carryFrames.add(new CarryFrame(entry.getKey(), current, next, entry.getValue().boxes()));
            }
            // Even a resting body must clear its previous movement, otherwise carrying replays it.
            if (COLLISION && index != null) {
                index.update(entry.getKey(), entry.getValue().boxes(), next, current, 0, 0);
            }
        }
        poses.keySet().removeIf(id -> !states.containsKey(id));
        carryLevel = level;
        pendingCarry = List.copyOf(carryFrames);
    }

    /** Runs after vanilla records the player's previous position, so carry also interpolates. */
    public static synchronized void carryLocalPlayer(LocalPlayer player) {
        var level = carryLevel;
        if (level == null || player.level() != level || player != Minecraft.getInstance().player) return;
        var frames = pendingCarry;
        pendingCarry = List.of(); // Extra entity ticks must not apply the same pose delta twice.
        var states = STATES.getOrDefault(level, Map.of());
        var carried = new HashSet<net.minecraft.world.entity.Entity>();
        for (var frame : frames) {
            var snapshot = states.get(frame.id());
            if (snapshot == null || snapshot.boxes() != frame.boxes()) continue;
            SubLevelContactResolver.carry(level, player, frame.previous(), frame.current(),
                    frame.boxes(), carried, null);
        }
    }

    /** SubLevel identity of a client projection, used by the crack overlay. */
    public static synchronized SubLevelId subLevelOf(NativeStructureProjectionEntity entity) {
        return entity == null ? null : PROJECTION_IDS.get(entity.getId());
    }

    public static synchronized List<Snapshot> snapshots(ClientLevel level) {
        var states = STATES.get(level);
        return states == null ? List.of() : states.values().stream().toList();
    }

    /** Entity values retain their level, so weak map keys alone cannot release an old world. */
    private static void retainCurrentLevel(ClientLevel current) {
        if (retainedCurrentLevel == current) return;
        retainedCurrentLevel = current;
        var oldLevels = new HashSet<ClientLevel>();
        oldLevels.addAll(STATES.keySet()); oldLevels.addAll(ENTITIES.keySet());
        oldLevels.addAll(INDEXES.keySet()); oldLevels.addAll(COLLISION_POSES.keySet());
        oldLevels.addAll(PACKET_COUNTS.keySet()); oldLevels.addAll(TELEPORT_OBSERVATIONS.keySet());
        for (var level : oldLevels) {
            if (level == current) continue;
            var entities = ENTITIES.remove(level);
            if (entities != null) for (var projection : entities.values()) {
                PROJECTION_IDS.remove(projection.getId());
                projection.discard();
                if (PACKET_DIAGNOSTICS) discardedOnLevelChange++;
            }
            var index = INDEXES.remove(level);
            if (index != null) SubLevelCollisionRuntime.unregister(level, index);
            STATES.remove(level); COLLISION_POSES.remove(level);
            PACKET_COUNTS.remove(level); TELEPORT_OBSERVATIONS.remove(level);
        }
        var liveIds = new HashSet<Integer>();
        if (current != null) for (var projection : ENTITIES.getOrDefault(current, Map.of()).values()) liveIds.add(projection.getId());
        PROJECTION_IDS.keySet().retainAll(liveIds);
        if (carryLevel != current) { pendingCarry = List.of(); carryLevel = null; }
    }

    public record LifecycleDiagnostics(int retainedLevels, int projectionIds, int pendingCarryFrames, long discardedProjections) {}
    public static synchronized LifecycleDiagnostics lifecycleDiagnostics() {
        var levels = new HashSet<ClientLevel>();
        levels.addAll(STATES.keySet()); levels.addAll(ENTITIES.keySet()); levels.addAll(INDEXES.keySet());
        levels.addAll(COLLISION_POSES.keySet()); levels.addAll(PACKET_COUNTS.keySet()); levels.addAll(TELEPORT_OBSERVATIONS.keySet());
        return new LifecycleDiagnostics(levels.size(), PROJECTION_IDS.size(), pendingCarry.size(), discardedOnLevelChange);
    }

    /** Captured at receipt before later simulation samples can move the structure again. */
    public record SnapObservation(StructurePose targetPose, StructurePose contactPose,
                                  StructurePose render0, StructurePose renderHalf, StructurePose render1, long count) {}
    public static synchronized java.util.Optional<SnapObservation> teleportObservation(ClientLevel level, SubLevelId id) {
        return java.util.Optional.ofNullable(TELEPORT_OBSERVATIONS.getOrDefault(level, Map.of()).get(id));
    }

    /** Opt-in, read-only observations for the isolated two-client snapshot fixture. */
    public record Diagnostics(int snapshots, int projections, int contactPoses, long statePackets,
                              long batchPackets, long poseEntries, long legacyPosePackets, long removals) {}
    public static synchronized Diagnostics diagnostics(ClientLevel level) {
        var counts = PACKET_COUNTS.getOrDefault(level, new long[5]);
        return new Diagnostics(STATES.getOrDefault(level, Map.of()).size(), ENTITIES.getOrDefault(level, Map.of()).size(),
                COLLISION_POSES.getOrDefault(level, Map.of()).size(), counts[0], counts[1], counts[2], counts[3], counts[4]);
    }
    public static synchronized java.util.Optional<StructurePose> snapshotPose(ClientLevel level, SubLevelId id) {
        var snapshot = STATES.getOrDefault(level, Map.of()).get(id);
        return snapshot == null ? java.util.Optional.empty() : java.util.Optional.of(snapshot.pose());
    }

    /** State-only lookup for local click feedback; the snapshot remains server authoritative. */
    public static synchronized net.minecraft.world.level.block.state.BlockState blockState(
            ClientLevel level, SubLevelId id, BlockPos local) {
        var snapshot = STATES.getOrDefault(level, Map.of()).get(id);
        return snapshot == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : snapshot.view().getBlockState(local);
    }

    /** Exact state-only raycast; no client-only entity AABB is consulted. */
    public static synchronized Hit raycast(ClientLevel level, Vec3 from, Vec3 to) {
        var states = STATES.get(level);
        if (states == null || from == null || to == null) return null;
        double best = Double.POSITIVE_INFINITY;
        Hit result = null;
        var entities = ENTITIES.getOrDefault(level, Map.of());
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (var entry : states.entrySet()) {
            var snapshot = entry.getValue();
            if (snapshot.view().isEmpty()) continue;
            var projection = entities.get(entry.getKey());
            var pose = projection == null ? snapshot.pose() : projection.renderPose(partialTick);
            var localStart = pose.worldToLocalPoint(from);
            var localEnd = pose.worldToLocalPoint(to);
            var clip = snapshot.view().clip(new ClipContext(localStart, localEnd,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (clip.getType() != HitResult.Type.BLOCK) continue;
            var point = pose.localToWorldPoint(clip.getLocation());
            double distance = from.distanceToSqr(point);
            if (distance >= best) continue;
            var localDirection = clip.getDirection();
            var worldDirection = pose.localToWorldDirection(new Vec3(localDirection.getStepX(),
                    localDirection.getStepY(), localDirection.getStepZ()));
            var face = Direction.getNearest((int) Math.round(worldDirection.x),
                    (int) Math.round(worldDirection.y), (int) Math.round(worldDirection.z), localDirection);
            best = distance;
            result = new Hit(entry.getKey(), clip.getBlockPos(), face == null ? localDirection : face, point);
        }
        return result;
    }

    public record Hit(SubLevelId id, BlockPos localPosition, Direction face, Vec3 worldPoint) {
        public Hit {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(localPosition, "localPosition");
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(worldPoint, "worldPoint");
        }
    }

    private record CarryFrame(SubLevelId id, StructurePose previous, StructurePose current, List<AABB> boxes) {
    }

    public record Snapshot(PlotPos plot, StructurePose pose, List<AABB> boxes,
                           org.academy.internal.common.structure.space.NativeStructureView view) {
        public Snapshot {
            boxes = List.copyOf(boxes);
        }
    }
}
