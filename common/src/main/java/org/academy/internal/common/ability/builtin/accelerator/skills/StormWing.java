package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.tick.ClientTickEvent;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.annotation.PacketHandler;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.world.entity.player.PlayerSyncSkillData;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public class StormWing extends Skill {
    public static final Skill INSTANCE = new StormWing();
    public static final String TAG_KEY = "activated_storm_wing";

    private StormWing() {
        super(SkillNames.STORM_WING, 4);
    }

    @Override
    public void initClient() {
        RendererManager.EFFECT_RENDERER_MAP.add(StormWingEffectRenderer.INSTANCE);
        AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(INSTANCE.name, Client.CONFIG);
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
        AcademyCraft.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.SERVER_PACKET_HANDLER_CLASSES.add(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.STORM_WING + "_toggle";
        public static final StormWingClientConfig CONFIG = new StormWingClientConfig();

        @SubscribeEvent
        public static void tick(ClientTickEvent event) {
            if (Minecraft.getInstance().level != null) {
                boolean handled = false;
                for (Integer key : InputSystem.KEYBOARD_STATE.keySet()) {
                    Integer state = InputSystem.KEYBOARD_STATE.get(key);
                    switch (key) {
                        case GLFW.GLFW_KEY_W:
                            if (state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT) {
                                front();
                                handled = true;
                            }
                            break;
                        case GLFW.GLFW_KEY_S:
                            if (state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT) {
                                back();
                                handled = true;
                            }
                            break;
                        case GLFW.GLFW_KEY_A:
                            if (state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT) {
                                left();
                                handled = true;
                            }
                            break;
                        case GLFW.GLFW_KEY_D:
                            if (state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT) {
                                right();
                                handled = true;
                            }
                            break;
                    }
                }
                if (!handled) {
                    keep();
                }
            }
        }

        public static void toggle() {
            NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_STORM_WING_TOGGLE));
        }

        public static void front() {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().screen == null) {
                NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_STORM_WING_FRONT));
            }
        }

        public static void back() {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().screen == null) {
                NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_STORM_WING_BACK));
            }
        }

        public static void left() {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().screen == null) {
                NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_STORM_WING_LEFT));
            }
        }

        public static void right() {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().screen == null) {
                NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_STORM_WING_RIGHT));
            }
        }

        public static void keep() {
            NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_STORM_WING_KEEP));
        }

        public static final class StormWingClientConfig extends SkillClientConfig.KeyBindingConfig {
            private StormWingClientConfig() {
            }
        }
    }

    @SuppressWarnings("unused")
    public static final class Server {
        @PacketHandler(packet = Packets.C2S_STORM_WING_TOGGLE)
        public static void handleToggle(ServerPlayer player) {
            SynchedEntityData synchedEntityData = player.getEntityData();
            CompoundTag compoundTag = synchedEntityData.get(PlayerSyncSkillData.SKILL_DATA);
            CompoundTag newTag = new CompoundTag();
            newTag.putBoolean(TAG_KEY, !compoundTag.getBoolean(TAG_KEY));
            synchedEntityData.set(PlayerSyncSkillData.SKILL_DATA, newTag);
        }

        @PacketHandler(packet = Packets.C2S_STORM_WING_FRONT)
        public static void handleFront(ServerPlayer player) {
            if (isActive(player)) {
                Vec3 vec3 = player.getLookAngle().add(0, 0.35, 0).scale(0.2);
                player.push(vec3.x, vec3.y * 1.5, vec3.z);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }

        @PacketHandler(packet = Packets.C2S_STORM_WING_BACK)
        public static void handleBack(ServerPlayer player) {
            if (isActive(player)) {
                Vec3 vec3 = player.getLookAngle().add(0, -0.35, 0).scale(-0.2);
                player.push(vec3.x, vec3.y, vec3.z);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }

        @PacketHandler(packet = Packets.C2S_STORM_WING_RIGHT)
        public static void handleRight(ServerPlayer player) {
            if (isActive(player)) {
                Vec3 look = player.getLookAngle();
                Vec3 right = new Vec3(-look.z, (-look.y + 0.15), look.x).scale(0.2);
                player.push(right.x, right.y, right.z);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }

        @PacketHandler(packet = Packets.C2S_STORM_WING_LEFT)
        public static void handleLeft(ServerPlayer player) {
            if (isActive(player)) {
                Vec3 look = player.getLookAngle();
                Vec3 left = new Vec3(look.z, (-look.y + 0.15), -look.x).scale(0.2);
                player.push(left.x, left.y, left.z);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }

        @PacketHandler(packet = Packets.C2S_STORM_WING_KEEP)
        public static void handleKeep(ServerPlayer player) {
            if (isActive(player)) {
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.995, 0.125, 0.995));
                player.resetFallDistance();
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }

        public static boolean isActive(ServerPlayer player) {
            return player.getEntityData().get(PlayerSyncSkillData.SKILL_DATA).getBoolean(StormWing.TAG_KEY);
        }
    }
}