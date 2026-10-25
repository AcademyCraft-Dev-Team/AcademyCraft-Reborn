package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilityEffectPolicy;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.space.NativeStructureBreakDrops;
import org.academy.internal.common.structure.transaction.StructureCellPayload;
import org.academy.internal.common.sublevel.SubLevelMiningRecovery;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Pull-driven bounded sphere damage. Each READY batch is consumed once; status prepares the
 * next slice on a later tick. Only active server players are authorized in this first release.
 * Impact-ledger and non-player policies remain explicitly unsupported.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class LiveStructureDamage implements StructureDamage {
    public static final LiveStructureDamage INSTANCE = new LiveStructureDamage();
    private static final Map<MinecraftServer, LinkedHashMap<UUID, Operation>> SERVERS = new IdentityHashMap<>();
    private static boolean mutating;

    private LiveStructureDamage() {}

    @Override public StructureOperation.Result<Batch> query(Authority authority, Request request) {
        var context = context(authority);
        if (context == null) return StructureOperation.unsupported("damage_requires_active_player");
        requireThread(context.host());
        if (!allowed(context)) return failure(StructureOperation.Status.DENIED, "damage_actor_unavailable");
        if (Math.abs(request.center().x) > 30_000_000 || Math.abs(request.center().z) > 30_000_000
                || !context.host().isInWorldBounds(BlockPos.containing(request.center()))
                || StructureBackendRouter.privateBlockPosition(context.host(), BlockPos.containing(request.center()))) {
            return failure(StructureOperation.Status.DENIED, "damage_requires_host_coordinates");
        }
        if (mutating) return failure(StructureOperation.Status.CONFLICT, "damage_callback_reentry");
        var registry = SERVERS.computeIfAbsent(context.host().getServer(), ignored -> new LinkedHashMap<>());
        if (registry.containsKey(request.effectId())) return failure(StructureOperation.Status.CONFLICT, "damage_effect_already_submitted");
        if (registry.size() >= 256 || registry.values().stream().filter(Operation::active).count() >= 16) {
            return failure(StructureOperation.Status.CONFLICT, "damage_capacity");
        }
        Operation operation;
        try {
            operation = new Operation(context, request,
                    StructureBackendRouter.damageDomains(context.host(), DamageRegion.bounds(request.center(), request.radius()), 256));
        } catch (RuntimeException unavailable) {
            return failure(StructureOperation.Status.BLOCKED, "damage_coverage_unavailable");
        }
        registry.put(request.effectId(), operation);
        prepare(operation);
        return new StructureOperation.Result<>(!operation.active() ? StructureOperation.Status.BLOCKED
                : operation.batch == null ? StructureOperation.Status.RUNNING : StructureOperation.Status.READY,
                "damage_query", Optional.of(operation.id), Optional.ofNullable(operation.batch));
    }

    @Override public StructureOperation.Result<Receipt> apply(Authority authority, Batch batch) {
        var operation = lookup(authority, batch.request().effectId());
        if (operation == null) return failure(StructureOperation.Status.DENIED, "damage_plan_owner");
        if (mutating || operation.batch != batch || operation.phase != Phase.READY) {
            return failure(StructureOperation.Status.STALE, "damage_plan_not_current");
        }
        if (!allowed(operation.context)) return failure(StructureOperation.Status.DENIED, "damage_actor_unavailable");
        long now = operation.context.host().getGameTime();
        if (operation.lastApplied == now) return new StructureOperation.Result<>(StructureOperation.Status.RUNNING,
                "damage_wait_next_tick", Optional.of(operation.id), Optional.of(operation.receipt()));
        operation.refreshBudget();
        operation.lastApplied = now;
        operation.batch = null; // consume before any callback, including nested query/apply/cancel
        operation.phase = Phase.APPLYING;
        mutating = true;
        var touched = new LinkedHashSet<DamageDomain>();
        try {
            var revisions = new IdentityHashMap<DamageDomain, Long>();
            for (var target : operation.prepared) {
                if (target.domain != null) revisions.putIfAbsent(target.domain, target.revision);
            }
            for (var target : operation.prepared) {
                if (!allowed(operation.context) || !target.current(operation, revisions)) { operation.rejected++; continue; }
                if (operation.changesLeft <= 0) { operation.retry.addLast(new Candidate(target.domain, target.local)); continue; }
                try {
                    if (target.domain == null) mutate(operation, target, revisions);
                    else {
                        touched.add(target.domain);
                        StructureContexts.run(target.domain.handle(), ignored -> mutate(operation, target, revisions));
                    }
                } finally {
                    if (target.domain != null && target.domain.current()
                            && !StructureCellPayload.read(target.level(operation), target.position()).equals(target.payload)) {
                        target.domain.changed();
                        revisions.put(target.domain, target.domain.contentRevision());
                    }
                }
            }
            operation.phase = operation.limitReached ? Phase.CANCELLED : operation.finishedScan() ? Phase.COMPLETED : Phase.QUERYING;
        } catch (RuntimeException failure) {
            operation.phase = Phase.RECOVERY_REQUIRED;
            AcademyCraft.LOGGER.error("Structure damage {} stopped after callback failure; never replay this effect", operation.id, failure);
        } finally {
            operation.prepared = List.of();
            try {
                for (var domain : touched) domain.flush();
            } catch (RuntimeException failure) {
                operation.phase = Phase.RECOVERY_REQUIRED;
                AcademyCraft.LOGGER.error("Structure damage {} geometry refresh requires recovery", operation.id, failure);
            } finally { mutating = false; }
        }
        return new StructureOperation.Result<>(operation.active() ? StructureOperation.Status.RUNNING
                : operation.phase == Phase.COMPLETED ? StructureOperation.Status.COMPLETED : StructureOperation.Status.BLOCKED,
                "damage_" + operation.phase.name().toLowerCase(Locale.ROOT), Optional.of(operation.id), Optional.of(operation.receipt()));
    }

    @Override public StructureOperation.Result<Progress> status(Authority authority, UUID operationId) {
        var operation = lookup(authority, operationId);
        if (operation == null) return failure(StructureOperation.Status.DENIED, "damage_operation_owner");
        if (!allowed(operation.context)) return failure(StructureOperation.Status.DENIED, "damage_actor_unavailable");
        if (!mutating && operation.phase == Phase.QUERYING && allowed(operation.context)) prepare(operation);
        return progress(operation);
    }

    @Override public StructureOperation.Result<Progress> cancel(Authority authority, UUID operationId) {
        var operation = lookup(authority, operationId);
        if (operation == null) return failure(StructureOperation.Status.DENIED, "damage_operation_owner");
        if (mutating) return failure(StructureOperation.Status.CONFLICT, "damage_callback_reentry");
        if (operation.active()) { operation.phase = Phase.CANCELLED; operation.batch = null; operation.prepared = List.of(); }
        return progress(operation);
    }

    private static void prepare(Operation operation) {
        long now = operation.context.host().getGameTime();
        if (operation.lastSlice == now) return;
        operation.lastSlice = now;
        operation.refreshBudget();
        try {
            // Sable can split/replace its activation between slices. Discover only newly loaded
            // identities in this fixed world region; stable cell keys still prevent repeat breaks.
            for (var domain : StructureBackendRouter.damageDomains(operation.context.host(),
                    DamageRegion.bounds(operation.request.center(), operation.request.radius()), 256)) {
                var key = new Activation(domain.handle().reference(), domain.handle().runtimeId());
                if (operation.activations.contains(key)) continue;
                if (operation.activations.size() >= 512) throw new IllegalStateException("damage_activation_budget");
                operation.activations.add(key);
                operation.cursors.add(operation.cursor, operation.cursor(domain));
            }
        } catch (RuntimeException incomplete) {
            operation.phase = Phase.CANCELLED; operation.limitReached = true; return;
        }
        var result = new ArrayList<Observation>();
        while (operation.scansLeft > 0 && result.size() < operation.request.maxChanges() && !operation.finishedScan()) {
            operation.scansLeft--;
            Candidate candidate;
            if (!operation.retry.isEmpty()) candidate = operation.retry.removeFirst();
            else {
                var cursor = operation.cursors.get(operation.cursor);
                if (!cursor.cells.hasNext()) { operation.cursor++; continue; }
                candidate = new Candidate(cursor.domain, cursor.cells.next().immutable());
            }
            var local = candidate.local;
            var domain = candidate.domain;
            if (operation.seen.contains(new Key(domain == null ? null : domain.handle().reference(), local))) continue;
            try {
                var target = Observation.read(operation, domain, local);
                if (target != null) result.add(target);
            } catch (RuntimeException unavailable) { operation.deferred++; }
        }
        operation.prepared = List.copyOf(result);
        operation.batch = new Batch(UUID.randomUUID(), operation.request, result.stream().map(Observation::cell).toList());
        operation.phase = Phase.READY;
    }

    private static void mutate(Operation op, Observation target, Map<DamageDomain, Long> revisions) {
        var level = target.level(op);
        var position = target.position();
        var actor = (ServerPlayer) op.context.actor();
        NativeStructureBreakDrops.Scope drops = null;
        if (target.domain != null) {
            var handle = target.domain.handle();
            var localPoint = Vec3.atCenterOf(target.local);
            var hit = new StructureHit(new SpaceBlockRef(handle.reference(), target.local), handle.runtimeId(),
                    handle.geometryRevision(), target.pose, Direction.UP, localPoint,
                    target.pose.localToWorldPoint(localPoint), false);
            drops = NativeStructureBreakDrops.openProjected(op.context.host(), level, hit, target.payload, target.entity,
                    target.domain.origin(), target.domain.localBounds().move(Vec3.atLowerCornerOf(target.domain.origin())).inflate(2));
        }
        try {
            var scope = drops;
            boolean changed;
            if (target.payload.state().getDestroySpeed(level, position) < 0 || target.payload.state().isAir()) {
                changed = admit(op, target, position, target.payload.state()) && target.current(op, revisions)
                        && clearFluid(level, actor, position, target.payload.state());
                if (changed) op.broken++;
            } else {
                changed = LevelUtil.destroyBlockSilently(level, position,
                        op.request.drops() == DropPolicy.EXISTING_SKILL_RULES, actor,
                        new LevelUtil.BreakGuard(() -> target.current(op, revisions), (pos, state) -> admit(op, target, pos, state),
                        pos -> op.scansLeft-- > 0 && (target.domain == null ? loaded(level, pos) : target.domain.chunk(pos) != null),
                        () -> allowed(op.context) && (target.domain == null
                                || target.domain.current() && target.pose.equals(target.domain.handle().pose())),
                        pos -> {
                            op.broken++;
                            if (target.domain != null && target.domain.current()) target.domain.changed();
                        }));
            }
            if (!changed) op.rejected++;
            if (scope != null) scope.publish();
        } catch (RuntimeException failure) {
            if (drops != null) SubLevelMiningRecovery.retain(op.context.host().getServer(), drops, failure);
            throw failure;
        } finally { if (drops != null) drops.close(); }
    }

    private static boolean admit(Operation op, Observation target, BlockPos position, BlockState state) {
        if (!allowed(op.context) || op.changesLeft <= 0) return false;
        if (org.academy.internal.common.structure.transaction.PayloadIsolation.locked(target.level(op), position)) return false;
        var local = target.domain == null ? position : position.subtract(target.domain.origin());
        if (target.domain != null && (!target.domain.current() || !target.pose.equals(target.domain.handle().pose())
                || !target.domain.localBounds().contains(Vec3.atCenterOf(local)))) return false;
        var key = new Key(target.domain == null ? null : target.domain.handle().reference(), local.immutable());
        if (op.seen.size() >= 65_536) { op.limitReached = true; return false; }
        if (!op.seen.add(key)) return false;
        op.changesLeft--;
        var world = target.domain == null ? position : BlockPos.containing(target.pose.localToWorldPoint(Vec3.atCenterOf(local)));
        var host = op.context.host();
        var actor = (ServerPlayer) op.context.actor();
        return loaded(host, world) && host.getWorldBorder().isWithinBounds(world) && host.mayInteract(actor, world)
                && LevelUtil.canAbilityBreak(host, world, state, actor) && allowed(op.context)
                && host.mayInteract(actor, world);
    }

    private static boolean clearFluid(ServerLevel level, ServerPlayer actor, BlockPos pos, BlockState state) {
        if (state.getFluidState().isEmpty()) return false;
        if (state.getBlock() instanceof BucketPickup pickup && !pickup.pickupBlock(actor, level, pos, state).isEmpty()) return true;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
        }
        return state.getBlock() instanceof LiquidBlock && level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static StructureOperation.@Nullable ActorContext context(Authority authority) {
        return authority instanceof ActiveActor active && active.context().actor() instanceof ServerPlayer ? active.context() : null;
    }
    private static boolean allowed(StructureOperation.ActorContext context) {
        var actor = (ServerPlayer) context.actor(); var host = context.host();
        return actor.level() == host && actor.isAlive() && !actor.isRemoved() && !actor.hasDisconnected()
                && host.getServer().getLevel(host.dimension()) == host
                && AbilityEffectPolicy.blockDestruction(host) != AbilityEffectPolicy.Decision.DENY;
    }
    private static @Nullable Operation lookup(Authority authority, UUID id) {
        var context = context(authority);
        if (context == null) return null;
        requireThread(context.host());
        var registry = SERVERS.get(context.host().getServer());
        var op = registry == null ? null : registry.get(id);
        return op != null && op.context.host() == context.host() && op.context.actor() == context.actor() ? op : null;
    }
    private static StructureOperation.Result<Progress> progress(Operation op) {
        return new StructureOperation.Result<>(op.phase == Phase.READY ? StructureOperation.Status.READY
                : op.active() ? StructureOperation.Status.RUNNING
                : op.phase == Phase.RECOVERY_REQUIRED || op.limitReached ? StructureOperation.Status.BLOCKED : StructureOperation.Status.COMPLETED,
                op.limitReached ? "damage_effect_cell_budget" : "damage_" + op.phase.name().toLowerCase(Locale.ROOT), Optional.of(op.id),
                Optional.of(new Progress(op.id, op.request.effectId(), op.phase, op.receipt(), Optional.ofNullable(op.batch))));
    }
    private static <T> StructureOperation.Result<T> failure(StructureOperation.Status status, String detail) {
        return new StructureOperation.Result<>(status, detail, Optional.empty(), Optional.empty());
    }
    private static void requireThread(ServerLevel host) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Damage requires the server thread");
    }
    static boolean loaded(ServerLevel level, BlockPos position) {
        return !level.isOutsideBuildHeight(position) && level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var registry = SERVERS.get(event.getServer());
        if (registry == null || mutating) return;
        registry.values().removeIf(op -> op.context.host().getGameTime() - (op.active() ? op.started : op.lastSlice) > (op.active() ? 1200 : 200));
        for (var op : registry.values()) if (op.active() && !allowed(op.context)) {
            op.phase = Phase.CANCELLED; op.batch = null; op.prepared = List.of();
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }

    private record Key(@Nullable StructureRef structure, BlockPos position) {}
    private record Activation(StructureRef structure, UUID runtime) {}
    private record Candidate(@Nullable DamageDomain domain, BlockPos local) {}
    private record Cursor(@Nullable DamageDomain domain, Iterator<BlockPos> cells) {}
    private static final class Operation {
        final UUID id;
        final StructureOperation.ActorContext context;
        final Request request;
        final List<Cursor> cursors = new ArrayList<>();
        final Set<Activation> activations = new HashSet<>();
        final Set<Key> seen = new HashSet<>();
        final ArrayDeque<Candidate> retry = new ArrayDeque<>();
        final long started;
        long lastSlice = Long.MIN_VALUE, lastApplied = Long.MIN_VALUE, budgetTick = Long.MIN_VALUE;
        int cursor, scansLeft, changesLeft, broken, rejected, deferred;
        boolean limitReached;
        Phase phase = Phase.QUERYING;
        @Nullable Batch batch;
        List<Observation> prepared = List.of();
        Operation(StructureOperation.ActorContext context, Request request, List<DamageDomain> domains) {
            this.context = context; this.request = request; this.id = request.effectId(); started = context.host().getGameTime();
            // Structures go first so a large terrain sphere cannot starve moving targets.
            for (var domain : domains) {
                activations.add(new Activation(domain.handle().reference(), domain.handle().runtimeId()));
                cursors.add(cursor(domain));
            }
            cursors.add(new Cursor(null, DamageRegion.cells(request.center(), request.radius(),
                    new AABB(-30_000_000, context.host().getMinY(), -30_000_000, 30_000_000, context.host().getMaxY(), 30_000_000))));
        }
        boolean active() { return phase == Phase.QUERYING || phase == Phase.READY || phase == Phase.APPLYING; }
        Cursor cursor(DamageDomain domain) {
            return new Cursor(domain, DamageRegion.cells(domain.handle().pose().worldToLocalPoint(request.center()), request.radius(), domain.localBounds()));
        }
        void refreshBudget() {
            long tick = context.host().getGameTime();
            if (tick != budgetTick) { budgetTick = tick; scansLeft = request.maxScans(); changesLeft = request.maxChanges(); }
        }
        boolean finishedScan() { return cursor >= cursors.size() && retry.isEmpty(); }
        Receipt receipt() { return new Receipt(broken, rejected, deferred); }
    }

    private record Observation(@Nullable DamageDomain domain, BlockPos local, StructurePose pose,
                               long revision, StructureBlockData payload,
                               net.minecraft.world.level.block.entity.@Nullable BlockEntity entity) {
        ServerLevel level(Operation op) { return domain == null ? op.context.host() : domain.storage(); }
        BlockPos position() { return domain == null ? local : domain.position(local); }
        Cell cell() {
            return new Cell(domain == null ? Optional.of(local) : Optional.empty(), domain == null ? Optional.empty()
                    : Optional.of(new SpaceBlockRef(domain.handle().reference(), local)), revision,
                    Integer.toHexString(payload.state().hashCode()) + ":" + StructureCellPayload.digest(payload));
        }
        static @Nullable Observation read(Operation op, @Nullable DamageDomain domain, BlockPos local) {
            if (domain == null && StructureBackendRouter.privateBlockPosition(op.context.host(), local)) return null;
            if (domain != null && !domain.current()) { op.deferred++; return null; }
            if (domain != null && !domain.localBounds().contains(Vec3.atCenterOf(local))) return null;
            var level = domain == null ? op.context.host() : domain.storage();
            var position = domain == null ? local : domain.position(local);
            if (org.academy.internal.common.structure.transaction.PayloadIsolation.locked(level, position)) { op.deferred++; return null; }
            if (level.isOutsideBuildHeight(position) || (domain == null ? !loaded(level, position) : domain.chunk(position) == null)) {
                op.deferred++; return null;
            }
            var pose = domain == null ? new StructurePose(Vec3.ZERO, new org.joml.Quaterniond()) : domain.handle().pose();
            long revision = domain == null ? 0 : domain.contentRevision();
            var payload = StructureCellPayload.read(level, position);
            var entity = level.getBlockEntity(position);
            var state = level.getBlockState(position);
            if (state.isAir() && state.getFluidState().isEmpty()) return null;
            if (domain == null) {
                if (!DamageRegion.hostCell(op.request.center(), op.request.radius(), local)) return null;
            } else {
                var shape = state.getShape(new DamageShapeView(domain), position);
                var center = pose.worldToLocalPoint(op.request.center()).subtract(Vec3.atLowerCornerOf(local));
                if (shape.toAabbs().stream().noneMatch(box -> DamageRegion.intersects(center, op.request.radius(), box))) return null;
            }
            var observed = new Observation(domain, local, pose, revision, payload, entity);
            if (!observed.current(op, Map.of())) { op.deferred++; return null; }
            return observed;
        }
        boolean current(Operation op, Map<DamageDomain, Long> revisions) {
            if (domain != null && (!domain.current() || domain.contentRevision() != revisions.getOrDefault(domain, revision)
                    || !pose.equals(domain.handle().pose()))) return false;
            var level = level(op); var position = position();
            if (org.academy.internal.common.structure.transaction.PayloadIsolation.locked(level, position)) return false;
            if (!(domain == null ? loaded(level, position) : domain.chunk(position) != null)
                    || level.getBlockEntity(position) != entity || !StructureCellPayload.read(level, position).equals(payload)) return false;
            if (domain != null) {
                var center = pose.worldToLocalPoint(op.request.center()).subtract(Vec3.atLowerCornerOf(local));
                if (payload.state().getShape(new DamageShapeView(domain), position).toAabbs().stream()
                        .noneMatch(box -> DamageRegion.intersects(center, op.request.radius(), box))) return false;
                if (!domain.current() || domain.contentRevision() != revisions.getOrDefault(domain, revision)
                        || !pose.equals(domain.handle().pose())) return false;
            }
            return level.getBlockEntity(position) == entity && StructureCellPayload.read(level, position).equals(payload);
        }
    }
}
