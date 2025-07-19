package org.academy.internal.common.ability.builtin.teleport.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SelfTeleport extends Skill {
    public static final Skill INSTANCE = new SelfTeleport();
    public static InputSystem.InputPair KEY_START;
    public static InputSystem.InputPair KEY_END;
    public static Client.Config CONFIG;

    private SelfTeleport() {
        super(SkillNames.SELF_TELEPORT, 2);
    }

    @Override
    public void initClient() {
        AcademyCraftConfig.registerConfigActions(INSTANCE.name, Client.Config.Action.INSTANCE);
        CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(INSTANCE.name);
        if (CONFIG == null) {
            CONFIG = new Client.Config();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, CONFIG);
        }

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
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.SERVER_NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Server {
        @SubscribePacket
        public static void handleTeleport(SelfTeleportPacket packet) {
            ServerPlayer serverPlayer = packet.packetListenerSupplier.get().getPlayer();
            if (packet.hasPosition) {
                EntityDimensions dimensions = serverPlayer.getDimensions(Pose.STANDING);
                double playerHeight = dimensions.height;
                double teleportY = packet.y - (playerHeight / 2.0);
                serverPlayer.teleportTo(packet.x, teleportY, packet.z);
                serverPlayer.resetFallDistance();
                serverPlayer.setDeltaMovement(0, 0.25, 0);

                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }
        }
    }

    private static final class Client {
        public static final String KEY_NAME_START = SkillNames.SELF_TELEPORT + "_start";
        public static final String KEY_NAME_END = SkillNames.SELF_TELEPORT + "_end";
        public static TeleportRenderContext currentContext = null;

        private static void start() {
            if (ClientUtil.hasScreen()) return;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            if (currentContext != null) return;

            currentContext = new TeleportRenderContext(player);
            AbilitySystemClient.registerContext(currentContext);
        }

        private static void end() {
            if (currentContext != null) {
                Vec3 finalTargetPos = currentContext.currentRenderPos;
                currentContext.cleanup();
                if (ClientUtil.hasScreen()) return;
                NetworkManagerClient.sendPacket(new C2SPacket(new SelfTeleportPacket(finalTargetPos.x(), finalTargetPos.y(), finalTargetPos.z())));
            } else {
                if (ClientUtil.hasScreen()) return;
                NetworkManagerClient.sendPacket(new C2SPacket(new SelfTeleportPacket()));
            }
        }

        public static class TeleportRenderContext implements ClientContext {
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
                this.playerDimensions = player.getDimensions(Pose.STANDING);
                this.currentRenderPos = calculateIdealTargetCenterPosFromEyes();
                this.visualRenderPos = this.currentRenderPos;
                this.previewBoxWorld = calculateAABBFromCenter(this.visualRenderPos);
            }

            private Vec3 calculateIdealTargetCenterPosFromEyes() {
                Vec3 eyePos = player.getEyePosition();
                Vec3 lookVec = player.getViewVector(1.0f);
                return eyePos.add(lookVec.scale(distance));
            }

            private AABB calculateAABBFromCenter(Vec3 centerPos) {
                float halfWidth = this.playerDimensions.width / 2.0f;
                float halfHeight = this.playerDimensions.height / 2.0f;
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

                Level level = player.level();
                Vec3 eyePos = player.getEyePosition(event.getPartialTick());
                Vec3 lookVec = player.getViewVector(event.getPartialTick());

                Vec3 logicalTargetPos = this.currentRenderPos;
                boolean foundSafeSpotThisFrame = false;

                for (double d = distance; d >= 0; d -= RAY_TRACE_STEP) {
                    Vec3 testCenterPos = eyePos.add(lookVec.scale(d));
                    AABB testAABB = calculateAABBFromCenter(testCenterPos);

                    if (level.noCollision(null, testAABB)) {
                        logicalTargetPos = testCenterPos;
                        foundSafeSpotThisFrame = true;
                        break;
                    }
                }

                if (!foundSafeSpotThisFrame) {
                    logicalTargetPos = eyePos.add(lookVec.scale(0.1));
                }

                AABB currentAABB = calculateAABBFromCenter(logicalTargetPos);
                if (!level.noCollision(player, currentAABB)) {
                    Vec3 upwardAdjustedPos = logicalTargetPos;
                    boolean foundUpwardSpot = false;
                    for (int i = 0; i < MAX_UPWARD_CHECKS; i++) {
                        upwardAdjustedPos = upwardAdjustedPos.add(0, STEP_HEIGHT, 0);
                        AABB upwardAABB = calculateAABBFromCenter(upwardAdjustedPos);
                        if (level.noCollision(player, upwardAABB)) {
                            logicalTargetPos = upwardAdjustedPos;
                            foundUpwardSpot = true;
                            break;
                        }
                    }
                    if (!foundUpwardSpot) {
                        logicalTargetPos = this.currentRenderPos;
                    }
                }

                double factor = ClientUtil.animationFactor(1.25);
                this.currentRenderPos = logicalTargetPos;
                this.visualRenderPos = this.visualRenderPos.lerp(this.currentRenderPos, factor);
                this.previewBoxWorld = calculateAABBFromCenter(this.visualRenderPos);

                MatrixStack matrixStack = event.getMatrixStack();
                Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
                MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                Vec3 camPos = camera.getPosition();

                matrixStack.pushPose();
                matrixStack.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
                LineBoxRenderer.renderWireframeBox(matrixStack, bufferSource, this.previewBoxWorld, 1f, 1f, 1f, 1f);
                matrixStack.popPose();
            }

            public void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (SelfTeleport.Client.currentContext == this) {
                    SelfTeleport.Client.currentContext = null;
                }
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements IConfigAction<Config> {
                public static final IConfigAction<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull SelfTeleport.Client.Config deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                    return gson.fromJson(jsonElement, Config.class);
                }

                @Override
                public @NotNull JsonElement serialize(@NotNull SelfTeleport.Client.Config configInstance, @NotNull Gson gson) {
                    return gson.toJsonTree(configInstance);
                }

                @Override
                public @NotNull SelfTeleport.Client.Config getDefaultConfig() {
                    return new Config();
                }

                @Override
                public @NotNull Class<Config> getConfigClass() {
                    return Config.class;
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SelfTeleportPacket extends IPacket<ServerGamePacketListenerImpl> {
        public boolean hasPosition;
        public double x, y, z;

        public SelfTeleportPacket() {
            this.hasPosition = false;
        }

        public SelfTeleportPacket(double x, double y, double z) {
            this.hasPosition = true;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            this.hasPosition = buf.readBoolean();
            if (this.hasPosition) {
                this.x = buf.readDouble();
                this.y = buf.readDouble();
                this.z = buf.readDouble();
            }
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeBoolean(this.hasPosition);
            if (this.hasPosition) {
                buf.writeDouble(this.x);
                buf.writeDouble(this.y);
                buf.writeDouble(this.z);
            }
        }
    }
}