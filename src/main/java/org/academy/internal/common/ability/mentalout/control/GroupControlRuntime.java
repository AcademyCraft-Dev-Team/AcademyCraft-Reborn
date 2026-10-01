package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.IShearable;
import org.academy.api.server.ability.AbilityBlockDrops;
import org.academy.internal.common.ability.mentalout.control.MentalControlMemory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.*;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.server.storage.SpatialStorageService;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class GroupControlRuntime {
    private static final int PATH_GRACE_TICKS = 40;
    private static final int PATH_STALL_TICKS = 200;
    private static final int FARM_RESCAN_TICKS = 20;
    private static final List<AdapterEntry> ADAPTERS = new ArrayList<>();
    private static final Map<TaskKey, DefaultTask> TASKS = new HashMap<>();
    private static final FairWorkQueue<DefaultTask> READY = new FairWorkQueue<>();
    private static final Map<UUID, DefaultTask> SCOPES = new HashMap<>();
    private record WorkGroupKey(UUID controller, Identifier source, String orderId) {}
    private static final Map<WorkGroupKey, SharedWorkPlan> WORK_GROUPS = new HashMap<>();
    private static final Map<Identifier, Map<Long, Set<SharedWorkPlan>>> WORK_CHUNKS = new HashMap<>();
    private static final class ReturnCursor { int slot, visited; }
    private static final Map<String, ReturnCursor> RETURNS = new HashMap<>();
    private static boolean defaultRegistered;

    private GroupControlRuntime() {
    }

    public static int activeWorkGroups() { return WORK_GROUPS.size(); }
    public static int activeTasks() { return TASKS.size(); }

    public static void blockChanged(ServerLevel level, BlockPos pos) {
        var chunks = WORK_CHUNKS.get(level.dimension().identifier());
        if (chunks == null) return;
        try (var ignored = WorkScheduling.budget(level).measure()) {
            changed(chunks, pos);
            for (var direction : Direction.values()) changed(chunks, pos.relative(direction));
        }
    }

    private static void changed(Map<Long, Set<SharedWorkPlan>> chunks, BlockPos pos) {
        var groups = chunks.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        if (groups == null) return;
        for (var group : groups) group.changed(pos);
    }

    private static long chunkKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    public static synchronized void registerAdapter(
            Identifier id,
            int priority,
            GroupControlAdapter adapter
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adapter, "adapter");
        ADAPTERS.removeIf(entry -> entry.id.equals(id));
        ADAPTERS.add(new AdapterEntry(id, priority, adapter));
        ADAPTERS.sort(Comparator.comparingInt(AdapterEntry::priority).reversed()
                .thenComparing(entry -> entry.id.toString()));
    }

    public static synchronized GroupControlResult dispatch(GroupControlRequest request) {
        ensureDefaultAdapter();
        var sharedWork = SharedWorkPlan.create(request.command());
        var handles = new ArrayList<GroupControlHandle>();
        var applied = 0;
        var unsupported = 0;
        var failed = 0;
        for (var index = 0; index < request.subjects().size(); index++) {
            var subject = request.subjects().get(index);
            var adapter = ADAPTERS.stream()
                    .map(AdapterEntry::adapter)
                    .filter(candidate -> candidate.supports(subject, request.command()))
                    .findFirst().orElse(null);
            if (adapter == null) {
                unsupported++;
                continue;
            }
            try {
                var handle = adapter == DefaultAdapter.INSTANCE
                        ? DefaultAdapter.INSTANCE.start(request, subject, index, sharedWork)
                        : adapter.start(request, subject, index);
                if (handle == null || handle.isClosed()) {
                    failed++;
                } else {
                    handles.add(handle);
                    applied++;
                }
            } catch (RuntimeException exception) {
                AcademyCraft.LOGGER.error(
                        "Failed to start group-control task {} for {}",
                        request.command().getClass().getSimpleName(),
                        subject.getUUID(),
                        exception
                );
                failed++;
            }
        }
        return new GroupControlResult(applied, unsupported, failed, handles);
    }

    public static synchronized void cancelByControllerAndSource(UUID controllerId, Identifier source) {
        for (var task : List.copyOf(TASKS.values())) {
            if (task.controllerId.equals(controllerId) && task.source.equals(source)) task.close();
        }
    }

    public static synchronized Optional<GroupControlInspection> inspect(LivingEntity subject) {
        if (subject == null) return Optional.empty();
        return TASKS.values().stream()
                .filter(task -> task.subject.getUUID().equals(subject.getUUID()))
                .findFirst()
                .map(task -> new GroupControlInspection(
                        subject.getUUID(),
                        task.command.getClass().getSimpleName(),
                        Optional.ofNullable(task.currentBlock),
                        task.sharedWork == null
                                ? task.pendingBlocks.size()
                                : task.sharedWork.pendingCount(),
                        Optional.ofNullable(task.movement).map(ControlHandle::state),
                        task.stalledTicks
                ));
    }

    public static synchronized void cancelSubjects(
            UUID controllerId,
            Identifier source,
            Set<UUID> subjectIds
    ) {
        if (subjectIds == null || subjectIds.isEmpty()) return;
        for (var task : List.copyOf(TASKS.values())) {
            if (task.controllerId.equals(controllerId) && task.source.equals(source)
                    && subjectIds.contains(task.subject.getUUID())) task.close();
        }
    }

    public static synchronized void clear() {
        for (var task : List.copyOf(TASKS.values())) {
            task.saveOrder();
            task.closeInternal();
        }
        TASKS.clear();
        READY.clear();
        SCOPES.clear();
        WORK_GROUPS.clear();
        WORK_CHUNKS.clear();
        RETURNS.clear();
    }

    private static void ensureDefaultAdapter() {
        if (defaultRegistered) return;
        defaultRegistered = true;
        registerAdapter(
                AcademyCraft.academy("default_living_group_control"),
                Integer.MIN_VALUE,
                DefaultAdapter.INSTANCE
        );
    }

    private static void tick(MinecraftServer server) {
        ensureDefaultAdapter();
        restoreOrders(server);
        var budget = WorkScheduling.budget(server);
        READY.rotateStart();
        var visited = Collections.newSetFromMap(new IdentityHashMap<DefaultTask, Boolean>());
        int turns = Math.min(512, READY.size() * 2);
        while (turns-- > 0 && budget.available(WorkBudget.Operation.WORKER)) {
            var task = READY.next();
            if (task == null) break;
            if (!visited.add(task)) continue;
            if (!budget.spend(WorkBudget.Operation.WORKER)) break;
            try (var ignored = budget.measure()) {
                task.tick(server);
            } catch (DeferredWork deferred) {
                task.workStatus = "queued_path";
            } catch (RuntimeException exception) {
                AcademyCraft.LOGGER.error(
                        "Group-control task {} failed for {}",
                        task.command.getClass().getSimpleName(),
                        task.subject.getUUID(),
                        exception
                );
                if (task.settings != null && !task.closed) {
                    task.paused = task.faulted = true;
                    task.workStatus = "error";
                    task.closeMovement();
                    task.releaseTarget();
                    task.saveOrder();
                } else task.finish(GroupControlTaskEvent.Status.PATH_FAILED);
            }
        }
    }

    public static boolean ownsWorkScope(UUID controller, UUID subject, UUID scope) {
        var task = SCOPES.get(scope);
        return task != null && task.settings != null && !task.closed
                && task.controllerId.equals(controller) && task.subject.getUUID().equals(subject);
    }

    private static final class DeferredWork extends RuntimeException {
        private static final DeferredWork INSTANCE = new DeferredWork();
        private DeferredWork() { super(null, null, false, false); }
    }

    private static void restoreOrders(MinecraftServer server) {
        try (var ignored = WorkScheduling.budget(server).measure()) { restoreBatch(server); }
    }

    private static void restoreBatch(MinecraftServer server) {
        var data = WorkOrderData.get(server);
        var budget = WorkScheduling.budget(server);
        for (int remaining = Math.min(32, data.size()); remaining > 0; remaining--) {
            if (!budget.available(WorkBudget.Operation.RESTORE)) break;
            var entry = data.recoveryBatch(1).getFirst();
            if (entry.faulted()) continue;
            var controllerId = UUID.fromString(entry.controller());
            var key = new TaskKey(controllerId, entry.source(), UUID.fromString(entry.subject()));
            if (!entry.returning() && TASKS.containsKey(key)) continue;
            var controller = server.getPlayerList().getPlayer(controllerId);
            if (controller == null || !controller.isAlive()) continue;
            if (entry.returning()) {
                if (budget.spend(WorkBudget.Operation.RESTORE)) returnCargo(server, entry);
                continue;
            }
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, entry.dimension()));
            if (level == null || !(level.getEntity(key.subjectId) instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()
                    || !MentalControlMemory.wasControlledBy(mob, controllerId)) continue;
            if (!budget.spend(WorkBudget.Operation.RESTORE)) break;
            try (var ignored = budget.measure()) {
                var command = new GroupControlCommand.Work(entry.region(), entry.settings());
                var groupKey = new WorkGroupKey(controllerId, entry.source(), entry.orderId());
                var shared = WORK_GROUPS.computeIfAbsent(groupKey, unused -> {
                    var plan = SharedWorkPlan.create(command);
                    plan.orderId = entry.orderId();
                    return plan;
                });
                var request = new GroupControlRequest(controller, entry.source(), List.of(mob), command, entry.priority());
                DefaultAdapter.INSTANCE.start(request, mob, 0, shared, entry);
            } catch (RuntimeException failure) {
                data.put(entry.withFault());
                AcademyCraft.LOGGER.error("Work assignment {} could not be restored; retained for manual retry", entry.key(), failure);
            }
        }
    }

    private static void returnCargo(MinecraftServer server, WorkOrderData.Entry entry) {
        try (var ignored = WorkScheduling.budget(server).measure()) { returnCargoMeasured(server, entry); }
    }

    private static void returnCargoMeasured(MinecraftServer server, WorkOrderData.Entry entry) {
        if (entry.cargo().isEmpty()) { WorkOrderData.get(server).remove(entry.key()); RETURNS.remove(entry.key()); return; }
        var controller = server.getPlayerList().getPlayer(UUID.fromString(entry.controller()));
        if (controller == null || !controller.isAlive()) return;
        var data = WorkOrderData.get(server);
        var cargo = new ArrayList<>(entry.cargo());
        var budget = WorkScheduling.budget(server);
        var cursor = RETURNS.computeIfAbsent(entry.key(), ignored -> new ReturnCursor());
        var remainder = cargo.getFirst();
        var inventory = controller.getInventory();
        int count = inventory.getNonEquipmentItems().size();
        try {
            var result = WorkInventoryTransfer.insert(inventory, remainder, cursor.slot, 16, budget, count);
            cursor.slot = result.nextSlot();
            cursor.visited += result.visited();
            if (!remainder.isEmpty() && cursor.visited >= count && budget.spend(WorkBudget.Operation.TRANSFER)) {
                Block.popResource(controller.level(), controller.blockPosition(), remainder.copy());
                remainder.setCount(0);
            }
        } catch (RuntimeException failure) {
            data.put(entry.withCargo(cargo).withFault());
            AcademyCraft.LOGGER.error("Cargo return {} failed; retained without automatic replay", entry.key(), failure);
            return;
        }
        if (remainder.isEmpty()) { cargo.removeFirst(); cursor.slot = cursor.visited = 0; }
        if (cargo.isEmpty()) { data.remove(entry.key()); RETURNS.remove(entry.key()); }
        else data.put(entry.withCargo(cargo));
    }

    public static void setWorkPaused(MinecraftServer server, UUID controller, Set<UUID> subjects, boolean paused) {
        var data = WorkOrderData.get(server);
        for (var entry : data.selected(controller, subjects)) {
            if (entry.returning()) continue;
            if (entry.controller().equals(controller.toString()) && subjects.contains(UUID.fromString(entry.subject()))) {
                data.put(entry.withPaused(paused));
                var task = TASKS.get(new TaskKey(controller, entry.source(), UUID.fromString(entry.subject())));
                if (task != null) {
                    task.paused = paused;
                    if (!paused) task.faulted = false;
                    task.revision = entry.revision() + 1;
                    task.closeMovement();
                    if (paused) task.releaseTarget();
                }
            }
        }
    }

    public static void cancelWork(MinecraftServer server, UUID controller, Set<UUID> subjects) {
        var data = WorkOrderData.get(server);
        for (var entry : data.selected(controller, subjects)) {
            if (!entry.controller().equals(controller.toString()) || !subjects.contains(UUID.fromString(entry.subject()))) continue;
            var task = TASKS.get(new TaskKey(controller, entry.source(), UUID.fromString(entry.subject())));
            if (task != null && !entry.returning()) task.close();
            else {
                var returning = entry.asReturning();
                data.put(returning);
                if (!entry.returning()) data.remove(entry.key());
                returnCargo(server, returning);
            }
        }
    }

    public static String workStatus(WorkOrderData.Entry entry) {
        if (entry.faulted()) return "error";
        if (entry.returning()) return "returning";
        var task = TASKS.get(new TaskKey(UUID.fromString(entry.controller()), entry.source(), UUID.fromString(entry.subject())));
        return entry.paused() ? "paused" : task == null ? "suspended" : task.workStatus;
    }

    public static Optional<MiningWorkPlan.Progress> miningProgress(WorkOrderData.Entry entry) {
        var task = TASKS.get(new TaskKey(UUID.fromString(entry.controller()), entry.source(), UUID.fromString(entry.subject())));
        return task == null || task.sharedWork == null || task.sharedWork.mining == null
                ? Optional.empty() : Optional.of(task.sharedWork.mining.progress());
    }

    private enum DefaultAdapter implements GroupControlAdapter {
        INSTANCE;

        @Override
        public boolean supports(LivingEntity subject, GroupControlCommand command) {
            return subject != null && subject.isAlive() && !subject.isRemoved()
                    && MentalControlApi.supports(subject, ControlCapability.PATH_CONTROL)
                    && (!(command instanceof GroupControlCommand.GatherResources
                    || command instanceof GroupControlCommand.Work
                    || command instanceof GroupControlCommand.Farm)
                    || MentalControlApi.supports(subject, ControlCapability.AI_CONTROL));
        }

        @Override
        public GroupControlHandle start(
                GroupControlRequest request,
                LivingEntity subject,
                int subjectIndex
        ) {
            return start(request, subject, subjectIndex, SharedWorkPlan.create(request.command()));
        }

        private GroupControlHandle start(
                GroupControlRequest request,
                LivingEntity subject,
                int subjectIndex,
                SharedWorkPlan sharedWork
        ) {
            return start(request, subject, subjectIndex, sharedWork, null);
        }

        private GroupControlHandle start(GroupControlRequest request, LivingEntity subject, int subjectIndex,
                                         SharedWorkPlan sharedWork, WorkOrderData.Entry restored) {
            var key = new TaskKey(request.controller().getUUID(), request.source(), subject.getUUID());
            var previous = TASKS.remove(key);
            if (previous != null) previous.close();
            var task = new DefaultTask(key, request, subject, subjectIndex, sharedWork);
            if (restored != null) {
                task.bufferedDrops.addAll(restored.cargo());
                task.paused = restored.paused();
                task.revision = restored.revision();
                task.faulted = restored.faulted();
            }
            if (sharedWork != null) {
                if (sharedWork.workers == 0) sharedWork.indexChunks(true);
                sharedWork.workers++;
                WORK_GROUPS.put(new WorkGroupKey(task.controllerId, task.source, sharedWork.orderId), sharedWork);
            }
            TASKS.put(key, task);
            READY.add(task.controllerId, sharedWork == null ? task.scopeId : sharedWork, task);
            SCOPES.put(task.scopeId, task);
            try { task.retainExclusiveWorkOrder(request.controller()); }
            catch (RuntimeException exception) { task.closeInternal(); throw exception; }
            task.saveOrder();
            task.notifyObserver(GroupControlTaskEvent.Status.ACCEPTED);
            return task;
        }
    }

    private static final class DefaultTask implements GroupControlHandle {
        private final TaskKey key;
        private final UUID controllerId;
        private final Identifier source;
        private final LivingEntity subject;
        private final GroupControlCommand command;
        private final WorkSettings settings;
        private boolean paused;
        private boolean faulted;
        private long revision;
        private String workStatus = "working";
        private long nextWorkScan;
        private long nextAnimalAction;
        private long retryAfter;
        private int supplySlot, seedSlot, depositSlot, depositVisited;
        private long nextSupplyCheck, nextSeedCheck, nextDepositCheck;
        private int blockedCargo;
        private int farmOutputSlot;
        private final GroupControlObserver observer;
        private final int priority;
        private final int subjectIndex;
        private final int subjectCount;
        private final SharedWorkPlan sharedWork;
        private final UUID scopeId = UUID.randomUUID();
        private final ArrayDeque<BlockPos> pendingBlocks = new ArrayDeque<>();
        private final HashSet<BlockPos> queuedBlocks = new HashSet<>();
        private final ArrayList<ItemStack> bufferedDrops = new ArrayList<>();
        private ControlHandle movement;
        private ControlHandle aiControl;
        private BlockPos currentBlock;
        private BlockPos workApproachBlock;
        private Vec3 workApproachPoint;
        private Entity workEntity;
        private UUID entityCandidateCursor;
        private boolean entitySelectionDeferred;
        private GroupControlNavigation.Search workSearch;
        private GroupControlNavigation.Search moveSearch;
        private long workApproachRetry;
        private Vec3 resolvedMovePoint;
        private ControlDestination resolvedMoveDestination;
        private long nextFarmScanTick;
        private long movementStartedTick;
        private double lastMovementDistance = Double.MAX_VALUE;
        private int stalledTicks;
        private int miningProgressTicks;
        private boolean closed;
        private boolean terminalNotified;
        private boolean remoteMining;

        private DefaultTask(
                TaskKey key,
                GroupControlRequest request,
                LivingEntity subject,
                int subjectIndex,
                SharedWorkPlan sharedWork
        ) {
            this.key = key;
            controllerId = request.controller().getUUID();
            source = request.source();
            this.subject = subject;
            settings = request.command() instanceof GroupControlCommand.Work work ? work.settings() : null;
            command = request.command() instanceof GroupControlCommand.Work work
                    ? settings.mode() == WorkSettings.Mode.FARMING
                    ? new GroupControlCommand.Farm(work.region())
                    : new GroupControlCommand.GatherResources(work.region()) : request.command();
            observer = request.observer();
            priority = request.priority();
            this.subjectIndex = subjectIndex;
            subjectCount = request.subjects().size();
            this.sharedWork = sharedWork;
            if (sharedWork == null
                    && command instanceof GroupControlCommand.GatherResources(var region)) {
                partitionAll(region);
            }
        }

        private void partitionAll(BlockWorkRegion region) {
            var index = 0;
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                if (index++ % subjectCount == subjectIndex) enqueueBlock(pos);
            }
        }

        private void enqueueBlock(BlockPos pos) {
            if (sharedWork != null) {
                sharedWork.defer(pos);
                return;
            }
            var immutable = pos.immutable();
            if (queuedBlocks.add(immutable)) pendingBlocks.addLast(immutable);
        }

        private BlockPos pollBlock() {
            if (sharedWork != null) return sharedWork.claimNearest(subject);
            var block = pendingBlocks.pollFirst();
            if (block != null) queuedBlocks.remove(block);
            return block;
        }

        private boolean isExclusiveWorkOrder() {
            return command instanceof GroupControlCommand.GatherResources
                    || command instanceof GroupControlCommand.Farm;
        }

        private void retainExclusiveWorkOrder(ServerPlayer controller) {
            if (!isExclusiveWorkOrder() || aiControl != null) return;
            aiControl = MentalControlApi.apply(ControlRequest.scopedPermanent(
                    controller,
                    subject,
                    source,
                    scopeId,
                    priority,
                    List.of(new ControlDirective.TakeoverAi())
            ));
            if (subject instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
                mob.setAggressive(false);
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            }
        }

        private void releaseExclusiveWorkOrder() {
            if (aiControl != null) aiControl.close();
            aiControl = null;
        }

        private void tick(MinecraftServer server) {
            if (closed) return;
            var controller = server.getPlayerList().getPlayer(controllerId);
            if (!subject.isAlive() && !subject.isRemoved()
                    || subject.getRemovalReason() != null && subject.getRemovalReason().shouldDestroy()) {
                finish(GroupControlTaskEvent.Status.CANCELLED);
                return;
            }
            if (controller == null || !controller.isAlive() || controller.hasDisconnected()
                    || subject.isRemoved() || settings == null && controller.level() != subject.level()) {
                if (settings != null) {
                    workStatus = "suspended";
                    saveOrder();
                    closeInternal();
                } else finish(GroupControlTaskEvent.Status.CANCELLED);
                return;
            }
            if (settings != null) {
                if (aiControl != null && aiControl.state().isTerminal()) {
                    aiControl = null;
                    closeMovement();
                }
                retainExclusiveWorkOrder(controller);
                if (paused) { workStatus = faulted ? "error" : "paused"; closeMovement(); return; }
            }
            if (isExclusiveWorkOrder() && aiControl != null
                    && aiControl.state() != ControlState.ACTIVE) {
                if (aiControl.state().isTerminal()) {
                    finish(GroupControlTaskEvent.Status.CANCELLED);
                } else {
                    closeMovement();
                    releaseTarget();
                    workStatus = "preempted";
                }
                return;
            }
            if (command instanceof GroupControlCommand.MoveTo(var destination)) {
                tickMove(controller, destination);
                return;
            }
            var region = command instanceof GroupControlCommand.GatherResources(var gather)
                    ? gather : ((GroupControlCommand.Farm) command).region();
            if (!region.dimension().equals(subject.level().dimension().identifier())) {
                finish(GroupControlTaskEvent.Status.PATH_FAILED);
                return;
            }
            if (settings != null && !bufferedDrops.isEmpty()
                    && bufferedDrops.size() >= 18) {
                if (!depositWorkCargo(controller, region)) return;
            }
            if (settings != null && subject.level().getGameTime() < retryAfter) return;
            workStatus = "working";
            if (settings != null && settings.mode() == WorkSettings.Mode.COLLECT) {
                tickCollection(controller, region);
                return;
            }
            if (settings != null && settings.mode().animals()) {
                tickAnimalWork(controller, region);
                return;
            }
            if (command instanceof GroupControlCommand.Farm) tickFarm(controller, region);
            else tickMining(controller, region);
        }

        private void tickMove(ServerPlayer controller, ControlDestination destination) {
            var point = resolveMove(destination, controller);
            if (point == null) {
                finish(GroupControlTaskEvent.Status.PATH_FAILED);
                return;
            }
            if (subject.distanceToSqr(point) <= 2.25) {
                finish(GroupControlTaskEvent.Status.COMPLETED);
                return;
            }
            if (!ensureMovement(controller,
                    resolvedMoveDestination == null ? destination : resolvedMoveDestination,
                    1.25) || hasStalledPath(point)) {
                finish(GroupControlTaskEvent.Status.PATH_FAILED);
            }
        }

        private Vec3 resolveMove(ControlDestination destination, ServerPlayer controller) {
            if (!(destination instanceof ControlDestination.Position(var dimension, var value))) {
                return resolve(destination, controller);
            }
            if (resolvedMovePoint != null) return resolvedMovePoint;
            if (!dimension.equals(subject.level().dimension().identifier())) return null;
            if (moveSearch == null) moveSearch = GroupControlNavigation.positionSearch(subject, value);
            var result = moveSearch.poll();
            if (result.status() == GroupControlNavigation.Status.DEFERRED) throw DeferredWork.INSTANCE;
            resolvedMovePoint = result.position();
            if (resolvedMovePoint != null) {
                resolvedMoveDestination = new ControlDestination.Position(dimension, resolvedMovePoint);
            }
            return resolvedMovePoint;
        }

        private void tickMining(ServerPlayer controller, BlockWorkRegion region) {
            if (sharedWork != null && sharedWork.mining != null) {
                tickPlannedMining(controller, region);
                return;
            }
            if (currentBlock == null) currentBlock = nextMiningBlock();
            if (currentBlock == null) {
                if (sharedWork != null && sharedWork.hasPending()) { workStatus = "scanning"; return; }
                if (settings != null) {
                    if (!depositWorkCargo(controller, region)) return;
                    if (settings.repeat()) {
                        workStatus = "waiting";
                        closeMovement();
                        if (subject.level().getGameTime() >= nextWorkScan) {
                            if (sharedWork == null) partitionAll(region);
                            else sharedWork.refreshFiltered(subject.level(), subject.level().getGameTime(), 100, this::matchesWorkBlock);
                            nextWorkScan = subject.level().getGameTime() + 100;
                        }
                        return;
                    }
                } else deliverToController(controller);
                finish(GroupControlTaskEvent.Status.COMPLETED);
                return;
            }
            if (settings != null) {
                var blockState = subject.level().getBlockState(currentBlock);
                if (blockState.isAir() || !matchesWorkBlock(currentBlock, blockState)) { advanceBlock(); return; }
                if (!isHarvestable(blockState, miningTool(blockState))
                        && !supplyTool(controller, region, stack -> !stack.isEmpty() && isHarvestable(blockState, stack))) return;
            }
            var target = Vec3.atCenterOf(currentBlock);
            if (subject.distanceToSqr(target) > 12.25) {
                var approach = workApproach(currentBlock);
                if (approach == null) {
                    workStatus = "path";
                    deferCurrentBlock();
                    return;
                }
                if (!ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5)
                        || hasStalledPath(approach)) deferCurrentBlock();
                return;
            }
            closeMovement();
            if (!(subject.level() instanceof ServerLevel level)) return;
            var state = level.getBlockState(currentBlock);
            var tool = miningTool(state);
            if (settings != null && !isHarvestable(state, tool)) {
                workStatus = "tool";
                closeMovement();
                return;
            }
            if (!isHarvestable(state, tool)) {
                advanceBlock();
                return;
            }
            miningProgressTicks++;
            if (miningProgressTicks % 5 == 1) subject.swing(InteractionHand.MAIN_HAND);
            if (miningProgressTicks < miningTicks(state, tool, level, currentBlock)) return;
            if (!WorkScheduling.budget(level).spend(WorkBudget.Operation.ACTION)) return;
            if (canBreak(controller, level, currentBlock)) {
                if (AbilityBlockDrops.harvestBlock(level, currentBlock, controller, subject, tool, drops -> {
                    drops.stream().filter(stack -> !stack.isEmpty())
                            .filter(stack -> !SpatialStorageService.collect(controller, stack))
                            .forEach(this::bufferMiningDrop);
                })) {
                    if (settings != null) ControlledEquipment.damageRealTool(subject, tool, 1);
                }
            } else if (settings != null) {
                workStatus = "permission";
                retryAfter = level.getGameTime() + 100;
            }
            saveOrder();
            advanceBlock();
        }

        private void refreshMiningPlan(ServerLevel level, boolean finalCheck) {
            sharedWork.mining.refresh(level.getGameTime(), finalCheck, pos -> {
                if (!level.hasChunkAt(pos)) return MiningWorkPlan.Eligibility.UNLOADED;
                return matchesWorkBlock(pos, level.getBlockState(pos))
                        ? MiningWorkPlan.Eligibility.ELIGIBLE : MiningWorkPlan.Eligibility.EXCLUDED;
            }, 64, () -> WorkScheduling.budget(level).spend(WorkBudget.Operation.BLOCK));
        }

        private void clearMiningTarget() {
            currentBlock = null;
            miningProgressTicks = 0;
            closeMovement();
            clearWorkApproach();
        }

        private void blockMiningTarget(String reason, int retryTicks) {
            sharedWork.mining.block(currentBlock, reason, subject.level().getGameTime() + retryTicks);
            workStatus = reason;
            clearMiningTarget();
        }

        private void tickPlannedMining(ServerPlayer controller, BlockWorkRegion region) {
            var level = (ServerLevel) subject.level();
            var plan = sharedWork.mining;
            var now = level.getGameTime();
            refreshMiningPlan(level, false);
            if (currentBlock == null) currentBlock = plan.claim(subject.getUUID(), subject.position(), now,
                    pos -> sharedWork.exposed(level, pos), 32,
                    () -> WorkScheduling.budget(level).spend(WorkBudget.Operation.CLAIM));
            if (currentBlock == null) {
                closeMovement();
                if (plan.progress().remaining() == 0) refreshMiningPlan(level, true);
                if (!plan.completionVerified()) {
                    workStatus = plan.blockedReason();
                    if (!bufferedDrops.isEmpty() && settings.output().isPresent()) depositWorkCargo(controller, region);
                    return;
                }
                if (!depositWorkCargo(controller, region)) return;
                if (settings.repeat()) { workStatus = "waiting"; return; }
                finish(GroupControlTaskEvent.Status.COMPLETED);
                return;
            }
            if (!plan.owns(subject.getUUID(), currentBlock)) { clearMiningTarget(); return; }
            if (!level.hasChunkAt(currentBlock)) { blockMiningTarget("unloaded", 20); return; }
            var state = level.getBlockState(currentBlock);
            if (!matchesWorkBlock(currentBlock, state)) {
                plan.resolve(currentBlock);
                clearMiningTarget();
                return;
            }
            var tool = miningTool(state);
            if (!isHarvestable(state, tool)) {
                if (!supplyTool(controller, region, stack -> !stack.isEmpty() && isHarvestable(state, stack))) {
                    if (movement == null || workStatus.equals("path")) blockMiningTarget("tool", 100);
                    return;
                }
                tool = miningTool(state);
            }
            var target = Vec3.atCenterOf(currentBlock);
            var inReach = subject.distanceToSqr(target) <= 12.25;
            var area = settings.miningReach() == WorkSettings.MiningReach.AREA
                    || settings.miningReach() == WorkSettings.MiningReach.ADAPTIVE && remoteMining;
            if (!inReach && !area) {
                var approach = workApproach(currentBlock);
                var failed = approach == null || !ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.0)
                        || hasStalledPath(approach) || stalledTicks >= 60;
                if (!failed) { workStatus = "approaching"; return; }
                if (settings.miningReach() == WorkSettings.MiningReach.NEARBY) {
                    blockMiningTarget("path", 40);
                    return;
                }
                remoteMining = true;
            }
            closeMovement();
            workStatus = inReach ? "working" : "remote";
            miningProgressTicks++;
            if (miningProgressTicks % 5 == 1) subject.swing(InteractionHand.MAIN_HAND);
            if (miningProgressTicks < miningTicks(state, tool, level, currentBlock)) return;
            if (!WorkScheduling.budget(level).spend(WorkBudget.Operation.ACTION)) return;
            if (!canBreak(controller, level, currentBlock)) { blockMiningTarget("permission", 100); return; }
            if (!AbilityBlockDrops.harvestBlock(level, currentBlock, controller, subject, tool, drops -> {
                for (var drop : drops) {
                    if (!drop.isEmpty() && !SpatialStorageService.collect(controller, drop)) {
                        bufferMiningDrop(drop);
                    }
                }
            })) {
                blockMiningTarget("permission", 100);
                return;
            }
            ControlledEquipment.damageRealTool(subject, tool, 1);
            plan.resolve(currentBlock);
            saveOrder();
            clearMiningTarget();
        }

        private void bufferMiningDrop(ItemStack drop) {
            var remaining = drop.copy();
            for (var cargo : bufferedDrops) {
                if (!ItemStack.isSameItemSameComponents(cargo, remaining)) continue;
                var moved = Math.clamp(cargo.getMaxStackSize() - cargo.getCount(), 0, remaining.getCount());
                cargo.grow(moved);
                remaining.shrink(moved);
                if (remaining.isEmpty()) return;
            }
            while (!remaining.isEmpty()) {
                var count = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                bufferedDrops.add(remaining.copyWithCount(count));
                remaining.shrink(count);
            }
        }

        private void tickFarm(ServerPlayer controller, BlockWorkRegion region) {
            var now = subject.level().getGameTime();
            if (currentBlock == null) {
                if (sharedWork != null || now >= nextFarmScanTick) {
                    populateMatureCrops(region);
                    nextFarmScanTick = now + FARM_RESCAN_TICKS;
                }
                currentBlock = pollBlock();
            }
            if (currentBlock == null) {
                if (sharedWork != null && (sharedWork.scanning() || sharedWork.hasPending() || !sharedWork.claimed.isEmpty())) {
                    workStatus = "scanning";
                    return;
                }
                if (settings != null) {
                    if (!depositWorkCargo(controller, region)) return;
                    workStatus = "waiting";
                    if (!settings.repeat()) { finish(GroupControlTaskEvent.Status.COMPLETED); return; }
                } else depositFarmDrops(controller, region, false);
                closeMovement();
                return;
            }
            if (settings != null && subject.level().getBlockState(currentBlock).isAir()) {
                if (!subject.level().getBlockState(currentBlock.below()).is(Blocks.FARMLAND)
                        && !(ControlledEquipment.tool(subject, Items.IRON_HOE).getItem() instanceof HoeItem)
                        && !supplyTool(controller, region, stack -> stack.getItem() instanceof HoeItem)) return;
                if (bufferedDrops.stream().noneMatch(this::isSeed) && !collectSeeds(controller, region)) return;
            }
            var target = Vec3.atCenterOf(currentBlock);
            if (subject.distanceToSqr(target) > 12.25) {
                var approach = workApproach(currentBlock);
                if (approach == null) {
                    workStatus = "path";
                    deferCurrentBlock();
                    return;
                }
                if (!ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5)
                        || hasStalledPath(approach)) deferCurrentBlock();
                return;
            }
            closeMovement();
            if (subject.level() instanceof ServerLevel level) {
                if (!level.hasChunkAt(currentBlock)) { deferCurrentBlock(); return; }
                if (!WorkScheduling.budget(level).spend(WorkBudget.Operation.ACTION)) return;
                if (settings != null && level.getBlockState(currentBlock).isAir()) plantCrop(controller, level, currentBlock);
                else harvestCrop(controller, level, currentBlock);
                if (settings == null) depositFarmDrops(controller, region, false);
                saveOrder();
            }
            advanceBlock();
        }

        private void populateMatureCrops(BlockWorkRegion region) {
            if (sharedWork != null) {
                if (settings == null) sharedWork.refreshCrops(subject.level(), subject.level().getGameTime());
                else sharedWork.refreshFiltered(subject.level(), subject.level().getGameTime(), 20, this::isFarmCandidate);
                return;
            }
            var index = 0;
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                if (index++ % subjectCount != subjectIndex) continue;
                var state = subject.level().getBlockState(pos);
                if (isFarmCandidate(pos, state)) {
                    enqueueBlock(pos);
                }
            }
        }

        private BlockPos nextMiningBlock() {
            int checked = 0;
            while (checked++ < 16 && (sharedWork != null ? sharedWork.hasPending() : !pendingBlocks.isEmpty())) {
                var candidate = pollBlock();
                if (candidate == null) return null;
                if (!subject.level().hasChunkAt(candidate)) { enqueueBlock(candidate); return null; }
                var state = subject.level().getBlockState(candidate);
                if (!state.isAir() && (settings == null ? isHarvestable(state, miningTool(state))
                        : matchesWorkBlock(candidate, state))) return candidate;
                if (sharedWork != null) sharedWork.complete(candidate);
            }
            return null;
        }

        private void advanceBlock() {
            if (sharedWork != null && currentBlock != null) sharedWork.complete(currentBlock);
            currentBlock = null;
            miningProgressTicks = 0;
            clearWorkApproach();
        }

        private void deferCurrentBlock() {
            if (settings != null) { retryAfter = subject.level().getGameTime() + 40; workStatus = "path"; }
            if (currentBlock != null) {
                if (sharedWork != null) sharedWork.defer(currentBlock);
                else enqueueBlock(currentBlock);
            }
            closeMovement();
            currentBlock = null;
            miningProgressTicks = 0;
            clearWorkApproach();
        }

        private Vec3 workApproach(BlockPos block) {
            var now = subject.level().getGameTime();
            if (block.equals(workApproachBlock) && (workApproachPoint != null || now < workApproachRetry)) return workApproachPoint;
            if (!block.equals(workApproachBlock)) {
                closeMovement();
                clearWorkApproach();
                workApproachBlock = block.immutable();
            }
            if (workSearch == null) workSearch = GroupControlNavigation.workSearch(subject, block);
            var result = workSearch.poll();
            if (result.status() == GroupControlNavigation.Status.DEFERRED) throw DeferredWork.INSTANCE;
            workApproachPoint = result.position();
            workSearch.close();
            workSearch = null;
            workApproachRetry = now + 40;
            return workApproachPoint;
        }

        private void clearWorkApproach() {
            if (workSearch != null) workSearch.close();
            workSearch = null;
            workApproachRetry = 0;
            workApproachBlock = null;
            workApproachPoint = null;
        }

        private ItemStack miningTool(BlockState state) {
            var held = subject.getMainHandItem();
            if (settings != null) return ControlledEquipment.miningTool(subject, state);
            var ironPickaxe = new ItemStack(Items.IRON_PICKAXE);
            if (held.isEmpty()) return ironPickaxe;
            var heldCorrect = held.isCorrectToolForDrops(state);
            var ironCorrect = ironPickaxe.isCorrectToolForDrops(state);
            if (ironCorrect && (!heldCorrect
                    || ironPickaxe.getDestroySpeed(state) > held.getDestroySpeed(state))) {
                return ironPickaxe;
            }
            if (heldCorrect || held.getDestroySpeed(state) > ironPickaxe.getDestroySpeed(state)) {
                return held;
            }
            return ironPickaxe;
        }

        private static boolean isHarvestable(
                BlockState state,
                ItemStack tool
        ) {
            return !state.isAir() && (!state.requiresCorrectToolForDrops()
                    || tool.isCorrectToolForDrops(state));
        }

        private static int miningTicks(
                BlockState state,
                ItemStack tool,
                ServerLevel level,
                BlockPos pos
        ) {
            var hardness = Math.max(0.0f, state.getDestroySpeed(level, pos));
            var speed = Math.max(1.0f, tool.getDestroySpeed(state));
            return Math.max(1, Mth.ceil(hardness * 30.0f / speed));
        }

        private Vec3 resolve(ControlDestination destination, ServerPlayer controller) {
            if (destination instanceof ControlDestination.Position(var dimension, var value)) {
                return dimension.equals(subject.level().dimension().identifier()) ? value : null;
            }
            if (destination instanceof ControlDestination.Entity(var uuid)) {
                var entity = controller.level().getEntity(uuid);
                return entity == null ? null : entity.position();
            }
            return null;
        }

        private boolean ensureMovement(
                ServerPlayer controller,
                ControlDestination destination,
                double arrivalRadius
        ) {
            if (movement != null) {
                if (movement.state() == ControlState.ACTIVE
                        || movement.state() == ControlState.PREEMPTED) return true;
                if (movement.failureReason().isPresent()) {
                    movement = null;
                    return false;
                }
            }
            movement = MentalControlApi.apply(new ControlRequest(
                    controller,
                    subject,
                    source,
                    scopeId,
                    priority,
                    Long.MAX_VALUE,
                    List.of(new ControlDirective.MoveTo(destination, arrivalRadius))
            ));
            movementStartedTick = subject.level().getGameTime();
            lastMovementDistance = Double.MAX_VALUE;
            stalledTicks = 0;
            return !movement.isClosed() || movement.failureReason().isEmpty();
        }

        private boolean hasStalledPath(Vec3 target) {
            if (GroupControlNavigation.waitingForPath(subject)) {
                movementStartedTick = subject.level().getGameTime();
                return false;
            }
            if (movement != null && movement.state() == ControlState.PREEMPTED) {
                movementStartedTick = subject.level().getGameTime();
                stalledTicks = 0;
                lastMovementDistance = subject.distanceToSqr(target);
                return false;
            }
            var distance = subject.distanceToSqr(target);
            var now = subject.level().getGameTime();
            if (lastMovementDistance - distance > 0.25) {
                lastMovementDistance = distance;
                stalledTicks = 0;
                return false;
            }
            if (now - movementStartedTick < PATH_GRACE_TICKS) return false;
            stalledTicks++;
            var navigationStopped = subject instanceof Mob mob && mob.getNavigation().isDone();
            return stalledTicks >= PATH_STALL_TICKS || navigationStopped && stalledTicks >= 20;
        }

        private void harvestCrop(ServerPlayer controller, ServerLevel level, BlockPos pos) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)
                    || settings != null && (!settings.harvest() || !settings.matches(state))
                    || !canBreak(controller, level, pos)) return;
            subject.swing(InteractionHand.MAIN_HAND);
            AbilityBlockDrops.harvestBlock(level, pos, controller, subject, subject.getMainHandItem(), drops -> {
                if (settings == null) level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
                else if (settings.replant()) {
                    var seed = drops.stream().filter(stack -> stack.getItem() instanceof BlockItem item
                            && item.getBlock() == crop && !stack.isEmpty()).findFirst();
                    if (seed.isPresent() && crop.getStateForAge(0).canSurvive(level, pos)) {
                        seed.get().shrink(1);
                        level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
                    }
                }
                drops.stream().filter(stack -> !stack.isEmpty())
                        .filter(stack -> !SpatialStorageService.collect(controller, stack))
                        .forEach(this::bufferMiningDrop);
            });
        }

        private boolean canBreak(ServerPlayer controller, ServerLevel level, BlockPos pos) {
            if (!DestroyBlocksSetting.canDestroyBlocks(controller)
                    || !level.hasChunkAt(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()
                    || !level.mayInteract(controller, pos)) return false;
            var state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0f
                    || controller.blockActionRestricted(
                    level, pos, controller.gameMode.getGameModeForPlayer())
                    || state.getBlock() instanceof GameMasterBlock
                    && !controller.canUseGameMasterBlocks()) return false;
            var event = new BreakBlockEvent(level, pos.immutable(), state, controller);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }

        private void depositFarmDrops(
                ServerPlayer controller,
                BlockWorkRegion region,
                boolean fallbackToController
        ) {
            if (bufferedDrops.isEmpty() || !(subject.level() instanceof ServerLevel level)) return;
            if (sharedWork != null) {
                var pos = sharedWork.nextContainer(level);
                if (pos != null && level.hasChunkAt(pos) && level.mayInteract(controller, pos)) {
                    var container = HopperBlockEntity.getContainerAt(level, pos);
                    if (container != null) {
                        var result = WorkInventoryTransfer.insert(container, bufferedDrops.getFirst(), farmOutputSlot, 16, WorkScheduling.budget(level));
                        farmOutputSlot = result.nextSlot();
                    }
                }
            }
            bufferedDrops.removeIf(ItemStack::isEmpty);
            if (fallbackToController) deliverToController(controller);
        }

        private void deliverToController(ServerPlayer controller) {
            for (var stack : bufferedDrops) addToController(controller, stack);
            bufferedDrops.clear();
        }

        private static void addToController(ServerPlayer controller, ItemStack stack) {
            if (stack.isEmpty()) return;
            var remainder = stack;
            var inventory = controller.getInventory();
            for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++) {
                for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && !remainder.isEmpty(); slot++) {
                    var existing = inventory.getItem(slot);
                    if (pass == 0 && (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remainder))) continue;
                    if (pass == 1 && !existing.isEmpty()) continue;
                    var room = Math.min(inventory.getMaxStackSize(), remainder.getMaxStackSize()) - existing.getCount();
                    var moved = Math.clamp(room, 0, remainder.getCount());
                    if (moved == 0) continue;
                    if (existing.isEmpty()) inventory.setItem(slot, remainder.split(moved));
                    else { existing.grow(moved); remainder.shrink(moved); }
                }
            }
            inventory.setChanged();
            if (!remainder.isEmpty() && controller.level() instanceof ServerLevel level) {
                Block.popResource(level, controller.blockPosition(), remainder.copy());
                remainder.setCount(0);
            }
        }

        private void closeMovement() {
            if (movement != null) movement.close();
            movement = null;
            stalledTicks = 0;
            lastMovementDistance = Double.MAX_VALUE;
        }

        private void notifyObserver(GroupControlTaskEvent.Status status) {
            try {
                observer.onTaskEvent(new GroupControlTaskEvent(
                        subject.getUUID(), subject.getDisplayName().getString(), status));
            } catch (RuntimeException ignored) {
            }
        }

        private BlockWorkRegion workRegion() {
            return command instanceof GroupControlCommand.Farm farm ? farm.region()
                    : ((GroupControlCommand.GatherResources) command).region();
        }

        private void saveOrder() {
            if (settings == null || closed || !(subject.level() instanceof ServerLevel level)) return;
            WorkOrderData.get(level.getServer()).put(orderSnapshot());
        }

        private WorkOrderData.Entry orderSnapshot() {
            var region = workRegion();
            return new WorkOrderData.Entry(controllerId.toString(),
                    subject.getUUID().toString(), source, region.dimension(), region.minimum(), region.maximum(),
                    settings, priority, paused, bufferedDrops, sharedWork.orderId, revision, false, faulted);
        }

        private void finishPersistent(GroupControlTaskEvent.Status status) {
            var server = ((ServerLevel) subject.level()).getServer();
            var data = WorkOrderData.get(server);
            var returning = orderSnapshot().asReturning();
            // Publish the cargo's new owner before deleting the active assignment.
            data.put(returning);
            removeOrder();
            bufferedDrops.clear();
            if (!terminalNotified) { terminalNotified = true; notifyObserver(status); }
            closeInternal();
            returnCargo(server, returning);
        }

        private void removeOrder() {
            if (settings != null && subject.level() instanceof ServerLevel level) {
                WorkOrderData.get(level.getServer()).remove(controllerId + "/" + source + "/" + subject.getUUID());
            }
        }

        private boolean matchesWorkBlock(BlockPos pos, BlockState state) {
            if (state.isAir() || !settings.harvest() || !settings.matches(state) || state.getDestroySpeed(subject.level(), pos) < 0
                    || subject.level().getBlockEntity(pos) != null) return false;
            return switch (settings.mode()) {
                case MINING -> true;
                case LOGGING -> state.is(BlockTags.LOGS);
                case SUGAR_CANE -> (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO))
                        && subject.level().getBlockState(pos.below()).is(state.getBlock());
                case CLEARING -> state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                        || state.is(Blocks.SNOW) || state.is(Blocks.FIRE);
                case FARMING, SHEARING, MILKING, FEEDING, COLLECT -> false;
            };
        }

        private boolean isFarmCandidate(BlockPos pos, BlockState state) {
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                return settings == null || settings.harvest() && settings.matches(state);
            }
            return settings != null && settings.replant() && state.isAir()
                    && (subject.level().getBlockState(pos.below()).is(Blocks.FARMLAND)
                    || pos.getY() > workRegion().minimum().getY()
                    && (subject.level().getBlockState(pos.below()).is(Blocks.DIRT)
                    || subject.level().getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)));
        }

        private void tickCollection(ServerPlayer controller, BlockWorkRegion region) {
            var level = (ServerLevel) subject.level();
            if (level.getGameTime() < nextAnimalAction || !settings.harvest()) return;
            var item = (ItemEntity) selectWorkEntity(candidate -> candidate instanceof ItemEntity drop
                    && !drop.hasPickUpDelay() && !drop.getItem().isEmpty() && settings.matches(drop.getItem()));
            if (item == null) {
                if (entitySelectionDeferred || sharedWork.entityScanPending() || !sharedWork.entityClaims.isEmpty()) { workStatus = "scanning"; return; }
                workStatus = "waiting";
                if (!depositWorkCargo(controller, region)) return;
                closeMovement();
                nextAnimalAction = level.getGameTime() + 20;
                if (!settings.repeat()) finish(GroupControlTaskEvent.Status.COMPLETED);
                return;
            }
            if (!level.mayInteract(controller, item.blockPosition())) { workStatus = "permission"; return; }
            if (subject.distanceToSqr(item) > 4) {
                var approach = workApproach(item.blockPosition());
                if (approach == null || !ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1) || hasStalledPath(approach)) {
                    closeMovement(); clearWorkApproach(); workStatus = "path";
                    retryAfter = level.getGameTime() + 40;
                }
                return;
            }
            closeMovement();
            if (!WorkScheduling.budget(level).spend(WorkBudget.Operation.ACTION)) return;
            bufferMiningDrop(item.getItem());
            item.discard();
            releaseEntity();
            nextAnimalAction = level.getGameTime() + 5;
            saveOrder();
        }

        private boolean supplyTool(ServerPlayer controller, BlockWorkRegion region,
                                   Predicate<ItemStack> suitable) {
            workStatus = "tool";
            if (subject.level().getGameTime() < nextSupplyCheck) return false;
            if (settings.input().isEmpty()) { closeMovement(); return false; }
            var pos = settings.input().get();
            var level = (ServerLevel) subject.level();
            if (!level.hasChunkAt(pos) || !level.mayInteract(controller, pos)) return false;
            if (subject.distanceToSqr(Vec3.atCenterOf(pos)) > 12.25) {
                var approach = workApproach(pos);
                if (approach == null || !ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5)
                        || hasStalledPath(approach)) { closeMovement(); clearWorkApproach(); workStatus = "path"; }
                return false;
            }
            closeMovement();
            clearWorkApproach();
            var container = HopperBlockEntity.getContainerAt(level, pos);
            if (container == null || container.getContainerSize() == 0) { nextSupplyCheck = level.getGameTime() + 20; return false; }
            var budget = WorkScheduling.budget(level);
            for (int checked = 0; checked < Math.min(16, container.getContainerSize()) && budget.spend(WorkBudget.Operation.SLOT); checked++) {
                int slot = Math.floorMod(supplySlot++, container.getContainerSize());
                if (!suitable.test(container.getItem(slot))) continue;
                if (!budget.spend(WorkBudget.Operation.TRANSFER)) return false;
                var replacement = container.removeItem(slot, 1);
                if (replacement.isEmpty()) continue;
                var previous = subject.getMainHandItem();
                subject.setItemSlot(EquipmentSlot.MAINHAND, replacement);
                if (!previous.isEmpty()) {
                    var returned = previous.copy();
                    try { WorkInventoryTransfer.insert(container, returned, 0, 16, budget); }
                    finally { bufferMiningDrop(returned); saveOrder(); }
                }
                container.setChanged();
                saveOrder();
                return true;
            }
            if (supplySlot >= container.getContainerSize()) {
                supplySlot = 0;
                nextSupplyCheck = level.getGameTime() + 20 + Math.floorMod(subject.getId(), 5);
                releaseEntity();
            }
            return false;
        }

        private void tickAnimalWork(ServerPlayer controller, BlockWorkRegion region) {
            var level = (ServerLevel) subject.level();
            if (level.getGameTime() < nextAnimalAction) return;
            if (!settings.harvest()) { workStatus = "waiting"; return; }
            var target = (Animal) selectWorkEntity(candidate -> candidate instanceof Animal animal && animal != subject
                    && !animal.isBaby() && settings.matches(animal) && switch (settings.mode()) {
                case SHEARING -> animal instanceof IShearable shearable && shearable.isShearable(controller,
                        ControlledEquipment.tool(subject, Items.SHEARS), level, animal.blockPosition());
                case MILKING -> animal instanceof Cow;
                case FEEDING -> animal.canFallInLove();
                default -> false;
            });
            if (target == null) {
                if (entitySelectionDeferred || sharedWork.entityScanPending() || !sharedWork.entityClaims.isEmpty()) { workStatus = "scanning"; return; }
                workStatus = "waiting";
                closeMovement();
                if (!depositWorkCargo(controller, region)) return;
                nextAnimalAction = level.getGameTime() + 20;
                if (!settings.repeat()) finish(GroupControlTaskEvent.Status.COMPLETED);
                return;
            }
            Predicate<ItemStack> suitable = stack -> switch (settings.mode()) {
                case SHEARING -> stack.is(Items.SHEARS);
                case MILKING -> stack.is(Items.BUCKET);
                case FEEDING -> !stack.isEmpty() && target.isFood(stack);
                default -> false;
            };
            if (!suitable.test(settings.mode() == WorkSettings.Mode.SHEARING
                    ? ControlledEquipment.tool(subject, Items.SHEARS) : subject.getMainHandItem())
                    && !supplyTool(controller, region, suitable)) return;
            var held = settings.mode() == WorkSettings.Mode.SHEARING
                    ? ControlledEquipment.tool(subject, Items.SHEARS) : subject.getMainHandItem();
            if (!level.mayInteract(controller, target.blockPosition())) { workStatus = "path"; return; }
            if (subject.distanceToSqr(target) > 9) {
                var approach = workApproach(target.blockPosition());
                if (approach == null || !ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5)
                        || hasStalledPath(approach)) {
                    closeMovement(); clearWorkApproach(); releaseEntity(); workStatus = "path";
                    retryAfter = level.getGameTime() + 40;
                }
                return;
            }
            closeMovement();
            if (!WorkScheduling.budget(level).spend(WorkBudget.Operation.ACTION)) return;
            subject.swing(InteractionHand.MAIN_HAND);
            switch (settings.mode()) {
                case SHEARING -> {
                    var shearable = (IShearable) target;
                    shearable.onSheared(controller, held, level, target.blockPosition()).stream()
                            .filter(stack -> !stack.isEmpty()).forEach(this::bufferMiningDrop);
                    ControlledEquipment.damageRealTool(subject, held, 1);
                }
                case MILKING -> { held.shrink(1); bufferedDrops.add(new ItemStack(Items.MILK_BUCKET)); }
                case FEEDING -> { held.shrink(1); target.setInLove(controller); }
                default -> { }
            }
            nextAnimalAction = level.getGameTime() + 40;
            releaseEntity();
            saveOrder();
            if (!settings.repeat()) finish(GroupControlTaskEvent.Status.COMPLETED);
        }

        private boolean isSeed(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                    && item.getBlock() instanceof CropBlock crop && settings.matches(crop.defaultBlockState());
        }

        private boolean collectSeeds(ServerPlayer controller, BlockWorkRegion region) {
            workStatus = "materials";
            if (subject.level().getGameTime() < nextSeedCheck) return false;
            if (settings.input().isEmpty()) { closeMovement(); return false; }
            var pos = settings.input().get();
            var level = (ServerLevel) subject.level();
            if (!level.hasChunkAt(pos) || !level.mayInteract(controller, pos)) return false;
            if (subject.distanceToSqr(Vec3.atCenterOf(pos)) > 12.25) {
                var approach = workApproach(pos);
                if (approach == null || !ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5)
                        || hasStalledPath(approach)) { closeMovement(); clearWorkApproach(); workStatus = "path"; }
                return false;
            }
            closeMovement();
            clearWorkApproach();
            var container = HopperBlockEntity.getContainerAt(level, pos);
            if (container == null || container.getContainerSize() == 0) { nextSeedCheck = level.getGameTime() + 20; return false; }
            var budget = WorkScheduling.budget(level);
            for (int checked = 0; checked < Math.min(16, container.getContainerSize()) && budget.spend(WorkBudget.Operation.SLOT); checked++) {
                int slot = Math.floorMod(seedSlot++, container.getContainerSize());
                if (!isSeed(container.getItem(slot))) continue;
                if (!budget.spend(WorkBudget.Operation.TRANSFER)) return false;
                bufferMiningDrop(container.removeItem(slot, 16));
                container.setChanged();
                saveOrder();
                return true;
            }
            if (seedSlot >= container.getContainerSize()) { seedSlot = 0; nextSeedCheck = level.getGameTime() + 20; }
            return false;
        }

        private void plantCrop(ServerPlayer controller, ServerLevel level, BlockPos pos) {
            if (!level.mayInteract(controller, pos) || !DestroyBlocksSetting.canDestroyBlocks(controller)) return;
            if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
                var soil = level.getBlockState(pos.below());
                if (pos.getY() <= workRegion().minimum().getY()
                        || !(soil.is(Blocks.DIRT) || soil.is(Blocks.GRASS_BLOCK))
                        || !(ControlledEquipment.tool(subject, Items.IRON_HOE).getItem() instanceof HoeItem)) return;
                if (!level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL)) return;
                ControlledEquipment.damageRealTool(subject, ControlledEquipment.tool(subject, Items.IRON_HOE), 1);
            }
            for (var stack : bufferedDrops) {
                if (!isSeed(stack)) continue;
                var crop = (CropBlock) ((BlockItem) stack.getItem()).getBlock();
                var state = crop.getStateForAge(0);
                if (!state.canSurvive(level, pos)) continue;
                if (level.setBlock(pos, state, Block.UPDATE_ALL)) {
                    stack.shrink(1);
                    subject.swing(InteractionHand.MAIN_HAND);
                }
                bufferedDrops.removeIf(ItemStack::isEmpty);
                return;
            }
            workStatus = "materials";
        }

        private boolean depositWorkCargo(ServerPlayer controller, BlockWorkRegion region) {
            bufferedDrops.removeIf(ItemStack::isEmpty);
            if (bufferedDrops.isEmpty()) return true;
            if (subject.level().getGameTime() < nextDepositCheck) { workStatus = "output"; return false; }
            if (settings.output().isEmpty()) {
                if (bufferedDrops.size() < 18) return true;
                workStatus = "output";
                closeMovement();
                return false;
            }
            var pos = settings.output().get();
            var level = (ServerLevel) subject.level();
            if (!level.hasChunkAt(pos) || !level.mayInteract(controller, pos)) { workStatus = "output"; return false; }
            var target = Vec3.atCenterOf(pos);
            if (subject.distanceToSqr(target) > 12.25) {
                var approach = workApproach(pos);
                workStatus = "delivering";
                if (approach == null || !ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5)
                        || hasStalledPath(approach)) { closeMovement(); workStatus = "path"; }
                return false;
            }
            closeMovement();
            clearWorkApproach();
            var container = HopperBlockEntity.getContainerAt(level, pos);
            if (container == null) { workStatus = "output"; nextDepositCheck = level.getGameTime() + 20; return false; }
            var budget = WorkScheduling.budget(level);
            var reservedSeeds = 0;
            var visited = 0;
            var transfers = 0;
            var moved = 0;
            for (var stack : bufferedDrops) {
                int reserved = 0;
                if (settings.mode() == WorkSettings.Mode.FARMING && settings.replant() && isSeed(stack)
                        && reservedSeeds < 16) {
                    reserved = Math.min(16 - reservedSeeds, stack.getCount());
                    reservedSeeds += reserved;
                }
                if (stack.getCount() == reserved) continue;
                var deposit = stack.copyWithCount(stack.getCount() - reserved);
                WorkInventoryTransfer.Result result;
                try { result = WorkInventoryTransfer.insert(container, deposit, depositSlot, 16 - visited, budget); }
                finally { stack.setCount(reserved + deposit.getCount()); saveOrder(); }
                visited += result.visited();
                moved += result.moved();
                depositSlot = result.nextSlot();
                depositVisited += result.visited();
                if (deposit.isEmpty()) { depositSlot = depositVisited = 0; }
                if (visited >= 16 || ++transfers >= 4 || !budget.hasTime()) break;
            }
            bufferedDrops.removeIf(ItemStack::isEmpty);
            if (moved == 0 && depositVisited >= container.getContainerSize()) {
                depositVisited = 0;
                Collections.rotate(bufferedDrops, -1);
                if (++blockedCargo >= bufferedDrops.size()) {
                    blockedCargo = 0;
                    nextDepositCheck = level.getGameTime() + 20 + Math.floorMod(subject.getId(), 5);
                }
            } else if (moved > 0) blockedCargo = 0;
            saveOrder();
            var onlySeeds = bufferedDrops.stream().allMatch(this::isSeed)
                    && bufferedDrops.stream().mapToInt(ItemStack::getCount).sum() <= 16;
            workStatus = bufferedDrops.isEmpty() || onlySeeds ? "working" : "output";
            return bufferedDrops.isEmpty() || onlySeeds;
        }

        private void finish(GroupControlTaskEvent.Status status) {
            if (closed) return;
            if (settings != null) { finishPersistent(status); return; }
            removeOrder();
            var server = subject.level() instanceof ServerLevel level ? level.getServer() : null;
            ServerPlayer controller = server == null
                    ? null : server.getPlayerList().getPlayer(controllerId);
            if (controller != null) {
                if (command instanceof GroupControlCommand.GatherResources) {
                    deliverToController(controller);
                } else if (command instanceof GroupControlCommand.Farm(var region)) {
                    depositFarmDrops(controller, region, true);
                }
            }
            if (!terminalNotified) {
                terminalNotified = true;
                notifyObserver(status);
            }
            closeInternal();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (closed) return;
            if (settings != null) { finishPersistent(GroupControlTaskEvent.Status.CANCELLED); return; }
            removeOrder();
            if (command instanceof GroupControlCommand.Farm(var region)) {
                var server = subject.level() instanceof ServerLevel level ? level.getServer() : null;
                ServerPlayer controller = server == null
                        ? null : server.getPlayerList().getPlayer(controllerId);
                if (controller != null) depositFarmDrops(controller, region, true);
            }
            if (!terminalNotified) {
                terminalNotified = true;
                notifyObserver(GroupControlTaskEvent.Status.CANCELLED);
            }
            closeInternal();
        }

        private void closeInternal() {
            if (closed) return;
            closed = true;
            closeMovement();
            releaseTarget();
            if (moveSearch != null) moveSearch.close();
            moveSearch = null;
            GroupControlNavigation.cancel(subject);
            releaseExclusiveWorkOrder();
            TASKS.remove(key, this);
            READY.remove(this);
            SCOPES.remove(scopeId);
            if (sharedWork != null && --sharedWork.workers == 0) {
                sharedWork.indexChunks(false);
                WORK_GROUPS.remove(new WorkGroupKey(controllerId, source, sharedWork.orderId), sharedWork);
            }
        }

        private void releaseTarget() {
            releaseEntity();
            if (sharedWork != null && sharedWork.mining != null) sharedWork.mining.release(subject.getUUID());
            else if (sharedWork != null && currentBlock != null) sharedWork.defer(currentBlock);
            currentBlock = null;
            miningProgressTicks = 0;
            clearWorkApproach();
        }

        private Entity selectWorkEntity(Predicate<Entity> filter) {
            if (workEntity != null && sharedWork.validEntity(workEntity)
                    && subject.getUUID().equals(sharedWork.entityClaims.get(workEntity.getUUID())) && filter.test(workEntity)) return workEntity;
            releaseEntity();
            sharedWork.scanEntities((ServerLevel) subject.level());
            Entity nearest = null;
            double distance = Double.MAX_VALUE;
            var budget = WorkScheduling.budget((ServerLevel) subject.level());
            var next = entityCandidateCursor == null ? sharedWork.entities.firstEntry() : sharedWork.entities.higherEntry(entityCandidateCursor);
            int count = 0;
            while (next != null && count++ < 32 && budget.spend(WorkBudget.Operation.CLAIM)) {
                entityCandidateCursor = next.getKey();
                var candidate = next.getValue();
                if (!sharedWork.validEntity(candidate)) {
                    sharedWork.entities.remove(next.getKey());
                    sharedWork.entityClaims.remove(next.getKey());
                } else if (!sharedWork.entityClaims.containsKey(next.getKey()) && filter.test(candidate)) {
                    double candidateDistance = subject.distanceToSqr(candidate);
                    if (candidateDistance < distance) { nearest = candidate; distance = candidateDistance; }
                }
                next = sharedWork.entities.higherEntry(entityCandidateCursor);
            }
            entitySelectionDeferred = next != null;
            if (next == null || nearest != null) entityCandidateCursor = null;
            workEntity = nearest;
            if (nearest != null) sharedWork.entityClaims.put(nearest.getUUID(), subject.getUUID());
            return workEntity;
        }

        private void releaseEntity() {
            if (workEntity != null && sharedWork != null) sharedWork.entityClaims.remove(workEntity.getUUID(), subject.getUUID());
            workEntity = null;
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Pre event) {
            tick(event.getServer());
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            clear();
            GroupControlNavigation.clear(event.getServer());
        }
    }

    private record TaskKey(UUID controllerId, Identifier source, UUID subjectId) {
    }

    private record AdapterEntry(Identifier id, int priority, GroupControlAdapter adapter) {
    }

    private static final class SharedWorkPlan {
        private String orderId = UUID.randomUUID().toString();
        private int workers;
        private static final int FARM_SCAN_INTERVAL_TICKS = 20;
        private final BlockWorkRegion region;
        private final boolean farming;
        private MiningWorkPlan mining;
        private long surfaceTick = Long.MIN_VALUE;
        private final Map<BlockPos, Boolean> surfaces = new HashMap<>();
        private final LinkedHashSet<BlockPos> pending = new LinkedHashSet<>();
        private final Set<BlockPos> queued = new HashSet<>();
        private final Set<BlockPos> claimed = new HashSet<>();
        private final LinkedHashSet<BlockPos> containerPositions = new LinkedHashSet<>();
        private Iterator<BlockPos> containerScan;
        private long nextContainerScan, containerScanTick = Long.MIN_VALUE;
        private long nextFarmScanTick = Long.MIN_VALUE;
        private Iterator<BlockPos> scan;
        private long scanTick = Long.MIN_VALUE;
        private boolean scanCompleted, scanUnloaded;
        private final LinkedHashSet<BlockPos> changedBlocks = new LinkedHashSet<>();
        private final TreeMap<UUID, Entity> entities = new TreeMap<>();
        private final Map<UUID, UUID> entityClaims = new HashMap<>();
        private org.academy.api.server.ability.SectionEntityIndex<Entity>.Cursor entityScan;
        private long nextEntityScan, entityScanTick = Long.MIN_VALUE;
        private boolean entitiesScanned;

        private SharedWorkPlan(BlockWorkRegion region, boolean farming) {
            this.region = region;
            this.farming = farming;
            if (!farming) scan = BlockPos.betweenClosed(region.minimum(), region.maximum()).iterator();
        }

        private void indexChunks(boolean add) {
            var chunks = WORK_CHUNKS.computeIfAbsent(region.dimension(), unused -> new HashMap<>());
            for (int x = region.minimum().getX() >> 4; x <= region.maximum().getX() >> 4; x++) {
                for (int z = region.minimum().getZ() >> 4; z <= region.maximum().getZ() >> 4; z++) {
                    long key = chunkKey(x, z);
                    if (add) chunks.computeIfAbsent(key, unused -> new HashSet<>()).add(this);
                    else {
                        var groups = chunks.get(key);
                        if (groups != null) { groups.remove(this); if (groups.isEmpty()) chunks.remove(key); }
                    }
                }
            }
            if (chunks.isEmpty()) WORK_CHUNKS.remove(region.dimension());
        }

        private void changed(BlockPos pos) {
            if (pos.getX() < region.minimum().getX() || pos.getX() > region.maximum().getX()
                    || pos.getY() < region.minimum().getY() || pos.getY() > region.maximum().getY()
                    || pos.getZ() < region.minimum().getZ() || pos.getZ() > region.maximum().getZ()) return;
            surfaces.remove(pos);
            if (mining != null) mining.changed(pos);
            else changedBlocks.add(pos.immutable());
            nextContainerScan = 0;
        }

        private static SharedWorkPlan create(GroupControlCommand command) {
            return switch (command) {
                case GroupControlCommand.GatherResources gather ->
                        new SharedWorkPlan(gather.region(), false);
                case GroupControlCommand.Farm farm -> new SharedWorkPlan(farm.region(), true);
                case GroupControlCommand.MoveTo ignored -> null;
                case GroupControlCommand.Work work -> {
                    var plan = new SharedWorkPlan(work.region(), true);
                    if (work.settings().minesBlocks()) plan.mining = new MiningWorkPlan(work.region());
                    yield plan;
                }
            };
        }

        private boolean exposed(ServerLevel level, BlockPos pos) {
            if (surfaceTick != level.getGameTime()) { surfaceTick = level.getGameTime(); surfaces.clear(); }
            return surfaces.computeIfAbsent(pos, candidate -> {
                for (var direction : Direction.values()) {
                    var adjacent = candidate.relative(direction);
                    if (level.hasChunkAt(adjacent) && level.getBlockState(adjacent).isAir()) return true;
                }
                return false;
            });
        }

        private boolean validEntity(Entity entity) {
            return !entity.isRemoved() && entity.isAlive() && entity.level().dimension().identifier().equals(region.dimension())
                    && entity.getX() >= region.minimum().getX() && entity.getX() < region.maximum().getX() + 1.0
                    && entity.getY() >= region.minimum().getY() && entity.getY() < region.maximum().getY() + 1.0
                    && entity.getZ() >= region.minimum().getZ() && entity.getZ() < region.maximum().getZ() + 1.0;
        }

        private void scanEntities(ServerLevel level) {
            long now = level.getGameTime();
            if (entityScanTick == now || entityScan == null && now < nextEntityScan) return;
            entityScanTick = now;
            if (entityScan == null) {
                var center = Vec3.atCenterOf(region.center());
                entityScan = WorkEntityIndex.get(level).cursor(center.x, center.y, center.z, 32);
            }
            var budget = WorkScheduling.budget(level);
            for (int i = 0; i < 32 && !entityScan.finished() && budget.spend(WorkBudget.Operation.ENTITY); i++) {
                var entity = entityScan.next();
                if (entity != null && validEntity(entity)) entities.put(entity.getUUID(), entity);
            }
            if (entityScan.finished()) { entityScan = null; entitiesScanned = true; nextEntityScan = now + 20; }
        }

        private boolean entityScanPending() { return !entitiesScanned || entityScan != null; }


        private synchronized void queue(BlockPos position) {
            var immutable = position.immutable();
            if (queued.contains(immutable) || claimed.contains(immutable)) return;
            queued.add(immutable);
            pending.add(immutable);
        }

        private synchronized BlockPos claimNearest(LivingEntity subject) {
            var budget = WorkScheduling.budget((ServerLevel) subject.level());
            if (!farming && scan != null && scanTick != subject.level().getGameTime()) {
                scanTick = subject.level().getGameTime();
                for (int i = 0; i < 64 && scan.hasNext() && budget.spend(WorkBudget.Operation.BLOCK); i++) queue(scan.next());
                if (!scan.hasNext()) { scan = null; scanCompleted = true; }
            }
            if (pending.isEmpty()) return null;
            BlockPos nearest = null;
            var nearestDistance = Double.MAX_VALUE;
            int candidates = Math.min(32, pending.size());
            while (candidates-- > 0 && budget.spend(WorkBudget.Operation.CLAIM)) {
                var candidate = pending.removeFirst();
                pending.add(candidate);
                var distance = subject.distanceToSqr(Vec3.atCenterOf(candidate));
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) return null;
            pending.remove(nearest);
            queued.remove(nearest);
            claimed.add(nearest);
            return nearest;
        }

        private synchronized void complete(BlockPos position) {
            claimed.remove(position);
        }

        private synchronized void defer(BlockPos position) {
            var immutable = position.immutable();
            claimed.remove(immutable);
            queue(immutable);
        }

        private synchronized boolean hasPending() {
            return !pending.isEmpty() || !farming && scan != null;
        }

        private synchronized int pendingCount() {
            return pending.size();
        }

        private synchronized void refreshFiltered(Level level, long now, int interval,
                                                  BiPredicate<BlockPos, BlockState> filter) {
            if (scanTick == now || scan == null && changedBlocks.isEmpty() && now < nextFarmScanTick) return;
            if (scan == null) {
                scan = BlockPos.betweenClosed(region.minimum(), region.maximum()).iterator();
                scanUnloaded = false;
            }
            scanTick = now;
            var budget = WorkScheduling.budget((ServerLevel) level);
            int visited = 0;
            while (!changedBlocks.isEmpty() && visited < 64 && budget.spend(WorkBudget.Operation.BLOCK)) {
                var pos = changedBlocks.removeFirst();
                if (!level.hasChunkAt(pos)) scanUnloaded = true;
                else if (filter.test(pos, level.getBlockState(pos))) queue(pos);
                else { pending.remove(pos); queued.remove(pos); }
                visited++;
            }
            while (scan.hasNext() && visited++ < 64 && budget.spend(WorkBudget.Operation.BLOCK)) {
                var pos = scan.next();
                if (!level.hasChunkAt(pos)) { scanUnloaded = true; continue; }
                if (filter.test(pos, level.getBlockState(pos))) queue(pos);
                else { pending.remove(pos); queued.remove(pos); }
            }
            if (!scan.hasNext()) {
                scan = null;
                scanCompleted = true;
                nextFarmScanTick = now + interval;
            }
        }

        private boolean scanning() { return !scanCompleted || scan != null || scanUnloaded || !changedBlocks.isEmpty(); }

        private synchronized void refreshCrops(Level level, long now) {
            if (farming) refreshFiltered(level, now, FARM_SCAN_INTERVAL_TICKS,
                    (pos, state) -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state));
        }

        private synchronized BlockPos nextContainer(ServerLevel level) {
            long now = level.getGameTime();
            if (containerScanTick != now && (containerScan != null || now >= nextContainerScan)) {
                containerScanTick = now;
                if (containerScan == null) containerScan = BlockPos.betweenClosed(region.minimum(), region.maximum()).iterator();
                var budget = WorkScheduling.budget(level);
                for (int i = 0; i < 64 && containerScan.hasNext() && budget.spend(WorkBudget.Operation.BLOCK); i++) {
                    var pos = containerScan.next();
                    if (level.hasChunkAt(pos) && HopperBlockEntity.getContainerAt(level, pos) != null) containerPositions.add(pos.immutable());
                    else containerPositions.remove(pos);
                }
                if (!containerScan.hasNext()) { containerScan = null; nextContainerScan = now + 100; }
            }
            if (containerPositions.isEmpty()) return null;
            var next = containerPositions.removeFirst();
            containerPositions.add(next);
            return next;
        }
    }
}
