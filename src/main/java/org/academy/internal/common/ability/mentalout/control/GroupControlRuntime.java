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
    private static final int CONTROL_LEASE_TICKS = 200;
    private static final int PATH_GRACE_TICKS = 40;
    private static final int PATH_STALL_TICKS = 100;
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
                var handle = adapter.start(request, subject, index);
                if (handle == null || handle.isClosed()) {
                    failed++;
                } else {
                    handles.add(handle);
                    applied++;
                }
            } catch (RuntimeException exception) {
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
                task.finish(GroupControlTaskEvent.Status.PATH_FAILED);
            }
        }
    }

    private enum DefaultAdapter implements GroupControlAdapter {
        INSTANCE;

        @Override
        public boolean supports(LivingEntity subject, GroupControlCommand command) {
            return subject != null && subject.isAlive() && !subject.isRemoved()
                    && MentalControlApi.supports(subject, ControlCapability.PATH_CONTROL);
        }

        @Override
        public GroupControlHandle start(
                GroupControlRequest request,
                LivingEntity subject,
                int subjectIndex
        ) {
            var key = new TaskKey(request.controller().getUUID(), request.source(), subject.getUUID());
            var previous = TASKS.remove(key);
            if (previous != null) previous.close();
            var task = new DefaultTask(key, request, subject, subjectIndex);
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
        private final ArrayDeque<BlockPos> pendingBlocks = new ArrayDeque<>();
        private final ArrayList<ItemStack> bufferedDrops = new ArrayList<>();
        private ControlHandle movement;
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
                int subjectIndex
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
            if (command instanceof GroupControlCommand.GatherResources(var region)) partitionAll(region);
        }

        private void partitionAll(BlockWorkRegion region) {
            var index = 0;
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                if (index++ % subjectCount == subjectIndex) pendingBlocks.add(pos.immutable());
            }
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
            ensureMovement(controller,
                    resolvedMoveDestination == null ? destination : resolvedMoveDestination,
                    1.25);
            checkPathProgress(point);
        }

        private Vec3 resolveMove(ControlDestination destination, ServerPlayer controller) {
            if (!(destination instanceof ControlDestination.Position(var dimension, var value))) {
                return resolve(destination, controller);
            }
            if (resolvedMovePoint != null) return resolvedMovePoint;
            if (!dimension.equals(subject.level().dimension().identifier())) return null;
            resolvedMovePoint = GroupControlNavigation.findNearestOccupablePosition(subject, value)
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
                    advanceBlock();
                    return;
                }
                ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5);
                checkPathProgress(approach);
                return;
            }
            closeMovement();
            if (!(subject.level() instanceof ServerLevel level)) return;
            var state = level.getBlockState(currentBlock);
            var tool = miningTool();
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
                if (pendingBlocks.isEmpty() && now >= nextFarmScanTick) {
                    populateMatureCrops(region);
                    nextFarmScanTick = now + FARM_RESCAN_TICKS;
                }
                currentBlock = pendingBlocks.pollFirst();
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
                    currentBlock = null;
                    clearWorkApproach();
                    return;
                }
                ensureMovement(controller,
                        new ControlDestination.Position(region.dimension(), approach), 1.5);
                checkPathProgress(approach);
                return;
            }
            closeMovement();
            if (subject.level() instanceof ServerLevel level) {
                harvestCrop(controller, level, currentBlock);
                depositFarmDrops(controller, region, false);
            }
            currentBlock = null;
            clearWorkApproach();
        }

        private void populateMatureCrops(BlockWorkRegion region) {
            var index = 0;
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                if (index++ % subjectCount != subjectIndex) continue;
                var state = subject.level().getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                    pendingBlocks.add(pos.immutable());
                }
            }
        }

        private BlockPos nextMiningBlock() {
            while (!pendingBlocks.isEmpty()) {
                var candidate = pendingBlocks.removeFirst();
                var state = subject.level().getBlockState(candidate);
                if (!state.isAir() && isHarvestable(state, miningTool())) return candidate;
            }
            return null;
        }

        private void advanceBlock() {
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

        private ItemStack miningTool() {
            var held = subject.getMainHandItem();
            return held.isEmpty() ? new ItemStack(Items.IRON_PICKAXE) : held;
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

        private void ensureMovement(
                ServerPlayer controller,
                ControlDestination destination,
                double arrivalRadius
        ) {
            if (movement != null && !movement.isClosed()) return;
            movement = MentalControlApi.apply(new ControlRequest(
                    controller,
                    subject,
                    source,
                    priority,
                    subject.level().getGameTime() + CONTROL_LEASE_TICKS,
                    List.of(new ControlDirective.MoveTo(destination, arrivalRadius))
            ));
            movementStartedTick = subject.level().getGameTime();
            lastMovementDistance = Double.MAX_VALUE;
            stalledTicks = 0;
        }

        private void checkPathProgress(Vec3 target) {
            var distance = subject.distanceToSqr(target);
            var now = subject.level().getGameTime();
            if (lastMovementDistance - distance > 0.25) {
                lastMovementDistance = distance;
                stalledTicks = 0;
                return;
            }
            if (now - movementStartedTick < PATH_GRACE_TICKS) return;
            stalledTicks++;
            var navigationStopped = subject instanceof Mob mob && mob.getNavigation().isDone();
            if (stalledTicks >= PATH_STALL_TICKS || navigationStopped && stalledTicks >= 20) {
                finish(GroupControlTaskEvent.Status.PATH_FAILED);
            }
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
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
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
}
