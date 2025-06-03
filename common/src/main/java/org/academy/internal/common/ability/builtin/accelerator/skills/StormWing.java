package org.academy.internal.common.ability.builtin.accelerator.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.config.IClientConfigActions;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;
import org.academy.internal.common.world.entity.player.PlayerSyncData;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class StormWing extends Skill {
    public static final Skill INSTANCE = new StormWing();
    public static final String TAG_KEY = "activated_storm_wing";

    private StormWing() {
        super(SkillNames.STORM_WING, 4, List.of(VectorReflection.INSTANCE));
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(StormWingEffectRenderer.INSTANCE);
        AcademyCraftClientConfig.registerConfigActions(INSTANCE.name, new Client.StormWingClientConfigData());
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(
                INSTANCE.name,
                Client.StormWingClientConfigData.class
        );
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.StormWingClientConfigData();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CONFIG);
        }

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE_ACTION, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE_ACTION,
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
        AcademyCraft.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Accelerator.INSTANCE, INSTANCE, List.of(VectorReflection.Client.SKILL_INFO),
                        TextureResources.TEXTURE_STORM_WING_ICON, 150, 70.25f);
        public static final String KEY_NAME_TOGGLE_ACTION = SkillNames.STORM_WING + "_toggle_action";
        public static StormWingClientConfigData CONFIG = new StormWingClientConfigData();

        @SubscribeEvent
        public static void tick(ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null && mc.player.getEntityData().get(PlayerSyncData.DATA).getBoolean(TAG_KEY)) {
                State state = State.KEEP;
                for (Integer key : InputSystem.KEYBOARD_STATE.keySet()) {
                    Integer keyState = InputSystem.KEYBOARD_STATE.get(key);
                    switch (key) {
                        case GLFW.GLFW_KEY_W:
                            if (keyState == GLFW.GLFW_PRESS || keyState == GLFW.GLFW_REPEAT) {
                                state = State.FRONT;
                            }
                            break;
                        case GLFW.GLFW_KEY_S:
                            if (keyState == GLFW.GLFW_PRESS || keyState == GLFW.GLFW_REPEAT) {
                                state = State.BACK;
                            }
                            break;
                        case GLFW.GLFW_KEY_A:
                            if (keyState == GLFW.GLFW_PRESS || keyState == GLFW.GLFW_REPEAT) {
                                state = State.LEFT;
                            }
                            break;
                        case GLFW.GLFW_KEY_D:
                            if (keyState == GLFW.GLFW_PRESS || keyState == GLFW.GLFW_REPEAT) {
                                state = State.RIGHT;
                            }
                            break;
                    }
                }
                NetworkSystemClient.sendPacket(new C2SPacket(new ControlPacket(state)));
            }
        }

        public static void toggle() {
            NetworkSystemClient.sendPacket(new C2SPacket(new TogglePacket()));
        }

        public static class StormWingClientConfigData implements IClientConfigActions<StormWingClientConfigData> {
            @SerializedName("keyBindings")
            private final Map<String, InputSystem.InputPair> keyBindings = new HashMap<>();

            public InputSystem.InputPair getKeyBinding(String name, InputSystem.InputPair defaultConfig) {
                if (!keyBindings.containsKey(name)) {
                    setKeyBinding(name, defaultConfig);
                }
                return keyBindings.get(name);
            }
            public void setKeyBinding(String name, InputSystem.InputPair keyBinding) {
                this.keyBindings.put(name, keyBinding);
            }

            @Override
            public @NotNull StormWingClientConfigData deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, StormWingClientConfigData.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull StormWingClientConfigData configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull StormWingClientConfigData getDefaultConfig() {
                return new StormWingClientConfigData();
            }

            @Override
            public @NotNull Class<StormWingClientConfigData> getConfigClass() {
                return StormWingClientConfigData.class;
            }
        }
    }

    public static final class Server {
        @SuppressWarnings("DataFlowIssue")
        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().player;
            SynchedEntityData synchedEntityData = player.getEntityData();
            CompoundTag compoundTag = synchedEntityData.get(PlayerSyncData.DATA);
            CompoundTag newTag = new CompoundTag();
            compoundTag.getAllKeys().forEach(key -> newTag.put(key, compoundTag.get(key)));
            newTag.putBoolean(TAG_KEY, !compoundTag.getBoolean(TAG_KEY));
            synchedEntityData.set(PlayerSyncData.DATA, newTag);
        }

        @SubscribePacket
        public static void handleControl(ControlPacket packet) {
            State state = packet.state;
            ServerPlayer player = packet.packetListenerSupplier.get().player;
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
            return player.getEntityData().get(PlayerSyncData.DATA).getBoolean(StormWing.TAG_KEY);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ControlPacket extends IPacket<ServerGamePacketListenerImpl> {
        public State state;

        public ControlPacket() {
        }

        public ControlPacket(State state) {
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
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }

    public enum State {
        FRONT, BACK, RIGHT, LEFT, KEEP
    }
}