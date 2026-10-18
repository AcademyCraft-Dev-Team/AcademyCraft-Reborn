package org.academy.internal.client.sublevel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.network.SubLevelStatePacket;
import org.academy.internal.common.sublevel.PlotPos;
import org.academy.internal.common.sublevel.SubLevelCollisionIndex;
import org.academy.internal.common.sublevel.SubLevelCollisionRuntime;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Client-side Phase 4 state store; collision registration is opt-in until rendering exists. */
public final class SubLevelClientState {
    private static final boolean COLLISION = Boolean.getBoolean("academy.sublevel.clientCollision");
    private static final Map<ClientLevel, Map<SubLevelId, Snapshot>> STATES = new WeakHashMap<>();
    private static final Map<ClientLevel, SubLevelCollisionIndex> INDEXES = new WeakHashMap<>();
    private static final Map<ClientLevel, Map<SubLevelId, NativeStructureProjectionEntity>> ENTITIES = new WeakHashMap<>();
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(-1_000_000);

    private SubLevelClientState() {
    }

    public static synchronized void accept(SubLevelStatePacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var states = STATES.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        var entities = ENTITIES.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (packet.removed()) {
            states.remove(packet.id());
            var removed = entities.remove(packet.id());
            if (removed != null) removed.discard();
            var index = INDEXES.get(level);
            if (index != null) index.remove(packet.id());
            return;
        }
        states.put(packet.id(), new Snapshot(packet.plot(), packet.pose(), packet.boxes(), packet.view()));
        org.academy.AcademyCraft.LOGGER.debug("SubLevel client snapshot id={} cells={} boxes={}",
                packet.id().value(), packet.view().cells().size(), packet.boxes().size());
        var projection = entities.get(packet.id());
        if (projection == null) {
            projection = new NativeStructureProjectionEntity(EntityTypes.NATIVE_STRUCTURE_PROJECTION.get(), level);
            projection.markClientOnly();
            projection.setId(ENTITY_IDS.getAndDecrement());
            projection.applyClientSnapshot(packet.view(), packet.pose(), 1);
            level.addEntity(projection);
            entities.put(packet.id(), projection);
            org.academy.AcademyCraft.LOGGER.info("SubLevel client projection id={} added entityId={}",
                    packet.id().value(), projection.getId());
        } else {
            projection.applyClientSnapshot(packet.view(), packet.pose(), projection.viewState().revision() + 1);
        }
        if (!COLLISION) return;
        var index = INDEXES.computeIfAbsent(level, ignored -> {
            var created = new SubLevelCollisionIndex();
            SubLevelCollisionRuntime.register(level, created);
            return created;
        });
        index.update(packet.id(), packet.boxes(), packet.pose());
    }

    public static synchronized List<Snapshot> snapshots(ClientLevel level) {
        var states = STATES.get(level);
        return states == null ? List.of() : states.values().stream().toList();
    }

    /** Exact state-only raycast; no client-only entity AABB is consulted. */
    public static synchronized Hit raycast(ClientLevel level, Vec3 from, Vec3 to) {
        var states = STATES.get(level);
        if (states == null || from == null || to == null) return null;
        double best = Double.POSITIVE_INFINITY;
        Hit result = null;
        for (var entry : states.entrySet()) {
            var snapshot = entry.getValue();
            if (snapshot.view().isEmpty()) continue;
            var pose = snapshot.pose();
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

    public record Snapshot(PlotPos plot, StructurePose pose, List<AABB> boxes,
                           org.academy.internal.common.structure.space.NativeStructureView view) {
        public Snapshot {
            boxes = List.copyOf(boxes);
        }
    }
}
