package org.academy.internal.common.ability.electromaster.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.util.QuantumUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.Branch;
import org.academy.api.common.arc.modifier.HelixModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.NoiseFieldModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.Knot;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.academy.internal.common.world.entity.skill.LightOrb;
import org.joml.AxisAngle4f;
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

public class BallLightning extends Skill {
    public static final String KEY_NAME_ACTIVATE = SkillNames.BALL_LIGHTNING + ".activate";
    public static final float MAX_RADIUS = 64.0F;
    public static final int MAX_DURATION_TICKS = 2000;

    public BallLightning() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.BallLightningConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(KEY_NAME_ACTIVATE, Client.CONFIG.getKeyBinding(KEY_NAME_ACTIVATE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Y)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>())
                )
        ), Client::handler);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static BallLightningConfig CONFIG = new BallLightningConfig();

        public static void handler() {
            MisakaNetworkClient.sendPacket(ActivatePacket.INSTANCE);
        }

        public static class BallLightningConfig extends KeyBindingConfig {
            public static final class Action implements TypeHandler<BallLightningConfig> {
                public static final TypeHandler<BallLightningConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public BallLightningConfig getDefault() {
                    return new BallLightningConfig();
                }

                @Override
                public Class<BallLightningConfig> getTypeClass() {
                    return BallLightningConfig.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            AbilitySystemServer.registerContext(new Context(player));
        }

        public static class Context extends ServerContext {
            private static final float BASE_SCALE = 0.2F;
            private static final int RING_COUNT = 8;
            private static final int RING_SEGMENTS = 12;
            private static final AttributeCurve TAPER_CURVE = new AttributeCurve(List.of(
                    new Knot(0.0f, 0.0f), new Knot(0.5f, 1.0f), new Knot(1.0f, 0.0f)
            ));
            private static final AttributeCurve NOISE_STR = new AttributeCurve(List.of(
                    new Knot(0f, 0.8f), new Knot(1f, 0.8f)
            ));

            private final ArcEffect visualEntity;
            private final LightOrb coreOrb;
            private final float timeSeed;

            private int existedTicks = 0;
            private Vec3 position;
            private Vec3 velocity;
            @Nullable
            private Vec3 roamTarget;
            private int strafeDir = 1;

            private boolean hasTarget = false;
            @Nullable
            private Entity targetEntity = null;

            private BehaviorState currentState;
            private int stateTimer = 0;

            private enum BehaviorState {
                START,
                ROAMING,
                TRACK,
                STAY,
                SLIDE,
                DASH
            }

            public Context(ServerPlayer player) {
                super(player);
                this.position = player.getEyePosition().add(0, 1, 0);
                this.timeSeed = (float) (MathUtil.RANDOM.nextDouble() * 10000);

                Vec3 look = player.getLookAngle();
                this.velocity = look.scale(0.5f);

                this.currentState = BehaviorState.START;
                this.stateTimer = 12;

                var level = level();
                this.visualEntity = new ArcEffect(level, MAX_DURATION_TICKS);
                this.coreOrb = new LightOrb(level, MAX_DURATION_TICKS, 0.15f, this::updateCoreOrb);

                visualEntity.setPos(position);
                coreOrb.setPos(position);
                level.addFreshEntity(coreOrb);
                level.addFreshEntity(visualEntity);
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Post event) {
                if (player.hasDisconnected() || existedTicks++ >= MAX_DURATION_TICKS || visualEntity.isRemoved()) {
                    end();
                    return;
                }

                updateTarget();
                updateStateTransition();
                updatePhysics();
                checkImpact();
                updateVisuals();
            }

            private void updateCoreOrb() {
                var lifeTime = coreOrb.getLifeTime();
                if (lifeTime <= 5) {
                    coreOrb.setScale(coreOrb.getScale() * 0.5f);
                } else if (lifeTime <= 10) {
                    coreOrb.setScale(coreOrb.getScale() * 2f);
                } else {
                    var scale = 0.1f + 0.2f * Mth.sin(lifeTime * 0.2f);
                    coreOrb.setScale(scale);
                    var bluePulse = 0.6f + 0.4f * Mth.sin(lifeTime * 0.1f);
                    coreOrb.setColor(0.3f, 0.6f, bluePulse);
                }
            }

            private void updateTarget() {
                if (targetEntity != null && (targetEntity.isRemoved() || !targetEntity.isAlive())) {
                    hasTarget = false;
                    targetEntity = null;
                }

                if (!hasTarget && currentState != BehaviorState.START) {
                    var entities = MathUtil.getEntitiesInSphereByHP(level(), position, MAX_RADIUS, e -> e != player);
                    if (!entities.isEmpty()) {
                        targetEntity = entities.getFirst();
                        hasTarget = true;
                    }
                }
            }

            private void updateStateTransition() {
                if (stateTimer > 0) {
                    stateTimer--;
                    return;
                }

                switch (currentState) {
                    case START -> {
                        currentState = BehaviorState.ROAMING;
                        stateTimer = 0;
                    }
                    case ROAMING -> {
                        if (hasTarget) {
                            currentState = BehaviorState.TRACK;
                        } else {
                            double rx = position.x + (MathUtil.RANDOM.nextDouble() - 0.5) * 40;
                            double rz = position.z + (MathUtil.RANDOM.nextDouble() - 0.5) * 40;
                            double ry = position.y + (MathUtil.RANDOM.nextDouble() - 0.5) * 10;
                            roamTarget = new Vec3(rx, ry, rz);
                            stateTimer = 40 + MathUtil.RANDOM.nextInt(40);
                        }
                    }
                    case TRACK -> {
                        if (!hasTarget) {
                            currentState = BehaviorState.ROAMING;
                            return;
                        }
                        double dist = position.distanceTo(targetEntity.getBoundingBox().getCenter());
                        if (dist <= 12.0) {
                            selectCombatState();
                        } else {
                            stateTimer = 5;
                        }
                    }
                    case STAY, SLIDE, DASH -> {
                        if (!hasTarget) {
                            currentState = BehaviorState.ROAMING;
                        } else {
                            currentState = BehaviorState.TRACK;
                            stateTimer = 0;
                        }
                    }
                }
            }

            private void selectCombatState() {
                float rng = MathUtil.RANDOM.nextFloat();
                if (rng < 0.2f) {
                    currentState = BehaviorState.SLIDE;
                    stateTimer = 20 + MathUtil.RANDOM.nextInt(30);
                    strafeDir = MathUtil.RANDOM.nextBoolean() ? 1 : -1;
                } else if (rng < 0.6f) {
                    currentState = BehaviorState.STAY;
                    stateTimer = 15 + MathUtil.RANDOM.nextInt(25);
                } else {
                    currentState = BehaviorState.DASH;
                    stateTimer = 30 + MathUtil.RANDOM.nextInt(15);
                }
            }

            private void updatePhysics() {
                Vec3 force = Vec3.ZERO;
                float drag = 0.96f;
                double maxSpeed = 0.5;

                if (currentState == BehaviorState.ROAMING) {
                    if (roamTarget != null) {
                        Vec3 toRoam = roamTarget.subtract(position);
                        force = toRoam.normalize().scale(0.03);
                    }
                }

                if (hasTarget) {
                    Vec3 toTarget = targetEntity.getBoundingBox().getCenter().subtract(position);

                    switch (currentState) {
                        case TRACK -> {
                            force = toTarget.normalize().scale(0.08);
                            maxSpeed = 1.2;
                            drag = 0.92f;
                        }
                        case STAY -> {
                            double t = existedTicks * 0.15 + timeSeed;
                            Vec3 noise = new Vec3(Math.sin(t), Math.cos(t * 0.8), Math.sin(t * 1.2)).scale(0.04);
                            force = noise.add(toTarget.normalize().scale(0.015));
                            drag = 0.85f;
                            maxSpeed = 0.1;
                        }
                        case SLIDE -> {
                            Vec3 up = new Vec3(0, 1, 0);
                            Vec3 tangent = toTarget.cross(up).normalize().scale(strafeDir);
                            force = tangent.scale(0.12).add(toTarget.normalize().scale(0.04));
                            double heightDiff = targetEntity.getY() + targetEntity.getBbHeight() / 2.0 - position.y;
                            force = force.add(0, heightDiff * 0.03, 0);
                            drag = 0.95f;
                        }
                        case DASH -> {
                            force = toTarget.normalize().scale(0.25);
                            drag = 0.99f;
                            maxSpeed = 2.0;
                        }
                    }
                }

                velocity = velocity.add(force);
                if (velocity.lengthSqr() > maxSpeed * maxSpeed) {
                    velocity = velocity.normalize().scale(maxSpeed);
                }

                velocity = velocity.scale(drag);
                position = position.add(velocity);

                visualEntity.setPos(position);
                visualEntity.setDeltaMovement(velocity);
                coreOrb.setPos(position);
                coreOrb.setDeltaMovement(velocity);
            }

            private void checkImpact() {
                if (targetEntity != null && position.distanceTo(targetEntity.position()) <= 4.0) {
                    var level = level();
                    var entities = MathUtil.getEntitiesInSphereByHP(level, position, 5.0, e -> e != player);
                    var damageSource = new DamageSource(level.damageSources().damageTypes.getOrThrow(DamageTypes.MAGIC));

                    for (var entity : entities) {
                        entity.setHealth(entity.getHealth() * 0.7f);
                        entity.hurtServer(level, damageSource, 10f);
                        QuantumUtil.enableQuantum(entity, 0.5f, 0x3366FF);
                    }
                    end();
                }
            }

            private void end() {
                unregister();
                visualEntity.discard();
                coreOrb.setLifeTime(10);
            }

            private void updateVisuals() {
                List<ArcPath> paths = new ArrayList<>();
                var time = existedTicks * 0.1f;

                for (var i = 0; i < RING_COUNT; i++) {
                    var breath = (float) Math.sin(time * 0.5f + i * 0.785f);
                    var currentRadius = BASE_SCALE * (1.0f + 0.8f * breath);
                    var rotSpeed = 1f + 0.6f * ((i * 12345L) % 100 / 100f);
                    var seed = 1000L + i * 123L;

                    var ax = ((seed * 31 % 100) / 50f) - 1f;
                    var ay = ((seed * 53 % 100) / 50f) - 1f;
                    var az = ((seed * 17 % 100) / 50f) - 1f;

                    var rotation = new AxisAngle4f(time * rotSpeed + i, ax, ay, az).normalize();
                    addRing(paths, rotation, currentRadius, time, i);
                }

                for (var i = 0; i < 8; i++) {
                    paths.add(new ArcPath(
                            new LinePath(randPos(0.6f), randPos(0.6f)),
                            List.of(
                                    new NoiseFieldModifier(NOISE_STR, 0.5f, 0.2f, 10086L + i * 100L),
                                    new TaperModifier(TAPER_CURVE, 1.0f)
                            ),
                            1.8f,
                            List.of()
                    ));
                }

                int branchCount = MathUtil.RANDOM.nextInt(1, 4);
                for (var i = 0; i < branchCount; i++) {
                    if (MathUtil.RANDOM.nextFloat() < 0.6f) {
                        double r = 2.0 + Math.abs(MathUtil.RANDOM.nextGaussian() * 4.0);
                        if (r <= 6f) {
                            double ang = Math.random() * 6.28;
                            var target = position.add(r * Math.cos(ang), MathUtil.RANDOM.nextDouble(-2, 2), r * Math.sin(ang));
                            paths.add(new ArcPath(
                                    new LinePath(position.toVector3f(), target.toVector3f()),
                                    List.of(
                                            new HelixModifier(0.15f, 1.0f, 0),
                                            new JaggedModifier(2f, 4, MathUtil.RANDOM.nextLong()),
                                            new TaperModifier(TAPER_CURVE, 1.0f)
                                    ),
                                    2.5f,
                                    generateRandomBranches(2.0f)
                            ));
                        }
                    }
                }
                visualEntity.setArcPaths(paths);
            }

            private void addRing(List<ArcPath> paths, AxisAngle4f rot, float radius, float time, int seedIndex) {
                var q = new Quaternionf(rot);
                for (var i = 0; i < RING_SEGMENTS; i++) {
                    var a1 = (float) (i * 6.28 / RING_SEGMENTS);
                    var a2 = (float) ((i + 1) * 6.28 / RING_SEGMENTS);

                    var p1 = new Vector3f((float) Math.cos(a1) * radius, 0, (float) Math.sin(a1) * radius)
                            .rotate(q).add(position.toVector3f());
                    var p2 = new Vector3f((float) Math.cos(a2) * radius, 0, (float) Math.sin(a2) * radius)
                            .rotate(q).add(position.toVector3f());

                    var tangent = new Vector3f(p2).sub(p1).normalize();

                    paths.add(new ArcPath(
                            new LinePath(p1, p2),
                            List.of(
                                    new HelixModifier(0.15f, 1.0f, time * 5.0f + seedIndex),
                                    new JaggedModifier(0.4f, 2, 100L + seedIndex + i),
                                    new TaperModifier(TAPER_CURVE, 1.0f)
                            ),
                            2f,
                            generateSmallAngleBranches(tangent, 0.7f)
                    ));
                }
            }

            private List<Branch> generateSmallAngleBranches(Vector3f mainTangent, float scale) {
                List<Branch> branches = new ArrayList<>();
                if (MathUtil.RANDOM.nextFloat() < 0.6f) {
                    var angle = MathUtil.RANDOM.nextFloat() * 0.1f;
                    var rotAxis = generateOrthoVector(mainTangent);
                    var branchDir = new Vector3f(mainTangent).rotateAxis(angle, rotAxis.x, rotAxis.y, rotAxis.z)
                            .normalize().mul(scale * 0.4f);

                    var mainBranchPath = new ArcPath(
                            new LinePath(new Vector3f(), branchDir),
                            List.of(
                                    new JaggedModifier(0.4f, 3, MathUtil.RANDOM.nextLong()),
                                    new TaperModifier(TAPER_CURVE, 1.0f)
                            ),
                            0.6f,
                            List.of()
                    );
                    branches.add(new Branch(0.3f + MathUtil.RANDOM.nextFloat() * 0.4f, mainBranchPath));

                    if (MathUtil.RANDOM.nextFloat() < 0.5f) {
                        var subBranchProgress = 0.4f + MathUtil.RANDOM.nextFloat() * 0.4f;
                        var subRotAxis = generateOrthoVector(branchDir);
                        var subAngle = 0.1f + MathUtil.RANDOM.nextFloat() * 0.2f;
                        var subDir = new Vector3f(branchDir).rotateAxis(subAngle, subRotAxis.x, subRotAxis.y, subRotAxis.z)
                                .normalize().mul(scale * 0.2f);

                        var subBranchPath = new ArcPath(
                                new LinePath(new Vector3f(), subDir),
                                List.of(
                                        new JaggedModifier(0.3f, 2, MathUtil.RANDOM.nextLong()),
                                        new TaperModifier(TAPER_CURVE, 1.0f)
                                ),
                                0.3f,
                                List.of()
                        );
                        branches.add(new Branch(subBranchProgress, subBranchPath));
                    }
                }
                return branches;
            }

            private List<Branch> generateRandomBranches(float scale) {
                List<Branch> branches = new ArrayList<>();
                if (MathUtil.RANDOM.nextFloat() < 0.3f) {
                    var count = 1 + MathUtil.RANDOM.nextInt(2);
                    for (var i = 0; i < count; i++) {
                        var dir = new Vector3f(
                                (float) MathUtil.RANDOM.nextGaussian(),
                                (float) MathUtil.RANDOM.nextGaussian(),
                                (float) MathUtil.RANDOM.nextGaussian()
                        ).normalize().mul(scale);

                        branches.add(new Branch(
                                0.2f + MathUtil.RANDOM.nextFloat() * 0.6f,
                                new ArcPath(
                                        new LinePath(new Vector3f(), dir),
                                        List.of(new JaggedModifier(0.6f, 2, MathUtil.RANDOM.nextLong())),
                                        0.8f,
                                        List.of()
                                )
                        ));
                    }
                }
                return branches;
            }

            private Vector3f randPos(float scale) {
                var r = (float) (BASE_SCALE * scale * Math.cbrt(MathUtil.RANDOM.nextDouble()));
                var theta = (float) (MathUtil.RANDOM.nextDouble() * 6.28);
                var phi = (float) Math.acos(2 * MathUtil.RANDOM.nextDouble() - 1);
                return position.toVector3f().add(
                        (float) (r * Math.sin(phi) * Math.cos(theta)),
                        (float) (r * Math.sin(phi) * Math.sin(theta)),
                        (float) (r * Math.cos(phi))
                );
            }

            private Vector3f generateOrthoVector(Vector3f input) {
                var randomVec = new Vector3f((float) Math.random(), (float) Math.random(), (float) Math.random());
                var ortho = new Vector3f(input).cross(randomVec).normalize();
                if (ortho.lengthSquared() < 0.001f) {
                    ortho = new Vector3f(input).cross(new Vector3f(0, 1, 0)).normalize();
                }
                return ortho;
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.LIGHTNING_NOVA_ACTIVATE.get();
        }
    }
}