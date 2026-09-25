package org.academy.internal.common.ability.accelerator.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.WingFlightConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.WingControlIntent;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.ability.WingFlightClient;
import org.academy.internal.client.hud.ability.ToggleStatusHud;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.WingFlightRuntime;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class WhiteWing extends Skill {
    public WhiteWing() {
        super(Builder.of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .hidden()
                .maintenanceCost(80)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.BLACK_WING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("Black Wing", "academy:black_wing")));
    }

    @Override
    public void initClient() {
        AdvancedWingSweepPacket.initClient();
        AdvancedWingTransitionPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        Client.CONFIG.register(this);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_B, InputConstants.RELEASE, InputConstants.MOD_SHIFT)
        ), _ -> Client.toggle());
        ToggleStatusHud.Companion.registerStateProvider(Skills.WHITE_WING.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.ACTIVATED_WHITE_WING.get());
        });
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.WHITE_WING.get(),
                        List.of(BlackWing.Client.SKILL_INFO),
                        R.textures.white_wing_icon,
                        180, 20
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.WHITE_WING + "_toggle";
        public static Config CONFIG = new Config();
        private static final WingControlIntent.Sender CONTROL_SENDER = new WingControlIntent.Sender();

        private Client() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            var mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null || !player.getData(AttachmentTypes.ACTIVATED_WHITE_WING.get()) || mc.isPaused()) {
                CONTROL_SENDER.reset();
                return;
            }
            var input = WingFlightClient.readControl();
            input = new WingControlIntent(input.buttons(), input.yaw(), input.pitch(), CONFIG.getRemainingMomentum());
            if (CONTROL_SENDER.shouldSend(input, System.nanoTime())) MisakaNetworkClient.send(new ControlPacket(input));
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.WHITE_WING.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends WingFlightConfig {
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

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.WHITE_WING.get();
            var enabling = !skill.isEnabled(player);
            var upgradingFromBlack = enabling
                    && player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get());
            if (enabling) {
                StormWing.Server.forceDeactivate(player);
                BlackWing.Server.forceDeactivate(player);
                PlatinumWing.Server.forceDeactivate(player);
            }
            skill.toggle(player);
            WingFlightSupport.sync(player, AttachmentTypes.ACTIVATED_WHITE_WING.get(),
                    skill.isEnabled(player));
            if (upgradingFromBlack && skill.isEnabled(player)) {
                WingFlightSupport.broadcastBlackToWhiteTransition(player);
            }
        }

        @SubscribePacket
        public static void handleControl(ControlPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (isActive(player)) WingFlightRuntime.accept(player, Skills.WHITE_WING.get(), packet.input);
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.WHITE_WING.get().isEnabled(player)
                    && player.getData(AttachmentTypes.ACTIVATED_WHITE_WING.get());
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            WingFlightSupport.forceDeactivateSkill(player, Skills.WHITE_WING.get());
            WingFlightRuntime.clear(player, Skills.WHITE_WING.get());
            WingFlightSupport.sync(player, AttachmentTypes.ACTIVATED_WHITE_WING.get(), false);
        }

        public static void onLeftClickSwing(ServerPlayer player) {
            if (!isActive(player)) return;
            if (!WingFlightSupport.trySweepCost(player, Skills.WHITE_WING.get())) return;
            WingFlightSupport.broadcastSweep(player, AdvancedWingSweepPacket.WingKind.WHITE);
            WingFlightSupport.fanAttack(player, Skills.WHITE_WING.get());
        }

        private static void tick(ServerPlayer player) {
            WingFlightSupport.tick(player, Skills.WHITE_WING.get(),
                    AttachmentTypes.ACTIVATED_WHITE_WING.get());
            if (isActive(player)) {
                WingFlightSupport.deflectFrontalProjectile(player, Skills.WHITE_WING.get());
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.WHITE_WING_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ControlPacket extends Packet<ServerGamePacketListenerImpl, ControlPacket> {
        public static final StreamCodec<ByteBuf, ControlPacket> CODEC = StreamCodec.of(
                (buf, packet) -> WingControlIntent.CODEC.encode(buf, packet.input),
                buf -> new ControlPacket(WingControlIntent.CODEC.decode(buf)));
        private final WingControlIntent input;

        public ControlPacket(WingControlIntent input) {
            this.input = input;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ControlPacket> getPacketType() {
            return PacketTypes.WHITE_WING_CONTROL.get();
        }
    }
}
