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
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(-1_000_000);
    private static List<CarryFrame> pendingCarry = List.of();
    private static ClientLevel carryLevel;

    private SubLevelClientState() {
    }

    public static synchronized void accept(SubLevelStatePacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(packet.dimension())) return;
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
        if (level == null || !level.dimension().identifier().equals(packet.dimension())) return;
        var states = STATES.get(level);
        var previous = states == null ? null : states.get(packet.id());
        if (previous == null) return; // A delta before the full snapshot is ignored.
        var updated = new Snapshot(previous.plot(), packet.pose(), previous.boxes(), previous.view());
        states.put(packet.id(), updated);
        var projection = ENTITIES.getOrDefault(level, Map.of()).get(packet.id());
        if (projection != null) {
            projection.applyClientSnapshot(previous.view(), packet.pose(), projection.viewState().revision() + 1);
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
