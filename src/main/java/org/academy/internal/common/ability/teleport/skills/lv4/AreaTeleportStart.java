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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.ability.teleport.AreaTeleportState;
import org.academy.internal.common.ability.teleport.TeleportChunkForceManager;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
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
            if (source == null || destination == null || !source.withinLimit() || !destination.withinLimit()
                    || source.volume() != destination.volume()
                    || !source.dimension().equals(destination.dimension())
                    || !source.dimension().equals(player.level().dimension())
                    || !(player.level() instanceof ServerLevel level)
                    || !validate(level, player, source, destination)) return;

            Skills.AREA_TELEPORT_START.get().executeActive(player, (ctx, actualCost) -> {
                if (move(level, player, source, destination)) {
                    AreaTeleportState.clear(player.getUUID());
                    AreaTeleportSelect.Server.sync(player);
                }
            });
        }

        private static boolean validate(ServerLevel level, ServerPlayer player,
                                        AreaTeleportState.Region source, AreaTeleportState.Region destination) {
            if (destination.min().getY() < level.getMinY() || destination.max().getY() >= level.getMaxY()) return false;
            var cursor = new BlockPos.MutableBlockPos();
            for (var region : List.of(source, destination)) {
                for (var x = region.min().getX(); x <= region.max().getX(); x++) {
                    for (var y = region.min().getY(); y <= region.max().getY(); y++) {
                        for (var z = region.min().getZ(); z <= region.max().getZ(); z++) {
                            cursor.set(x, y, z);
                            var state = level.getBlockState(cursor);
                            if (!state.isAir() && !canMove(level, player, cursor, state)) return false;
                        }
                    }
                }
            }
            return true;
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
                                    AreaTeleportState.Region source, AreaTeleportState.Region destination) {
            var operation = "area_" + player.getStringUUID();
            TeleportChunkForceManager.forceRegion(level, operation + "_source",
                    source.min().getX(), source.min().getZ(), source.max().getX(), source.max().getZ(), 200);
            TeleportChunkForceManager.forceRegion(level, operation + "_destination",
                    destination.min().getX(), destination.min().getZ(), destination.max().getX(), destination.max().getZ(), 200);
            loadChunks(level, source);
            loadChunks(level, destination);

            var sourceCells = capture(level, source);
            var destinationCells = capture(level, destination);
            var entities = freezeEntities(level, source, player);
            try {
                fill(level, source, Blocks.AIR.defaultBlockState());
                write(level, destination, sourceCells);
                var offset = new Vec3(destination.min().getX() - source.min().getX(),
                        destination.min().getY() - source.min().getY(),
                        destination.min().getZ() - source.min().getZ());
                for (var frozen : entities) {
                    var position = frozen.position.add(offset);
                    TeleportSync.teleportInstantly(frozen.entity, position);
                    frozen.entity.resetFallDistance();
                }
                return true;
            } catch (Throwable error) {
                AcademyCraft.getLogger().error("Area Teleport transaction rolled back", error);
                write(level, destination, destinationCells);
                write(level, source, sourceCells);
                return false;
            } finally {
                entities.forEach(FrozenEntity::restore);
                TeleportChunkForceManager.release(operation + "_source");
                TeleportChunkForceManager.release(operation + "_destination");
            }
        }

        private static void loadChunks(ServerLevel level, AreaTeleportState.Region region) {
            for (var x = region.min().getX() >> 4; x <= region.max().getX() >> 4; x++)
                for (var z = region.min().getZ() >> 4; z <= region.max().getZ() >> 4; z++) level.getChunk(x, z);
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
                frozen.freeze();
                result.add(frozen);
            }
            return result;
        }
    }

    private record Cell(BlockState state, CompoundTag tag) {
    }

    private static final class FrozenEntity {
        private final Entity entity;
        private final Vec3 position;
        private final boolean noGravity;
        private final boolean noAi;
        private FrozenEntity(Entity entity) {
            this.entity = entity;
            position = entity.position();
            noGravity = entity.isNoGravity();
            noAi = entity instanceof Mob mob && mob.isNoAi();
        }
        private void freeze() {
            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity instanceof Mob mob) mob.setNoAi(true);
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
