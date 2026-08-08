package org.academy.internal.common.ability.accelerator.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorReduction;
import org.academy.internal.common.ability.accelerator.reflection.VectorReflectionRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.ReflectedSkillDamageSource;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorAttackAttributionResolver;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorIncomingDamageCoordinator;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorIncomingDamageResult;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorReflectedDamageAccumulator;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileRedirects;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileStateAdapter;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorMotionRedirects;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectEffectPacket;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorDefenseFeedbackPacket;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorDefenseFeedbackTickets;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorContinuousInterceptionLeases;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorCompatibilityEffectLimiter;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorEnvironmentalFeedbackController;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorInterceptionTickets;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.apache.commons.lang3.tuple.Pair;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VectorReflection extends Skill {
    public VectorReflection() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .maintenanceCost(50)
                .iterationTicks(10)
                .passive()
                .initiallyDisabled()
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VECTOR_REDUCTION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition("Vector Reduction", "academy:vector_reduction"))
        );
    }

    @Override
    public void initClient() {
        VectorRedirectEffectPacket.initClient();
        VectorDefenseFeedbackPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, InputConstants.MOD_ALT)
                ), ctx -> Client.onToggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.VECTOR_REFLECTION.get(),
                        List.of(),
                        R.textures.ability.accelerator.skill.vector_reflection.icon,
                        210, 50
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.VECTOR_REFLECTION + "_toggle";
        public static Config CONFIG = new Config();
        public static void onToggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.VECTOR_REFLECTION.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public VectorReflection.Client.Config getDefault() {
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
        private static final long REFLECTION_SOUND_COOLDOWN_TICKS = 10;
        private static final Map<UUID, Long> LAST_SOUND_TICK = new HashMap<>();
        private static final Map<UUID, Float> PROTECTED_HEALTH = new ConcurrentHashMap<>();
        private static final ThreadLocal<Map<UUID, Integer>> LEGITIMATE_HEALTH_MUTATIONS =
                ThreadLocal.withInitial(HashMap::new);

        private Server() {
        }

        @SubscribePacket
        public static void toggleReflection(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.VECTOR_REFLECTION.get();
            if (!skill.isEnabled(player)) {
                VectorReduction.Server.forceDeactivate(player);
            }
            skill.toggle(player);
            if (skill.isEnabled(player)) maintainProtection(player);
            else clearProtection(player);
        }

        public static boolean isActive(ServerPlayer player) {
            return canMaintainLinearReflectionLease(player)
                    && AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID()) > 0.0f;
        }

        public static boolean canMaintainLinearReflectionLease(ServerPlayer player) {
            return player != null
                    && player.connection != null
                    && !player.isSpectator()
                    && Skills.VECTOR_REFLECTION.get().isEnabled(player);
        }

        public static void purgeProtectedEffects(ServerPlayer player) {
            for (var effect : new ArrayList<>(player.getActiveEffects())) {
                if (ReflectionFilter.shouldReflectEffect(player, effect)) {
                    player.removeEffect(effect.getEffect());
                }
            }
        }

        public static boolean shouldReflection(Player player, DamageSource damageSource) {
            if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return false;
            if (!isActive(serverPlayer)) return false;
            return canReflectSource(serverPlayer, damageSource);
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            var skill = Skills.VECTOR_REFLECTION.get();
            var data = skill.getRuntimeData(player).orElse(null);
            if (data != null && data.isEnabled()) {
                var system = AbilitySystemServer.getSystem(player);
                system.toggleSkill(player.getUUID(), skill.getKeyString());
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            }
            clearProtection(player);
        }

        private static boolean canReflectSource(ServerPlayer player, DamageSource damageSource) {
            if (player == null || damageSource == null) return false;
            if (ReflectedSkillDamageSource.isReflected(damageSource)) return false;
            if (damageSource instanceof SkillDamageSource skillSource
                    && skillSource.getSkill() == Skills.VECTOR_REFLECTION.get()) return false;
            if (damageSource.getDirectEntity() instanceof Projectile projectile
                    && VectorProjectileRedirects.isRedirected(projectile)) return false;
            if (damageSource.getDirectEntity() != null
                    && VectorMotionRedirects.isRedirected(damageSource.getDirectEntity())) return false;
            return true;
        }

        public static boolean reflectAnomalousDamage(
                ServerPlayer player,
                DamageSource source,
                float damage
        ) {
            if (!(damage > VectorIncomingDamageCoordinator.ANOMALOUS_DAMAGE_THRESHOLD)
                    || !Float.isFinite(damage)
                    || !canMaintainLinearReflectionLease(player)
                    || !canReflectSource(player, source)) {
                return false;
            }
            applyReflection(player, (ServerLevel) player.level(), source, damage);
            player.invulnerableTime = 0;
            VectorDefenseFeedbackTickets.commitFull(player, source);
            return true;
        }

        public static boolean tryReflectLinearAttack(
                ServerPlayer player,
                float incomingDamage,
                Vec3 mirrorPoint,
                Vec3 incomingDirection
        ) {
            return tryReflectLinearAttack(player, incomingDamage, mirrorPoint, incomingDirection, true);
        }

        public static boolean tryReflectLinearAttack(
                ServerPlayer player,
                float incomingDamage,
                Vec3 mirrorPoint,
                Vec3 incomingDirection,
                boolean emitFeedback
        ) {
            if (!isActive(player)
                    || !(incomingDamage > 0.0f)
                    || !Float.isFinite(incomingDamage)
                    || !isFiniteVector(mirrorPoint)
                    || !isFiniteVector(incomingDirection)
                    || incomingDirection.lengthSqr() < 1.0E-8) {
                return false;
            }

            var skill = Skills.VECTOR_REFLECTION.get();
            var system = AbilitySystemServer.getSystem(player);
            var result = calculateReflection(
                    incomingDamage,
                    system.getPlayerAvailableCP(player.getUUID()),
                    system.getPlayerCalculationIntensity(player.getUUID()),
                    system.isPlayerSkillDebugMode(player.getUUID())
            );
            if (result.remainingDamage() > 0.0f
                    || result.reflectedDamage() + 1.0E-5f < incomingDamage) {
                return false;
            }

            var executed = skill.executeContinuous(player, _ -> result.baseCpCost(), (_, _) -> {
                if (emitFeedback) {
                    playReflectionSound(player);
                    spawnGlowCircle(player, incomingDirection.scale(-1.0), mirrorPoint);
                }
            }, true);
            if (!executed) return false;
            player.invulnerableTime = 0;
            maintainProtection(player);
            return true;
        }

        public static Pair<Boolean, Float> hurtServer(Player player, ServerLevel level, DamageSource source, float originalDamage) {
            if (!(player instanceof ServerPlayer serverPlayer)) return Pair.of(false, originalDamage);
            var coordinated = VectorIncomingDamageCoordinator.interceptVectorDefense(
                    serverPlayer, source, originalDamage);
            return Pair.of(coordinated.handled(), coordinated.remainingDamage());
        }

        public static VectorIncomingDamageResult applyPartialReflection(
                ServerPlayer serverPlayer,
                ServerLevel level,
                DamageSource source,
                float originalDamage
        ) {
            if (!shouldReflection(serverPlayer, source)
                    || !(originalDamage > 0.0f)
                    || !Float.isFinite(originalDamage)) {
                return VectorIncomingDamageResult.passThrough(originalDamage);
            }
            var skill = Skills.VECTOR_REFLECTION.get();
            var system = AbilitySystemServer.getSystem(serverPlayer);
            var result = calculateReflection(
                    originalDamage,
                    system.getPlayerAvailableCP(serverPlayer.getUUID()),
                    system.getPlayerCalculationIntensity(serverPlayer.getUUID()),
                    false
            );
            var reflectedDamage = result.reflectedDamage();
            var executed = reflectedDamage <= 0.0f;
            if (reflectedDamage > 0.0f) {
                executed = skill.executeContinuous(serverPlayer, _ -> result.baseCpCost(),
                        (_, _) -> {
                            applyReflection(serverPlayer, level, source, reflectedDamage);
                        }, true);
            }
            serverPlayer.invulnerableTime = 0;
            maintainProtection(serverPlayer);
            var remaining = executed ? result.remainingDamage() : originalDamage;
            if (executed && !(remaining > 0.0f)) {
                VectorDefenseFeedbackTickets.commitFull(serverPlayer, source);
                return VectorIncomingDamageResult.fullRedirect();
            }
            return VectorIncomingDamageResult.partial(remaining);
        }

        static float calculateReflectedDamage(float damage, float availableCP,
                                              float calculationIntensity, boolean devMode) {
            return calculateReflection(damage, availableCP, calculationIntensity, devMode).reflectedDamage();
        }

        static ReflectionResult calculateReflection(float damage, float availableCP,
                                                    float calculationIntensity, boolean devMode) {
            if (!(damage > 0.0f) || !Float.isFinite(damage)) {
                return new ReflectionResult(0.0f, 0.0f, 0.0f);
            }
            if (devMode) return new ReflectionResult(damage, 0.0f, 0.0f);
            if (!(availableCP > 0.0f) || !Float.isFinite(availableCP)
                    || !(calculationIntensity > 0.0f) || !Float.isFinite(calculationIntensity)) {
                return new ReflectionResult(0.0f, damage, 0.0f);
            }

            var requiredPower = damage * calculationIntensity;
            if (Float.isFinite(requiredPower) && availableCP >= requiredPower) {
                return new ReflectionResult(damage, 0.0f, damage);
            }

            var reflectedDamage = Math.min(damage, availableCP / 10.0f);
            var remainingDamage = Math.max(0.0f, damage - reflectedDamage);
            var baseCpCost = availableCP / calculationIntensity;
            return new ReflectionResult(reflectedDamage, remainingDamage, baseCpCost);
        }

        public static Vec3 reflectedVelocity(Vec3 incoming) {
            if (incoming == null || incoming.lengthSqr() < 1.0E-8 || !Double.isFinite(incoming.lengthSqr())) {
                return Vec3.ZERO;
            }
            return incoming.scale(-1.2);
        }

        public static boolean shouldReflectProjectileFor(ServerPlayer player, Projectile projectile) {
            if (!isActive(player) || !canRedirectProjectileGeometry(player, projectile)) return false;
            var velocity = projectile.getDeltaMovement();
            var toPlayer = player.getBoundingBox().getCenter().subtract(projectile.position());
            return toPlayer.lengthSqr() <= 1.0E-8 || velocity.dot(toPlayer) > 0.0;
        }

        private static boolean canRedirectProjectileGeometry(ServerPlayer player, Projectile projectile) {
            if (player == null || projectile == null || projectile.isRemoved()) return false;
            if (VectorProjectileRedirects.isRedirected(projectile)) return false;
            var owner = projectile.getOwner();
            if (owner == player || owner != null && owner.getUUID().equals(player.getUUID())) return false;
            var velocity = projectile.getDeltaMovement();
            return isFiniteVector(velocity) && velocity.lengthSqr() >= 1.0E-8;
        }

        public static boolean reflectProjectile(ServerPlayer player, Projectile projectile) {
            return reflectProjectile(player, projectile, true, true);
        }

        private static boolean reflectProjectile(ServerPlayer player, Projectile projectile,
                                                 boolean spawnEffect, boolean chargeCp) {
            if (chargeCp
                    ? !shouldReflectProjectileFor(player, projectile)
                    : !canRedirectProjectileGeometry(player, projectile)) {
                return false;
            }
            var velocity = projectile.getDeltaMovement();
            var speed = velocity.length();
            if (!Double.isFinite(speed)) return false;
            speed = Math.max(speed, projectileReflectionCost(0.0));
            var reflected = velocity.normalize().scale(-speed * 1.2);
            if (!isFiniteVector(reflected) || reflected.lengthSqr() < 1.0E-8) return false;

            var redirect = (Runnable) () -> {
                VectorProjectileRedirects.mark(projectile, player, VectorRedirectKind.REFLECTION);
                projectile.setOwner(player);
                var pushDistance = Math.max(player.getBbWidth(), 0.75) + 0.5;
                projectile.setPos(player.getBoundingBox().getCenter().add(reflected.normalize().scale(pushDistance)));
                VectorProjectileStateAdapter.applyRedirect(projectile, reflected);
                if (spawnEffect) {
                    spawnGlowCircle(player, projectile.getBoundingBox().getCenter()
                            .subtract(player.getBoundingBox().getCenter()));
                }
                playReflectionSound(player);
            };
            if (!chargeCp) {
                redirect.run();
                return true;
            }
            var projectileCost = projectileReflectionCost(speed);
            return Skills.VECTOR_REFLECTION.get().executeContinuous(
                    player,
                    _ -> projectileCost,
                    (_, _) -> redirect.run(),
                    true
            );
        }

        public static boolean tryProtectForcedMovement(ServerPlayer player) {
            if (!isActive(player)) return false;
            return Skills.VECTOR_REFLECTION.get().executeContinuous(
                    player,
                    _ -> projectileReflectionCost(0.0),
                    (_, _) -> playReflectionSound(player),
                    true
            );
        }

        static float projectileReflectionCost(double speed) {
            if (!Double.isFinite(speed)) return 1.5f;
            return Math.max(1.5f, (float) Math.max(0.0, speed));
        }

        private static boolean isFiniteVector(Vec3 vector) {
            return vector != null
                    && Double.isFinite(vector.x)
                    && Double.isFinite(vector.y)
                    && Double.isFinite(vector.z);
        }

        public static void maintainProtection(ServerPlayer player) {
            if (!isActive(player)) return;
            VectorReflectionRuntime.maintain(player);
            var protectedHealth = protectedHealth(player, player.getHealth());
            player.clearFire();
            player.setTicksFrozen(0);
            if (player.getAirSupply() < player.getMaxAirSupply()) {
                player.setAirSupply(player.getMaxAirSupply());
            }
            if (Float.isFinite(protectedHealth) && protectedHealth > 0.0f
                    && Float.compare(player.getHealth(), protectedHealth) != 0) {
                beginLegitimateHealthMutation(player);
                try {
                    player.setHealth(protectedHealth);
                } finally {
                    endLegitimateHealthMutation(player, false);
                }
            }
            purgeProtectedEffects(player);
        }

        public static float protectHealthWrite(ServerPlayer player, float requested) {
            if (!isActive(player) || isLegitimateHealthMutation(player)) return requested;
            return protectedHealth(player, player.getHealth());
        }

        public static float protectHealthRead(ServerPlayer player, float original) {
            if (!isActive(player) || isLegitimateHealthMutation(player)) return original;
            return protectedHealth(player, original);
        }

        public static boolean shouldForceAlive(ServerPlayer player) {
            return isActive(player) && protectedHealth(player, player.getHealth()) > 0.0f
                    && !isLegitimateHealthMutation(player);
        }

        public static void beginLegitimateHealthMutation(ServerPlayer player) {
            if (player == null) return;
            var depths = LEGITIMATE_HEALTH_MUTATIONS.get();
            depths.merge(player.getUUID(), 1, Integer::sum);
        }

        public static void endLegitimateHealthMutation(ServerPlayer player, boolean captureHealth) {
            if (player == null) return;
            var uuid = player.getUUID();
            var depths = LEGITIMATE_HEALTH_MUTATIONS.get();
            var depth = depths.getOrDefault(uuid, 0);
            if (captureHealth && depth > 0) {
                var health = player.getHealth();
                if (Float.isFinite(health)) PROTECTED_HEALTH.put(uuid, Math.max(0.0f, health));
            }
            if (depth <= 1) depths.remove(uuid);
            else depths.put(uuid, depth - 1);
            if (depths.isEmpty()) LEGITIMATE_HEALTH_MUTATIONS.remove();
        }

        public static boolean isLegitimateHealthMutation(ServerPlayer player) {
            return player != null
                    && LEGITIMATE_HEALTH_MUTATIONS.get().getOrDefault(player.getUUID(), 0) > 0;
        }

        public static void clearProtection(ServerPlayer player) {
            if (player == null) return;
            VectorReflectionRuntime.deactivate(player);
            VectorContinuousInterceptionLeases.clear(player);
            VectorInterceptionTickets.clear(player);
            PROTECTED_HEALTH.remove(player.getUUID());
            LAST_SOUND_TICK.remove(player.getUUID());
            VectorDefenseFeedbackTickets.clear(player);
            VectorReflectedDamageAccumulator.clear(player);
            VectorAttackAttributionResolver.clear(player);
            VectorCompatibilityEffectLimiter.clear(player);
        }

        private static float protectedHealth(ServerPlayer player, float fallback) {
            var safeFallback = Float.isFinite(fallback) ? Math.max(0.0f, fallback) : 0.0f;
            return PROTECTED_HEALTH.computeIfAbsent(player.getUUID(), _ -> safeFallback);
        }

        private static void applyReflection(ServerPlayer player, ServerLevel level,
                                            DamageSource source, float reflectedDamage) {
            var attribution = VectorAttackAttributionResolver.resolve(player, source);
            var directEntity = attribution.directEntity();
            var attacker = attribution.attacker();
            var direction = attribution.effectDirection();
            if (direction.lengthSqr() < 1.0E-6) direction = player.getLookAngle();
            direction = direction.normalize();
            var playerCenter = player.getBoundingBox().getCenter();
            var sourcePos = directEntity == null
                    ? playerCenter.add(direction)
                    : directEntity.getBoundingBox().getCenter();
            var offset = Math.max(player.getBbWidth() * 0.95, 0.75);
            var pos = directEntity instanceof Projectile && sourcePos.distanceToSqr(playerCenter) < 4.0
                    ? sourcePos
                    : playerCenter.add(direction.scale(offset));

            VectorEnvironmentalFeedbackController.emitReflection(
                    player,
                    source,
                    direction,
                    pos
            );

            if (directEntity instanceof Projectile projectile
                    && !VectorProjectileRedirects.isRedirected(projectile)) {
                reflectProjectile(player, projectile, false, false);
            }

            if (attacker != null) {
                VectorReflectedDamageAccumulator.submit(
                        player,
                        attacker,
                        source,
                        attacker,
                        VectorRedirectKind.REFLECTION,
                        reflectedDamage
                );
            }
        }

        private static void spawnGlowCircle(ServerPlayer player, Vec3 direction) {
            var normalized = direction.lengthSqr() < 1.0E-6
                    ? player.getLookAngle()
                    : direction.normalize();
            var offset = Math.max(player.getBbWidth() * 0.95, 0.75);
            spawnGlowCircle(player, normalized,
                    player.getBoundingBox().getCenter().add(normalized.scale(offset)));
        }

        public static void spawnGlowCircle(ServerPlayer player, Vec3 direction, Vec3 position) {
            var glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE.get(), player.level());
            glowCircle.setPos(position);
            var yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0f;
            var pitch = (float) -Math.toDegrees(Math.asin(direction.y));
            glowCircle.setYRot(yaw);
            glowCircle.setXRot(pitch);
            player.level().addFreshEntity(glowCircle);
        }

        public static void playReflectionSound(ServerPlayer player) {
            var tick = player.level().getGameTime();
            var last = LAST_SOUND_TICK.get(player.getUUID());
            if (last != null && tick - last < REFLECTION_SOUND_COOLDOWN_TICKS) return;
            LAST_SOUND_TICK.put(player.getUUID(), tick);
            player.level().playSound(null, player, SoundEvents.VECTOR_REFLECTION.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        record ReflectionResult(float reflectedDamage, float remainingDamage, float baseCpCost) {
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.VECTOR_REFLECTION.get();
            if (skill.isEnabled(player) && Skills.VECTOR_REDUCTION.get().isEnabled(player)) {
                VectorReduction.Server.forceDeactivate(player);
            }
            if (skill.isEnabled(player)) {
                var system = AbilitySystemServer.getSystem(player);
                var maintained = system.ensurePermanentOccupation(
                        player.getUUID(),
                        ReflectionFilter.getReflectionMaintenanceCost(player),
                        skill
                );
                if (!maintained) {
                    Server.forceDeactivate(player);
                }
            }
            if (!Server.isActive(player)) {
                Server.clearProtection(player);
                return;
            }
            Server.maintainProtection(player);
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            VectorReflectionRuntime.onServerTick();
            VectorReflectedDamageAccumulator.tick();
        }

        @SubscribeEvent
        public static void onKnockBack(LivingKnockBackEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && Server.isActive(player)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
            if (event.getAffectedEntity() instanceof ServerPlayer player && Server.isActive(player)) {
                event.setKnockbackVelocity(Vec3.ZERO);
            }
        }

        @SubscribeEvent
        public static void onDrown(LivingDrownEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || !Server.isActive(player)) return;
            player.setAirSupply(player.getMaxAirSupply());
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!Server.isActive(player)) {
                Server.clearProtection(player);
                return;
            }
            if (Server.isLegitimateHealthMutation(player)) return;
            if (!Server.shouldForceAlive(player)) {
                Server.clearProtection(player);
                return;
            }
            event.setCanceled(true);
            VectorReflectionRuntime.requestObserverRebuild(player);
            Server.maintainProtection(player);
        }

        @SubscribeEvent
        public static void onEffectApplicable(MobEffectEvent.Applicable event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!Skills.VECTOR_REFLECTION.get().isEnabled(player)) return;
            if (ReflectionFilter.shouldReflectEffect(player, event.getEffectInstance())) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.VECTOR_REFLECTION_TOGGLE.get();
        }
    }
}
