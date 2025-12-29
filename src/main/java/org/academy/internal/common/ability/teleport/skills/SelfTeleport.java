package org.academy.internal.common.ability.teleport.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SelfTeleport extends Skill {
    public static InputSystem.@Nullable InputPair KEY_START;
    public static InputSystem.@Nullable InputPair KEY_END;
    public static Client.@Nullable Config CONFIG;

    public SelfTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        KEY_START = CONFIG.getKeyBinding(Client.KEY_NAME_START,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Z)),
                        GLFW.GLFW_PRESS,
                        new LinkedHashSet<>()
                )));
        KEY_END = CONFIG.getKeyBinding(Client.KEY_NAME_END,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Z)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>()
                )));

        InputSystem.addKeyBinding(Client.KEY_NAME_START, KEY_START, Client::start);
        InputSystem.addKeyBinding(Client.KEY_NAME_END, KEY_END, Client::end);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Server {
        @SubscribePacket
        public static void handleTeleport(SelfTeleportPacket packet) {
            var serverPlayer = packet.getPacketListener().getPlayer();
            var pos = packet.getPosition();
                var dimensions = serverPlayer.getDimensions(Pose.STANDING);
                var playerHeight = dimensions.height();
                var teleportY = pos.y() - (playerHeight / 2.0);
                serverPlayer.teleportTo(pos.x(), teleportY, pos.z());
                serverPlayer.resetFallDistance();
                serverPlayer.setDeltaMovement(0, 0.25, 0);
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
    }

    private static final class Client {
        public static final String KEY_NAME_START = SkillNames.SELF_TELEPORT + "_start";
        public static final String KEY_NAME_END = SkillNames.SELF_TELEPORT + "_end";
        @Nullable
        public static TeleportRenderContext currentContext = null;

        private static void start() {
            if (ClientUtil.hasScreen()) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (currentContext != null) return;

            currentContext = new TeleportRenderContext(player);
            AbilitySystemClient.registerContext(currentContext);
        }

        private static void end() {
            if (currentContext != null) {
                var finalTargetPos = currentContext.currentRenderPos;
                currentContext.cleanup();
                if (ClientUtil.hasScreen()) return;
                MisakaNetworkClient.sendPacket(new SelfTeleportPacket(finalTargetPos));
            }
        }

        public static class TeleportRenderContext extends ClientContext {
            private double distance = 10;
            private final LocalPlayer player;
            public Vec3 currentRenderPos;
            private Vec3 visualRenderPos;
            private AABB previewBoxWorld;
            private static final float STEP_HEIGHT = 0.125f;
            private static final int MAX_UPWARD_CHECKS = 16;
            private static final float RAY_TRACE_STEP = 1;
            private final EntityDimensions playerDimensions;

            public TeleportRenderContext(LocalPlayer player) {
                this.player = player;
                playerDimensions = player.getDimensions(Pose.STANDING);
                currentRenderPos = calculateIdealTargetCenterPosFromEyes();
                visualRenderPos = currentRenderPos;
                previewBoxWorld = calculateAABBFromCenter(visualRenderPos);
            }

            private Vec3 calculateIdealTargetCenterPosFromEyes() {
                var eyePos = player.getEyePosition();
                var lookVec = player.getViewVector(1.0f);
                return eyePos.add(lookVec.scale(distance));
            }

            private AABB calculateAABBFromCenter(Vec3 centerPos) {
                var halfWidth = playerDimensions.width() / 2.0f;
                var halfHeight = playerDimensions.height() / 2.0f;
                return new AABB(centerPos.x - halfWidth,
                        centerPos.y - halfHeight,
                        centerPos.z - halfWidth,
                        centerPos.x + halfWidth,
                        centerPos.y + halfHeight,
                        centerPos.z + halfWidth);
            }

            @SubscribeEvent
            public void onScroll(MouseScrollEvent event) {
                distance += event.yOffset;
                distance = Math.min(20, Math.max(0, distance));
                event.setCanceled(true);
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (player.isRemoved()) {
                    cleanup();
                    return;
                }

                var level = player.level();
                var eyePos = player.getEyePosition(event.getPartialTick());
                var lookVec = player.getViewVector(event.getPartialTick());

                var logicalTargetPos = currentRenderPos;
                var foundSafeSpotThisFrame = false;

                for (var d = distance; d >= 0; d -= RAY_TRACE_STEP) {
                    var testCenterPos = eyePos.add(lookVec.scale(d));
                    var testAABB = calculateAABBFromCenter(testCenterPos);

                    if (level.noCollision(null, testAABB)) {
                        logicalTargetPos = testCenterPos;
                        foundSafeSpotThisFrame = true;
                        break;
                    }
                }

                if (!foundSafeSpotThisFrame) {
                    logicalTargetPos = eyePos.add(lookVec.scale(0.1));
                }

                var currentAABB = calculateAABBFromCenter(logicalTargetPos);
                if (!level.noCollision(player, currentAABB)) {
                    var upwardAdjustedPos = logicalTargetPos;
                    var foundUpwardSpot = false;
                    for (var i = 0; i < MAX_UPWARD_CHECKS; i++) {
                        upwardAdjustedPos = upwardAdjustedPos.add(0, STEP_HEIGHT, 0);
                        var upwardAABB = calculateAABBFromCenter(upwardAdjustedPos);
                        if (level.noCollision(player, upwardAABB)) {
                            logicalTargetPos = upwardAdjustedPos;
                            foundUpwardSpot = true;
                            break;
                        }
                    }
                    if (!foundUpwardSpot) {
                        logicalTargetPos = currentRenderPos;
                    }
                }

                var factor = ClientUtil.animationFactor(1.25);
                currentRenderPos = logicalTargetPos;
                visualRenderPos = visualRenderPos.lerp(currentRenderPos, factor);
                previewBoxWorld = calculateAABBFromCenter(visualRenderPos);

                var matrixStack = event.getMatrixStack();
                var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
                var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                var camPos = camera.position();

                matrixStack.pushPose();
                matrixStack.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
                LineBoxRenderer.renderWireframeBox(matrixStack, bufferSource, previewBoxWorld, 1f, 1f, 1f, 1f);
                matrixStack.popPose();
            }

            public void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (currentContext == this) {
                    currentContext = null;
                }
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public SelfTeleport.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SelfTeleportPacket extends Packet<ServerGamePacketListenerImpl, SelfTeleportPacket> {
        public static final StreamCodec<ByteBuf, SelfTeleportPacket> CODEC = Vec3.STREAM_CODEC
                .map(SelfTeleportPacket::new, SelfTeleportPacket::getPosition);

        private final Vec3 position;

        public SelfTeleportPacket(Vec3 position) {
            this.position = position;
        }

        public Vec3 getPosition() {
            return position;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SelfTeleportPacket> getPacketType() {
            return PacketTypes.SELF_TELEPORT.get();
        }
    }
}