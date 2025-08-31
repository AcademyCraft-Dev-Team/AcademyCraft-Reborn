package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

public final class StormWing extends Skill {
    public StormWing() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL4)
                .dependsOn(Skills.VECTOR_REFLECTION)
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(StormWingEffectRenderer.getInstance());
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_B)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(
                                        Set.of(
                                                0
                                        )
                                )
                        )
                )
        ), Client::toggle);
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.STORM_WING + "_toggle";
        public static Config CONFIG = new Config();

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null && mc.player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get())) {
                Map<Integer, Integer> keyStates = InputSystem.KEYBOARD_STATE;

                boolean front = keyStates.containsKey(GLFW_KEY_W) && keyStates.get(GLFW_KEY_W) != GLFW.GLFW_RELEASE;
                boolean back = keyStates.containsKey(GLFW_KEY_S) && keyStates.get(GLFW_KEY_S) != GLFW.GLFW_RELEASE;
                boolean left = keyStates.containsKey(GLFW_KEY_A) && keyStates.get(GLFW_KEY_A) != GLFW.GLFW_RELEASE;
                boolean right = keyStates.containsKey(GLFW_KEY_D) && keyStates.get(GLFW_KEY_D) != GLFW.GLFW_RELEASE;

                Set<State> states = new HashSet<>();

                if (front && !back) states.add(State.FRONT);
                else if (back && !front) states.add(State.BACK);

                if (left && !right) states.add(State.LEFT);
                else if (right && !left) states.add(State.RIGHT);

                if (states.isEmpty()) states.add(State.KEEP);

                for (State state : states) AcademyCraftClient.sendPacket(new C2SPacket(new ControlPacket(state)));
            }
        }

        public static void toggle() {
            AcademyCraftClient.sendPacket(new C2SPacket(new TogglePacket()));
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull StormWing.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public @NotNull Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var type = AttachmentTypes.ACTIVATED_STORM_WING.get();
            var activated = player.getData(type);
            player.setData(type, !activated);
            player.syncData(type);
        }

        @SubscribePacket
        public static void handleControl(ControlPacket packet) {
            State state = packet.state;
            ServerPlayer player = packet.getPacketListener().player;
            if (isActive(player)) {
                switch (state) {
                    case FRONT: {
                        Vec3 vec3 = player.getLookAngle().add(0, 0.35, 0).scale(0.2);
                        player.push(vec3.x, vec3.y * 1.5, vec3.z);
                        break;
                    }
                    case BACK: {
                        Vec3 vec3 = player.getLookAngle().add(0, -0.35, 0).scale(-0.2);
                        player.push(vec3.x, vec3.y, vec3.z);
                        break;
                    }
                    case LEFT: {
                        Vec3 look = player.getLookAngle();
                        Vec3 left = new Vec3(look.z, (-look.y + 0.15), -look.x).scale(0.2);
                        player.push(left.x, left.y, left.z);
                        break;
                    }
                    case RIGHT: {
                        Vec3 look = player.getLookAngle();
                        Vec3 right = new Vec3(-look.z, (-look.y + 0.15), look.x).scale(0.2);
                        player.push(right.x, right.y, right.z);
                        break;
                    }
                    case KEEP: {
                        if (Math.abs(player.getDeltaMovement().y) > 0.25) {
                            player.setDeltaMovement(player.getDeltaMovement().multiply(0.995, 0.685, 0.995));
                        } else {
                            player.setDeltaMovement(player.getDeltaMovement().multiply(0.995, 0, 0.995));
                        }
                        player.resetFallDistance();
                        break;
                    }
                }
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }

        public static boolean isActive(ServerPlayer player) {
            return player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get());
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ControlPacket extends Packet<ServerGamePacketListenerImpl> {
        public State state;

        public ControlPacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public ControlPacket(State state) {
            super(null);
            this.state = state;
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            state = State.values()[buf.readVarInt()];
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeVarInt(state.ordinal());
        }

        @Override
        public @NotNull PacketType<ServerGamePacketListenerImpl, ? extends Packet<ServerGamePacketListenerImpl>> getPacketType() {
            return PacketTypes.STORM_WING_CONTROL.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends EmptyPacket<ServerGamePacketListenerImpl> {
        public TogglePacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public TogglePacket() {
            super(null);
        }

        @Override
        public @NotNull PacketType<ServerGamePacketListenerImpl, ? extends Packet<ServerGamePacketListenerImpl>> getPacketType() {
            return PacketTypes.STORM_WING_TOGGLE.get();
        }
    }

    public enum State {
        FRONT, BACK, RIGHT, LEFT, KEEP
    }
}