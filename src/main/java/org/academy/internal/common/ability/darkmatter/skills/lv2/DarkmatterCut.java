package org.academy.internal.common.ability.darkmatter.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.darkmatter.DarkmatterLawMark;
import org.academy.internal.common.ability.darkmatter.DarkmatterPhase;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.DarkmatterCutSlash;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DarkmatterCut extends Skill {
    static final double RADIUS = 5.0;
    static final double SIX_WINGS_RADIUS = 24.0;
    static final double MIN_DOT = 0.5;
    static final float BASE_DAMAGE = 12.0f;
    static final float SIX_WINGS_DAMAGE = 16.0f;
    static final float MATTER_COST = 3.0f;
    private static final Identifier PENETRATION_ID = AcademyCraft.academy(
            "darkmatter_cut_phase_penetration");

    public DarkmatterCut() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .damage()
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(0)
                .iterationTicks(10)
                .maxStacks(10)
                .dependsOn(Skills.DARKMATTER_DISASSEMBLE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Deconstruction", "academy:darkmatter_disassemble"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.RELEASE, 0)
        ), context -> Client.cast());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_CUT.get(),
                        List.of(DarkmatterDisassemble.Client.SKILL_INFO),
                        R.textures.darkmatter_cut_icon,
                        140,
                        104
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_CUT + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_CUT.get())) return;
            MisakaNetworkClient.send(CastPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            tryCast(player, player.getLookAngle());
        }

        public static boolean tryCast(ServerPlayer player, Vec3 direction) {
            return tryCastInternal(player, direction, -1.0, 1.0f, -1.0f);
        }

        public static boolean tryProgramCast(
                ServerPlayer player,
                Vec3 direction,
                double maximumRadius,
                float damageScale,
                float baseCost
        ) {
            return tryCastInternal(player, direction, maximumRadius, damageScale, baseCost);
        }

        private static boolean tryCastInternal(
                ServerPlayer player,
                Vec3 direction,
                double maximumRadius,
                float damageScale,
                float baseCost
        ) {
            if (player == null
                    || direction == null
                    || !Double.isFinite(direction.x)
                    || !Double.isFinite(direction.y)
                    || !Double.isFinite(direction.z)
                    || direction.lengthSqr() < 1.0E-12
                    || !Double.isFinite(maximumRadius)
                    || (maximumRadius != -1.0
                    && (maximumRadius <= 0.0 || maximumRadius > SIX_WINGS_RADIUS))
                    || !Float.isFinite(damageScale)
                    || damageScale < 0.0f
                    || damageScale > 2.0f
                    || !Float.isFinite(baseCost)
                    || baseCost < -1.0f
                    || !(player.level() instanceof ServerLevel level)) {
                return false;
            }
            var horizontal = new Vec3(direction.x, 0.0, direction.z);
            if (maximumRadius > 0.0 && horizontal.lengthSqr() < 1.0E-8) return false;
            var skill = Skills.DARKMATTER_CUT.get();
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var phase = DarkmatterPhase.weights(player);
            var calculatedRadius = effectiveRadius(
                    phase.alpha(), milestone, phase.gamma() > 0.0f,
                    Skills.DARKMATTER_SIX_WINGS.get().getEffectiveProficiencyMilestone(player));
            if (maximumRadius > 0.0) calculatedRadius = Math.min(calculatedRadius, maximumRadius);
            var radius = calculatedRadius;
            var minimumDot = effectiveMinimumDot(phase.alpha(), milestone, false);
            var origin = player.position().add(0, player.getBbHeight() * 0.5, 0);
            var visualDirection = direction.normalize();
            var look = horizontalLook(visualDirection, player.getYRot());
            var targets = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius), target ->
                            DarkmatterTargeting.isAttackableBy(player, target)
                                    && insideCone(origin, look, target.getBoundingBox().getCenter(),
                                    radius, minimumDot));
            var requestedBaseCost = baseCost < 0.0f ? MATTER_COST : baseCost;
            var cost = matterCost(requestedBaseCost, milestone);
            var manager = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
            if (manager.getView(player).totalMatter() + 1.0e-5f < cost) return false;
            var applied = new boolean[1];
            var executed = skill.executeActive(
                    player,
                    context -> 0.0f,
                    (context, actualCost) -> {
                        var currentCost = matterCost(requestedBaseCost, context.milestone());
                        if (currentCost > 1.0e-5f && !manager.consume(
                                player, currentCost, skill,
                                skill.getIterationTicks(player))) return;
                        applied[0] = true;
                        spawnSlash(level, player, visualDirection, phase.gamma() > 0.0f ? 2.0f : 1.0f);
                        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                                SoundSource.PLAYERS, 1.0f, 1.0f);
                        var system = AbilitySystemServer.getSystem(player);
                        var damage = directDamage(phase.alpha(), phase.beta())
                                * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                                * system.getPlayerDamageMultiplier(player.getUUID())
                                * damageScale;
                        var source = SkillDamageSource.of(player, skill);
                        var hitTargets = new ArrayList<UUID>();
                        for (var target : targets) {
                            var detonation = DarkmatterLawMark.detonate(player, target);
                            if (detonation > 0.0f) {
                                target.invulnerableTime = 0;
                                SkillDamageUtil.applyDirect(level, target, source, detonation);
                                target.invulnerableTime = 0;
                                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
                            }
                            if (!hurtWithPhase(level, target, source, damage, phase.beta())) continue;
                            hitTargets.add(target.getUUID());
                            var push = target.position().subtract(player.position());
                            if (push.horizontalDistanceSqr() > 1.0e-6) {
                                push = push.normalize().scale(knockback(phase.alpha()));
                                target.push(push.x, 0.08 + phase.alpha() * 0.04, push.z);
                            }
                            DarkmatterLawMark.apply(player, target, phase.beta(),
                                    markDuration(phase.beta(), context.milestone()));
                            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                                    target.getX(), target.getY() + target.getBbHeight() * 0.5,
                                    target.getZ(), 6, 0.25, 0.25, 0.25, 0.01);
                        }
                        var delayedDamageMultiplier = delayedRiftDamageMultiplier(
                                phase.gamma(), context.milestone())
                                * DarkmatterSixWings.Server.gammaMagnitudeMultiplier(player);
                        if (delayedDamageMultiplier > 0.0f && !hitTargets.isEmpty()) {
                            var mirroredTargets = List.copyOf(hitTargets);
                            TimedSkillEffectRuntime.schedule(player,
                                    context.milestone() >= 3 ? 4 : 8, () -> {
                                        if (!player.isAlive() || player.level() != level) return;
                                        spawnSlash(level, player, visualDirection.scale(-1.0),
                                                1.5f);
                                        for (var targetId : mirroredTargets) {
                                            if (!(level.getEntity(targetId) instanceof LivingEntity target)
                                                    || !DarkmatterTargeting.isAttackableBy(player, target)) continue;
                                            target.invulnerableTime = 0;
                                            var detonation = DarkmatterLawMark.detonate(player, target);
                                            if (detonation > 0.0f) {
                                                SkillDamageUtil.applyDirect(
                                                        level, target, source, detonation);
                                                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
                                                target.invulnerableTime = 0;
                                            }
                                            hurtWithPhase(level, target, source,
                                                    damage * delayedDamageMultiplier, phase.beta());
                                        }
                                    });
                        }
                    });
            return executed && applied[0];
        }

        private static boolean hurtWithPhase(
                ServerLevel level,
                LivingEntity target,
                DamageSource source,
                float damage,
                float beta
        ) {
            var armor = target.getAttribute(Attributes.ARMOR);
            if (armor == null || beta <= 0.0f) {
                return DarkmatterTargeting.hurt(level, target, source, damage);
            }
            var existing = armor.getModifier(PENETRATION_ID);
            if (existing != null) armor.removeModifier(PENETRATION_ID);
            armor.addTransientModifier(new AttributeModifier(
                    PENETRATION_ID,
                    -penetration(beta),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            try {
                return DarkmatterTargeting.hurt(level, target, source, damage);
            } finally {
                armor.removeModifier(PENETRATION_ID);
                if (existing != null) armor.addTransientModifier(existing);
            }
        }

        static Vec3 horizontalLook(Vec3 look, float yaw) {
            var horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() < 1.0e-6) horizontal = Vec3.directionFromRotation(0, yaw);
            return horizontal.normalize();
        }

        static double effectiveRadius(
                float alpha,
                int milestone,
                boolean sixWings,
                int sixWingsMilestone
        ) {
            var radius = RADIUS + Math.max(0.0f, alpha);
            if (Math.clamp(milestone, 0, 3) >= 2) radius *= 1.15;
            if (sixWings) radius *= DarkmatterSixWings.Server.areaMultiplier(sixWingsMilestone);
            return radius;
        }

        static double effectiveMinimumDot(float alpha, int milestone, boolean alternate) {
            if (alternate) return -1.0;
            var halfAngle = 30.0 + Math.max(0.0f, alpha) * 6.0;
            if (Math.clamp(milestone, 0, 3) >= 2) halfAngle *= 1.15;
            return Math.cos(Math.toRadians(Math.min(179.0, halfAngle)));
        }

        static float delayedRiftDamageMultiplier(int milestone) {
            return delayedRiftDamageMultiplier(1.0f, milestone);
        }

        static float delayedRiftDamageMultiplier(float gamma, int milestone) {
            if (!(gamma > 0.0f)) return 0.0f;
            return 0.25f + 0.10f * gamma
                    + (Math.clamp(milestone, 0, 3) >= 3 ? 0.15f : 0.0f);
        }

        static int markDuration(float beta, int milestone) {
            var ticks = 40.0f + Math.max(0.0f, beta) * 20.0f;
            return Math.round(Math.clamp(milestone, 0, 3) >= 2 ? ticks * 1.5f : ticks);
        }

        static float directDamage(float alpha, float beta) {
            return (6.0f + 2.0f * Math.max(0.0f, alpha))
                    * Math.max(0.6f, 1.0f - 0.08f * Math.max(0.0f, beta));
        }

        static double knockback(float alpha) {
            return 0.2 + Math.max(0.0f, alpha) * 0.12;
        }

        static float penetration(float beta) {
            return Math.clamp(Math.max(0.0f, beta) * 0.10f, 0.0f, 0.50f);
        }

        static float matterCost(float base, int milestone) {
            return Math.max(0.0f, base)
                    * (Math.clamp(milestone, 0, 3) >= 1 ? 0.9f : 1.0f);
        }

        static boolean insideCone(Vec3 origin, Vec3 look, Vec3 target,
                                  double radius, double minimumDot) {
            var offset = target.subtract(origin);
            if (offset.lengthSqr() > radius * radius) return false;
            var horizontal = new Vec3(offset.x, 0, offset.z);
            return horizontal.lengthSqr() > 1.0e-6
                    && look.dot(horizontal.normalize()) >= minimumDot;
        }

        private static void spawnSlash(
                ServerLevel level,
                ServerPlayer player,
                Vec3 direction,
                float scale
        ) {
            var slash = new DarkmatterCutSlash(EntityTypes.DARKMATTER_CUT_SLASH.get(), level);
            var position = player.position().add(0, player.getBbHeight() * 0.3, 0)
                    .add(direction.scale(1.85));
            slash.setPos(position);
            slash.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            slash.setXRot((float) Math.toDegrees(-Math.asin(Math.clamp(direction.y, -1.0, 1.0)))
                    + player.getRandom().nextFloat() * 60 - 30);
            slash.setScale(scale);
            slash.setDuration(4);
            slash.setSwingDirection(player.getRandom().nextBoolean() ? 1 : -1);
            level.addFreshEntity(slash);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CUT_CAST.get();
        }
    }
}
