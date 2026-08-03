package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
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

import java.util.Map;
import java.util.WeakHashMap;

public class SpacialReplace extends Skill {
    public static final double MAX_SELECTION_DISTANCE = 64.0;
    private static final int BLOCK_FLAGS = 3;

    public SpacialReplace() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(300)
                .iterationTicks(60)
                .maxStacks(1)
                .dependsOn(Skills.COORDINATE_TELEPORT)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_CORNER1, Client.CONFIG.getKeyBinding(Client.KEY_CORNER1,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_I,
                                InputConstants.PRESS,
                                InputConstants.MOD_ALT | InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL)),
                _ -> Client.selectCorner(1));
        InputSystem.addKeyBinding(Client.KEY_CORNER2, Client.CONFIG.getKeyBinding(Client.KEY_CORNER2,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_J,
                                InputConstants.PRESS,
                                InputConstants.MOD_ALT | InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL)),
                _ -> Client.selectCorner(2));
        InputSystem.addKeyBinding(Client.KEY_PASTE, Client.CONFIG.getKeyBinding(Client.KEY_PASTE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_P,
                                InputConstants.PRESS,
                                InputConstants.MOD_ALT | InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL)),
                _ -> Client.paste());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_CORNER1 = SkillNames.SPACIAL_REPLACE + "_corner1";
        public static final String KEY_CORNER2 = SkillNames.SPACIAL_REPLACE + "_corner2";
        public static final String KEY_PASTE = SkillNames.SPACIAL_REPLACE + "_paste";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void selectCorner(int corner) {
            if (!AbilitySystemClient.canUseSkill(Skills.SPACIAL_REPLACE.get())) return;
            MisakaNetworkClient.send(new SetCornerPacket(corner));
        }

        private static void paste() {
            if (!AbilitySystemClient.canUseSkill(Skills.SPACIAL_REPLACE.get())) return;
            MisakaNetworkClient.send(PastePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        private static final Map<ServerPlayer, BlockPos[]> SELECTIONS = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleSetCorner(SetCornerPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.SPACIAL_REPLACE.get().isEnabled(player)
                    || packet.corner() < 1 || packet.corner() > 2) return;
            var position = lookedAtBlock(player);
            if (position == null) return;
            SELECTIONS.computeIfAbsent(player, _ -> new BlockPos[2])[packet.corner() - 1] = position;
        }

        @SubscribePacket
        public static void handlePaste(PastePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var corners = SELECTIONS.get(player);
            var targetOrigin = lookedAtBlock(player);
            if (corners == null || corners[0] == null || corners[1] == null || targetOrigin == null) return;

            var source = region(player, corners[0], corners[1]);
            if (source == null) return;
            var destination = new AreaTeleportState.Region(
                    player.level().dimension(),
                    targetOrigin,
                    targetOrigin.offset(source.sizeX() - 1, source.sizeY() - 1, source.sizeZ() - 1)
            );
            if (!destination.withinLimit() || source.min().equals(destination.min())) return;
            if (!(player.level() instanceof ServerLevel level)
                    || !validate(level, player, source, destination)) return;

            Skills.SPACIAL_REPLACE.get().executeActive(player, (_, _) -> {
                if (move(level, player, source, destination)) {
                    SELECTIONS.remove(player);
                    var center = destination.box().getCenter();
                    level.sendParticles(ParticleTypes.PORTAL,
                            center.x, center.y, center.z, 80,
                            destination.sizeX() * 0.25,
                            destination.sizeY() * 0.25,
                            destination.sizeZ() * 0.25,
                            0.15);
                    level.playSound(null, targetOrigin, SoundEvents.ENDERMAN_TELEPORT,
                            SoundSource.PLAYERS, 1.0f, 0.7f);
                }
            });
        }

        private static BlockPos lookedAtBlock(ServerPlayer player) {
            var hit = player.pick(MAX_SELECTION_DISTANCE, 1.0f, false);
            if (!(hit instanceof BlockHitResult blockHit)) return null;
            var pos = blockHit.getBlockPos();
            return player.level().hasChunkAt(pos) ? pos.immutable() : null;
        }

        static AreaTeleportState.Region region(ServerPlayer player, BlockPos first, BlockPos second) {
            var min = new BlockPos(
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ())
            );
            var max = new BlockPos(
                    Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()),
                    Math.max(first.getZ(), second.getZ())
            );
            var region = new AreaTeleportState.Region(player.level().dimension(), min, max);
            return region.withinLimit() ? region : null;
        }

        private static boolean validate(ServerLevel level, ServerPlayer player,
                                        AreaTeleportState.Region source,
                                        AreaTeleportState.Region destination) {
            if (destination.min().getY() < level.getMinY()
                    || destination.max().getY() >= level.getMaxY()) return false;
            for (var region : new AreaTeleportState.Region[]{source, destination}) {
                var cursor = new BlockPos.MutableBlockPos();
                for (var x = region.min().getX(); x <= region.max().getX(); x++)
                    for (var y = region.min().getY(); y <= region.max().getY(); y++)
                        for (var z = region.min().getZ(); z <= region.max().getZ(); z++) {
                            cursor.set(x, y, z);
                            var state = level.getBlockState(cursor);
                            if (!state.isAir() && !canMove(level, player, cursor, state)) return false;
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
                                    AreaTeleportState.Region source,
                                    AreaTeleportState.Region destination) {
            var operation = "replace_" + player.getStringUUID();
            TeleportChunkForceManager.forceRegion(level, operation + "_source",
                    source.min().getX(), source.min().getZ(), source.max().getX(), source.max().getZ(), 200);
            TeleportChunkForceManager.forceRegion(level, operation + "_destination",
                    destination.min().getX(), destination.min().getZ(),
                    destination.max().getX(), destination.max().getZ(), 200);
            loadChunks(level, source);
            loadChunks(level, destination);

            var sourceCells = capture(level, source);
            var destinationCells = capture(level, destination);
            try {
                fill(level, source, Blocks.AIR.defaultBlockState());
                write(level, destination, sourceCells);
                return true;
            } catch (RuntimeException exception) {
                write(level, destination, destinationCells);
                write(level, source, sourceCells);
                return false;
            }
        }

        private static void loadChunks(ServerLevel level, AreaTeleportState.Region region) {
            for (var x = region.min().getX() >> 4; x <= region.max().getX() >> 4; x++)
                for (var z = region.min().getZ() >> 4; z <= region.max().getZ() >> 4; z++)
                    level.getChunk(x, z);
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
                        CompoundTag tag = entity == null ? null
                                : entity.saveWithFullMetadata(level.registryAccess());
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
                        level.setBlock(cursor, cell.state(), BLOCK_FLAGS);
                        if (cell.tag() != null) {
                            var entity = BlockEntity.loadStatic(
                                    cursor.immutable(), cell.state(), cell.tag(), level.registryAccess());
                            if (entity != null) {
                                level.setBlockEntity(entity);
                                entity.setChanged();
                            }
                        }
                    }
        }
    }

    private record Cell(BlockState state, CompoundTag tag) {
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SetCornerPacket extends Packet<ServerGamePacketListenerImpl, SetCornerPacket> {
        public static final StreamCodec<ByteBuf, SetCornerPacket> CODEC = ByteBufCodecs.VAR_INT
                .map(SetCornerPacket::new, SetCornerPacket::corner);
        private final int corner;

        public SetCornerPacket(int corner) {
            this.corner = corner;
        }

        public int corner() {
            return corner;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SetCornerPacket> getPacketType() {
            return PacketTypes.SPACIAL_REPLACE_SET_CORNER.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class PastePacket extends Packet<ServerGamePacketListenerImpl, PastePacket> {
        public static final PastePacket INSTANCE = new PastePacket();
        public static final StreamCodec<ByteBuf, PastePacket> CODEC = StreamCodec.unit(INSTANCE);

        private PastePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, PastePacket> getPacketType() {
            return PacketTypes.SPACIAL_REPLACE_PASTE.get();
        }
    }
}
