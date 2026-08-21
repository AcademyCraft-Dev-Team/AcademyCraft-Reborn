package org.academy.internal.common.ability.teleport.program;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Authoritative Minecraft-server adapter for Teleport programs. */
public final class ServerTeleportProgramRuntime implements TeleportProgramRuntime {
    public static final double MAX_QUERY_RANGE = 128.0;
    public static final int MAX_QUERY_RESULTS = 128;
    private static final double BLOCK_CELL_CONTACT_EPSILON = 1.0e-7;

    private final ServerPlayer player;
    private final float costMultiplier;
    private final ServerProgramTargetResolver targets;

    public ServerTeleportProgramRuntime(ServerPlayer player) {
        this(player, 1.0f);
    }

    public ServerTeleportProgramRuntime(ServerPlayer player, float costMultiplier) {
        this.player = Objects.requireNonNull(player, "player");
        this.costMultiplier = requireCostMultiplier(costMultiplier);
        targets = new ServerProgramTargetResolver(player, MAX_QUERY_RANGE, MAX_QUERY_RESULTS);
    }

    @Override
    public Object caster() {
        return targets.caster();
    }

    @Override
    public Optional<Object> lookTarget() {
        return targets.lookTarget();
    }

    @Override
    public Optional<ProgramBlockPosition> lookBlockTarget() {
        return targets.lookBlockTarget();
    }

    @Override
    public ProgramActionTransaction.ProgramAction teleportSelf(
            ProgramWorldPosition destination,
            float power
    ) {
        return teleportAction(
                player,
                destination,
                power,
                Skills.SELF_TELEPORT.get(),
                selfRange(power),
                selfCost(power),
                false,
                false,
                false,
                true,
                null
        );
    }

