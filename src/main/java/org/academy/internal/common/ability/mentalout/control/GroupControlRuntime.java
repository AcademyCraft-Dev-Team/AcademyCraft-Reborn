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
        List.copyOf(TASKS.values()).forEach(DefaultTask::close);
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

    private enum DefaultAdapter implements GroupControlAdapter {
        INSTANCE;

        @Override
        public boolean supports(LivingEntity subject, GroupControlCommand command) {
            return subject != null && subject.isAlive() && !subject.isRemoved()
                    && MentalControlApi.supports(subject, ControlCapability.PATH_CONTROL)
                    && (!(command instanceof GroupControlCommand.GatherResources
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
            task.retainExclusiveWorkOrder(request.controller());
            TASKS.put(key, task);
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
            command = request.command();
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
            if (controller == null || !controller.isAlive() || controller.hasDisconnected()
                    || !subject.isAlive() || subject.isRemoved()
                    || controller.level() != subject.level()) {
                finish(GroupControlTaskEvent.Status.CANCELLED);
                return;
            }
            if (isExclusiveWorkOrder() && aiControl != null
                    && aiControl.state() != ControlState.ACTIVE) {
                if (aiControl.state().isTerminal()) {
                    finish(GroupControlTaskEvent.Status.CANCELLED);
                } else {
                    // Atomic work ownership: pause the complete job while AI execution is
                    // preempted instead of continuing movement/action with only half the rights.
                    closeMovement();
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
            if (currentBlock == null) currentBlock = nextMiningBlock();
            if (currentBlock == null) {
                deliverToController(controller);
                finish(GroupControlTaskEvent.Status.COMPLETED);
                return;
            }
            var target = Vec3.atCenterOf(currentBlock);
            if (subject.distanceToSqr(target) > 12.25) {
                var approach = workApproach(currentBlock);
                if (approach == null) {
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
            if (!isHarvestable(state, tool)) {
                advanceBlock();
                return;
            }
            miningProgressTicks++;
            if (miningProgressTicks % 5 == 1) subject.swing(InteractionHand.MAIN_HAND);
            if (miningProgressTicks < miningTicks(state, tool, level, currentBlock)) return;
            if (canBreak(controller, level, currentBlock)) {
                var drops = Block.getDrops(
                        state, level, currentBlock, level.getBlockEntity(currentBlock), subject, tool);
                if (level.destroyBlock(currentBlock, false, subject)) {
                    drops.stream().filter(stack -> !stack.isEmpty())
                            .map(ItemStack::copy).forEach(bufferedDrops::add);
                }
            }
            advanceBlock();
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
                depositFarmDrops(controller, region, false);
                // Farming is persistent. Waiting for the next mature crop must never turn
                // into an invalid path-to-the-solid-region-center order that ends the task.
                closeMovement();
                return;
            }
            var target = Vec3.atCenterOf(currentBlock);
            if (subject.distanceToSqr(target) > 12.25) {
                var approach = workApproach(currentBlock);
                if (approach == null) {
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
                harvestCrop(controller, level, currentBlock);
                depositFarmDrops(controller, region, false);
            }
            advanceBlock();
        }

        private void populateMatureCrops(BlockWorkRegion region) {
            if (sharedWork != null) {
                sharedWork.refreshCrops(subject.level(), subject.level().getGameTime());
                return;
            }
            var index = 0;
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                if (index++ % subjectCount != subjectIndex) continue;
                var state = subject.level().getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                    enqueueBlock(pos);
                }
            }
        }

        private BlockPos nextMiningBlock() {
            while (sharedWork != null ? sharedWork.hasPending() : !pendingBlocks.isEmpty()) {
                var candidate = pollBlock();
                if (candidate == null) return null;
                var state = subject.level().getBlockState(candidate);
                if (!state.isAir() && isHarvestable(state, miningTool(state))) return candidate;
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
                    || !canBreak(controller, level, pos)) return;
            subject.swing(InteractionHand.MAIN_HAND);
            var drops = Block.getDrops(
                    state, level, pos, level.getBlockEntity(pos), subject, subject.getMainHandItem());
            if (!level.destroyBlock(pos, false, subject)) return;
            level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
            drops.stream().filter(stack -> !stack.isEmpty())
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
            controller.getInventory().add(remainder);
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

        private void finish(GroupControlTaskEvent.Status status) {
            if (closed) return;
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
            if (sharedWork != null && currentBlock != null) sharedWork.defer(currentBlock);
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
            };
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
