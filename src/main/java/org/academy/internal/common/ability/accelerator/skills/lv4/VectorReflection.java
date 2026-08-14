package org.academy.internal.common.ability.accelerator.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
import org.academy.api.client.config.SkillSettingsRegistry;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorDeviation;
import org.academy.internal.common.ability.accelerator.reflection.VectorReflectionRuntime;
import org.academy.internal.common.ability.accelerator.reflection.VectorDefenseProficiency;
import org.academy.internal.common.ability.accelerator.reflection.VectorReflectionRuntime;
import org.academy.internal.common.ability.accelerator.reflection.compat.*;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorDeviation;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.ReflectedSkillDamageSource;
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

import java.util.*;
import net.minecraft.util.Mth;

public class VectorReflection extends Skill {
    public VectorReflection() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(30_000)
                .maintenanceCost(40)
                .iterationTicks(10)
                .proficiencyProfile(SkillProficiencyProfile.builder()
                        .iterationTicks(10, 10, 10, 5)
                        .build())
                .passive()
                .initiallyDisabled()
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VECTOR_DEVIATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Vector Reduction", "academy:vector_deviation"))
        );
    }

    @Override
    public void initClient() {
        VectorRedirectEffectPacket.initClient();
        VectorDefenseFeedbackPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        Client.registerSettings();

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
        private static boolean settingsRegistered;

        private static void registerSettings() {
            if (settingsRegistered) return;
            settingsRegistered = true;
            SkillSettingsRegistry.INSTANCE.register(
                    Skills.VECTOR_REFLECTION.get(),
                    new SkillSettingsRegistry.Module(
                            "distortion_ring",
                            "",
                            List.of(new SkillSettingsRegistry.Toggle(
                                    "first_person_distortion_ring",
                                    "app.academy.skill_settings.advanced.vector_reflection_first_person_distortion_ring",
                                    () -> CONFIG.isFirstPersonDistortionRingVisible(),
                                    enabled -> {
                                        CONFIG.setFirstPersonDistortionRingVisible(enabled);
                                        AcademyCraftClient.Config.INSTANCE.save();
                                    }
                            ))
                    )
            );
        }

        public static void onToggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.VECTOR_REFLECTION.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            private boolean firstPersonDistortionRingVisible = true;

            public boolean isFirstPersonDistortionRingVisible() {
                return firstPersonDistortionRingVisible;
            }

            public void setFirstPersonDistortionRingVisible(boolean visible) {
                firstPersonDistortionRingVisible = visible;
            }

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
        private static final float CP_DEPLETION_EPSILON = 1.0E-4f;
        private static final Map<UUID, Long> LAST_SOUND_TICK = new HashMap<>();
        private static final ThreadLocal<Set<UUID>> IMAGINE_BREAKER_MUTATIONS =
                ThreadLocal.withInitial(HashSet::new);

        private Server() {
        }

        @SubscribePacket
        public static void toggleReflection(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.VECTOR_REFLECTION.get();
            var runtimeData = skill.getRuntimeData(player).orElse(null);
            if (runtimeData != null && runtimeData.isEnabled()) {
                forceDeactivate(player);
                return;
            }

            var system = AbilitySystemServer.getSystem(player);
            if (runtimeData == null
                    || !LearningHelper.isSkillAvailableForCategory(
                    system.getPlayerAbilityCategory(player.getUUID()), skill
            )) {
                clearProtection(player);
                return;
            }
            var maintenanceCost = ReflectionFilter.getReflectionMaintenanceCost(player);
            if (system.getPlayerStatus(player.getUUID()) != AbilityData.Status.NORMAL
                    || !hasSufficientCpToEnable(
                    system.getPlayerAvailableCP(player.getUUID()),
                    maintenanceCost,
                    system.getPlayerCalculationIntensity(player.getUUID())
            )) {
                clearProtection(player);
                return;
            }

            skill.toggle(player);
            if (!skill.isEnabled(player)
                    || !system.ensurePermanentOccupation(
                    player.getUUID(), maintenanceCost, skill
            )) {
                forceDeactivate(player);
                return;
            }

            VectorDeviation.Server.forceDeactivate(player);
            maintainProtection(player);
        }

        static boolean hasSufficientCpToEnable(
                float availableCp,
                float maintenanceCost,
                float calculationIntensity
        ) {
            if (!Float.isFinite(availableCp)
                    || !Float.isFinite(maintenanceCost) || maintenanceCost < 0.0f
                    || !Float.isFinite(calculationIntensity) || !(calculationIntensity > 0.0f)) {
                return false;
            }
            var actualCost = maintenanceCost * calculationIntensity;
            return Float.isFinite(actualCost) && availableCp > actualCost;
        }

        public static boolean isActive(ServerPlayer player) {
            var system = AbilitySystemServer.getSystem(player);
            return canMaintainLinearReflectionLease(player)
                    && (system.isPlayerSkillDebugMode(player.getUUID())
                    || !isComputingPowerDepleted(system.getPlayerAvailableCP(player.getUUID())));
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
            return damageSource.getDirectEntity() == null
                    || !VectorMotionRedirects.isRedirected(damageSource.getDirectEntity());
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
            applyReflection(player, player.level(), source, damage);
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
                    VectorDefenseProficiency.effectiveMilestone(player, skill),
                    system.getPlayerMaxCP(player.getUUID()) * 0.01f,
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
            deactivateAfterVectorChargeIfNeeded(player);
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
                    VectorDefenseProficiency.effectiveMilestone(serverPlayer, skill),
                    system.getPlayerMaxCP(serverPlayer.getUUID()) * 0.01f,
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
            // The unreflected remainder is applied after this method returns. Restore the vanilla
            // player class first when this charge spent the final CP, so lethal damage cannot enter
            // the death/respawn pipeline through the dispatch subclass.
            if (executed) deactivateAfterVectorChargeIfNeeded(serverPlayer);
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
            return calculateReflection(damage, availableCP, calculationIntensity, 3, devMode).reflectedDamage();
        }

        static ReflectionResult calculateReflection(float damage, float availableCP,
                                                    float calculationIntensity, boolean devMode) {
            return calculateReflection(damage, availableCP, calculationIntensity, 3, devMode);
        }

        static ReflectionResult calculateReflection(float damage, float availableCP,
                                                    float calculationIntensity, int milestone,
                                                    boolean devMode) {
            return calculateReflection(damage, availableCP, calculationIntensity, milestone, 0.0f, devMode);
        }

        public static boolean isVectorDefenseActive(ServerPlayer player) {
            return isActive(player) || VectorDeviation.Server.isActive(player);
        }

        static ReflectionResult calculateReflection(float damage, float availableCP,
                                                    float calculationIntensity, int milestone,
                                                    float freeDamageThreshold, boolean devMode) {
            var result = VectorDefenseProficiency.calculate(
                    damage,
                    availableCP,
                    calculationIntensity,
                    milestone,
                    freeDamageThreshold,
                    devMode
            );
            return new ReflectionResult(
                    result.processedDamage(),
                    result.remainingDamage(),
                    result.baseCpCost()
            );
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
            var previousOwner = projectile.getOwner();

            var redirect = (Runnable) () -> {
                VectorProjectileRedirects.mark(projectile, player, VectorRedirectKind.REFLECTION);
                projectile.setOwner(player);
                var pushDistance = Math.max(player.getBbWidth(), 0.75) + 0.5;
                projectile.setPos(player.getBoundingBox().getCenter().add(reflected.normalize().scale(pushDistance)));
                VectorProjectileStateAdapter.applyRedirect(projectile, reflected, previousOwner);
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
            var executed = Skills.VECTOR_REFLECTION.get().executeContinuous(
                    player,
                    _ -> projectileCost,
                    (_, _) -> redirect.run(),
                    true
            );
            if (executed) deactivateAfterVectorChargeIfNeeded(player);
            return executed;
        }

        public static boolean tryProtectForcedMovement(ServerPlayer player) {
            if (!isActive(player)) return false;
            var executed = Skills.VECTOR_REFLECTION.get().executeContinuous(
                    player,
                    _ -> projectileReflectionCost(0.0),
                    (_, _) -> playReflectionSound(player),
                    true
            );
            if (executed) deactivateAfterVectorChargeIfNeeded(player);
            return executed;
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
            if (!isVectorDefenseActive(player)) return;
            VectorReflectionRuntime.maintain(player);
            player.hurtTime = 0;
            player.hurtDuration = 0;
            player.hurtMarked = false;
            player.deathTime = 0;
            player.invulnerableTime = 0;
            player.clearFire();
            player.setTicksFrozen(0);
            if (player.getAirSupply() < player.getMaxAirSupply()) {
                player.setAirSupply(player.getMaxAirSupply());
            }
            if (isActive(player)) purgeProtectedEffects(player);
        }

        public static boolean deactivateUnavailableProtection(ServerPlayer player) {
            if (player == null || isActive(player)
                    || !Skills.VECTOR_REFLECTION.get().isEnabled(player)) return false;
            forceDeactivate(player);
            return true;
        }

        public static void imaginebreaker(ServerPlayer player, float amount) {
            if (player == null || !Float.isFinite(amount) || !(amount > 0.0f)) return;
            if (!isVectorDefenseActive(player)) return;

            var uuid = player.getUUID();
            var mutations = IMAGINE_BREAKER_MUTATIONS.get();
            mutations.add(uuid);
            try {
                var original = player.getHealth();
                setOriginalHealth(player, Math.max(0.0f, original - amount));
            } finally {
                mutations.remove(uuid);
                if (mutations.isEmpty()) IMAGINE_BREAKER_MUTATIONS.remove();
            }
        }

        public static boolean shouldForceAlive(ServerPlayer player) {
            return isVectorDefenseActive(player);
        }

        public static boolean isImagineBreakerMutation(ServerPlayer player) {
            return player != null && IMAGINE_BREAKER_MUTATIONS.get().contains(player.getUUID());
        }

        public static void clearProtection(ServerPlayer player) {
            if (player == null) return;
            VectorReflectionRuntime.deactivate(player);
            clearProtectionState(player);
        }

        public static void forceDeactivateForDeath(ServerPlayer player) {
            if (player == null) return;
            if (VectorDeviation.Server.canMaintain(player)) {
                VectorDeviation.Server.forceDeactivate(player);
            }
            var skill = Skills.VECTOR_REFLECTION.get();
            var data = skill.getRuntimeData(player).orElse(null);
            if (data != null && data.isEnabled()) {
                var system = AbilitySystemServer.getSystem(player);
                system.toggleSkill(player.getUUID(), skill.getKeyString());
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            }
            VectorReflectionRuntime.deactivateForDeath(player);
            clearProtectionState(player);
        }

        private static void clearProtectionState(ServerPlayer player) {
            VectorContinuousInterceptionLeases.clear(player);
            VectorInterceptionTickets.clear(player);
            LAST_SOUND_TICK.remove(player.getUUID());
            VectorDefenseFeedbackTickets.clear(player);
            VectorReflectedDamageAccumulator.clear(player);
            VectorAttackAttributionResolver.clear(player);
            VectorCompatibilityEffectLimiter.clear(player);
        }

        private static void setOriginalHealth(ServerPlayer player, float health) {
            PlayerAttributeRuntime.runWithoutResistance(
                    () -> EntityControlApi.forceSetTrueHealth(player, health)
            );
        }

        public static void deactivateAfterVectorChargeIfNeeded(ServerPlayer player) {
            if (player == null) return;
            var availableCp = AbilitySystemServer.getSystem(player)
                    .getPlayerAvailableCP(player.getUUID());
            if (!isComputingPowerDepleted(availableCp)) return;
            if (canMaintainLinearReflectionLease(player)) forceDeactivate(player);
            if (VectorDeviation.Server.canMaintain(player)) VectorDeviation.Server.forceDeactivate(player);
            if (!isVectorDefenseActive(player)) clearProtection(player);
        }

        public static boolean isComputingPowerDepleted(float availableCp) {
            return !Float.isFinite(availableCp) || !(availableCp > CP_DEPLETION_EPSILON);
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
            spawnGlowCircle(player, direction, position, VectorRedirectKind.REFLECTION);
        }

        public static void spawnGlowCircle(
                ServerPlayer player,
                Vec3 direction,
                Vec3 position,
                VectorRedirectKind kind
        ) {
            var glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE.get(), player.level());
            glowCircle.setPos(position);
            glowCircle.setEffectOwner(player.getId(), kind);
            var yaw = (float) (Mth.atan2(direction.z, direction.x)) * Mth.RAD_TO_DEG - 90.0f;
            var pitch = (float) -(Math.asin(direction.y)) * Mth.RAD_TO_DEG;
            glowCircle.setYRot(yaw);
            glowCircle.setXRot(pitch);
            player.level().addFreshEntity(glowCircle);
        }

        public static void playReflectionSound(ServerPlayer player) {
            tryPlayReflectionSound(player);
        }

        /**
         * Plays the shared reflection cue once per feedback cooldown window.
         */
        public static boolean tryPlayReflectionSound(ServerPlayer player) {
            var tick = player.level().getGameTime();
            var last = LAST_SOUND_TICK.get(player.getUUID());
            if (last != null && tick - last < REFLECTION_SOUND_COOLDOWN_TICKS) return false;
            LAST_SOUND_TICK.put(player.getUUID(), tick);
            player.level().playSound(null, player, SoundEvents.VECTOR_REFLECTION.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            return true;
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
            if (skill.isEnabled(player) && Skills.VECTOR_DEVIATION.get().isEnabled(player)) {
                VectorDeviation.Server.forceDeactivate(player);
            }
            if (Server.deactivateUnavailableProtection(player)) return;
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
            if (!Server.isVectorDefenseActive(player)) {
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
            if (event.getEntity() instanceof ServerPlayer player
                    && Server.isVectorDefenseActive(player)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
            if (event.getAffectedEntity() instanceof ServerPlayer player
                    && Server.isVectorDefenseActive(player)) {
                event.setKnockbackVelocity(Vec3.ZERO);
            }
        }

        @SubscribeEvent
        public static void onDrown(LivingDrownEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !Server.isVectorDefenseActive(player)) return;
            player.setAirSupply(player.getMaxAirSupply());
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!Server.isVectorDefenseActive(player)) {
                Server.forceDeactivateForDeath(player);
                return;
            }
            if (!Server.shouldForceAlive(player)) {
                Server.forceDeactivateForDeath(player);
                return;
            }
            event.setCanceled(true);
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