    @Override
    public ProgramActionTransaction.ProgramAction teleportEntity(
            Object targetReference,
            Object destinationReference,
            @Nullable ProgramDirection direction,
            float power,
            TeleportProgramNodeCatalog.TargetType targetType
    ) {
        if (targetType == TeleportProgramNodeCatalog.TargetType.BLOCK) {
            return teleportBlockAction(targetReference, destinationReference, direction, power);
        }
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;
            private ProgramActionTransaction.ProgramAction action;

            @Override
            public void validate() throws Exception {
                target = requireEntityTarget(targetReference);
                requireEntityInRange(target, entityTargetRange(power));
                if (target != player) requireMovementAllowed(target);
                action = teleportAction(
                        target,
                        destinationPosition(destinationReference),
                        power,
                        Skills.QUICK_LOCATION_TELEPORT.get(),
                        entityMoveRange(power),
                        entityBaseCost(power),
                        true,
                        target != player,
                        true,
                        false,
                        direction
                );
                action.validate();
            }

            @Override
            public ProgramActionTransaction.Undo apply() throws Exception {
                if (action == null) {
                    throw new IllegalStateException("Entity Teleport was not validated");
                }
                return action.apply();
            }
        };
    }

    @Override
    public boolean isSpaceSafe(Object entityReference, ProgramWorldPosition position) {
        var entity = requireEntityTarget(entityReference);
        var destination = targets.requireLocalPosition(position);
        var block = BlockPos.containing(destination);
        if (!targets.level().hasChunkAt(block)
                || block.getY() < targets.level().getMinY()
                || block.getY() >= targets.level().getMaxY()) return false;
        var moved = entity.getBoundingBox().move(destination.subtract(entity.position()));
        return targets.level().getWorldBorder().isWithinBounds(moved)
                && targets.level().noCollision(entity, moved);
    }

    private ProgramActionTransaction.ProgramAction teleportBlockAction(
            Object sourceReference,
            Object destinationReference,
            @Nullable ProgramDirection direction,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private BlockPos source;
            private BlockPos destination;
            private BlockState state;
            private CompoundTag blockEntityTag;
            private BlockState replacedState;
            private CompoundTag replacedBlockEntityTag;

            @Override
            public void validate() {
                requireCasterReady(Skills.QUICK_LOCATION_TELEPORT.get());
                source = requireLocalBlock(sourceReference);
                destination = destinationBlock(destinationReference);
                if (source.equals(destination)) {
                    throw new IllegalArgumentException("Teleport block source equals destination");
                }
                if (Math.sqrt(source.distSqr(destination)) > entityMoveRange(power)) {
                    throw new IllegalArgumentException("Block teleport exceeds its power limit");
                }
                requireEditableBlock(source);
                requireEditableBlock(destination);
                var level = targets.level();
                state = level.getBlockState(source);
                if (state.isAir()
                        || state.getDestroySpeed(level, source) < 0.0f
                        || state.getBlock() instanceof GameMasterBlock
                        && !player.canUseGameMasterBlocks()) {
                    throw new IllegalArgumentException("Block teleport target is invalid");
                }
                replacedState = level.getBlockState(destination);
                if (!replacedState.isAir()
                        && (replacedState.getDestroySpeed(level, destination) < 0.0f
                        || replacedState.getBlock() instanceof GameMasterBlock
                        && !player.canUseGameMasterBlocks())) {
                    throw new IllegalArgumentException(
                            "Block teleport destination cannot be replaced");
                }
                var rotated = orient(state, direction);
                if (!rotated.canSurvive(level, destination)) {
                    throw new IllegalArgumentException("Teleported block cannot survive at destination");
                }
                var blockEntity = level.getBlockEntity(source);
                blockEntityTag = blockEntity == null ? null
                        : blockEntity.saveWithFullMetadata(level.registryAccess());
                var replacedBlockEntity = level.getBlockEntity(destination);
                replacedBlockEntityTag = replacedBlockEntity == null ? null
                        : replacedBlockEntity.saveWithFullMetadata(level.registryAccess());
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                validate();
                var movedState = orient(state, direction);
                var level = targets.level();
                charge(Skills.QUICK_LOCATION_TELEPORT.get(), entityCost(
                        power, Math.sqrt(source.distSqr(destination))));
                if (!replacedState.isAir() && !level.setBlock(
                        destination,
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS
                )) {
                    throw new IllegalStateException("Unable to clear teleport destination");
                }
                level.setBlock(source, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                if (!level.setBlock(destination, movedState,
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS)) {
                    level.setBlock(source, state,
                            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                    restoreBlockEntity(source, state, blockEntityTag);
                    level.setBlock(destination, replacedState,
                            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                    restoreBlockEntity(destination, replacedState, replacedBlockEntityTag);
                    throw new IllegalStateException("Unable to place teleported block");
                }
                restoreBlockEntity(destination, movedState, blockEntityTag);
                return () -> {
                    if (level.getBlockState(destination).equals(movedState)) {
                        level.setBlock(destination, replacedState,
                                Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                        restoreBlockEntity(destination, replacedState, replacedBlockEntityTag);
                    }
                    if (level.getBlockState(source).isAir()) {
                        level.setBlock(source, state,
                                Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                        restoreBlockEntity(source, state, blockEntityTag);
                    }
                };
            }
        };
    }

    @Override
    public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
        return targets.positionOf(entityReference);
    }

    @Override
    public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
        return targets.lookDirectionOf(entityReference);
    }

    @Override
    public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
        return targets.entitiesAround(center, radius);
    }

    @Override
    public Optional<ProgramBlockPosition> raycastBlock(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return targets.raycastBlock(origin, direction, maximumDistance);
    }

    @Override
    public Optional<Object> raycastEntity(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return targets.raycastEntity(origin, direction, maximumDistance);
    }

    private ProgramActionTransaction.ProgramAction teleportAction(
            Entity target,
            ProgramWorldPosition destination,
            float power,
            Skill skill,
            double maximumDisplacement,
            float cost,
            boolean distanceScaledCost,
            boolean externallyMoved,
            boolean allowCollision,
            boolean unrestricted,
            @Nullable ProgramDirection direction
    ) {
        Objects.requireNonNull(destination, "destination");
        ProgramPowerScale.require(power);
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 requested;
            private Vec3 resolvedDestination;
            private ServerLevel destinationLevel;

            @Override
            public void validate() {
                requireCasterReady(skill);
                requireTeleportable(target, externallyMoved);
                destinationLevel = unrestricted
                        ? requireDestinationLevel(destination)
                        : targets.level();
                requested = unrestricted
                        ? new Vec3(destination.x(), destination.y(), destination.z())
                        : targets.requireLocalPosition(destination);
                if (unrestricted) {
                    var block = BlockPos.containing(requested);
                    destinationLevel.getChunk(block.getX() >> 4, block.getZ() >> 4);
                }
                resolvedDestination = requireDestination(
                        target,
                        destinationLevel,
                        requested,
                        maximumDisplacement,
                        allowCollision,
                        !unrestricted
                );
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(skill);
                requireTeleportable(target, externallyMoved);
                resolvedDestination = requireDestination(
                        target,
                        destinationLevel,
                        requested,
                        maximumDisplacement,
                        allowCollision,
                        !unrestricted
                );
                var origin = target.position();
                var originLevel = (ServerLevel) target.level();
                var previousMotion = target.getDeltaMovement();
                var previousYaw = target.getYRot();
                var previousPitch = target.getXRot();
                var actualCost = distanceScaledCost
                        ? distanceAdjustedCost(cost, origin.distanceTo(resolvedDestination))
                        : cost;
                charge(skill, actualCost);
                if (!teleport(target, destinationLevel, resolvedDestination)) {
                    throw new IllegalStateException("Target rejected program teleport");
                }
                setMotion(target, Vec3.ZERO);
                applyRotation(target, direction);
                target.resetFallDistance();
                return () -> restore(
                        target, originLevel, origin, previousMotion, previousYaw, previousPitch);
            }
        };
    }

    private Entity requireEntityTarget(Object value) {
        if (!(value instanceof Entity entity) || !targets.sameUsableLevel(entity)) {
            throw new IllegalArgumentException("Entity target is invalid");
        }
        return entity;
    }

    private void requireCasterReady(Skill skill) {
        if (!player.isAlive()
                || player.hasDisconnected()
                || player.isSpectator()
                || !skill.isEnabled(player)) {
            throw new IllegalStateException("Required Teleport skill is unavailable");
        }
    }

    private void requireTeleportable(Entity target, boolean externallyMoved) {
        if (!targets.sameUsableLevel(target)
                || target.isSpectator()
                || target.isPassenger()
                || target.isVehicle()) {
            throw new IllegalArgumentException("Entity cannot be teleported by this program");
        }
        if (externallyMoved && !EntityMotionGuard.canApplyMotionFrom(player, target)) {
            throw new IllegalArgumentException("Entity rejected forced teleportation");
        }
    }

    private void requireMovementAllowed(Entity target) {
        if (target instanceof LivingEntity living
                && CtaFriendlyFireWhitelist.shouldProtect(player, living)) {
            throw new IllegalArgumentException("Friendly-fire policy protects the target");
        }
    }

    private Vec3 requireSafeDestination(
            Entity target,
            ServerLevel level,
            Vec3 requested,
            double maximumDisplacement,
            boolean limitDistance
    ) {
        var safe = TeleportSafety.findSafe(target, level, requested);
        if (safe == null) {
            throw new IllegalArgumentException("Teleport destination is not safe and loaded");
        }
        if (limitDistance && safe.distanceToSqr(target.position())
                > maximumDisplacement * maximumDisplacement) {
            throw new IllegalArgumentException("Teleport exceeds its power limit");
        }
        return safe;
    }

    @Override
    public ProgramActionTransaction.ProgramAction teleportBlockOrItem(
            ProgramBlockPosition position,
            int hotbarSlot,
            TeleportProgramNodeCatalog.BlockItemTeleportMode mode
    ) {
        Objects.requireNonNull(mode, "mode");
        return mode == TeleportProgramNodeCatalog.BlockItemTeleportMode.PLACE
                ? placeBlockOrItem(position, hotbarSlot)
                : collectBlockAndItems(position);
    }

    private ProgramActionTransaction.ProgramAction placeBlockOrItem(
            ProgramBlockPosition position,
            int hotbarSlot
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private BlockPos target;
            private int inventorySlot;
            private BlockState replacedState;
            private CompoundTag replacedBlockEntityTag;
            private ItemStack transmitted;

            @Override
            public void validate() {
                requireCasterReady(Skills.SELF_TELEPORT.get());
                target = requireLocalBlock(position);
                requireEditableBlock(target);
                inventorySlot = resolveHotbarSlot(hotbarSlot);
                transmitted = player.getInventory().getItem(inventorySlot).copyWithCount(1);
                if (transmitted.isEmpty()) {
                    throw new IllegalArgumentException("Teleport item slot is empty");
                }
                var level = targets.level();
                replacedState = level.getBlockState(target);
                requireBreakable(target, replacedState);
                var replacedBlockEntity = level.getBlockEntity(target);
                replacedBlockEntityTag = replacedBlockEntity == null ? null
                        : replacedBlockEntity.saveWithFullMetadata(level.registryAccess());
                if (transmitted.getItem() instanceof BlockItem blockItem
                        && !blockItem.getBlock().defaultBlockState().canSurvive(level, target)) {
                    throw new IllegalArgumentException("Teleported block cannot survive at target");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                validate();
                var level = targets.level();
                var inventory = snapshotInventory();
                var spawned = new ArrayList<ItemEntity>();
                var damageTargets = entitiesTouchingBlockCell(target);
                try {
                    charge(Skills.SELF_TELEPORT.get(), 10.0f);
                    var drops = blockDrops(target, replacedState, transmitted);
                    if (!replacedState.isAir() && !level.setBlock(
                            target, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS)) {
                        throw new IllegalStateException("Unable to break target block");
                    }
                    for (var drop : drops) spawned.add(spawnItem(target, drop));
                    var source = player.getInventory().getItem(inventorySlot);
                    if (!player.isCreative()) source.shrink(1);
                    if (transmitted.getItem() instanceof BlockItem blockItem) {
                        if (!level.setBlock(target, blockItem.getBlock().defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS)) {
                            throw new IllegalStateException("Unable to place teleported block");
                        }
                    } else {
                        spawned.add(spawnItem(target, transmitted));
                    }
                    damageAtTarget(target, transmitted, damageTargets);
                    player.getInventory().setChanged();
                    return () -> {
                        spawned.forEach(Entity::discard);
                        level.setBlock(target, replacedState,
                                Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                        restoreBlockEntity(target, replacedState, replacedBlockEntityTag);
                        restoreInventory(inventory);
                    };
                } catch (RuntimeException exception) {
                    spawned.forEach(Entity::discard);
                    level.setBlock(target, replacedState,
                            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                    restoreBlockEntity(target, replacedState, replacedBlockEntityTag);
                    restoreInventory(inventory);
                    throw exception;
                }
            }
        };
    }

    private ProgramActionTransaction.ProgramAction collectBlockAndItems(
            ProgramBlockPosition position
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private BlockPos target;
            private BlockState state;
            private CompoundTag blockEntityTag;

            @Override
            public void validate() {
                requireCasterReady(Skills.SELF_TELEPORT.get());
                target = requireLocalBlock(position);
                requireEditableBlock(target);
                var level = targets.level();
                state = level.getBlockState(target);
                requireBreakable(target, state);
                var blockEntity = level.getBlockEntity(target);
                blockEntityTag = blockEntity == null ? null
                        : blockEntity.saveWithFullMetadata(level.registryAccess());
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                validate();
                var level = targets.level();
                var inventory = snapshotInventory();
                var nearby = level.getEntitiesOfClass(
                        ItemEntity.class, new AABB(target), Entity::isAlive);
                var removed = nearby.stream()
                        .map(item -> new RemovedItem(item.getItem().copy(), item.position()))
                        .toList();
                try {
                    charge(Skills.SELF_TELEPORT.get(), 10.0f);
                    var collected = new ArrayList<ItemStack>(blockDrops(
                            target, state, player.getMainHandItem()));
                    removed.forEach(item -> collected.add(item.stack.copy()));
                    if (!state.isAir() && !level.setBlock(
                            target, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS)) {
                        throw new IllegalStateException("Unable to break collected block");
                    }
                    for (var stack : collected) addToInventory(stack);
                    nearby.forEach(Entity::discard);
                    player.getInventory().setChanged();
                    return () -> {
                        restoreInventory(inventory);
                        level.setBlock(target, state,
                                Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                        restoreBlockEntity(target, state, blockEntityTag);
                        removed.forEach(this::respawn);
                    };
                } catch (RuntimeException exception) {
                    restoreInventory(inventory);
                    level.setBlock(target, state,
                            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                    restoreBlockEntity(target, state, blockEntityTag);
                    for (var index = 0; index < nearby.size(); index++) {
                        if (!nearby.get(index).isAlive()) respawn(removed.get(index));
                    }
                    throw exception;
                }
            }

            private void respawn(RemovedItem item) {
                var restored = new ItemEntity(
                        targets.level(), item.position.x, item.position.y, item.position.z,
                        item.stack.copy());
                restored.setDeltaMovement(Vec3.ZERO);
                targets.level().addFreshEntity(restored);
            }
        };
    }

    private Vec3 requireDestination(
            Entity target,
            ServerLevel level,
            Vec3 requested,
            double maximumDisplacement,
            boolean allowCollision,
            boolean limitDistance
    ) {
        if (!allowCollision) {
            return requireSafeDestination(
                    target, level, requested, maximumDisplacement, limitDistance);
        }
        var block = BlockPos.containing(requested);
        if (!level.hasChunkAt(block)
                || block.getY() < level.getMinY()
                || block.getY() >= level.getMaxY()) {
            throw new IllegalArgumentException("Teleport destination is not loaded");
        }
        if (limitDistance && requested.distanceToSqr(target.position())
                > maximumDisplacement * maximumDisplacement) {
            throw new IllegalArgumentException("Teleport exceeds its power limit");
        }
        var moved = target.getBoundingBox().move(requested.subtract(target.position()));
        if (!level.getWorldBorder().isWithinBounds(moved)) {
            throw new IllegalArgumentException("Teleport destination is outside the world border");
        }
        return requested;
    }

    private void requireEntityInRange(Entity entity, double range) {
        if (entity.distanceToSqr(player) > range * range) {
            throw new IllegalArgumentException("Entity target is outside program range");
        }
    }

    private void charge(Skill skill, float cost) {
        if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(
                player, cost * costMultiplier, skill)) {
            throw new IllegalStateException("Insufficient CP for Teleport program action");
        }
    }

    private boolean teleport(Entity entity, ServerLevel level, Vec3 destination) {
        return Boolean.TRUE.equals(EntityMotionGuard.callWithMotionSource(
                player,
                () -> TeleportSync.teleportInstantly(entity, level, destination)
        ));
    }

    private static float requireCostMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f) {
            throw new IllegalArgumentException("Program cost multiplier must be positive");
        }
        return multiplier;
    }

    private void setMotion(Entity entity, Vec3 motion) {
        EntityMotionGuard.runWithMotionSource(player, () -> entity.setDeltaMovement(motion));
        entity.hurtMarked = true;
        syncMotion(entity);
    }

    private static void restore(
            Entity entity,
            ServerLevel level,
            Vec3 position,
            Vec3 motion,
            float yaw,
            float pitch
    ) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel)
                || entity.isRemoved()) {
            return;
        }
        EntityMotionGuard.runInternalCorrection(entity, () -> {
            if (!TeleportSync.teleportInstantly(entity, level, position)) {
                throw new IllegalStateException("Unable to restore teleported entity");
            }
            entity.setDeltaMovement(motion);
            entity.setYRot(yaw);
            entity.setXRot(pitch);
        });
        entity.hurtMarked = true;
        syncMotion(entity);
    }

    private static void syncMotion(Entity entity) {
        if (entity instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private ProgramWorldPosition destinationPosition(Object value) {
        if (value instanceof ProgramWorldPosition world) return world;
        if (value instanceof ProgramBlockPosition block) {
            return new ProgramWorldPosition(
                    block.dimension(), block.x() + 0.5, block.y(), block.z() + 0.5);
        }
        throw new IllegalArgumentException("Teleport destination must be a block or world position");
    }

    private ServerLevel requireDestinationLevel(ProgramWorldPosition destination) {
        var key = ResourceKey.create(Registries.DIMENSION, destination.dimension());
        var level = player.level().getServer().getLevel(key);
        if (level == null) {
            throw new IllegalArgumentException("Teleport destination dimension is unavailable");
        }
        return level;
    }

    private BlockPos destinationBlock(Object value) {
        if (value instanceof ProgramBlockPosition block) return requireLocalBlock(block);
        if (value instanceof ProgramWorldPosition world) {
            return requireLocalBlock(ProgramBlockPosition.containing(world));
        }
        throw new IllegalArgumentException("Block teleport destination is invalid");
    }

    private BlockPos requireLocalBlock(Object value) {
        if (!(value instanceof ProgramBlockPosition block)
                || !block.dimension().equals(targets.level().dimension().identifier())) {
            throw new IllegalArgumentException("Teleport block is in another dimension");
        }
        var position = new BlockPos(block.x(), block.y(), block.z());
        if (!targets.level().hasChunkAt(position)
                || position.getY() < targets.level().getMinY()
                || position.getY() >= targets.level().getMaxY()
                || Vec3.atCenterOf(position).distanceToSqr(player.position())
                > MAX_QUERY_RANGE * MAX_QUERY_RANGE) {
            throw new IllegalArgumentException("Teleport block is outside program range");
        }
        return position;
    }

    private void requireEditableBlock(BlockPos position) {
        if (!targets.level().mayInteract(player, position)
                || player.blockActionRestricted(
                        targets.level(), position, player.gameMode.getGameModeForPlayer())) {
            throw new IllegalArgumentException("Teleport block is protected");
        }
    }

    private int resolveHotbarSlot(int configuredSlot) {
        if (configuredSlot == 0) return player.getInventory().getSelectedSlot();
        if (configuredSlot < 1 || configuredSlot > 9) {
            throw new IllegalArgumentException("Teleport hotbar slot must be between 1 and 9");
        }
        return configuredSlot - 1;
    }

    private void requireBreakable(BlockPos position, BlockState state) {
        if (state.isAir()) return;
        if (state.getDestroySpeed(targets.level(), position) < 0.0f
                || state.getBlock() instanceof GameMasterBlock
                && !player.canUseGameMasterBlocks()) {
            throw new IllegalArgumentException("Teleport target block cannot be broken");
        }
    }

    private List<ItemStack> blockDrops(
            BlockPos position,
            BlockState state,
            ItemStack tool
    ) {
        if (state.isAir()) return List.of();
        return Block.getDrops(
                state,
                targets.level(),
                position,
                targets.level().getBlockEntity(position),
                player,
                tool
        );
    }

    private ItemEntity spawnItem(BlockPos position, ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot teleport an empty item stack");
        }
        var center = Vec3.atCenterOf(position);
        var item = new ItemEntity(
                targets.level(), center.x, center.y, center.z, stack.copy());
        item.setDeltaMovement(Vec3.ZERO);
        item.setDefaultPickUpDelay();
        item.setThrower(player);
        if (!targets.level().addFreshEntity(item)) {
            throw new IllegalStateException("Unable to spawn teleported item");
        }
        return item;
    }

    private List<ItemStack> snapshotInventory() {
        var inventory = player.getInventory();
        var snapshot = new ArrayList<ItemStack>(inventory.getContainerSize());
        for (var slot = 0; slot < inventory.getContainerSize(); slot++) {
            snapshot.add(inventory.getItem(slot).copy());
        }
        return List.copyOf(snapshot);
    }

    private void restoreInventory(List<ItemStack> snapshot) {
        var inventory = player.getInventory();
        for (var slot = 0; slot < Math.min(snapshot.size(), inventory.getContainerSize()); slot++) {
            inventory.setItem(slot, snapshot.get(slot).copy());
        }
        inventory.setChanged();
    }

    private void addToInventory(ItemStack value) {
        if (value.isEmpty()) return;
        var remaining = value.copy();
        player.getInventory().add(remaining);
        if (!remaining.isEmpty()) {
            throw new IllegalStateException("Player inventory cannot hold all collected items");
        }
    }

    private List<Entity> entitiesTouchingBlockCell(BlockPos target) {
        var cell = new AABB(target);
        return List.copyOf(targets.level().getEntities(
                player,
                cell.inflate(BLOCK_CELL_CONTACT_EPSILON),
                entity -> entity.isAlive()
                        && touchesBlockCell(entity.getBoundingBox(), cell)
        ));
    }

    static boolean touchesBlockCell(AABB entityBounds, AABB cell) {
        return entityBounds.maxX >= cell.minX && entityBounds.minX <= cell.maxX
                && entityBounds.maxY >= cell.minY && entityBounds.minY <= cell.maxY
                && entityBounds.maxZ >= cell.minZ && entityBounds.minZ <= cell.maxZ;
    }

    private void damageAtTarget(
            BlockPos target,
            ItemStack transmitted,
            List<Entity> damageTargets
    ) {
        var damage = transmitted.getItem() instanceof BlockItem blockItem
                ? Math.max(0.0f, blockItem.getBlock().defaultBlockState()
                .getDestroySpeed(targets.level(), target)) * 10.0f
                : (float) Math.max(0.0, transmitted.getAttributeModifiers().compute(
                Attributes.ATTACK_DAMAGE,
                player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                EquipmentSlot.MAINHAND
        ));
        if (!(damage > 0.0f)) return;
        var source = SkillDamageSource.of(player, Skills.SELF_TELEPORT.get());
        for (var entity : damageTargets) {
            if (!entity.isAlive()) continue;
            if (entity instanceof LivingEntity living
                    && CtaFriendlyFireWhitelist.shouldProtect(player, living)) continue;
            entity.hurtServer(targets.level(), source, damage);
        }
    }

    private static BlockState orient(
            BlockState state,
            @Nullable ProgramDirection direction
    ) {
        if (direction == null) return state;
        var facing = nearestDirection(direction);
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, facing);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            if (facing.getAxis().isVertical()) {
                facing = Math.abs(direction.x()) >= Math.abs(direction.z())
                        ? direction.x() >= 0.0 ? Direction.EAST : Direction.WEST
                        : direction.z() >= 0.0 ? Direction.SOUTH : Direction.NORTH;
            }
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return state;
    }

    private static Direction nearestDirection(ProgramDirection direction) {
        var x = direction.x();
        var y = direction.y();
        var z = direction.z();
        if (Math.abs(y) >= Math.abs(x) && Math.abs(y) >= Math.abs(z)) {
            return y >= 0.0 ? Direction.UP : Direction.DOWN;
        }
        return Math.abs(x) >= Math.abs(z)
                ? x >= 0.0 ? Direction.EAST : Direction.WEST
                : z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private void restoreBlockEntity(
            BlockPos position,
            BlockState state,
            @Nullable CompoundTag tag
    ) {
        if (tag == null) return;
        var blockEntity = BlockEntity.loadStatic(
                position.immutable(), state, tag, targets.level().registryAccess());
        if (blockEntity != null) {
            targets.level().setBlockEntity(blockEntity);
            blockEntity.setChanged();
        }
    }

    private static void applyRotation(
            Entity entity,
            @Nullable ProgramDirection direction
    ) {
        if (direction == null) return;
        entity.setYRot((float) Math.toDegrees(Math.atan2(-direction.x(), direction.z())));
        entity.setXRot((float) Math.toDegrees(-Math.asin(
                Math.clamp(direction.y(), -1.0, 1.0))));
        if (entity instanceof LivingEntity living) living.setYHeadRot(entity.getYRot());
    }

    private static double selfRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 16.0, 32.0);
    }

    private static float selfCost(float power) {
        return ProgramPowerScale.cost(10.0f, power);
    }

    static double entityTargetRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 64.0, 128.0);
    }

    static double entityMoveRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 64.0, 128.0);
    }

    private static float entityBaseCost(float power) {
        return ProgramPowerScale.cost(30.0f, power);
    }

    static float entityCost(float power, double actualDistance) {
        return distanceAdjustedCost(entityBaseCost(power), actualDistance);
    }

    private static float distanceAdjustedCost(float baseCost, double actualDistance) {
        if (!Double.isFinite(actualDistance) || actualDistance < 0.0) {
            throw new IllegalArgumentException("Teleport distance must be finite and non-negative");
        }
        if (actualDistance <= 16.0) return baseCost * 0.25f;
        if (actualDistance <= 32.0) return baseCost * 0.5f;
        return baseCost;
    }

    private record RemovedItem(ItemStack stack, Vec3 position) {
    }
}
