package org.academy.internal.common.ability.teleport.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.gui.screen.LocationTeleportScreen;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportChunkForceManager;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.LocationTeleportData;
import org.academy.internal.common.skilldata.LocationTeleportData.Mark;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;

public final class LocationTeleport extends Skill {
    public static final int MAX_MARKS = 32;

    public LocationTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(30)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.CUT_THROUGH)
                .withCustomData(LocationTeleportData.ID, LocationTeleportData.class, player -> new LocationTeleportData())
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition("Cut Through", "academy:cut_through"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
        InputSystem.addKeyBinding(Client.KEY_NAME_OPEN, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_OPEN,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_L, InputConstants.PRESS, 0)
        ), ctx -> Client.open());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.LOCATION_TELEPORT.get(),
                        List.of(CutThrough.Client.SKILL_INFO),
                        R.textures.location_teleport_icon,
                        118,
                        50
                )
        );
        public static final String KEY_NAME_OPEN = SkillNames.LOCATION_TELEPORT + "_open";
        public static Config CONFIG = new Config();
        private static LocationTeleportScreen lastScreen;

        private static void open() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.LOCATION_TELEPORT.get())) return;
            lastScreen = new LocationTeleportScreen();
            Minecraft.getInstance().gui.setScreen(lastScreen);
        }

        @SubscribePacket
        public static void handleSync(MarksSyncPacket packet) {
            if (lastScreen != null && Minecraft.getInstance().gui.screen() == lastScreen) {
                lastScreen.setMarks(packet.getMarks(), packet.getSelectedIndex());
            }
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
        @SubscribePacket
        public static void handleRequest(RequestMarksPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var data = getData(player);
            if (data != null && Skills.LOCATION_TELEPORT.get().isEnabled(player)) sync(player, data);
        }

        @SubscribePacket
        public static void handleSave(SaveMarkPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var data = getData(player);
            if (data == null || !Skills.LOCATION_TELEPORT.get().isEnabled(player)
                    || data.getMarks().size() >= MAX_MARKS) return;

            var name = packet.getName().strip();
            if (name.isEmpty()) name = "Mark " + (data.getMarks().size() + 1);
            if (name.length() > 64) name = name.substring(0, 64);
            var pos = packet.useCurrent ? player.blockPosition()
                    : new net.minecraft.core.BlockPos(packet.x, packet.y, packet.z);
            data.getMarks().add(new Mark(name, player.level().dimension().identifier().toString(),
                    pos.getX(), pos.getY(), pos.getZ()));
            dirtyAndSync(player, data);
        }

        @SubscribePacket
        public static void handleRemove(RemoveMarkPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var data = getData(player);
            if (data == null || !Skills.LOCATION_TELEPORT.get().isEnabled(player)) return;
            var index = packet.getIndex();
            if (index < 0 || index >= data.getMarks().size()) return;
            data.getMarks().remove(index);
            if (data.getSelectedMarkIndex() == index) data.setSelectedMarkIndex(-1);
            else if (data.getSelectedMarkIndex() > index) data.setSelectedMarkIndex(data.getSelectedMarkIndex() - 1);
            dirtyAndSync(player, data);
        }

        @SubscribePacket
        public static void handleSelect(SelectMarkPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var data = getData(player);
            if (data == null || !Skills.LOCATION_TELEPORT.get().isEnabled(player)) return;
            if (packet.index < -1 || packet.index >= data.getMarks().size()) return;
            data.setSelectedMarkIndex(packet.index);
            dirtyAndSync(player, data);
        }

        @SubscribePacket
        public static void handleTeleport(TeleportToMarkPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var data = getData(player);
            if (data == null || packet.index < 0 || packet.index >= data.getMarks().size()) return;
            var mark = data.getMarks().get(packet.index);
            var level = resolveLevel(player, mark);
            if (level == null) return;
            var destination = safeDestination(player, level, mark);
            if (destination == null) return;

            Skills.LOCATION_TELEPORT.get().executeActive(player, (ctx, actualCost) -> {
                forceDestinationChunk(level, mark.x(), mark.z(), "location_" + player.getStringUUID());
                level.getChunk(mark.x() >> 4, mark.z() >> 4);
                player.teleportTo(level, destination.x, destination.y, destination.z,
                        java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                player.resetFallDistance();
                data.setSelectedMarkIndex(packet.index);
                dirtyAndSync(player, data);
            });
        }

        public static LocationTeleportData getData(ServerPlayer player) {
            return Skills.LOCATION_TELEPORT.get().<LocationTeleportData>getRuntimeData(player).orElse(null);
        }

        public static Mark getSelectedMark(ServerPlayer player) {
            var data = getData(player);
            if (data == null) return null;
            var index = data.getSelectedMarkIndex();
            return index < 0 || index >= data.getMarks().size() ? null : data.getMarks().get(index);
        }

        public static ServerLevel resolveLevel(ServerPlayer player, Mark mark) {
            var id = Identifier.tryParse(mark.dimension());
            if (id == null) return null;
            return player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }

        public static Vec3 safeDestination(ServerPlayer player, ServerLevel level, Mark mark) {
            forceDestinationChunk(level, mark.x(), mark.z(), "location_check_" + player.getStringUUID());
            level.getChunk(mark.x() >> 4, mark.z() >> 4);
            var center = new Vec3(mark.x() + 0.5, mark.y() + 0.5, mark.z() + 0.5);
            var dimensions = player.getDimensions(Pose.STANDING);
            var halfWidth = dimensions.width() / 2.0;
            var halfHeight = dimensions.height() / 2.0;
            var box = new AABB(center.x - halfWidth, center.y - halfHeight, center.z - halfWidth,
                    center.x + halfWidth, center.y + halfHeight, center.z + halfWidth);
            return level.noCollision(player, box)
                    ? new Vec3(center.x, center.y - halfHeight, center.z)
                    : null;
        }

        public static void forceDestinationChunk(ServerLevel level, int x, int z, String operation) {
            TeleportChunkForceManager.forceChunk(level, operation, x, z,
                    TeleportChunkForceManager.DEFAULT_TIMEOUT_TICKS);
        }

        private static void dirtyAndSync(ServerPlayer player, LocationTeleportData data) {
            var system = AbilitySystemServer.getSystem(player);
            var playerData = system.getPlayerData(player.getUUID());
            if (playerData != null) playerData.markDirty();
            system.schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
            sync(player, data);
        }

        private static void sync(ServerPlayer player, LocationTeleportData data) {
            MisakaNetworkServer.send(player,
                    new MarksSyncPacket(new ArrayList<>(data.getMarks()), data.getSelectedMarkIndex()));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RequestMarksPacket extends Packet<ServerGamePacketListenerImpl, RequestMarksPacket> {
        public static final RequestMarksPacket INSTANCE = new RequestMarksPacket();
        public static final StreamCodec<ByteBuf, RequestMarksPacket> CODEC = StreamCodec.unit(INSTANCE);
        private RequestMarksPacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, RequestMarksPacket> getPacketType() {
            return PacketTypes.LOCATION_TELEPORT_REQUEST.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SaveMarkPacket extends Packet<ServerGamePacketListenerImpl, SaveMarkPacket> {
        public static final StreamCodec<ByteBuf, SaveMarkPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.BOOL.encode(buf, packet.useCurrent);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.name);
                    ByteBufCodecs.INT.encode(buf, packet.x);
                    ByteBufCodecs.INT.encode(buf, packet.y);
                    ByteBufCodecs.INT.encode(buf, packet.z);
                },
                buf -> new SaveMarkPacket(ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf))
        );
        private final boolean useCurrent;
        private final String name;
        private final int x;
        private final int y;
        private final int z;
        public SaveMarkPacket(boolean useCurrent, String name, int x, int y, int z) {
            this.useCurrent = useCurrent;
            this.name = name == null ? "" : name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        public String getName() { return name; }
        @Override public PacketType<ServerGamePacketListenerImpl, SaveMarkPacket> getPacketType() {
            return PacketTypes.LOCATION_TELEPORT_SAVE.get();
        }
    }

    public abstract static class IndexPacket<T extends IndexPacket<T>> extends Packet<ServerGamePacketListenerImpl, T> {
        protected final int index;
        protected IndexPacket(int index) { this.index = index; }
        public int getIndex() { return index; }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RemoveMarkPacket extends IndexPacket<RemoveMarkPacket> {
        public static final StreamCodec<ByteBuf, RemoveMarkPacket> CODEC = ByteBufCodecs.VAR_INT.map(RemoveMarkPacket::new, RemoveMarkPacket::getIndex);
        public RemoveMarkPacket(int index) { super(index); }
        @Override public PacketType<ServerGamePacketListenerImpl, RemoveMarkPacket> getPacketType() { return PacketTypes.LOCATION_TELEPORT_REMOVE.get(); }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SelectMarkPacket extends IndexPacket<SelectMarkPacket> {
        public static final StreamCodec<ByteBuf, SelectMarkPacket> CODEC = ByteBufCodecs.INT.map(SelectMarkPacket::new, SelectMarkPacket::getIndex);
        public SelectMarkPacket(int index) { super(index); }
        @Override public PacketType<ServerGamePacketListenerImpl, SelectMarkPacket> getPacketType() { return PacketTypes.LOCATION_TELEPORT_SELECT.get(); }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TeleportToMarkPacket extends IndexPacket<TeleportToMarkPacket> {
        public static final StreamCodec<ByteBuf, TeleportToMarkPacket> CODEC = ByteBufCodecs.VAR_INT.map(TeleportToMarkPacket::new, TeleportToMarkPacket::getIndex);
        public TeleportToMarkPacket(int index) { super(index); }
        @Override public PacketType<ServerGamePacketListenerImpl, TeleportToMarkPacket> getPacketType() { return PacketTypes.LOCATION_TELEPORT_RUN.get(); }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class MarksSyncPacket extends Packet<ClientPacketListener, MarksSyncPacket> {
        public static final StreamCodec<ByteBuf, MarksSyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.marks.size());
                    for (var mark : packet.marks) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, mark.name());
                        ByteBufCodecs.STRING_UTF8.encode(buf, mark.dimension());
                        ByteBufCodecs.INT.encode(buf, mark.x());
                        ByteBufCodecs.INT.encode(buf, mark.y());
                        ByteBufCodecs.INT.encode(buf, mark.z());
                    }
                    ByteBufCodecs.INT.encode(buf, packet.selectedIndex);
                },
                buf -> {
                    var count = Math.min(MAX_MARKS, Math.max(0, ByteBufCodecs.VAR_INT.decode(buf)));
                    var marks = new ArrayList<Mark>(count);
                    for (var i = 0; i < count; i++) {
                        marks.add(new Mark(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                                ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf)));
                    }
                    return new MarksSyncPacket(marks, ByteBufCodecs.INT.decode(buf));
                }
        );
        private final List<Mark> marks;
        private final int selectedIndex;
        public MarksSyncPacket(List<Mark> marks, int selectedIndex) {
            this.marks = List.copyOf(marks);
            this.selectedIndex = selectedIndex;
        }
        public List<Mark> getMarks() { return marks; }
        public int getSelectedIndex() { return selectedIndex; }
        @Override public PacketType<ClientPacketListener, MarksSyncPacket> getPacketType() { return PacketTypes.LOCATION_TELEPORT_SYNC.get(); }
    }
}
