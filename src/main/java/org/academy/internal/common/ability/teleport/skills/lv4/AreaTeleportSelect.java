package org.academy.internal.common.ability.teleport.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.AreaTeleportState;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
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

public final class AreaTeleportSelect extends Skill {
    private static final double BASE_PICK_REACH = 80.0;
    private static final double PROFICIENT_PICK_REACH = BASE_PICK_REACH * 1.2;

    public AreaTeleportSelect() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(50)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.LOCATION_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Location Teleport", "academy:location_teleport"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
        NeoForge.EVENT_BUS.register(Client.class);
        InputSystem.addKeyBinding(Client.KEY_NAME_MARK, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_MARK,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y, InputConstants.PRESS, 0)
        ), ctx -> Client.mark());
        InputSystem.addKeyBinding(Client.KEY_NAME_SETUP, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_SETUP,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                        InputConstants.PRESS, InputConstants.MOD_ALT)
        ), ctx -> Client.setup());
        InputSystem.addKeyBinding(Client.KEY_NAME_TRANSFORM, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TRANSFORM,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                        InputConstants.PRESS, InputConstants.MOD_SHIFT | InputConstants.MOD_ALT)
        ), ctx -> Client.transform());
        InputSystem.addKeyBinding(Client.KEY_NAME_SWAP, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_SWAP,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                        InputConstants.PRESS, InputConstants.MOD_CONTROL | InputConstants.MOD_ALT)
        ), ctx -> Client.toggleSwap());
        InputSystem.addKeyBinding(Client.KEY_NAME_RUN, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_RUN,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                        InputConstants.PRESS, InputConstants.MOD_SHIFT)
        ), ctx -> Client.run());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
        MisakaNetworkServer.NETWORK_MANAGER.register(AreaTeleportSetup.Server.class);
        MisakaNetworkServer.NETWORK_MANAGER.register(AreaTeleportStart.Server.class);
    }

    public boolean executeAreaTeleport(ServerPlayer player, SkillAction action) {
        return executeActive(player, action);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(Skills.AREA_TELEPORT_SELECT.get(),
                        List.of(LocationTeleport.Client.SKILL_INFO),
                        R.textures.area_teleport_select_icon, 146, 60)
        );
        public static final String KEY_NAME_MARK = SkillNames.AREA_TELEPORT_SELECT + "_mark";
        public static final String KEY_NAME_SETUP = SkillNames.AREA_TELEPORT_SELECT + "_setup";
        public static final String KEY_NAME_TRANSFORM = SkillNames.AREA_TELEPORT_SELECT + "_transform";
        public static final String KEY_NAME_SWAP = SkillNames.AREA_TELEPORT_SELECT + "_swap";
        public static final String KEY_NAME_RUN = SkillNames.AREA_TELEPORT_SELECT + "_run";
        public static Config CONFIG = new Config();
        private static List<Preview> previews = List.of();

        private static void mark() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.AREA_TELEPORT_SELECT.get())) return;
            MisakaNetworkClient.send(MarkPacket.INSTANCE);
        }

        private static void setup() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.AREA_TELEPORT_SELECT.get())) return;
            MisakaNetworkClient.send(AreaTeleportSetup.MarkPacket.MARK);
        }

        private static void toggleSwap() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.AREA_TELEPORT_SELECT.get())) return;
            MisakaNetworkClient.send(AreaTeleportSetup.MarkPacket.TOGGLE_SWAP);
        }

        private static void transform() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.AREA_TELEPORT_SELECT.get())) return;
            MisakaNetworkClient.send(AreaTeleportSetup.MarkPacket.TRANSFORM);
        }

        private static void run() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.AREA_TELEPORT_SELECT.get())) return;
            MisakaNetworkClient.send(AreaTeleportStart.RunPacket.INSTANCE);
        }

        @SubscribePacket
        public static void handleSync(SyncPacket packet) {
            previews = packet.previews;
        }

        @SubscribeEvent
        public static void onLevelRender(LevelRenderEvent event) {
            if (previews.isEmpty()) return;
            var minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            var dimension = minecraft.level.dimension().identifier().toString();
            var renderType = Render.RenderTypes.MINE_DETECT_LINES;
            var camera = minecraft.gameRenderer.mainCamera().position();
            var matrices = event.getMatrixStack();
            matrices.pushPose();
            matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
            event.submitCustomGeometry(renderType, (snapshot, consumer) -> {
                for (var preview : previews) {
                    if (!dimension.equals(preview.dimension)) continue;
                    var box = new AABB(preview.min.getX(), preview.min.getY(), preview.min.getZ(),
                            preview.max.getX() + 1.0, preview.max.getY() + 1.0, preview.max.getZ() + 1.0);
                    var color = preview.color;
                    LineBoxRenderer.renderWireframeBox(snapshot, consumer, box,
                            color == 0 ? 1.0f : 0.1f,
                            color == 1 ? 1.0f : color == 2 ? 0.9f : 0.8f,
                            color == 2 ? 1.0f : 0.1f,
                            1.0f);
                }
            });
            matrices.popPose();
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
        public static void handle(MarkPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.AREA_TELEPORT_SELECT.get().isEnabled(player)) return;
            var pos = pickBlock(player);
            if (pos == null) return;
            var uuid = player.getUUID();
            if (!AreaTeleportState.hasPending(uuid)) {
                AreaTeleportState.setFirstCorner(uuid, player.level().dimension(), pos);
            } else if (AreaTeleportState.complete(uuid, player.level().dimension(), pos,
                    maximumAxis(player)) == null) {
                AreaTeleportState.setFirstCorner(uuid, player.level().dimension(), pos);
            }
            sync(player);
        }

        private static int maximumAxis(ServerPlayer player) {
            var milestone = Skills.AREA_TELEPORT_SELECT.get().getEffectiveProficiencyMilestone(player);
            var designed = milestone >= 3 ? 40 : milestone >= 2 ? 36 : 32;
            return Math.min(designed, ProficiencyPolicy.server(player).maxAreaTeleportAxis());
        }

        static BlockPos pickBlock(ServerPlayer player) {
            var reach = Skills.AREA_TELEPORT_SELECT.get().hasProficiencyMilestone(player, 1)
                    ? PROFICIENT_PICK_REACH : BASE_PICK_REACH;
            var hit = player.pick(reach, 1.0f, false);
            return hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos().immutable() : null;
        }

        public static void sync(ServerPlayer player) {
            var snapshot = AreaTeleportState.snapshot(player.getUUID());
            var previews = new ArrayList<Preview>(3);
            if (snapshot.pending() != null && snapshot.dimension() != null) {
                previews.add(new Preview(snapshot.dimension().identifier().toString(),
                        snapshot.pending(), snapshot.pending(), 0));
            }
            addRegion(previews, snapshot.selected(), 1);
            addRegion(previews, snapshot.destination(), 2);
            MisakaNetworkServer.send(player, new SyncPacket(previews));
        }

        private static void addRegion(List<Preview> previews, AreaTeleportState.Region region, int color) {
            if (region != null) previews.add(new Preview(region.dimension().identifier().toString(),
                    region.min(), region.max(), color));
        }
    }

    public record Preview(String dimension, BlockPos min, BlockPos max, int color) {
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MarkPacket extends Packet<ServerGamePacketListenerImpl, MarkPacket> {
        public static final MarkPacket INSTANCE = new MarkPacket();
        public static final StreamCodec<ByteBuf, MarkPacket> CODEC = StreamCodec.unit(INSTANCE);

        private MarkPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MarkPacket> getPacketType() {
            return PacketTypes.AREA_TELEPORT_SELECT_MARK.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.previews.size());
                    for (var preview : packet.previews) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, preview.dimension);
                        BlockPos.STREAM_CODEC.encode(buf, preview.min);
                        BlockPos.STREAM_CODEC.encode(buf, preview.max);
                        ByteBufCodecs.VAR_INT.encode(buf, preview.color);
                    }
                },
                buf -> {
                    var count = Math.min(3, Math.max(0, ByteBufCodecs.VAR_INT.decode(buf)));
                    var previews = new ArrayList<Preview>(count);
                    for (var i = 0; i < count; i++) {
                        previews.add(new Preview(ByteBufCodecs.STRING_UTF8.decode(buf),
                                BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf),
                                ByteBufCodecs.VAR_INT.decode(buf)));
                    }
                    return new SyncPacket(previews);
                }
        );
        private final List<Preview> previews;

        public SyncPacket(List<Preview> previews) {
            this.previews = List.copyOf(previews);
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.AREA_TELEPORT_SYNC.get();
        }
    }
}
