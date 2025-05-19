package org.academy.internal.common.ability.builtin.accelerator.skills;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.ClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.renderer.CameraRenderer;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.*;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.vanilla.EnvType;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.tick.ServerTickEvent;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VectorAccel extends Skill {
    public static final int MAX_CHARGE_TICKS = 40;
    public static final Skill INSTANCE = new VectorAccel();

    static {
        NetworkSystem.registerPacketType(DashPacket.class);
    }

    private VectorAccel() {
        super(SkillNames.VECTOR_ACCEL, 1);
    }

    @Override
    public void initClient() {
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(name, new Client.VecAccelClientConfig());
        InputSystem.addKeyBinding(Client.KEY_NAME_CHARGE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CHARGE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_C)),
                                GLFW.GLFW_PRESS,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::onChargeStart);
        InputSystem.addKeyBinding(Client.KEY_NAME_RELEASE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_RELEASE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_C)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::onChargeRelease);
        RendererManager.registerCameraRenderer(Client.CAMERA_RENDERER);
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystem.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static Client.VecAccelClientConfig CONFIG = new Client.VecAccelClientConfig();
        public static Context currentContext = null;
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Accelerator.INSTANCE, INSTANCE, List.of(),
                        new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/accelerator/skill/vec_accel/icon.png"), 20, 40);

        public static final String KEY_NAME_CHARGE = SkillNames.VECTOR_ACCEL + "_charge";
        public static final String KEY_NAME_RELEASE = SkillNames.VECTOR_ACCEL + "_release";

        private static final CameraRenderer CAMERA_RENDERER = (poseStack, partialTick, finishNanoTime, renderBlockOutline, camera, gameRenderer, lightTexture, projectionMatrix) -> {
            if (Client.currentContext == null || Client.currentContext.player == null || Minecraft.getInstance().screen != null) {
                return;
            }

            LocalPlayer player = Client.currentContext.player;
            Vec3 camPos = camera.getPosition();

            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());

            ArrayList<Vec3> worldVertices = new ArrayList<>();
            Vec3 initialSpeedWorld = Client.currentContext.calculateInitSpeed(partialTick);

            float yawLerp = MathUtil.lerpFactorStartEnd(partialTick, player.yRotO, player.getYRot());
            float pitchLerp = MathUtil.lerpFactorStartEnd(partialTick, player.xRotO, player.getXRot());

            Vec3 playerLookWorld = Vec3.directionFromRotation(pitchLerp, yawLerp);
            Vec3 playerEyeWorld = player.getEyePosition(partialTick);

            Vec3 horizontalLookNorm = new Vec3(playerLookWorld.x(), 0, playerLookWorld.z()).normalize();
            Vec3 playerLeftWorld = new Vec3(horizontalLookNorm.z(), 0, -horizontalLookNorm.x());
            Vec3 sideComponent = playerLeftWorld.scale(0.08);
            Vec3 forwardComponentOffset = playerLookWorld.scale(0.12);
            Vec3 verticalOffset = Vec3.directionFromRotation(pitchLerp - 90, yawLerp).normalize().scale(-0.06);

            Vec3 startOffsetFromEye = sideComponent.add(verticalOffset).add(forwardComponentOffset);
            Vec3 currentPosWorld = playerEyeWorld.add(startOffsetFromEye);


            final double dt = 0.02;
            Vec3 currentSpeedWorld = initialSpeedWorld;
            for (int i = 0; i < 100; i++) {
                worldVertices.add(currentPosWorld);
                currentSpeedWorld = currentSpeedWorld.scale(0.98);
                currentPosWorld = currentPosWorld.add(currentSpeedWorld.scale(dt));
                currentSpeedWorld = new Vec3(currentSpeedWorld.x(), currentSpeedWorld.y() - dt * 1.9, currentSpeedWorld.z());
            }

            Matrix4f matrix = poseStack.last().pose();
            float r, g, b;
            float a = 0.6f;

            for (int i = 1; i < worldVertices.size(); i++) {
                Vec3 p1World = worldVertices.get(i - 1);
                Vec3 p2World = worldVertices.get(i);

                Vec3 p1Cam = p1World.subtract(camPos);
                Vec3 p2Cam = p2World.subtract(camPos);

                float alphaMultiplier = (1.0f - (float) i / worldVertices.size() * 0.7f);
                if (!Client.currentContext.canPerformDashClient()) {
                    r = 1f;
                    g = 0.2f;
                    b = 0.2f;
                } else {
                    r = 1f;
                    g = 1f;
                    b = 1f;
                }

                lineBuffer.vertex(matrix, (float) p1Cam.x(), (float) p1Cam.y(), (float) p1Cam.z()).color(r, g, b, a * alphaMultiplier).normal(1, 0, 0).endVertex();
                lineBuffer.vertex(matrix, (float) p2Cam.x(), (float) p2Cam.y(), (float) p2Cam.z()).color(r, g, b, a * alphaMultiplier).normal(1, 0, 0).endVertex();
            }
        };

        public static void onChargeStart() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || Client.currentContext != null || Minecraft.getInstance().screen != null) {
                return;
            }
            Client.currentContext = new Context(player);
            AbilitySystemClient.registerContext(Client.currentContext);
        }

        public static void onChargeRelease() {
            if (Client.currentContext != null) {
                Client.currentContext.release();
            }
        }


        public static final class VecAccelClientConfig extends ClientConfig.KeyBindingConfig {
        }

        public static final class Context implements ClientContext {
            private final LocalPlayer player;
            private boolean released = false;
            private int clientTicker = 0;

            public Context(LocalPlayer player) {
                this.player = player;
            }

            public void release() {
                if (released) return;
                released = true;
                NetworkSystemClient.sendPacket(new C2SPacket(new DashPacket(Math.min(clientTicker, MAX_CHARGE_TICKS))));
                cleanup();
            }

            private void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (Client.currentContext == this) {
                    Client.currentContext = null;
                }
            }

            public Vec3 calculateInitSpeed(float partialTicks) {
                float yawLerp = MathUtil.lerpFactorStartEnd(partialTicks, player.yRotO, player.getYRot());
                float pitchLerp = MathUtil.lerpFactorStartEnd(partialTicks, player.xRotO, player.getXRot());
                Vec3 look = Vec3.directionFromRotation(pitchLerp - 10, yawLerp);
                return look.scale(calculateSpeedScalar());
            }

            private double calculateSpeedScalar() {
                double prog = MathUtil.lerpFactorStartEnd(MathUtil.clamp((float) clientTicker / MAX_CHARGE_TICKS, 0f, 1f), 0.4f, 1.0f);
                return Math.sin(prog) * Server.MAX_VELOCITY_SCALAR;
            }

            public boolean canPerformDashClient() {
                return true;
            }

            @SubscribeEvent
            public void onClientTick(ClientTickEvent event) {
                if (player.isRemoved() || released) {
                    cleanup();
                    return;
                }
                clientTicker++;

                if (clientTicker >= MAX_CHARGE_TICKS) {
                    release();
                }
            }
        }
    }

    public static final class Server {
        public static final double MAX_VELOCITY_SCALAR = 2.5;

        @ClassPacketHandler
        public static void handleDash(DashPacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
            float chargeTicks = packet.chargeTicks;
            float chargeRatio = MathUtil.clamp(chargeTicks / MAX_CHARGE_TICKS, 0.0f, 1.0f);

            double speedScalarProg = MathUtil.lerpFactorStartEnd(chargeRatio, 0.4f, 1.0f);
            double actualSpeedScalar = Math.sin(speedScalarProg) * Server.MAX_VELOCITY_SCALAR;

            Vec3 lookAngle = Vec3.directionFromRotation(player.getXRot() - 10, player.getYRot());
            Vec3 dashVelocity = lookAngle.scale(actualSpeedScalar);

            float durationTicks = 5;

            Context context = new Context(player, dashVelocity, durationTicks);
            AbilitySystemServer.registerContext(context);
            player.fallDistance = 0;
        }

        private static final class Context implements ServerContext {
            private final ServerPlayer player;
            private float remainingTicks;

            public Context(ServerPlayer player, Vec3 dashVelocity, float durationTicks) {
                this.player = player;
                this.remainingTicks = durationTicks;
                player.setDeltaMovement(dashVelocity);
                player.hurtMarked = true;
            }

            @SubscribeEvent
            public void onServerTick(ServerTickEvent event) {
                if (player.isRemoved() || remainingTicks <= 0) {
                    AbilitySystemServer.unregisterContext(this);
                    player.setDeltaMovement(player.getDeltaMovement().x(), 0, player.getDeltaMovement().z());
                    return;
                }

                player.setDeltaMovement(player.getDeltaMovement().scale(0.98));
                player.setDeltaMovement(player.getDeltaMovement().subtract(0, 0.02 * 1.9, 0));

                remainingTicks--;

                if (player.horizontalCollision || player.verticalCollision) {
                    remainingTicks = 0;
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class DashPacket extends IPacket<ServerGamePacketListenerImpl> {
        public int chargeTicks;

        @ReceiverConstructor
        public DashPacket() {
        }

        @SenderConstructor
        public DashPacket(int chargeTicks) {
            this.chargeTicks = chargeTicks;
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            chargeTicks = buf.readVarInt();
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeVarInt(chargeTicks);
        }
    }
}