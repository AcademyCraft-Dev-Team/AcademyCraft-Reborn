package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.neoforged.neoforge.common.IShearable;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.ability.mentalout.MentalControlMemory;
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

import java.util.*;

/** Internal scheduler and default living-entity adapter for public group-control orders. */
public final class GroupControlRuntime {
    private static final int PATH_GRACE_TICKS = 40;
    private static final int PATH_STALL_TICKS = 200;
    private static final int FARM_RESCAN_TICKS = 20;
    private static final List<AdapterEntry> ADAPTERS = new ArrayList<>();
    private static final Map<TaskKey, DefaultTask> TASKS = new HashMap<>();
    private static boolean defaultRegistered;

    private GroupControlRuntime() {
    }

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
        if (server.overworld().getGameTime() % 20 == 0) restoreOrders(server);
        for (var task : List.copyOf(TASKS.values())) {
            try {
                task.tick(server);
            } catch (RuntimeException exception) {
                AcademyCraft.LOGGER.error(
                        "Group-control task {} failed for {}",
                        task.command.getClass().getSimpleName(),
                        task.subject.getUUID(),
                        exception
                );
                task.finish(GroupControlTaskEvent.Status.PATH_FAILED);
            }
        }
    }

    public static boolean ownsWorkScope(UUID controller, UUID subject, UUID scope) {
        return TASKS.values().stream().anyMatch(task -> task.settings != null && !task.closed
                && task.controllerId.equals(controller) && task.subject.getUUID().equals(subject)
                && task.scopeId.equals(scope));
    }

    private record RestoreGroup(UUID controller, Identifier source, BlockWorkRegion region,
                                WorkSettings settings, int priority) {}

    private static void restoreOrders(MinecraftServer server) {
        var groups = new LinkedHashMap<RestoreGroup, List<WorkOrderData.Entry>>();
        for (var entry : WorkOrderData.get(server).entries()) {
            var controller = UUID.fromString(entry.controller());
            var key = new TaskKey(controller, entry.source(), UUID.fromString(entry.subject()));
            if (TASKS.containsKey(key)) continue;
            var group = new RestoreGroup(controller, entry.source(), entry.region(), entry.settings(), entry.priority());
            groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(entry);
        }
        for (var groupEntry : groups.entrySet()) {
            var group = groupEntry.getKey();
            var controller = server.getPlayerList().getPlayer(group.controller());
            if (controller == null || !controller.isAlive()) continue;
            var level = java.util.stream.StreamSupport.stream(server.getAllLevels().spliterator(), false)
                    .filter(candidate -> candidate.dimension().identifier().equals(group.region().dimension())).findFirst().orElse(null);
            if (level == null) continue;
            var subjects = new ArrayList<LivingEntity>();
            for (var entry : groupEntry.getValue()) {
                var entity = level.getEntity(UUID.fromString(entry.subject()));
                if (entity instanceof Mob mob && mob.isAlive()
                        && MentalControlMemory.wasControlledBy(mob, group.controller())) subjects.add(mob);
            }
            if (subjects.isEmpty()) continue;
            dispatch(new GroupControlRequest(controller, group.source(), subjects,
                    new GroupControlCommand.Work(group.region(), group.settings()), group.priority()));
            for (var entry : groupEntry.getValue()) {
                var task = TASKS.get(new TaskKey(group.controller(), group.source(), UUID.fromString(entry.subject())));
                if (task != null) {
                    task.bufferedDrops.addAll(entry.cargo().stream().map(ItemStack::copy).toList());
                    task.paused = entry.paused();
                    task.saveOrder();
                }
            }
        }
    }

    public static void setWorkPaused(MinecraftServer server, UUID controller, Set<UUID> subjects, boolean paused) {
        var data = WorkOrderData.get(server);
        for (var entry : data.entries()) {
            if (entry.controller().equals(controller.toString()) && subjects.contains(UUID.fromString(entry.subject()))) {
                data.put(entry.withPaused(paused));
                var task = TASKS.get(new TaskKey(controller, entry.source(), UUID.fromString(entry.subject())));
                if (task != null) {
                    task.paused = paused;
                    task.closeMovement();
                    if (paused && task.sharedWork != null && task.sharedWork.mining != null) {
                        task.sharedWork.mining.release(task.subject.getUUID());
                        task.clearMiningTarget();
                    }
                }
            }
        }
    }

    public static void cancelWork(MinecraftServer server, UUID controller, Set<UUID> subjects) {
        var data = WorkOrderData.get(server);
        for (var entry : data.entries()) {
            if (!entry.controller().equals(controller.toString()) || !subjects.contains(UUID.fromString(entry.subject()))) continue;
            var task = TASKS.get(new TaskKey(controller, entry.source(), UUID.fromString(entry.subject())));
            if (task != null) task.close();
            else {
                var player = server.getPlayerList().getPlayer(controller);
                if (player != null) entry.cargo().forEach(stack -> DefaultTask.addToController(player, stack));
            }
            data.remove(entry.key());
        }
    }

    public static String workStatus(WorkOrderData.Entry entry) {
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
            var key = new TaskKey(request.controller().getUUID(), request.source(), subject.getUUID());
            var previous = TASKS.remove(key);
            if (previous != null) previous.close();
            var task = new DefaultTask(key, request, subject, subjectIndex, sharedWork);
            TASKS.put(key, task);
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
        private String workStatus = "working";
        private long nextWorkScan;
        private long nextAnimalAction;
        private long retryAfter;
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
                if (paused) { workStatus = "paused"; closeMovement(); return; }
            }
            if (isExclusiveWorkOrder() && aiControl != null
                    && aiControl.state() != ControlState.ACTIVE) {
                if (aiControl.state().isTerminal()) {
                    finish(GroupControlTaskEvent.Status.CANCELLED);
                } else {
                    // Atomic work ownership: pause the complete job while AI execution is
                    // preempted instead of continuing movement/action with only half the rights.
                    closeMovement();
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
            resolvedMovePoint = GroupControlNavigation.findNearestReachablePosition(subject, value)
                    .orElse(null);
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
            if (canBreak(controller, level, currentBlock)) {
                var drops = org.academy.api.server.ability.AbilityBlockDrops.getDrops(controller,
                        state, level, currentBlock, level.getBlockEntity(currentBlock), subject, tool);
                if (org.academy.api.server.ability.AbilityBlockDrops.run(
                        level, controller, () -> level.destroyBlock(currentBlock, false, subject))) {
                    drops.stream().filter(stack -> !stack.isEmpty())
                            .filter(stack -> !org.academy.internal.server.storage.SpatialStorageService.collect(controller, stack))
                            .map(ItemStack::copy).forEach(bufferedDrops::add);
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
            });
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
                    pos -> sharedWork.exposed(level, pos));
            if (currentBlock == null) {
                closeMovement();
                if (plan.progress().remaining() == 0) refreshMiningPlan(level, true);
                if (plan.progress().remaining() != 0) {
                    workStatus = plan.blockedReason();
                    if (!bufferedDrops.isEmpty() && settings.output().isPresent()) depositWorkCargo(controller, region);
                    return;
                }
                if (!depositWorkCargo(controller, region)) return;
                if (settings.repeat()) { workStatus = "waiting"; return; }
                finish(GroupControlTaskEvent.Status.COMPLETED);
                return;
            }
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
                // Keep a stable position instead of repeatedly walking into an excavation pit.
                remoteMining = true;
            }
            closeMovement();
            workStatus = inReach ? "working" : "remote";
            miningProgressTicks++;
            if (miningProgressTicks % 5 == 1) subject.swing(InteractionHand.MAIN_HAND);
            if (miningProgressTicks < miningTicks(state, tool, level, currentBlock)) return;
            if (!canBreak(controller, level, currentBlock)) { blockMiningTarget("permission", 100); return; }
            var drops = org.academy.api.server.ability.AbilityBlockDrops.getDrops(controller,
                    state, level, currentBlock, level.getBlockEntity(currentBlock), subject, tool);
            if (!org.academy.api.server.ability.AbilityBlockDrops.run(
                    level, controller, () -> level.destroyBlock(currentBlock, false, subject))) {
                blockMiningTarget("permission", 100);
                return;
            }
            for (var drop : drops) {
                if (!drop.isEmpty() && !org.academy.internal.server.storage.SpatialStorageService.collect(controller, drop)) {
                    bufferMiningDrop(drop);
                }
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
                var moved = Math.min(remaining.getCount(), Math.max(0, cargo.getMaxStackSize() - cargo.getCount()));
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
                if (now >= nextFarmScanTick) {
                    populateMatureCrops(region);
                    nextFarmScanTick = now + FARM_RESCAN_TICKS;
                }
                currentBlock = pollBlock();
            }
            if (currentBlock == null) {
                if (settings != null) {
                    if (!depositWorkCargo(controller, region)) return;
                    workStatus = "waiting";
                    if (!settings.repeat()) { finish(GroupControlTaskEvent.Status.COMPLETED); return; }
                } else depositFarmDrops(controller, region, false);
                // Farming is persistent. Waiting for the next mature crop must never turn
                // into an invalid path-to-the-solid-region-center order that ends the task.
                closeMovement();
                return;
            }
            if (settings != null && subject.level().getBlockState(currentBlock).isAir()) {
                if (!subject.level().getBlockState(currentBlock.below()).is(Blocks.FARMLAND)
                        && !(ControlledEquipment.tool(subject, Items.IRON_HOE).getItem() instanceof net.minecraft.world.item.HoeItem)
                        && !supplyTool(controller, region, stack -> stack.getItem() instanceof net.minecraft.world.item.HoeItem)) return;
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
            while (sharedWork != null ? sharedWork.hasPending() : !pendingBlocks.isEmpty()) {
                var candidate = pollBlock();
                if (candidate == null) return null;
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
            if (block.equals(workApproachBlock)) return workApproachPoint;
            closeMovement();
            workApproachBlock = block.immutable();
            workApproachPoint = GroupControlNavigation.findNearestWorkPosition(subject, block)
                    .orElse(null);
            return workApproachPoint;
        }

        private void clearWorkApproach() {
            workApproachBlock = null;
            workApproachPoint = null;
        }

        private ItemStack miningTool(net.minecraft.world.level.block.state.BlockState state) {
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
                net.minecraft.world.level.block.state.BlockState state,
                ItemStack tool
        ) {
            return !state.isAir() && (!state.requiresCorrectToolForDrops()
                    || tool.isCorrectToolForDrops(state));
        }

        private static int miningTicks(
                net.minecraft.world.level.block.state.BlockState state,
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
            var drops = org.academy.api.server.ability.AbilityBlockDrops.getDrops(controller,
                    state, level, pos, level.getBlockEntity(pos), subject, subject.getMainHandItem());
            if (!org.academy.api.server.ability.AbilityBlockDrops.run(
                    level, controller, () -> level.destroyBlock(pos, false, subject))) return;
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
                    .filter(stack -> !org.academy.internal.server.storage.SpatialStorageService.collect(controller, stack))
                    .map(ItemStack::copy).forEach(bufferedDrops::add);
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
            var containers = new ArrayList<Container>();
            var identities = Collections.newSetFromMap(new IdentityHashMap<Container, Boolean>());
            var positions = sharedWork == null
                    ? BlockPos.betweenClosed(region.minimum(), region.maximum())
                    : sharedWork.containerPositions(level);
            for (var pos : positions) {
                var container = HopperBlockEntity.getContainerAt(level, pos);
                if (container != null && identities.add(container)) containers.add(container);
            }
            var remaining = new ArrayList<ItemStack>();
            for (var buffered : bufferedDrops) {
                var remainder = buffered.copy();
                for (var container : containers) {
                    if (remainder.isEmpty()) break;
                    remainder = HopperBlockEntity.addItem(null, container, remainder, null);
                }
                if (!remainder.isEmpty() && fallbackToController) addToController(controller, remainder);
                else if (!remainder.isEmpty()) remaining.add(remainder);
            }
            bufferedDrops.clear();
            bufferedDrops.addAll(remaining);
        }

        private void deliverToController(ServerPlayer controller) {
            for (var stack : bufferedDrops) addToController(controller, stack);
            bufferedDrops.clear();
        }

        private static void addToController(ServerPlayer controller, ItemStack stack) {
            if (stack.isEmpty()) return;
            var remainder = stack.copy();
            var inventory = controller.getInventory();
            // Inventory.add deliberately discards overflow for creative players. Work cargo is real.
            for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++) {
                for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && !remainder.isEmpty(); slot++) {
                    var existing = inventory.getItem(slot);
                    if (pass == 0 && (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remainder))) continue;
                    if (pass == 1 && !existing.isEmpty()) continue;
                    var room = Math.min(inventory.getMaxStackSize(), remainder.getMaxStackSize()) - existing.getCount();
                    var moved = Math.min(remainder.getCount(), Math.max(0, room));
                    if (moved == 0) continue;
                    if (existing.isEmpty()) inventory.setItem(slot, remainder.split(moved));
                    else { existing.grow(moved); remainder.shrink(moved); }
                }
            }
            inventory.setChanged();
            if (!remainder.isEmpty() && controller.level() instanceof ServerLevel level) {
                Block.popResource(level, controller.blockPosition(), remainder);
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
            var region = workRegion();
            WorkOrderData.get(level.getServer()).put(new WorkOrderData.Entry(controllerId.toString(),
                    subject.getUUID().toString(), source, region.dimension(), region.minimum(), region.maximum(),
                    settings, priority, paused, bufferedDrops.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList()));
        }

        private void removeOrder() {
            if (settings != null && subject.level() instanceof ServerLevel level) {
                WorkOrderData.get(level.getServer()).remove(controllerId + "/" + source + "/" + subject.getUUID());
            }
        }

        private boolean matchesWorkBlock(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
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

        private boolean isFarmCandidate(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
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
            var bounds = new AABB(region.minimum().getX(), region.minimum().getY(), region.minimum().getZ(),
                    region.maximum().getX() + 1, region.maximum().getY() + 1, region.maximum().getZ() + 1);
            var item = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds,
                    candidate -> candidate.isAlive() && !candidate.hasPickUpDelay()
                            && settings.matches(candidate.getItem())).stream()
                    .min(Comparator.comparingDouble(subject::distanceToSqr)).orElse(null);
            if (item == null) {
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
            bufferedDrops.add(item.getItem().copy());
            item.discard();
            nextAnimalAction = level.getGameTime() + 5;
            saveOrder();
        }

        private boolean supplyTool(ServerPlayer controller, BlockWorkRegion region,
                                   java.util.function.Predicate<ItemStack> suitable) {
            workStatus = "tool";
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
            if (container == null) return false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!suitable.test(container.getItem(slot))) continue;
                var replacement = container.removeItem(slot, 1);
                if (replacement.isEmpty()) continue;
                var previous = subject.getMainHandItem();
                subject.setItemSlot(EquipmentSlot.MAINHAND, replacement);
                if (!previous.isEmpty()) {
                    var remainder = HopperBlockEntity.addItem(null, container, previous.copy(), null);
                    if (!remainder.isEmpty()) bufferedDrops.add(remainder);
                }
                container.setChanged();
                saveOrder();
                return true;
            }
            return false;
        }

        private void tickAnimalWork(ServerPlayer controller, BlockWorkRegion region) {
            var level = (ServerLevel) subject.level();
            if (level.getGameTime() < nextAnimalAction) return;
            if (!settings.harvest()) { workStatus = "waiting"; return; }
            java.util.function.Predicate<ItemStack> suitable = stack -> switch (settings.mode()) {
                case SHEARING -> stack.is(Items.SHEARS);
                case MILKING -> stack.is(Items.BUCKET);
                case FEEDING -> !stack.isEmpty() && level.getEntitiesOfClass(Animal.class,
                        new AABB(subject.blockPosition()).inflate(32), animal -> animal.isFood(stack)).size() > 0;
                default -> false;
            };
            if (!suitable.test(settings.mode() == WorkSettings.Mode.SHEARING
                    ? ControlledEquipment.tool(subject, Items.SHEARS) : subject.getMainHandItem())
                    && !supplyTool(controller, region, suitable)) return;
            var held = settings.mode() == WorkSettings.Mode.SHEARING
                    ? ControlledEquipment.tool(subject, Items.SHEARS) : subject.getMainHandItem();
            var bounds = new AABB(region.minimum().getX(), region.minimum().getY(), region.minimum().getZ(),
                    region.maximum().getX() + 1, region.maximum().getY() + 1, region.maximum().getZ() + 1);
            var target = level.getEntitiesOfClass(Animal.class, bounds, animal -> animal != subject
                    && animal.isAlive() && !animal.isBaby() && settings.matches(animal) && switch (settings.mode()) {
                case SHEARING -> animal instanceof IShearable shearable
                        && shearable.isShearable(controller, held, level, animal.blockPosition());
                case MILKING -> animal instanceof Cow;
                case FEEDING -> animal.canFallInLove() && animal.isFood(held);
                default -> false;
            }).stream().min(Comparator.comparingDouble(subject::distanceToSqr)).orElse(null);
            if (target == null) {
                workStatus = "waiting";
                closeMovement();
                depositWorkCargo(controller, region);
                nextAnimalAction = level.getGameTime() + 20;
                if (!settings.repeat()) finish(GroupControlTaskEvent.Status.COMPLETED);
                return;
            }
            if (!level.mayInteract(controller, target.blockPosition())) { workStatus = "path"; return; }
            if (subject.distanceToSqr(target) > 9) {
                var approach = workApproach(target.blockPosition());
                if (approach == null || !ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5)
                        || hasStalledPath(approach)) { closeMovement(); clearWorkApproach(); workStatus = "path"; }
                return;
            }
            closeMovement();
            subject.swing(InteractionHand.MAIN_HAND);
            switch (settings.mode()) {
                case SHEARING -> {
                    var shearable = (IShearable) target;
                    shearable.onSheared(controller, held, level, target.blockPosition()).stream()
                            .filter(stack -> !stack.isEmpty()).map(ItemStack::copy).forEach(bufferedDrops::add);
                    ControlledEquipment.damageRealTool(subject, held, 1);
                }
                case MILKING -> { held.shrink(1); bufferedDrops.add(new ItemStack(Items.MILK_BUCKET)); }
                case FEEDING -> { held.shrink(1); target.setInLove(controller); }
                default -> { }
            }
            nextAnimalAction = level.getGameTime() + 40;
            saveOrder();
            if (!settings.repeat()) finish(GroupControlTaskEvent.Status.COMPLETED);
        }

        private boolean isSeed(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                    && item.getBlock() instanceof CropBlock crop && settings.matches(crop.defaultBlockState());
        }

        private boolean collectSeeds(ServerPlayer controller, BlockWorkRegion region) {
            workStatus = "materials";
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
            if (container == null) return false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!isSeed(container.getItem(slot))) continue;
                bufferedDrops.add(container.removeItem(slot, 16));
                container.setChanged();
                saveOrder();
                return true;
            }
            return false;
        }

        private void plantCrop(ServerPlayer controller, ServerLevel level, BlockPos pos) {
            if (!level.mayInteract(controller, pos) || !DestroyBlocksSetting.canDestroyBlocks(controller)) return;
            if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
                var soil = level.getBlockState(pos.below());
                if (pos.getY() <= workRegion().minimum().getY()
                        || !(soil.is(Blocks.DIRT) || soil.is(Blocks.GRASS_BLOCK))
                        || !(ControlledEquipment.tool(subject, Items.IRON_HOE).getItem() instanceof net.minecraft.world.item.HoeItem)) return;
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
            if (settings.output().isEmpty()) {
                // No remote player-inventory teleport: carry a bounded amount until output is assigned.
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
            if (container == null) { workStatus = "output"; return false; }
            var remaining = new ArrayList<ItemStack>();
            var reservedSeeds = 0;
            for (var stack : bufferedDrops) {
                var deposit = stack.copy();
                if (settings.mode() == WorkSettings.Mode.FARMING && settings.replant() && isSeed(stack)
                        && reservedSeeds < 16) {
                    var count = Math.min(16 - reservedSeeds, deposit.getCount());
                    remaining.add(deposit.copyWithCount(count));
                    deposit.shrink(count);
                    reservedSeeds += count;
                }
                var rest = deposit.isEmpty() ? ItemStack.EMPTY : HopperBlockEntity.addItem(null, container, deposit, null);
                if (!rest.isEmpty()) remaining.add(rest);
            }
            bufferedDrops.clear();
            bufferedDrops.addAll(remaining);
            saveOrder();
            var onlySeeds = bufferedDrops.stream().allMatch(this::isSeed)
                    && bufferedDrops.stream().mapToInt(ItemStack::getCount).sum() <= 16;
            workStatus = bufferedDrops.isEmpty() || onlySeeds ? "working" : "output";
            return bufferedDrops.isEmpty() || onlySeeds;
        }

        private void finish(GroupControlTaskEvent.Status status) {
            if (closed) return;
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
            removeOrder();
            if (settings != null && subject.level() instanceof ServerLevel level) {
                var controller = level.getServer().getPlayerList().getPlayer(controllerId);
                if (controller != null) deliverToController(controller);
                else {
                    for (var stack : bufferedDrops) Block.popResource(level, subject.blockPosition(), stack);
                    bufferedDrops.clear();
                }
            }
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
            if (sharedWork != null && sharedWork.mining != null) sharedWork.mining.release(subject.getUUID());
            else if (sharedWork != null && currentBlock != null) sharedWork.defer(currentBlock);
            currentBlock = null;
            releaseExclusiveWorkOrder();
            TASKS.remove(key, this);
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
        }
    }

    private record TaskKey(UUID controllerId, Identifier source, UUID subjectId) {
    }

    private record AdapterEntry(Identifier id, int priority, GroupControlAdapter adapter) {
    }

    /** One region scan and one work-stealing queue shared by every worker in a dispatch. */
    private static final class SharedWorkPlan {
        private static final int FARM_SCAN_INTERVAL_TICKS = 20;
        private final BlockWorkRegion region;
        private final boolean farming;
        private MiningWorkPlan mining;
        private long surfaceTick = Long.MIN_VALUE;
        private final Map<BlockPos, Boolean> surfaces = new HashMap<>();
        private final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        private final Set<BlockPos> queued = new HashSet<>();
        private final Set<BlockPos> claimed = new HashSet<>();
        private List<BlockPos> containerPositions;
        private long nextFarmScanTick = Long.MIN_VALUE;

        private SharedWorkPlan(BlockWorkRegion region, boolean farming) {
            this.region = region;
            this.farming = farming;
            if (!farming) {
                for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                    queue(pos);
                }
            }
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
                for (var direction : net.minecraft.core.Direction.values()) {
                    var adjacent = candidate.relative(direction);
                    if (level.hasChunkAt(adjacent) && level.getBlockState(adjacent).isAir()) return true;
                }
                return false;
            });
        }

        private synchronized void queue(BlockPos position) {
            var immutable = position.immutable();
            if (queued.contains(immutable) || claimed.contains(immutable)) return;
            queued.add(immutable);
            pending.addLast(immutable);
        }

        private synchronized BlockPos claimNearest(LivingEntity subject) {
            if (pending.isEmpty()) return null;
            BlockPos nearest = null;
            var nearestDistance = Double.MAX_VALUE;
            for (var candidate : pending) {
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
            return !pending.isEmpty();
        }

        private synchronized int pendingCount() {
            return pending.size();
        }

        private synchronized void refreshFiltered(net.minecraft.world.level.Level level, long now, int interval,
                java.util.function.BiPredicate<BlockPos, net.minecraft.world.level.block.state.BlockState> filter) {
            if (now < nextFarmScanTick) return;
            nextFarmScanTick = now + interval;
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                if (level.hasChunkAt(pos) && filter.test(pos, level.getBlockState(pos))) queue(pos);
            }
        }

        private synchronized void refreshCrops(net.minecraft.world.level.Level level, long now) {
            if (!farming || now < nextFarmScanTick) return;
            nextFarmScanTick = now + FARM_SCAN_INTERVAL_TICKS;
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) queue(pos);
            }
        }

        private synchronized List<BlockPos> containerPositions(ServerLevel level) {
            if (containerPositions == null) {
                var found = new ArrayList<BlockPos>();
                for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                    if (HopperBlockEntity.getContainerAt(level, pos) != null) {
                        found.add(pos.immutable());
                    }
                }
                containerPositions = List.copyOf(found);
            }
            return containerPositions;
        }
    }
}
