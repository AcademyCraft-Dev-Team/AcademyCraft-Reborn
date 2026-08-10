package org.academy.internal.common.ability.teleport.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.ability.teleport.AreaTeleportState;
import org.academy.internal.common.ability.teleport.TeleportChunkForceManager;
import org.academy.internal.common.network.PacketTypes;
import org.academy.mixin.common.LevelTicksAccessor;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class AreaTeleportStart extends Skill {
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS;

    public AreaTeleportStart() {
        super(Builder.of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(50)
                .iterationTicks(40)
                .maxStacks(1)
                .dependsOn(Skills.AREA_TELEPORT_SETUP)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Area Teleport Setup", "academy:area_teleport_setup")));
    }

    @Override public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_RUN, Client.CONFIG.getKeyBinding(Client.KEY_NAME_RUN,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                        InputConstants.PRESS, InputConstants.MOD_SHIFT)), ctx -> Client.run());
    }

    @Override public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(), new AbilitySystemClient.SkillInfo(
                        Skills.AREA_TELEPORT_START.get(), List.of(AreaTeleportSetup.Client.SKILL_INFO),
                        R.textures.area_teleport_start_icon, 146, 112));
        public static final String KEY_NAME_RUN = SkillNames.AREA_TELEPORT_START + "_run";
        public static Config CONFIG = new Config();
        private static void run() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.AREA_TELEPORT_START.get())) return;
            MisakaNetworkClient.send(RunPacket.INSTANCE);
        }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {
                }
                @Override public Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket public static void handle(RunPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var source = AreaTeleportState.selected(player.getUUID());
            var destination = AreaTeleportState.destination(player.getUUID());
            var policy = ProficiencyPolicy.server(player);
            var transform = AreaTeleportState.transform(player.getUUID());
            var swapSelected = AreaTeleportState.swap(player.getUUID());
            var setupMilestone = Skills.AREA_TELEPORT_SETUP.get()
                    .getEffectiveProficiencyMilestone(player);
            var transformed = transform != AreaTeleportState.Transform.IDENTITY;
            var mirrored = transform == AreaTeleportState.Transform.MIRROR_X
                    || transform == AreaTeleportState.Transform.MIRROR_Z;
            if (transformed && (!policy.allowAreaTeleportTransforms()
                    || setupMilestone < 2 || mirrored && setupMilestone < 3)
                    || swapSelected && (!policy.allowAreaTeleportSwap()
                    || !Skills.AREA_TELEPORT_START.get().hasProficiencyMilestone(player, 3))) {
                return;
            }
            var swap = swapSelected
                    && Skills.AREA_TELEPORT_START.get().hasProficiencyMilestone(player, 3)
                    && policy.allowAreaTeleportSwap();
            var selectMilestone = Skills.AREA_TELEPORT_SELECT.get().getEffectiveProficiencyMilestone(player);
            var designedAxis = selectMilestone >= 3 ? 40 : selectMilestone >= 2 ? 36 : 32;
            var maxAxis = Math.min(designedAxis, policy.maxAreaTeleportAxis());
            if (source == null || destination == null || !source.withinLimit(maxAxis) || !destination.withinLimit(maxAxis)
                    || source.volume() != destination.volume()
                    || !source.dimension().equals(destination.dimension())
                    || !source.dimension().equals(player.level().dimension())
                    || !(player.level() instanceof ServerLevel level)
                    || !validate(level, player, source, destination, transform, swap)) return;

            Skills.AREA_TELEPORT_START.get().executeActive(player, (ctx, actualCost) -> {
                if (move(level, player, source, destination, transform, swap,
                        ctx.milestone() >= 2)) {
                    AreaTeleportState.clear(player.getUUID());
                    AreaTeleportSelect.Server.sync(player);
                }
            });
        }

        private static boolean validate(ServerLevel level, ServerPlayer player,
                                        AreaTeleportState.Region source,
                                        AreaTeleportState.Region destination,
                                        AreaTeleportState.Transform transform,
                                        boolean swap) {
            if (destination.min().getY() < level.getMinY() || destination.max().getY() >= level.getMaxY()) return false;
            if (regionsOverlap(source, destination)) return false;
            var entityCap = ProficiencyPolicy.server(player).maxBonusEntitiesPerTick();
            var entityCount = level.getEntities(player, source.box(), entity ->
                    !(entity instanceof Player)).size();
            if (swap) {
                entityCount += level.getEntities(player, destination.box(), entity ->
                        !(entity instanceof Player)).size();
            }
            if (entityCount > entityCap) return false;
            var cursor = new BlockPos.MutableBlockPos();
            for (var region : List.of(source, destination)) {
                for (var x = region.min().getX(); x <= region.max().getX(); x++) {
                    for (var y = region.min().getY(); y <= region.max().getY(); y++) {
                        for (var z = region.min().getZ(); z <= region.max().getZ(); z++) {
                            cursor.set(x, y, z);
                            if (!level.isLoaded(cursor)) return false;
                            var state = level.getBlockState(cursor);
                            if (!state.isAir() && !canMove(level, player, cursor, state)) return false;
                            try {
                                if (transformState(state, transform).getBlock() != state.getBlock()) {
                                    return false;
                                }
                            } catch (RuntimeException unsupportedTransform) {
                                return false;
                            }
                        }
                    }
                }
            }
            return true;
        }

        private static boolean regionsOverlap(
                AreaTeleportState.Region first,
                AreaTeleportState.Region second
        ) {
            return first.min().getX() <= second.max().getX()
                    && first.max().getX() >= second.min().getX()
                    && first.min().getY() <= second.max().getY()
                    && first.max().getY() >= second.min().getY()
                    && first.min().getZ() <= second.max().getZ()
                    && first.max().getZ() >= second.min().getZ();
        }

        private static boolean canMove(ServerLevel level, ServerPlayer player,
                                       BlockPos pos, BlockState state) {
            var restricted = player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
                    || state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks();
            var event = new BreakBlockEvent(level, pos.immutable(), state, player);
            event.setCanceled(restricted);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }

        private static boolean move(ServerLevel level, ServerPlayer player,
                                    AreaTeleportState.Region source, AreaTeleportState.Region destination,
                                    AreaTeleportState.Transform transform,
                                    boolean swap,
                                    boolean fallProtection) {
            var sourceCells = capture(level, source);
            var destinationCells = capture(level, destination);
            var sourceBlockTicks = captureScheduledTicks(level.getBlockTicks(), source);
            var destinationBlockTicks = captureScheduledTicks(level.getBlockTicks(), destination);
            var sourceFluidTicks = captureScheduledTicks(level.getFluidTicks(), source);
            var destinationFluidTicks = captureScheduledTicks(level.getFluidTicks(), destination);
            var sourceEntities = freezeEntities(level, source, player);
            var destinationEntities = swap ? freezeEntities(level, destination, player) : List.<FrozenEntity>of();
            var committed = false;
            try {
                if (swap) write(level, source, transformCells(
                        destinationCells, destination, source, transform.inverse()));
                else fill(level, source, Blocks.AIR.defaultBlockState());
                write(level, destination, transformCells(sourceCells, source, destination, transform));
                for (var frozen : sourceEntities) {
                    frozen.relocate(transformPosition(
                            frozen.position, source, destination, transform),
                            transformYaw(frozen.yRot, transform));
                }
                if (swap) {
                    for (var frozen : destinationEntities) {
                        frozen.relocate(transformPosition(
                                frozen.position, destination, source, transform.inverse()),
                                transformYaw(frozen.yRot, transform.inverse()));
                    }
                }
                reconnectRiding(sourceEntities);
                reconnectRiding(destinationEntities);
                replaceScheduledTicks(level.getBlockTicks(), source, destination, transform,
                        swap, sourceBlockTicks, destinationBlockTicks);
                replaceScheduledTicks(level.getFluidTicks(), source, destination, transform,
                        swap, sourceFluidTicks, destinationFluidTicks);
                if (fallProtection) {
                    for (var frozen : sourceEntities) frozen.grantFallProtection(player);
                    for (var frozen : destinationEntities) frozen.grantFallProtection(player);
                }
                committed = true;
                return true;
            } catch (Throwable error) {
                AcademyCraft.getLogger().error("Area Teleport transaction rolled back", error);
                write(level, destination, destinationCells);
                write(level, source, sourceCells);
                sourceEntities.forEach(FrozenEntity::restorePosition);
                destinationEntities.forEach(FrozenEntity::restorePosition);
                reconnectRiding(sourceEntities);
                reconnectRiding(destinationEntities);
                restoreScheduledTicks(level.getBlockTicks(), source, destination,
                        sourceBlockTicks, destinationBlockTicks);
                restoreScheduledTicks(level.getFluidTicks(), source, destination,
                        sourceFluidTicks, destinationFluidTicks);
                return false;
            } finally {
                sourceEntities.forEach(FrozenEntity::restore);
                destinationEntities.forEach(FrozenEntity::restore);
                if (!committed) {
                    sourceEntities.forEach(FrozenEntity::restorePosition);
                    destinationEntities.forEach(FrozenEntity::restorePosition);
                }
            }
        }

        private static Vec3 transformPosition(
                Vec3 position,
                AreaTeleportState.Region source,
                AreaTeleportState.Region destination,
                AreaTeleportState.Transform transform
        ) {
            var relativeX = position.x - source.min().getX();
            var relativeY = position.y - source.min().getY();
            var relativeZ = position.z - source.min().getZ();
            var transformedX = relativeX;
            var transformedZ = relativeZ;
            switch (transform) {
                case ROTATE_90 -> {
                    transformedX = source.sizeZ() - relativeZ;
                    transformedZ = relativeX;
                }
                case ROTATE_180 -> {
                    transformedX = source.sizeX() - relativeX;
                    transformedZ = source.sizeZ() - relativeZ;
                }
                case ROTATE_270 -> {
                    transformedX = relativeZ;
                    transformedZ = source.sizeX() - relativeX;
                }
                case MIRROR_X -> transformedX = source.sizeX() - relativeX;
                case MIRROR_Z -> transformedZ = source.sizeZ() - relativeZ;
                default -> { }
            }
            return new Vec3(
                    destination.min().getX() + transformedX,
                    destination.min().getY() + relativeY,
                    destination.min().getZ() + transformedZ
            );
        }

        private static float transformYaw(float yaw, AreaTeleportState.Transform transform) {
            return switch (transform) {
                case ROTATE_90 -> yaw - 90.0f;
                case ROTATE_180 -> yaw + 180.0f;
                case ROTATE_270 -> yaw + 90.0f;
                case MIRROR_X -> -yaw;
                case MIRROR_Z -> 180.0f - yaw;
                default -> yaw;
            };
        }

        private static void reconnectRiding(List<FrozenEntity> entities) {
            var included = new HashSet<Entity>();
            entities.forEach(frozen -> included.add(frozen.entity));
            for (var frozen : entities) {
                if (frozen.vehicle != null && included.contains(frozen.vehicle)) {
                    frozen.entity.startRiding(frozen.vehicle, true, true);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> List<ScheduledTickSnapshot<T>> captureScheduledTicks(
                LevelTicks<T> ticks,
                AreaTeleportState.Region region
        ) {
            var snapshots = new ArrayList<ScheduledTickSnapshot<T>>();
            var containers = ((LevelTicksAccessor<T>) (Object) ticks).academy$getAllContainers();
            for (var container : containers.values()) {
                container.getAll()
                        .filter(tick -> contains(region, tick.pos()))
                        .forEach(tick -> snapshots.add(new ScheduledTickSnapshot<>(
                                tick.type(),
                                tick.pos().immutable(),
                                tick.triggerTick(),
                                tick.priority(),
                                tick.subTickOrder()
                        )));
            }
            return List.copyOf(snapshots);
        }

        private static boolean contains(AreaTeleportState.Region region, BlockPos pos) {
            return pos.getX() >= region.min().getX() && pos.getX() <= region.max().getX()
                    && pos.getY() >= region.min().getY() && pos.getY() <= region.max().getY()
                    && pos.getZ() >= region.min().getZ() && pos.getZ() <= region.max().getZ();
        }

        private static <T> void replaceScheduledTicks(
                LevelTicks<T> ticks,
                AreaTeleportState.Region source,
                AreaTeleportState.Region destination,
                AreaTeleportState.Transform transform,
                boolean swap,
                List<ScheduledTickSnapshot<T>> sourceTicks,
                List<ScheduledTickSnapshot<T>> destinationTicks
        ) {
            clearScheduledTicks(ticks, source, destination);
            for (var tick : sourceTicks) {
                schedule(ticks, tick, transformBlockPosition(
                        tick.position, source, destination, transform));
            }
            if (swap) {
                for (var tick : destinationTicks) {
                    schedule(ticks, tick, transformBlockPosition(
                            tick.position, destination, source, transform.inverse()));
                }
            }
        }

        private static <T> void restoreScheduledTicks(
                LevelTicks<T> ticks,
                AreaTeleportState.Region source,
                AreaTeleportState.Region destination,
                List<ScheduledTickSnapshot<T>> sourceTicks,
                List<ScheduledTickSnapshot<T>> destinationTicks
        ) {
            clearScheduledTicks(ticks, source, destination);
            sourceTicks.forEach(tick -> schedule(ticks, tick, tick.position));
            destinationTicks.forEach(tick -> schedule(ticks, tick, tick.position));
        }

        private static <T> void clearScheduledTicks(
                LevelTicks<T> ticks,
                AreaTeleportState.Region source,
                AreaTeleportState.Region destination
        ) {
            ticks.clearArea(BoundingBox.fromCorners(source.min(), source.max()));
            ticks.clearArea(BoundingBox.fromCorners(destination.min(), destination.max()));
        }

        private static <T> void schedule(
                LevelTicks<T> ticks,
                ScheduledTickSnapshot<T> snapshot,
                BlockPos position
        ) {
            ticks.schedule(new ScheduledTick<>(
                    snapshot.type,
                    position.immutable(),
                    snapshot.triggerTick,
                    snapshot.priority,
                    snapshot.subTickOrder
            ));
        }

        private static BlockPos transformBlockPosition(
                BlockPos position,
                AreaTeleportState.Region source,
                AreaTeleportState.Region destination,
                AreaTeleportState.Transform transform
        ) {
            var x = position.getX() - source.min().getX();
            var y = position.getY() - source.min().getY();
            var z = position.getZ() - source.min().getZ();
            var transformedX = x;
            var transformedZ = z;
            switch (transform) {
                case ROTATE_90 -> {
                    transformedX = source.sizeZ() - 1 - z;
                    transformedZ = x;
                }
                case ROTATE_180 -> {
                    transformedX = source.sizeX() - 1 - x;
                    transformedZ = source.sizeZ() - 1 - z;
                }
                case ROTATE_270 -> {
                    transformedX = z;
                    transformedZ = source.sizeX() - 1 - x;
                }
                case MIRROR_X -> transformedX = source.sizeX() - 1 - x;
                case MIRROR_Z -> transformedZ = source.sizeZ() - 1 - z;
                default -> { }
            }
            return destination.min().offset(transformedX, y, transformedZ);
        }

        private static Cell[] transformCells(Cell[] cells,
                                             AreaTeleportState.Region source,
                                             AreaTeleportState.Region destination,
                                             AreaTeleportState.Transform transform) {
            var result = new Cell[cells.length];
            var sourceSizeX = source.sizeX();
            var sourceSizeY = source.sizeY();
            var sourceSizeZ = source.sizeZ();
            for (var x = 0; x < sourceSizeX; x++) {
                for (var y = 0; y < sourceSizeY; y++) {
                    for (var z = 0; z < sourceSizeZ; z++) {
                        var sourceIndex = (x * sourceSizeY + y) * sourceSizeZ + z;
                        var transformedX = x;
                        var transformedZ = z;
                        switch (transform) {
                            case ROTATE_90 -> {
                                transformedX = sourceSizeZ - 1 - z;
                                transformedZ = x;
                            }
                            case ROTATE_180 -> {
                                transformedX = sourceSizeX - 1 - x;
                                transformedZ = sourceSizeZ - 1 - z;
                            }
                            case ROTATE_270 -> {
                                transformedX = z;
                                transformedZ = sourceSizeX - 1 - x;
                            }
                            case MIRROR_X -> transformedX = sourceSizeX - 1 - x;
                            case MIRROR_Z -> transformedZ = sourceSizeZ - 1 - z;
                            default -> { }
                        }
                        var destinationIndex = (transformedX * destination.sizeY() + y)
                                * destination.sizeZ() + transformedZ;
                        var cell = cells[sourceIndex];
                        result[destinationIndex] = new Cell(
                                transformState(cell.state, transform),
                                cell.tag
                        );
                    }
                }
            }
            return result;
        }

        private static BlockState transformState(
                BlockState state,
                AreaTeleportState.Transform transform
        ) {
            return switch (transform) {
                case ROTATE_90 -> state.rotate(Rotation.CLOCKWISE_90);
                case ROTATE_180 -> state.rotate(Rotation.CLOCKWISE_180);
                case ROTATE_270 -> state.rotate(Rotation.COUNTERCLOCKWISE_90);
                case MIRROR_X -> state.mirror(Mirror.FRONT_BACK);
                case MIRROR_Z -> state.mirror(Mirror.LEFT_RIGHT);
                default -> state;
            };
        }

        private static Cell[] capture(ServerLevel level, AreaTeleportState.Region region) {
            var cells = new Cell[(int) region.volume()];
            var cursor = new BlockPos.MutableBlockPos();
            var index = 0;
            for (var x = region.min().getX(); x <= region.max().getX(); x++)
                for (var y = region.min().getY(); y <= region.max().getY(); y++)
                    for (var z = region.min().getZ(); z <= region.max().getZ(); z++) {
                        cursor.set(x, y, z);
                        var entity = level.getBlockEntity(cursor);
                        var tag = entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
                        cells[index++] = new Cell(level.getBlockState(cursor), tag);
                    }
            return cells;
        }

        private static void fill(ServerLevel level, AreaTeleportState.Region region, BlockState state) {
            var cursor = new BlockPos.MutableBlockPos();
            for (var x = region.min().getX(); x <= region.max().getX(); x++)
                for (var y = region.min().getY(); y <= region.max().getY(); y++)
                    for (var z = region.min().getZ(); z <= region.max().getZ(); z++) {
                        cursor.set(x, y, z);
                        level.setBlock(cursor, state, BLOCK_FLAGS);
                    }
        }

        private static void write(ServerLevel level, AreaTeleportState.Region region, Cell[] cells) {
            var cursor = new BlockPos.MutableBlockPos();
            var index = 0;
            for (var x = region.min().getX(); x <= region.max().getX(); x++)
                for (var y = region.min().getY(); y <= region.max().getY(); y++)
                    for (var z = region.min().getZ(); z <= region.max().getZ(); z++) {
                        cursor.set(x, y, z);
                        var cell = cells[index++];
                        level.setBlock(cursor, cell.state, BLOCK_FLAGS);
                        if (cell.tag != null) {
                            var entity = BlockEntity.loadStatic(cursor.immutable(), cell.state, cell.tag,
                                    level.registryAccess());
                            if (entity != null) {
                                level.setBlockEntity(entity);
                                entity.setChanged();
                            }
                        }
                    }
        }

        private static List<FrozenEntity> freezeEntities(ServerLevel level, AreaTeleportState.Region source,
                                                         ServerPlayer player) {
            var result = new ArrayList<FrozenEntity>();
            for (var entity : level.getEntities(player, source.box(), entity -> !(entity instanceof Player))) {
                var frozen = new FrozenEntity(entity);
                result.add(frozen);
            }
            var included = new HashSet<Entity>();
            result.forEach(frozen -> included.add(frozen.entity));
            result.forEach(frozen -> frozen.freeze(included));
            return result;
        }
    }

    private record Cell(BlockState state, CompoundTag tag) {
    }

    private record ScheduledTickSnapshot<T>(
            T type,
            BlockPos position,
            long triggerTick,
            TickPriority priority,
            long subTickOrder
    ) {
    }

    private static final class FrozenEntity {
        private final Entity entity;
        private final Vec3 position;
        private final float yRot;
        private final float xRot;
        private final Entity vehicle;
        private final boolean noGravity;
        private final boolean noAi;
        private FrozenEntity(Entity entity) {
            this.entity = entity;
            position = entity.position();
            yRot = entity.getYRot();
            xRot = entity.getXRot();
            vehicle = entity.getVehicle();
            noGravity = entity.isNoGravity();
            noAi = entity instanceof Mob mob && mob.isNoAi();
        }
        private void freeze(java.util.Set<Entity> included) {
            if (vehicle != null && included.contains(vehicle)) entity.stopRiding();
            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity instanceof Mob mob) mob.setNoAi(true);
        }
        private void relocate(Vec3 destination, float destinationYaw) {
            if (!TeleportSync.teleportInstantly(entity, destination)) {
                throw new IllegalStateException("Entity rejected area teleport: " + entity.getStringUUID());
            }
            entity.setYRot(destinationYaw);
            entity.setXRot(xRot);
            entity.setYHeadRot(destinationYaw);
            entity.resetFallDistance();
        }
        private void restorePosition() {
            TeleportSync.teleportInstantly(entity, position);
            entity.setYRot(yRot);
            entity.setXRot(xRot);
            entity.setYHeadRot(yRot);
            entity.resetFallDistance();
        }
        private void grantFallProtection(ServerPlayer player) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                TimedSkillEffectRuntime.put(
                        player,
                        entity.getUUID(),
                        Skills.AREA_TELEPORT_START.get(),
                        "fall_protection",
                        40,
                        1.0f
                );
            }
        }
        private void restore() {
            entity.setNoGravity(noGravity);
            if (entity instanceof Mob mob) mob.setNoAi(noAi);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RunPacket extends Packet<ServerGamePacketListenerImpl, RunPacket> {
        public static final RunPacket INSTANCE = new RunPacket();
        public static final StreamCodec<ByteBuf, RunPacket> CODEC = StreamCodec.unit(INSTANCE);
        private RunPacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, RunPacket> getPacketType() { return PacketTypes.AREA_TELEPORT_START_RUN.get(); }
    }
}
