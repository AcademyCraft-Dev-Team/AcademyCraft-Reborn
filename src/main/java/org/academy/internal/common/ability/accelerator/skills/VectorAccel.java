package org.academy.internal.common.ability.accelerator.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VectorAccel extends Skill {
    public static final long MAX_CHARGE_TIME_MS = 2000;

    public VectorAccel() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL1)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

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
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static Config CONFIG = new Config();
        public static @Nullable Context currentContext = null;

        public static final String KEY_NAME_CHARGE = SkillNames.VECTOR_ACCEL + "_charge";
        public static final String KEY_NAME_RELEASE = SkillNames.VECTOR_ACCEL + "_release";

        public static void onChargeStart() {
            var player = Minecraft.getInstance().player;
            if (player == null || currentContext != null || Minecraft.getInstance().screen != null) {
                return;
            }
            currentContext = new Context(player);
            AbilitySystemClient.registerContext(currentContext);
        }

        public static void onChargeRelease() {
            if (currentContext != null) {
                currentContext.release();
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

                private Action() {
                }

                @Override
                public VectorAccel.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }

        public static final class Context extends ClientContext {
            private final LocalPlayer player;
            private boolean released = false;
            private final long chargeStartTime;
            private float chargeRatio;
            private @Nullable HitResult lastHitResult;
            private final List<Vec3> trajectoryPath = new ArrayList<>();
            private float ringAlpha;
            private Vec3 lastCalculatedDirection = Vec3.ZERO;
            private double distance = 10;

            public Context(LocalPlayer player) {
                this.player = player;
                chargeStartTime = System.nanoTime();
            }

            public void release() {
                if (released) return;
                released = true;
                MisakaNetworkClient.sendPacket(new DashPacket(chargeRatio, lastCalculatedDirection));
                cleanup();
            }

            private void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (currentContext == this) {
                    currentContext = null;
                }
            }

            private Vec3 calculateDashDirection(float partialTick) {
                var mc = Minecraft.getInstance();
                var camera = mc.gameRenderer.getMainCamera();
                var cameraPos = camera.position();
                var lookVec = new Vec3(camera.forwardVector());
                var farPoint = cameraPos.add(lookVec.scale(100.0));

                var hitResult = player.level().clip(new ClipContext(cameraPos, farPoint, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
                var targetPoint = hitResult.getLocation();

                var trajectoryStartPos = player.getPosition(partialTick);
                var direction = targetPoint.subtract(trajectoryStartPos).normalize();

                final var maxDownwardY = -0.5;
                if (direction.y < maxDownwardY) {
                    direction = new Vec3(direction.x, maxDownwardY, direction.z).normalize();
                }

                return direction;
            }

            private Vec3 calculateInitSpeed() {
                return lastCalculatedDirection.scale(calculateSpeedScalar());
            }

            private double calculateSpeedScalar() {
                var prog = Mth.lerp(chargeRatio, 0.4f, 1.0f);
                return Math.sin(prog) * Server.MAX_VELOCITY_SCALAR;
            }

            private void simulatePath(float partialTick) {
                trajectoryPath.clear();
                lastHitResult = null;

                var level = player.level();
                var currentPos = player.getPosition(partialTick);
                var currentVel = calculateInitSpeed();
                var playerBox = player.getBoundingBox();

                for (var i = 0; i < 300; i++) {
                    trajectoryPath.add(currentPos);

                    var collisionBox = playerBox.move(currentPos.vectorTo(currentPos.add(currentVel)));
                    var collisions = level.getEntityCollisions(player, collisionBox);
                    var adjustedVel = currentVel.lengthSqr() == 0.0D ? currentVel : collideWithShapes(currentVel, playerBox, collisions);

                    var nextPos = currentPos.add(adjustedVel);

                    var blockHit = level.clip(new ClipContext(currentPos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    if (blockHit.getType() != HitResult.Type.MISS) {
                        nextPos = blockHit.getLocation();
                    }

                    var searchBox = playerBox.move(nextPos.subtract(currentPos)).inflate(1.0);
                    var entityHit = ProjectileUtil.getEntityHitResult(level, player, currentPos, nextPos, searchBox, e -> !e.isSpectator() && e.isPickable() && !e.is(player), 0.3f);

                    if (entityHit != null) {
                        lastHitResult = entityHit;
                        trajectoryPath.add(entityHit.getLocation());
                        return;
                    }

                    if (blockHit.getType() != HitResult.Type.MISS) {
                        lastHitResult = blockHit;
                        trajectoryPath.add(blockHit.getLocation());
                        return;
                    }

                    currentPos = nextPos;
                    currentVel = adjustedVel.multiply(0.91, 0.98, 0.91);
                    currentVel = currentVel.subtract(0, 0.08, 0);
                }
            }

            private Vec3 collideWithShapes(Vec3 pDeltaMovement, AABB pEntityBB, List<VoxelShape> pShapes) {
                if (pShapes.isEmpty()) {
                    return pDeltaMovement;
                } else {
                    var d0 = pDeltaMovement.x;
                    var d1 = pDeltaMovement.y;
                    var d2 = pDeltaMovement.z;
                    if (d1 != 0.0D) {
                        d1 = Shapes.collide(Direction.Axis.Y, pEntityBB, pShapes, d1);
                        if (d1 != 0.0D) {
                            pEntityBB = pEntityBB.move(0.0D, d1, 0.0D);
                        }
                    }

                    var flag = Math.abs(d0) < Math.abs(d2);
                    if (flag && d2 != 0.0D) {
                        d2 = Shapes.collide(Direction.Axis.Z, pEntityBB, pShapes, d2);
                        if (d2 != 0.0D) {
                            pEntityBB = pEntityBB.move(0.0D, 0.0D, d2);
                        }
                    }

                    if (d0 != 0.0D) {
                        d0 = Shapes.collide(Direction.Axis.X, pEntityBB, pShapes, d0);
                        if (!flag && d0 != 0.0D) {
                            pEntityBB = pEntityBB.move(d0, 0.0D, 0.0D);
                        }
                    }

                    if (!flag && d2 != 0.0D) {
                        d2 = Shapes.collide(Direction.Axis.Z, pEntityBB, pShapes, d2);
                    }

                    return new Vec3(d0, d1, d2);
                }
            }

            private void renderTrajectoryPath(MatrixStack matrixStack, MultiBufferSource.BufferSource bufferSource, Camera camera) {
                if (trajectoryPath.size() < 2) return;

                var buffer = bufferSource.getBuffer(RenderTypes.lightning());

                for (var i = 0; i < trajectoryPath.size() - 1; i++) {
                    var p1 = trajectoryPath.get(i);
                    var p2 = trajectoryPath.get(i + 1);

                    var dir = p2.subtract(p1).normalize();
                    var cross = dir.cross(p1.subtract(camera.position())).normalize();

                    var width = 0.025f * (1 - (float) i / trajectoryPath.size());
                    var v1 = p1.add(cross.scale(width));
                    var v2 = p1.add(cross.scale(-width));
                    var v3 = p2.add(cross.scale(-width));
                    var v4 = p2.add(cross.scale(width));

                    var alpha = 0.4f * (1 - (float) i / trajectoryPath.size());

                    buffer.addVertex(matrixStack.lastMatrix(), (float) v1.x, (float) v1.y, (float) v1.z).setColor(1f, 1f, 1f, alpha);
                    buffer.addVertex(matrixStack.lastMatrix(), (float) v2.x, (float) v2.y, (float) v2.z).setColor(1f, 1f, 1f, alpha);
                    buffer.addVertex(matrixStack.lastMatrix(), (float) v3.x, (float) v3.y, (float) v3.z).setColor(1f, 1f, 1f, alpha);
                    buffer.addVertex(matrixStack.lastMatrix(), (float) v4.x, (float) v4.y, (float) v4.z).setColor(1f, 1f, 1f, alpha);
                }
            }

            private void renderLandingPoint(MatrixStack matrixStack, MultiBufferSource.BufferSource bufferSource) {
                if (lastHitResult == null) return;

                if (lastHitResult instanceof BlockHitResult blockHitResult) {
                    var hitPos = blockHitResult.getLocation();
                    var normal = Vec3.atLowerCornerOf(blockHitResult.getDirection().getUnitVec3i());

                    var lerpFactor = ClientUtil.animationFactor(1.5f);
                    final var ringRadius = 0.4f;
                    var targetAlpha = 0.5f + 0.5f * chargeRatio;
                    ringAlpha = Mth.lerp(lerpFactor, ringAlpha, targetAlpha);

                    matrixStack.pushPose();
                    matrixStack.translate((float) hitPos.x, (float) hitPos.y, (float) hitPos.z);

                    var rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) normal.x, (float) normal.y, (float) normal.z));
                    matrixStack.mulPose(rotation);

                    matrixStack.translate(0, 0.005f, 0);

                    var consumer = bufferSource.getBuffer(RenderTypes.lightning());
                    var matrix = matrixStack.lastMatrix();
                    var ringHeight = 0.25f;
                    var y_bottom = -ringHeight / 2.0f;
                    var y_top = ringHeight / 2.0f;

                    var segments = 40;
                    for (var i = 0; i < segments; i++) {
                        var angle1 = (float) i / segments * Mth.TWO_PI;
                        var angle2 = (float) (i + 1) / segments * Mth.TWO_PI;
                        var x1 = (float) Math.cos(angle1) * ringRadius;
                        var z1 = (float) Math.sin(angle1) * ringRadius;
                        var x2 = (float) Math.cos(angle2) * ringRadius;
                        var z2 = (float) Math.sin(angle2) * ringRadius;

                        consumer.addVertex(matrix, x1, y_bottom, z1).setColor(1f, 1f, 1f, ringAlpha);
                        consumer.addVertex(matrix, x2, y_bottom, z2).setColor(1f, 1f, 1f, ringAlpha);
                        consumer.addVertex(matrix, x2, y_top, z2).setColor(1f, 1f, 1f, ringAlpha);
                        consumer.addVertex(matrix, x1, y_top, z1).setColor(1f, 1f, 1f, ringAlpha);
                    }
                    matrixStack.popPose();
                }
            }

            @SubscribeEvent
            public void onScroll(MouseScrollEvent event) {
                distance += event.yOffset;
                distance = Math.min(20, Math.max(0, distance));
                event.setCanceled(true);
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (player.isRemoved() || Minecraft.getInstance().screen != null) {
                    cleanup();
                    return;
                }

                var currentTime = System.nanoTime();
                var elapsedMillis = (currentTime - chargeStartTime) / 1_000_000;
                chargeRatio = Mth.clamp((float) elapsedMillis / MAX_CHARGE_TIME_MS, 0f, 1f);

                lastCalculatedDirection = calculateDashDirection(event.getPartialTick());
                simulatePath(event.getPartialTick());

                var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                var matrixStack = event.getMatrixStack();
                var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
                var camPos = camera.position();

                matrixStack.pushPose();
                matrixStack.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);

                renderTrajectoryPath(matrixStack, bufferSource, camera);
                renderLandingPoint(matrixStack, bufferSource);

                matrixStack.popPose();

                if (elapsedMillis >= MAX_CHARGE_TIME_MS) {
                    release();
                }
            }
        }
    }

    public static final class Server {
        public static final double MAX_VELOCITY_SCALAR = 2.5;

        @SubscribePacket
        public static void handleDash(DashPacket packet) {
            var player = packet.getPacketListener().getPlayer();

            var speedScalarProg = Mth.lerp(packet.getChargeRatio(), 0.4f, 1.0f);
            var actualSpeedScalar = Math.sin(speedScalarProg) * MAX_VELOCITY_SCALAR;

            var dashVelocity = packet.getDirection().scale(actualSpeedScalar);
            player.setDeltaMovement(dashVelocity);

            player.resetFallDistance();
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class DashPacket extends Packet<ServerGamePacketListenerImpl, DashPacket> {
        private static final StreamCodec<ByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Vec3::x,
                ByteBufCodecs.DOUBLE, Vec3::y,
                ByteBufCodecs.DOUBLE, Vec3::z,
                Vec3::new
        );

        public static final StreamCodec<ByteBuf, DashPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT,
                DashPacket::getChargeRatio,
                VEC3_STREAM_CODEC,
                DashPacket::getDirection,
                DashPacket::new
        );

        private final float chargeRatio;
        private final Vec3 direction;

        public DashPacket(float chargeRatio, Vec3 direction) {
            this.chargeRatio = chargeRatio;
            this.direction = direction;
        }

        public float getChargeRatio() {
            return chargeRatio;
        }

        public Vec3 getDirection() {
            return direction;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, DashPacket> getPacketType() {
            return PacketTypes.VECTOR_ACCEL_DASH.get();
        }
    }
}