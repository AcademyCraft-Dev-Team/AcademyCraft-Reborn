package org.academy.internal.common.ability.electromaster.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
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
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.academy.internal.common.world.entity.skill.LightOrb;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
    public void initServer(MinecraftServer server) {
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
            new BallLightningEntity(player).start();
        }
    }

    public static class BallLightningEntity {
        private final ServerPlayer player;
        private final Level level;
        private final ArcEffect visualEntity;
        private int existedTicks = 0;
        private Vec3 position;
        private Vec3 velocity;
        private boolean hasTarget = false;
        private Entity targetEntity = null;
        private LightOrb coreOrb;

        private static final float BASE_SCALE = 0.4F;
        private static final int RING_COUNT = 8;
        private static final int RING_SEGMENTS = 10;

        private static final AttributeCurve TAPER_CURVE = new AttributeCurve(List.of(
                new Knot(0.0f, 0.0f), new Knot(0.5f, 1.0f), new Knot(1.0f, 0.0f)
        ));
        private static final AttributeCurve NOISE_STR = new AttributeCurve(List.of(
                new Knot(0f, 0.8f), new Knot(1f, 0.8f)
        ));

        public BallLightningEntity(ServerPlayer player) {
            this.player = player;
            this.position = player.position().add(0, 4, 0);
            this.velocity = new Vec3(
                    (MathUtil.RANDOM.nextDouble() - 0.5) * 0.1,
                    0,
                    (MathUtil.RANDOM.nextDouble() - 0.5) * 0.1
            );
            this.level = player.level();
            this.visualEntity = new ArcEffect(level, MAX_DURATION_TICKS);
            this.coreOrb = new LightOrb(level, MAX_DURATION_TICKS, 0.15f, () -> {
                int lifeTime = coreOrb.getLifeTime();

                if (lifeTime <= 5) {
                    coreOrb.setScale(coreOrb.getScale() * 0.5f);
                } else if (lifeTime <= 10) {
                    coreOrb.setScale(coreOrb.getScale() * 2f);
                }else{
                    float scale = 0.1f + 0.2f * Mth.sin(lifeTime * 0.2f);
                    coreOrb.setScale(scale);

                    float bluePulse = 0.6f + 0.4f * Mth.sin(lifeTime * 0.1f);
                    coreOrb.setColor(0.3f, 0.6f, bluePulse);
                }
            });
        }

        public void start() {
            this.visualEntity.setPos(this.position);
            this.coreOrb.setPos(this.position);
            level.addFreshEntity(this.coreOrb);
            level.addFreshEntity(this.visualEntity);
            NeoForge.EVENT_BUS.register(this);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Post event) {
            if (player.hasDisconnected() || existedTicks++ >= MAX_DURATION_TICKS || visualEntity.isRemoved()) {
                end();
                return;
            }

            if (targetEntity != null && (targetEntity.isRemoved() || !targetEntity.isAlive())) {
                hasTarget = false;
                targetEntity = null;
            }

            if (!hasTarget) {
                List<LivingEntity> entities = MathUtil.getEntitiesInSphereByHP(level, position, MAX_RADIUS,
                        entity -> entity != player);
                if (!entities.isEmpty()) {
                    targetEntity = entities.getFirst();
                    hasTarget = true;
                }
            }

            move();

            if (targetEntity != null && position.distanceTo(targetEntity.position()) <= 4) {
                List<LivingEntity> entities = MathUtil.getEntitiesInSphereByHP(level, position, 4.0,
                        entity -> entity != player);

                DamageSource damageSource = new DamageSource(level.damageSources().damageTypes.getOrThrow(DamageTypes.MAGIC));

                for (LivingEntity entity : entities) {
                    float newHealth = entity.getHealth() * 0.7f;
                    entity.setHealth(newHealth);
                    entity.hurt(damageSource, 10f);
                    QuantumUtil.enableQuantum(entity, 0.5f, 0x3366FF);
                }

                end();
                return;
            }

            List<ArcPath> paths = new ArrayList<>();
            float time = existedTicks * 0.1f;

            for (int i = 0; i < RING_COUNT; i++) {
                float breath = (float) Math.sin(time * 0.5f + i * 0.785f);
                float currentRadius = BASE_SCALE * (1.0f + 0.8f * breath);

                float rotSpeed = 1f + 0.6f * ((i * 12345L) % 100 / 100f);
                long seed = 1000L + i * 123L;
                float ax = ((seed * 31 % 100) / 50f) - 1f;
                float ay = ((seed * 53 % 100) / 50f) - 1f;
                float az = ((seed * 17 % 100) / 50f) - 1f;

                AxisAngle4f rotation = new AxisAngle4f(
                        time * rotSpeed + i,
                        ax, ay, az
                ).normalize();

                addRing(paths, rotation, currentRadius, time, i);
            }

            //球状闪电核心
            for (int i = 0; i < 8; i++) {
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

            //电弧逸散
            int branchCount = MathUtil.RANDOM.nextInt(1, 4);
            for (int i = 0; i < branchCount; i++) {
                if (MathUtil.RANDOM.nextFloat() < 0.6f) {
                    double r = 2.0 + Math.abs(MathUtil.RANDOM.nextGaussian() * 4.0);
                    if (r <= 6f) {
                        double ang = Math.random() * 6.28;
                        Vec3 target = position.add(r * Math.cos(ang), MathUtil.RANDOM.nextDouble(-2, 2), r * Math.sin(ang));
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

        private void end() {
            NeoForge.EVENT_BUS.unregister(this);
            if (visualEntity != null) visualEntity.discard();
            if (coreOrb != null) coreOrb.setLifeTime(10);
        }

        private void move() {
            velocity = velocity.scale(MathUtil.RANDOM.nextDouble() * 0.45 + 0.6);

            if (MathUtil.RANDOM.nextFloat() < 0.7f) {
                velocity = velocity.add(
                        (MathUtil.RANDOM.nextDouble(-1, 1)) * 0.3,
                        (MathUtil.RANDOM.nextDouble(-1, 1)) * 0.2,
                        (MathUtil.RANDOM.nextDouble(-1, 1)) * 0.3
                );
            }

            if (hasTarget) {
                Vec3 targetCenter = targetEntity.getBoundingBox().getCenter();
                Vec3 toTarget = targetCenter.subtract(position);

                Vec3 dir = toTarget.normalize();
                double trackingStrength = 0.6;
                velocity = velocity.add(dir.scale(trackingStrength));
            }

            double maxSpeed = 0.6;
            if (MathUtil.RANDOM.nextDouble() < 0.8 && velocity.lengthSqr() > maxSpeed * maxSpeed) {
                velocity = velocity.normalize().scale(maxSpeed);
            }

            position = position.add(velocity);

            visualEntity.setPos(position);
            visualEntity.setDeltaMovement(velocity);
            coreOrb.setPos(position);
            coreOrb.setDeltaMovement(velocity);
        }

        private void addRing(List<ArcPath> paths, AxisAngle4f rot, float radius, float time, int seedIndex) {
            Quaternionf q = new Quaternionf(rot);

            for (int i = 0; i < RING_SEGMENTS; i++) {
                float a1 = (float) (i * 6.28 / RING_SEGMENTS);
                float a2 = (float) ((i + 1) * 6.28 / RING_SEGMENTS);

                Vector3f p1 = new Vector3f((float) Math.cos(a1) * radius, 0, (float) Math.sin(a1) * radius)
                        .rotate(q).add(position.toVector3f());
                Vector3f p2 = new Vector3f((float) Math.cos(a2) * radius, 0, (float) Math.sin(a2) * radius)
                        .rotate(q).add(position.toVector3f());

                Vector3f tangent = new Vector3f(p2).sub(p1).normalize();

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

        //生成范围指示环
//        private void spawnRadiusRing(List<ArcPath> paths, double radius) {
//            int segments = (int) (12 + radius * 2);
//            double step = (Math.PI * 2) / segments;
//            float ringY = (float) player.getY() + 0.1f;
//
//            for (int i = 0; i < segments; i++) {
//                double a1 = i * step;
//                double a2 = (i + 1) * step;
//                Vector3f p1 = new Vector3f((float) (position.x + radius * Math.cos(a1)), ringY, (float) (position.z + radius * Math.sin(a1)));
//                Vector3f p2 = new Vector3f((float) (position.x + radius * Math.cos(a2)), ringY, (float) (position.z + radius * Math.sin(a2)));
//
//                paths.add(new ArcPath(
//                        new LinePath(p1, p2),
//                        List.of(new JaggedModifier(0.3f, 2, MathUtil.RANDOM.nextLong())),
//                        2.0f,
//                        List.of()
//                ));
//            }
//        }

        private List<Branch> generateSmallAngleBranches(Vector3f mainTangent, float scale) {
            List<Branch> branches = new ArrayList<>();

            if (MathUtil.RANDOM.nextFloat() < 0.6f) {
                float angle = MathUtil.RANDOM.nextFloat() * 0.1f;
                Vector3f rotAxis = generateOrthoVector(mainTangent);
                Vector3f branchDir = new Vector3f(mainTangent).rotateAxis(angle, rotAxis.x, rotAxis.y, rotAxis.z)
                        .normalize().mul(scale * 0.7f);

                ArcPath mainBranchPath = new ArcPath(
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
                    float subBranchProgress = 0.4f + MathUtil.RANDOM.nextFloat() * 0.4f;
                    Vector3f subRotAxis = generateOrthoVector(branchDir);

                    float subAngle = 0.1f + MathUtil.RANDOM.nextFloat() * 0.2f;
                    Vector3f subDir = new Vector3f(branchDir).rotateAxis(subAngle, subRotAxis.x, subRotAxis.y, subRotAxis.z)
                            .normalize().mul(scale * 0.4f);

                    ArcPath subBranchPath = new ArcPath(
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
                int count = 1 + MathUtil.RANDOM.nextInt(2);
                for (int i = 0; i < count; i++) {
                    Vector3f dir = new Vector3f(
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
            float r = (float) (BASE_SCALE * scale * Math.cbrt(MathUtil.RANDOM.nextDouble()));
            float theta = (float) (MathUtil.RANDOM.nextDouble() * 6.28);
            float phi = (float) Math.acos(2 * MathUtil.RANDOM.nextDouble() - 1);
            return position.toVector3f().add(
                    (float) (r * Math.sin(phi) * Math.cos(theta)),
                    (float) (r * Math.sin(phi) * Math.sin(theta)),
                    (float) (r * Math.cos(phi))
            );
        }

        private Vector3f generateOrthoVector(Vector3f input) {
            Vector3f randomVec = new Vector3f((float) Math.random(), (float) Math.random(), (float) Math.random());
            Vector3f ortho = new Vector3f(input).cross(randomVec).normalize();
            if (ortho.lengthSquared() < 0.001f) {
                ortho = new Vector3f(input).cross(new Vector3f(0, 1, 0)).normalize();
            }
            return ortho;
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