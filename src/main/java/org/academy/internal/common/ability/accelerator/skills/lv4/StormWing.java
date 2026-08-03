package org.academy.internal.common.ability.accelerator.skills.lv4;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv2.VectorAccel;
import org.academy.internal.common.ability.accelerator.skills.lv5.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlatinumWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.WhiteWing;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.mixin.common.EntitySharedFlagInvoker;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.lwjgl.glfw.GLFW.*;

public final class StormWing extends Skill {
    public static final float RESERVED_CP = 20.0f;

    public StormWing() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(RESERVED_CP)
                .iterationTicks(30)
                .maxStacks(1)
                .dependsOn(Skills.VECTOR_REFLECTION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Vector Reflection", "academy:vector_reflection"))
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(StormWingEffectRenderer.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        GLFW_KEY_N,
                        GLFW_RELEASE,
                        GLFW_MOD_ALT
                )
        ), ctx -> Client.toggle());
        ToggleStatusHud.registerStateProvider(Skills.STORM_WING.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get());
        });
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum State {
        FRONT, BACK, RIGHT, LEFT, KEEP, BOOST
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.STORM_WING.get(),
                        List.of(VectorReflection.Client.SKILL_INFO),
                        R.textures.ability.accelerator.skill.storm_wing.icon,
                        130, 20
                )
        );

        public static final String KEY_NAME_TOGGLE = SkillNames.STORM_WING + "_toggle";
        public static Config CONFIG = new Config();

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            var mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null && mc.player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get())) {
                if (mc.gui.screen() == null
                        && InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_SPACE)) {
                    MisakaNetworkClient.send(new ControlPacket(State.BOOST));
                    return;
                }
                var front = InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_W);
                var back = InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_S);
                var left = InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_A);
                var right = InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_D);

                var states = new HashSet<State>();

                var canMove = mc.gui.screen() == null;

                if (canMove) {
                    if (front && !back) states.add(State.FRONT);
                    else if (back && !front) states.add(State.BACK);
                    if (left && !right) states.add(State.LEFT);
                    else if (right && !left) states.add(State.RIGHT);
                }

                if (states.isEmpty()) states.add(State.KEEP);

                for (var state : states) MisakaNetworkClient.send(new ControlPacket(state));
            }
        }

        public static void toggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.STORM_WING.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public StormWing.Client.Config getDefault() {
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
        private static final long BOOST_GRACE_TICKS = 5;

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.STORM_WING.get();
            if (!skill.isEnabled(player)) {
                BlackWing.Server.forceDeactivate(player);
                WhiteWing.Server.forceDeactivate(player);
                PlatinumWing.Server.forceDeactivate(player);
            }
            skill.toggle(player);
            sync(player);
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            var skill = Skills.STORM_WING.get();
            var data = skill.getRuntimeData(player).orElse(null);
            if (data != null && data.isEnabled()) {
                var system = AbilitySystemServer.getSystem(player);
                system.toggleSkill(player.getUUID(), skill.getKeyString());
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            }
            sync(player);
        }

        @SubscribePacket
        public static void handleControl(ControlPacket packet) {
            var state = packet.getState();
            var player = packet.getPacketListener().getPlayer();
            if (isActive(player)) {
                if (state == State.BOOST) {
                    LAST_BOOST_TICK.put(player.getUUID(), player.level().getGameTime());
                }
                switch (state) {
                    case FRONT -> {
                        var vec3 = player.getLookAngle().add(0, 0.35, 0).scale(0.2);
                        player.push(vec3.x, vec3.y * 1.5, vec3.z);
                    }
                    case BACK -> {
                        var vec3 = player.getLookAngle().add(0, -0.35, 0).scale(-0.2);
                        player.push(vec3.x, vec3.y, vec3.z);
                    }
                    case LEFT -> {
                        var look = player.getLookAngle();
                        var left = new Vec3(look.z, (-look.y + 0.15), -look.x).scale(0.2);
                        player.push(left.x, left.y, left.z);
                    }
                    case RIGHT -> {
                        var look = player.getLookAngle();
                        var right = new Vec3(-look.z, (-look.y + 0.15), look.x).scale(0.2);
                        player.push(right.x, right.y, right.z);
                    }
                    case KEEP -> {
                        if (Math.abs(player.getDeltaMovement().y) > 0.25) {
                            player.setDeltaMovement(player.getDeltaMovement().multiply(0.995, 0.685, 0.995));
                        } else {
                            player.setDeltaMovement(player.getDeltaMovement().multiply(0.995, 0, 0.995));
                        }
                        player.resetFallDistance();
                    }
                    case BOOST -> {
                        var vec3 = player.getLookAngle().scale(2.0);
                        player.push(vec3.x, vec3.y, vec3.z);
                        player.resetFallDistance();
                    }
                }
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.STORM_WING.get().isEnabled(player)
                    && player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get());
        }

        private static void sync(ServerPlayer player) {
            var active = Skills.STORM_WING.get().isEnabled(player)
                    && player.isAlive() && !player.hasDisconnected();
            var type = AttachmentTypes.ACTIVATED_STORM_WING.get();
            var wasActive = player.getData(type);
            if (wasActive != active) {
                player.setData(type, active);
                player.syncData(type);
            }
            if (!active) {
                // This path runs for every player tick. Never clear fall distance here.
                LAST_BOOST_TICK.remove(player.getUUID());
                if (wasActive) {
                    ((EntitySharedFlagInvoker) player).academy$setSharedFlag(7, false);
                }
            }
        }

        private static void tick(ServerPlayer player) {
            var skill = Skills.STORM_WING.get();
            var active = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (active) {
                active = AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                        player.getUUID(), RESERVED_CP, skill);
                if (!active && skill.isEnabled(player)) skill.toggle(player);
            }
            sync(player);
            if (!isActive(player)) return;
            var boostTick = LAST_BOOST_TICK.get(player.getUUID());
            ((EntitySharedFlagInvoker) player).academy$setSharedFlag(
                    7,
                    boostTick != null && player.level().getGameTime() - boostTick <= BOOST_GRACE_TICKS
            );
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
    public static final class ControlPacket extends Packet<ServerGamePacketListenerImpl, ControlPacket> {
        public static final StreamCodec<ByteBuf, State> STATE_CODEC = ByteBufCodecs.idMapper(i -> State.values()[i], Enum::ordinal);
        public static final StreamCodec<ByteBuf, ControlPacket> CODEC = STATE_CODEC.map(ControlPacket::new, ControlPacket::getState);

        private final State state;

        public ControlPacket(State state) {
            this.state = state;
        }

        public State getState() {
            return state;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ControlPacket> getPacketType() {
            return PacketTypes.STORM_WING_CONTROL.get();
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
            return PacketTypes.STORM_WING_TOGGLE.get();
        }
    }
}
