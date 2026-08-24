package org.academy.internal.common.ability.accelerator.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class VectorAccel extends Skill {
    public static final long MAX_CHARGE_TICKS = 40;
    public static final long MAX_CHARGE_TIME_MS = MAX_CHARGE_TICKS * 50L;

    public VectorAccel() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(10)
                .maxStacks(10)
                .iterationTicks(5)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_CHARGE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CHARGE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, 0)
        ), ctx -> Client.onChargeStart());
        InputSystem.addKeyBinding(Client.KEY_NAME_RELEASE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_RELEASE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.RELEASE, 0)
        ), ctx -> Client.onChargeRelease());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(Skills.VECTOR_ACCEL.get(), List.of(), R.textures.ability.accelerator.skill.vec_accel.icon, 76, 40)
        );
        public static final String KEY_NAME_CHARGE = SkillNames.VECTOR_ACCEL + "_charge";
        public static final String KEY_NAME_RELEASE = SkillNames.VECTOR_ACCEL + "_release";
        public static Config CONFIG = new Config();
        public static @Nullable Context currentContext = null;

        public static void onChargeStart() {
            var player = Minecraft.getInstance().player;
            if (player == null || currentContext != null || Minecraft.getInstance().gui.screen() != null) {
                return;
            }
            if (!AbilitySystemClient.canUseSkill(Skills.VECTOR_ACCEL.get())) return;
            currentContext = new Context(player);
            AbilitySystemClient.registerContext(currentContext);
            MisakaNetworkClient.send(StartPacket.INSTANCE);
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
            private final double chargeStartTick;
            private final List<Vec3> trajectoryPath = new ArrayList<>();
            private boolean released = false;
            private float chargeRatio;
            private @Nullable HitResult lastHitResult;
            private float ringAlpha;
            private Vec3 lastCalculatedDirection = Vec3.ZERO;
            private double distance = 10;

            public Context(LocalPlayer player) {
                this.player = player;
                chargeStartTick = player.tickCount
                        + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            }

            private static float modifiedFriction(float friction, float modifier) {
                return Mth.clamp(1.0f - (1.0f - friction) * modifier, 0.0f, 1.0f);
            }

            public void release() {
                if (released) return;
                released = true;
                MisakaNetworkClient.send(DashPacket.INSTANCE);
                cleanup();
            }

            private void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (currentContext == this) {
                    currentContext = null;
                }
            }

            private Vec3 calculateDashDirection(float partialTick) {
                return Server.normalizeDashDirection(player.getViewVector(partialTick));
            }

            private Vec3 calculateInitSpeed() {
                return lastCalculatedDirection.scale(calculateSpeedScalar());
            }

            private double calculateSpeedScalar() {
                return Server.getSpeed(chargeRatio);
            }

            private void simulatePath(float partialTick) {
                trajectoryPath.clear();
                lastHitResult = null;

                var level = player.level();
                var startPos = player.getPosition(partialTick);
                var currentPos = startPos;
                var currentVel = calculateInitSpeed();
                var startBox = player.getBoundingBox().move(startPos.subtract(player.position()));
                var simulatedOnGround = player.onGround();

                for (var i = 0; i < 300; i++) {
                    trajectoryPath.add(currentPos);

                    var currentBox = startBox.move(currentPos.subtract(startPos));
                    var collisionBox = currentBox.expandTowards(currentVel).inflate(1.0e-4);
                    var collisions = level.getEntityCollisions(player, collisionBox);
                    var adjustedVel = currentVel.lengthSqr() == 0.0D
                            ? currentVel
                            : Entity.collideBoundingBox(player, currentVel, currentBox, level, collisions);

                    var nextPos = currentPos.add(adjustedVel);
                    var collidedX = !Mth.equal(currentVel.x, adjustedVel.x);
                    var collidedY = !Mth.equal(currentVel.y, adjustedVel.y);
                    var collidedZ = !Mth.equal(currentVel.z, adjustedVel.z);
                    var landed = collidedY && currentVel.y < 0.0;
                    if (collidedX || collidedY || collidedZ) {
                        var center = currentBox.getCenter();
                        var rayStartY = collidedY
                                ? currentVel.y < 0.0 ? currentBox.minY + 1.0e-4 : currentBox.maxY - 1.0e-4
                                : center.y;
                        var rayStart = new Vec3(center.x, rayStartY, center.z);
                        var blockHit = level.clip(new ClipContext(
                                rayStart,
                                rayStart.add(currentVel),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                player
                        ));
                        if (blockHit.getType() != HitResult.Type.MISS) {
                            lastHitResult = blockHit;
                        }
                        currentVel = new Vec3(
                                collidedX ? 0.0 : currentVel.x,
                                collidedY ? 0.0 : currentVel.y,
                                collidedZ ? 0.0 : currentVel.z
                        );
                    }

                    currentPos = nextPos;
                    var movedBox = currentBox.move(adjustedVel);
                    var nextOnGround = landed
                            || (currentVel.y <= 0.0
                            && !level.noCollision(player, movedBox.move(0.0, -1.0e-4, 0.0)));
                    currentVel = advanceVelocity(currentVel, currentPos, movedBox, simulatedOnGround);
                    simulatedOnGround = nextOnGround;
                    if (currentVel.lengthSqr() < 1.0e-4) {
                        trajectoryPath.add(currentPos);
                        return;
                    }
                }
            }

            private Vec3 advanceVelocity(Vec3 movement, Vec3 position, AABB boundingBox, boolean onGround) {
                var movementY = movement.y;
                var levitation = player.getEffect(MobEffects.LEVITATION);
                if (levitation != null) {
                    movementY += (0.05 * (levitation.getAmplifier() + 1) - movement.y) * 0.2;
                } else {
                    var gravity = player.getAttributeValue(Attributes.GRAVITY);
                    if (movement.y <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) {
                        gravity = Math.min(gravity, 0.01);
                    }
                    movementY -= gravity;
                }

                var frictionModifier = (float) player.getAttributeValue(Attributes.FRICTION_MODIFIER);
                var blockFriction = 1.0f;
                if (onGround) {
                    var below = BlockPos.containing(position.x, boundingBox.minY - 0.500001, position.z);
                    blockFriction = modifiedFriction(
                            player.level().getBlockState(below).getBlock().getFriction(),
                            frictionModifier
                    );
                }

                var airDragModifier = (float) player.getAttributeValue(Attributes.AIR_DRAG_MODIFIER);
                var airDrag = modifiedFriction(0.91f, airDragModifier);
                var horizontalFriction = blockFriction * airDrag;
                var verticalFriction = modifiedFriction(0.98f, airDragModifier);
                return new Vec3(
                        movement.x * horizontalFriction,
                        movementY * verticalFriction,
                        movement.z * horizontalFriction
                );
            }

            private Vec3 calculateLeftHandOffset(float partialTick) {
                var yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
                var forward = Vec3.directionFromRotation(0.0f, yaw);
                var flatForward = new Vec3(forward.x, 0.0, forward.z);
                if (flatForward.lengthSqr() <= 1.0e-6) flatForward = new Vec3(0.0, 0.0, 1.0);
                flatForward = flatForward.normalize();
                var left = new Vec3(0.0, 1.0, 0.0).cross(flatForward).normalize();
                return left.scale(0.42)
                        .add(flatForward.scale(0.16))
                        .add(0.0, player.getEyeHeight() - 0.32, 0.0);
            }

            private void renderTrajectoryPath(MatrixStack matrixStack, VertexConsumer buffer, Camera camera,
                                              Vec3 renderOffset) {
                if (trajectoryPath.size() < 2) return;

                for (var i = 0; i < trajectoryPath.size() - 1; i++) {
                    var p1 = trajectoryPath.get(i).add(renderOffset);
                    var p2 = trajectoryPath.get(i + 1).add(renderOffset);

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

            private void renderLandingPoint(MatrixStack matrixStack, VertexConsumer consumer, Vec3 renderOffset) {
                if (lastHitResult == null) return;

                if (lastHitResult instanceof BlockHitResult blockHitResult) {
                    var hitPos = blockHitResult.getLocation().add(renderOffset);
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

                    var matrix = matrixStack.lastMatrix();
                    var ringHeight = 0.25f;
                    var y_bottom = -ringHeight / 2.0f;
                    var y_top = ringHeight / 2.0f;

                    var segments = 40;
                    for (var i = 0; i < segments; i++) {
                        var angle1 = (float) i / segments * Mth.TWO_PI;
                        var angle2 = (float) (i + 1) / segments * Mth.TWO_PI;
                        var x1 = Mth.cos(angle1) * ringRadius;
                        var z1 = Mth.sin(angle1) * ringRadius;
                        var x2 = Mth.cos(angle2) * ringRadius;
                        var z2 = Mth.sin(angle2) * ringRadius;

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
                distance = Mth.clamp(distance, 0, 20);
                event.setCanceled(true);
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (player.isRemoved() || Minecraft.getInstance().gui.screen() != null) {
                    cleanup();
                    return;
                }

                var partialTick = event.getPartialTick();
                chargeRatio = Server.getChargeRatio(chargeStartTick, player.tickCount + partialTick);

                lastCalculatedDirection = calculateDashDirection(partialTick);
                simulatePath(partialTick);

                var matrixStack = event.getMatrixStack();
                var camera = Minecraft.getInstance().gameRenderer.mainCamera();
                var camPos = camera.position();
                var renderOffset = calculateLeftHandOffset(partialTick);

                matrixStack.pushPose();
                matrixStack.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);

                event.submitCustomGeometry(RenderTypes.lightning(), (matrices, consumer) -> {
                    renderTrajectoryPath(matrices, consumer, camera, renderOffset);
                    renderLandingPoint(matrices, consumer, renderOffset);
                });

                matrixStack.popPose();

                if (chargeRatio >= 1.0f) {
                    release();
                }
            }
        }
    }

    public static final class Server {
        public static final double MAX_VELOCITY_SCALAR = 7.0;
        private static final Map<ServerPlayer, Long> CHARGE_START_TICKS = new WeakHashMap<>();

        public static float getChargeRatio(double startTick, double releaseTick) {
            return getChargeRatio(startTick, releaseTick, MAX_CHARGE_TICKS);
        }

        public static float getChargeRatio(double startTick, double releaseTick, long maxChargeTicks) {
            return Mth.clamp((float) Math.max(0, releaseTick - startTick)
                    / Math.max(1L, maxChargeTicks), 0.0f, 1.0f);
        }

        public static Vec3 normalizeDashDirection(Vec3 direction) {
            if (direction == null
                    || !Double.isFinite(direction.x)
                    || !Double.isFinite(direction.y)
                    || !Double.isFinite(direction.z)
                    || direction.lengthSqr() < 1.0e-8) {
                return Vec3.ZERO;
            }

            direction = direction.normalize();
            if (direction.y < -0.5) {
                direction = new Vec3(direction.x, -0.5, direction.z).normalize();
            }
            return direction;
        }

        public static double getSpeed(float chargeRatio) {
            var speedScalarProg = Mth.lerp(Mth.clamp(chargeRatio, 0.0f, 1.0f), 0.4f, 1.0f);
            return Mth.sin(speedScalarProg) * MAX_VELOCITY_SCALAR;
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.VECTOR_ACCEL.get().isEnabled(player)) return;
            CHARGE_START_TICKS.put(player, player.level().getGameTime());
        }

        @SubscribePacket
        public static void handleDash(DashPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var startTick = CHARGE_START_TICKS.remove(player);
            if (startTick == null) return;
            var skill = Skills.VECTOR_ACCEL.get();
            var maxChargeTicks = skill.hasProficiencyMilestone(player, 2) ? 30L : MAX_CHARGE_TICKS;
            var chargeRatio = getChargeRatio(startTick, player.level().getGameTime(), maxChargeTicks);
            skill.executeActive(player, (_, _) -> {
                var direction = normalizeDashDirection(player.getLookAngle());
                EntityMotionGuard.runWithMotionSource(
                        player,
                        () -> player.setDeltaMovement(direction.scale(getSpeed(chargeRatio)))
                );
                player.resetFallDistance();
                player.level().playSound(null, player.blockPosition(), SoundEvents.VECTOR_ACCEL.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                if (skill.hasProficiencyMilestone(player, 3)) {
                    TimedSkillEffectRuntime.put(player, player.getUUID(), skill,
                            "dash_impact", 40, 6.0f);
                }
            });
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.VECTOR_ACCEL.get();
            var now = player.level().getGameTime();
            var impact = TimedSkillEffectRuntime.get(
                    player.getUUID(), player.getUUID(), skill, "dash_impact", now).orElse(null);
            if (impact == null) return;
            var target = player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(0.45),
                    entity -> entity != player && entity.isAlive() && !player.isAlliedTo(entity)
            ).stream().findFirst().orElse(null);
            if (target == null) return;
            TimedSkillEffectRuntime.consume(
                    player.getUUID(), player.getUUID(), skill, "dash_impact", now);
            var system = AbilitySystemServer.getSystem(player);
            var damage = impact.value()
                    * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                    * system.getPlayerDamageMultiplier(player.getUUID());
            target.hurtServer(
                    player.level(),
                    SkillDamageSource.of(player, skill,
                            org.academy.internal.common.world.damagesource.DamageTypes.VEC),
                    damage
            );
            var direction = Server.normalizeDashDirection(player.getLookAngle());
            target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(1.2)));
            target.hurtMarked = true;
            player.setDeltaMovement(player.getDeltaMovement().scale(0.5));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.VECTOR_ACCEL_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class DashPacket extends Packet<ServerGamePacketListenerImpl, DashPacket> {
        public static final DashPacket INSTANCE = new DashPacket();
        public static final StreamCodec<ByteBuf, DashPacket> CODEC = StreamCodec.unit(INSTANCE);

        private DashPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, DashPacket> getPacketType() {
            return PacketTypes.VECTOR_ACCEL_DASH.get();
        }
    }
}
