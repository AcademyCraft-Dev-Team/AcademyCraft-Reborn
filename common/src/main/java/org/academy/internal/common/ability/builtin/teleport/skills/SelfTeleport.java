package org.academy.internal.common.ability.builtin.teleport.skills;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.renderer.CameraRenderer;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.api.server.util.ServerUtil;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SelfTeleport extends Skill {
    public static final Skill INSTANCE = new SelfTeleport();
    public static final String KEY_NAME_START = "self_teleport.start";
    public static final String KEY_NAME_END = "self_teleport.end";
    public static final float DISTANCE = 10F;
    public static InputSystem.InputPair KEY_START;
    public static InputSystem.InputPair KEY_END;

    private SelfTeleport() {
        super(SkillNames.SELF_TELEPORT, 2);
    }

    @Override
    public void initClient() {
        KEY_START = AcademyCraftClient.CLIENT_CONFIG.getKey(KEY_NAME_START,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Z)),
                        GLFW.GLFW_PRESS,
                        new LinkedHashSet<>()
                )));
        KEY_END = AcademyCraftClient.CLIENT_CONFIG.getKey(KEY_NAME_END,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Z)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>()
                )));

        InputSystem.addKeyBinding(KEY_NAME_START, KEY_START, Client::start);
        InputSystem.addKeyBinding(KEY_NAME_END, KEY_END, Client::end);
    }

    @Override
    public void initServer(MinecraftServer server) {
        try {
            NetworkSystemServer.registerC2SPacketHandler(
                    Packets.C2S_SELF_TELEPORT,
                    Server.class.getDeclaredMethod("handleTeleport", ServerPlayer.class),
                    objects -> Server.handleTeleport((ServerPlayer) objects[0])
            );
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Server {
        private static void handleTeleport(ServerPlayer serverPlayer) {
            if (ServerUtil.lacksSkill(serverPlayer.getUUID(), INSTANCE)) return;
            Vec3 lookDirection = serverPlayer.getLookAngle();
            Vec3 targetPosition = serverPlayer.position().add(lookDirection.scale(DISTANCE));
            serverPlayer.teleportTo(targetPosition.x, targetPosition.y, targetPosition.z);
            serverPlayer.resetFallDistance();
        }
    }

    private static final class Client {
        private static final Minecraft mc = Minecraft.getInstance();
        private static final ClientLevel level = mc.level;
        private static final LocalPlayer localPlayer = mc.player;
        private static final CameraRenderer CAMERA_RENDERER = (poseStack, f, l, bl, camera, gameRenderer, lightTexture, matrix4f, ci) -> {
            if (level != null && localPlayer != null) {
                poseStack.pushPose();
                final AABB aabb = new AABB(0, 0, 0, 1, 2, 1);

                final Vec3 lookVec = localPlayer.getLookAngle();
                final Vec3 offsetVec = lookVec.scale(DISTANCE);

                RenderUtil.applyCameraOffset(poseStack, Minecraft.getInstance().options.getCameraType(), lookVec);
                RenderUtil.applyOffset(poseStack, offsetVec);

                final RenderBuffers renderBuffers = mc.renderBuffers();

                poseStack.translate(-0.5f, -1f, -0.5f);
                RenderUtil.BoxRenderer.renderWireframeBox(poseStack, renderBuffers.bufferSource(), aabb, 1f, 1f, 1f, 1f);
                poseStack.popPose();
            }
        };

        private static void start() {
            if (ClientUtil.hasScreen() || ClientUtil.lacksSkill(INSTANCE)) return;
            RendererManager.CAMERA_RENDERER_MAP.put(SkillNames.SELF_TELEPORT, Client.CAMERA_RENDERER);
        }

        private static void end() {
            RendererManager.CAMERA_RENDERER_MAP.remove(SkillNames.SELF_TELEPORT);
            if (ClientUtil.hasScreen() || ClientUtil.lacksSkill(INSTANCE)) return;
            NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_SELF_TELEPORT, new FriendlyByteBuf(Unpooled.buffer())));
        }
    }
}