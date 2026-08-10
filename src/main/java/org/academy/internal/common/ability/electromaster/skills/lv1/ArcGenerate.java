package org.academy.internal.common.ability.electromaster.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.Branch;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackExecutor;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackPayload;
import org.academy.internal.common.ability.accelerator.reflection.LinearReflectionResolver;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;

public final class ArcGenerate extends Skill {
    public static final String KEY_NAME_GENERATE = SkillNames.ARC_GENERATE + ".generate";
    static final float BASE_DAMAGE = 4.0f;
    private static final long RETURN_SEED_MASK = 0xD1B54A32D192ED03L;

    public ArcGenerate() {
        super(
                Builder
                        .of(AbilityCategories.ELECTROMASTER.get())
                        .level(AbilityLevel.LEVEL1)
                        .energyCost(5_000)
                        .cpCost(10)
                        .iterationTicks(4)
                        .maxStacks(1)
                        .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    static float getDamage(float abilityPower, float playerDamageMultiplier) {
        return BASE_DAMAGE * Math.max(0, abilityPower) * Math.max(0, playerDamageMultiplier);
    }

    static long deriveReturnSeed(long seed) {
        return seed ^ RETURN_SEED_MASK;
    }

    static List<ArcPath> createUnreflectedArcPaths(
            Vec3 start,
            Vec3 end,
            long trunkSeed,
            List<BranchSpec> branchSpecs
    ) {
        var branches = branchSpecs.stream()
                .map(spec -> createBranch(spec.progress(), spec, spec.seed(), 1.0f))
                .toList();
        return List.of(createRootPath(start, end, trunkSeed, branches));
    }

    static List<ArcPath> createReflectedArcPaths(
            Vec3 start,
            Vec3 mirrorPoint,
            Vec3 returnEnd,
            double reflectionProgress,
            long trunkSeed,
            List<BranchSpec> branchSpecs
    ) {
        var t = (float) Mth.clamp(reflectionProgress, 0.0, 1.0);
        var reflectedSpecs = t <= 0.0f
                ? List.<BranchSpec>of()
                : branchSpecs.stream().filter(spec -> spec.progress() <= t).toList();
        var outboundBranches = reflectedSpecs.stream()
                .map(spec -> createBranch(spec.progress() / t, spec, spec.seed(), t))
                .toList();
        var returnBranches = reflectedSpecs.stream()
                .map(spec -> createBranch(
                        t - spec.progress(),
                        spec,
                        deriveReturnSeed(spec.seed()),
                        t
                ))
                .toList();

        return List.of(
                createRootPath(start, mirrorPoint, trunkSeed, outboundBranches),
                createRootPath(mirrorPoint, returnEnd, deriveReturnSeed(trunkSeed), returnBranches)
        );
    }

    private static Branch createBranch(
            float attachmentProgress,
            BranchSpec spec,
            long seed,
            float lengthScale
    ) {
        var childPath = new ArcPath(
                new LinePath(
                        new Vector3f(0, 0, 0),
                        new Vector3f(spec.localEnd()).mul(Math.max(0.0f, lengthScale))
                ),
                List.of(new JaggedModifier(1, 3, seed)),
                2.0f,
                List.of()
        );
        return new Branch(attachmentProgress, childPath);
    }

    private static ArcPath createRootPath(Vec3 start, Vec3 end, long seed, List<Branch> branches) {
        return new ArcPath(
                new LinePath(start.toVector3f(), end.toVector3f()),
                List.of(new JaggedModifier(1, 4, seed)),
                2.0f,
                branches
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.ArcGenerateConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(KEY_NAME_GENERATE, Client.CONFIG.getKeyBinding(KEY_NAME_GENERATE,
                InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), ctx -> Client.handler());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    record BranchSpec(float progress, Vector3f localEnd, long seed) {
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(Skills.ARC_GENERATE.get(), List.of(), R.textures.ability.electromaster.skill.arc_generate.icon, 24, 46)
        );
        public static ArcGenerateConfig CONFIG = new ArcGenerateConfig();

        public static void handler() {
            if (!AbilitySystemClient.canUseSkill(Skills.ARC_GENERATE.get())) return;
            MisakaNetworkClient.send(GeneratePacket.INSTANCE);
        }

        public static class ArcGenerateConfig extends KeyBindingConfig {
            public static final class Action implements TypeHandler<ArcGenerateConfig> {
                public static final TypeHandler<ArcGenerateConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public ArcGenerate.Client.ArcGenerateConfig getDefault() {
                    return new ArcGenerateConfig();
                }

                @Override
                public Class<ArcGenerateConfig> getTypeClass() {
                    return ArcGenerateConfig.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(GeneratePacket packet) {
            tryAutomatedAttack(packet.getPacketListener().getPlayer());
        }

        public static boolean tryAutomatedAttack(ServerPlayer player) {
            var level = player.level();
            return Skills.ARC_GENERATE.get().executeActive(player, (context, _) -> {
                var yawRad = (float) Math.toRadians(-player.getVisualRotationYInDegrees());
                var eyePos = player.getEyePosition();

                var playerOrientation = new Quaternionf().rotateY(yawRad);

                var look = new Vector3f(0, 0, 1).rotate(playerOrientation);
                var up = new Vector3f(0, 1, 0).rotate(playerOrientation);
                var right = new Vector3f(-1, 0, 0).rotate(playerOrientation);

                var handPos = eyePos
                        .add(new Vec3(right).scale(0.35))
                        .add(new Vec3(up).scale(-0.8))
                        .add(new Vec3(look).scale(0.35));

                var length = LevelUtil.getValidViewDistance(player, context.milestone() >= 2 ? 12 : 10);
                var targetPos = eyePos.add(player.getLookAngle().scale(length));
                var trunkLength = (float) handPos.distanceTo(targetPos);

                var radius = context.milestone() >= 2 ? 0.15f : 0.125f;
                var system = AbilitySystemServer.getSystem(player);
                var src = SkillDamageSource.of(player, Skills.ARC_GENERATE.get());
                var damage = getDamage(
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID())
                );
                var payload = LinearAttackPayload.builder(
                                player,
                                Skills.ARC_GENERATE.get(),
                                src,
                                radius
                        )
                        .damage(_ -> damage)
                        .targetFilter(entity -> entity.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get())
                        .build();
                var resolved = LinearReflectionResolver.resolve(
                        level,
                        new LinearSegment(handPos, targetPos),
                        payload
                );

                var arc = new ArcEffect(level, 20);
                arc.setPos(handPos);

                var branchSpecs = new ArrayList<BranchSpec>();
                var branchCount = 4 + MathUtil.RANDOM.nextInt(3);
                var maxAngleRad = (10.0) * Mth.DEG_TO_RAD;

                for (var i = 0; i < branchCount; i++) {
                    var progress = 0.2f + MathUtil.RANDOM.nextFloat() * 0.7f;
                    var branchLength = trunkLength * (0.3f + MathUtil.RANDOM.nextFloat() * 0.2f);

                    var phi = MathUtil.RANDOM.nextDouble() * maxAngleRad;

                    var x = Mth.sin(phi);
                    var y = Mth.sin(phi);
                    var z = Mth.cos(phi);

                    var localDir = new Vector3f(x, y, z).normalize().mul(branchLength);

                    branchSpecs.add(new BranchSpec(progress, localDir, MathUtil.RANDOM.nextLong()));
                }

                var trunkSeed = MathUtil.RANDOM.nextLong();
                var arcPaths = resolved.isReflected()
                        ? createReflectedArcPaths(
                        handPos,
                        resolved.mirrorPoint(),
                        resolved.returnSegment().orElseThrow().end(),
                        resolved.reflectionProgress(),
                        trunkSeed,
                        branchSpecs
                )
                        : createUnreflectedArcPaths(handPos, targetPos, trunkSeed, branchSpecs);
                arc.setArcPaths(arcPaths);
                level.addFreshEntity(arc);
                arc.playSound(SoundEvents.ARC_WEAK.get());

                var result = LinearAttackExecutor.execute(level, resolved, payload);
                if (context.milestone() >= 3) {
                    chainArc(player, level, result, src, damage);
                }
            });
        }

        private static void chainArc(net.minecraft.server.level.ServerPlayer player,
                                     net.minecraft.server.level.ServerLevel level,
                                     LinearAttackExecutor.ExecutionResult result,
                                     SkillDamageSource source,
                                     float damage) {
            var hit = new java.util.LinkedHashSet<net.minecraft.world.entity.Entity>();
            hit.addAll(result.outboundHits());
            hit.addAll(result.returnHits());
            var origin = hit.stream().filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast).findFirst().orElse(null);
            if (origin == null) return;
            var candidates = level.getEntitiesOfClass(LivingEntity.class,
                    origin.getBoundingBox().inflate(4.0),
                    target -> target != player && target.isAlive() && !hit.contains(target)
                            && !player.isAlliedTo(target));
            candidates.sort(java.util.Comparator.comparingDouble(origin::distanceToSqr));
            var factors = new float[]{0.5f, 0.3f};
            for (var index = 0; index < Math.min(2, candidates.size()); index++) {
                var target = candidates.get(index);
                target.hurtServer(level, source, damage * factors[index]);
                var effect = new ArcEffect(level, 8);
                effect.setPos(origin.getBoundingBox().getCenter());
                effect.setArcPaths(List.of(createRootPath(origin.getBoundingBox().getCenter(),
                        target.getBoundingBox().getCenter(), MathUtil.RANDOM.nextLong(), List.of())));
                level.addFreshEntity(effect);
                origin = target;
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class GeneratePacket extends Packet<ServerGamePacketListenerImpl, GeneratePacket> {
        public static final GeneratePacket INSTANCE = new GeneratePacket();
        public static final StreamCodec<ByteBuf, GeneratePacket> CODEC = StreamCodec.unit(INSTANCE);

        private GeneratePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, GeneratePacket> getPacketType() {
            return PacketTypes.ARC_GENERATE_GENERATE.get();
        }
    }
}
