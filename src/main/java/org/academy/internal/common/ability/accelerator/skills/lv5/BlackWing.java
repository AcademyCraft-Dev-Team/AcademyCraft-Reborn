package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.WingEffectRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_B;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public final class BlackWing extends Skill {
    public BlackWing() {
        super(Builder.of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(WingFlightSupport.RESERVED_CP)
                .iterationTicks(30)
                .maxStacks(1)
                .dependsOn(Skills.STORM_WING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("Storm Wing", "academy:storm_wing")));
    }

    @Override
    public void initClient() {
        AdvancedWingSweepPacket.initClient();
        RendererManager.registerEffectRenderer(WingEffectRenderer.BLACK);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, GLFW_KEY_B, GLFW_RELEASE, GLFW_MOD_ALT)
        ), _ -> Client.toggle());
        ToggleStatusHud.registerStateProvider(Skills.BLACK_WING.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get());
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
                        Skills.BLACK_WING.get(),
                        List.of(StormWing.Client.SKILL_INFO),
                        R.textures.black_wing_icon,
                        155, 20
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.BLACK_WING + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            var player = Minecraft.getInstance().player;
            WingFlightSupport.clientTick(
                    player != null && player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get()),
                    state -> MisakaNetworkClient.send(new ControlPacket(state))
            );
        }

        private static void toggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.BLACK_WING.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
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
        private static final Map<UUID, Long> LAST_BOOST_TICK = new HashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.BLACK_WING.get();
            if (!skill.isEnabled(player)) {
                StormWing.Server.forceDeactivate(player);
                WhiteWing.Server.forceDeactivate(player);
                PlatinumWing.Server.forceDeactivate(player);
            }
            skill.toggle(player);
            WingFlightSupport.sync(player, AttachmentTypes.ACTIVATED_BLACK_WING.get(),
                    skill.isEnabled(player), LAST_BOOST_TICK);
        }

        @SubscribePacket
        public static void handleControl(ControlPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!isActive(player)) return;
            WingFlightSupport.applyControl(player, packet.state, LAST_BOOST_TICK);
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.BLACK_WING.get().isEnabled(player)
                    && player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get());
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            WingFlightSupport.forceDeactivateSkill(player, Skills.BLACK_WING.get());
            WingFlightSupport.sync(player, AttachmentTypes.ACTIVATED_BLACK_WING.get(), false, LAST_BOOST_TICK);
        }

        public static void onEntitySwing(ServerPlayer player, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND || !isActive(player)) return;
            WingFlightSupport.broadcastSweep(player, AdvancedWingSweepPacket.WingKind.BLACK);
            WingFlightSupport.fanAttack(player, Skills.BLACK_WING.get());
        }

        private static void tick(ServerPlayer player) {
            WingFlightSupport.tick(player, Skills.BLACK_WING.get(),
                    AttachmentTypes.ACTIVATED_BLACK_WING.get(), LAST_BOOST_TICK);
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
            return PacketTypes.BLACK_WING_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ControlPacket extends Packet<ServerGamePacketListenerImpl, ControlPacket> {
        private static final StreamCodec<ByteBuf, StormWing.State> STATE_CODEC =
                ByteBufCodecs.idMapper(index -> StormWing.State.values()[index], Enum::ordinal);
        public static final StreamCodec<ByteBuf, ControlPacket> CODEC =
                STATE_CODEC.map(ControlPacket::new, packet -> packet.state);
        private final StormWing.State state;

        public ControlPacket(StormWing.State state) {
            this.state = state;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ControlPacket> getPacketType() {
            return PacketTypes.BLACK_WING_CONTROL.get();
        }
    }
}
