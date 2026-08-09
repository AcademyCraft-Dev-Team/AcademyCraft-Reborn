package org.academy.internal.common.ability.accelerator.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileInterceptionService;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileRedirects;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileStateAdapter;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectEffectPacket;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorContinuousInterceptionLeases;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorInterceptionTickets;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorCompatibilityEffectLimiter;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorEnvironmentalFeedbackController;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorDefenseFeedbackTickets;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorAttackAttributionResolver;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorAttackFingerprint;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorIncomingDamageCoordinator;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorMotionRedirects;
import org.academy.internal.common.world.damagesource.VectorRedirectedDamageSourceInfo;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public class VectorReduction extends Skill {
    private static final double INTERCEPT_MARGIN = 1.0;
    private static final double COS_60 = 0.5;
    private static final double SIN_60 = 0.8660254037844386;
    private static final double MAX_REFRACTION_VERTICAL_COMPONENT = 0.5;

    public VectorReduction() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(75)
                .iterationTicks(40)
                .dependsOn(Skills.KINETIC_ENERGY_APPLIED)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition(
                        "Kinetic Energy Applied", "academy:kinetic_energy_applied"))
        );
    }

    public static Vec3 refractedDirection(Vec3 viewDirection, Vec3 incomingDirection) {
        var view = normalizeOrZero(viewDirection);
        var incoming = normalizeOrZero(incomingDirection);
        if (view == Vec3.ZERO || incoming == Vec3.ZERO) return Vec3.ZERO;

        var tangent = incoming.subtract(view.scale(incoming.dot(view)));
        if (tangent.lengthSqr() < 1.0E-8) {
            tangent = view.cross(new Vec3(0.0, 1.0, 0.0));
            if (tangent.lengthSqr() < 1.0E-8) {
                tangent = view.cross(new Vec3(1.0, 0.0, 0.0));
            }
        }
        tangent = tangent.normalize();
        var rawDirection = view.scale(COS_60).add(tangent.scale(SIN_60)).normalize();
        return projectToUpwardElevationBand(rawDirection, view, incoming);
    }

    private static Vec3 projectToUpwardElevationBand(
            Vec3 direction,
            Vec3 viewDirection,
            Vec3 incomingDirection
    ) {
        var clampedY = Math.clamp(direction.y, 0.0, MAX_REFRACTION_VERTICAL_COMPONENT);
        var horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8) {
            horizontal = horizontalOrZero(viewDirection);
        }
        if (horizontal.lengthSqr() < 1.0E-8) {
            horizontal = horizontalOrZero(incomingDirection);
        }
        if (horizontal.lengthSqr() < 1.0E-8) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        }
        var horizontalLength = Math.sqrt(Math.max(0.0, 1.0 - clampedY * clampedY));
        return horizontal.normalize().scale(horizontalLength).add(0.0, clampedY, 0.0);
    }

    private static Vec3 horizontalOrZero(Vec3 direction) {
        if (direction == null) return Vec3.ZERO;
        var horizontal = new Vec3(direction.x, 0.0, direction.z);
        return horizontal.lengthSqr() < 1.0E-8 ? Vec3.ZERO : horizontal.normalize();
    }

    private static Vec3 normalizeOrZero(Vec3 value) {
        if (value == null || !Double.isFinite(value.lengthSqr()) || value.lengthSqr() < 1.0E-8) {
            return Vec3.ZERO;
        }
        return value.normalize();
    }

    @Override
    public void initClient() {
        VectorRedirectEffectPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N, InputConstants.PRESS, 0)
        ), ctx -> Client.onToggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(Skills.VECTOR_REDUCTION.get(), List.of(), R.textures.ability.accelerator.skill.vector_reduction.icon, 145, 53)
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.VECTOR_REDUCTION + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.VECTOR_REDUCTION.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public VectorReduction.Client.Config getDefault() {
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
        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.VECTOR_REDUCTION.get();
            if (!skill.isEnabled(player)) {
                VectorReflection.Server.forceDeactivate(player);
            }
            skill.toggle(player);
            if (!skill.isEnabled(player)) clearState(player);
        }

        public static boolean isActive(ServerPlayer player) {
            return canMaintain(player)
                    && !VectorReflection.Server.canMaintainLinearReflectionLease(player)
                    && AbilitySystemServer.getSystem(player)
                    .getPlayerAvailableCP(player.getUUID()) > 0.0f;
        }

        public static boolean canMaintain(ServerPlayer player) {
            return player != null
                    && player.connection != null
                    && !player.isSpectator()
                    && Skills.VECTOR_REDUCTION.get().isEnabled(player);
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            var skill = Skills.VECTOR_REDUCTION.get();
            var data = skill.getRuntimeData(player).orElse(null);
            if (data != null && data.isEnabled()) {
                var system = AbilitySystemServer.getSystem(player);
                system.toggleSkill(player.getUUID(), skill.getKeyString());
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            }
            clearState(player);
        }

        private static void clearState(ServerPlayer player) {
            VectorContinuousInterceptionLeases.clear(player);
            VectorInterceptionTickets.clear(player);
            VectorCompatibilityEffectLimiter.clear(player);
            VectorDefenseFeedbackTickets.clear(player);
        }

        public static boolean canRefractSource(ServerPlayer player, DamageSource source) {
            if (player == null || source == null
                    || VectorRedirectedDamageSourceInfo.isRedirected(source)) return false;
            var direct = source.getDirectEntity();
            if (source.getEntity() == player || direct == player) return false;
            if (direct instanceof Projectile projectile
                    && VectorProjectileRedirects.isRedirected(projectile)) return false;
            return direct == null || !VectorMotionRedirects.isRedirected(direct);
        }

        public static boolean tryAbsorbDamage(
                ServerPlayer player,
                DamageSource source,
                float incomingDamage
        ) {
            if (!isActive(player)
                    || VectorReflection.Server.canMaintainLinearReflectionLease(player)
                    || !canRefractSource(player, source)
                    || !(incomingDamage > 0.0f)
                    || !Float.isFinite(incomingDamage)) {
                return false;
            }
            var attribution = VectorAttackAttributionResolver.resolve(player, source);
            var incoming = attribution.effectDirection().scale(-1.0);
            if (normalizeOrZero(incoming) == Vec3.ZERO) incoming = player.getLookAngle().scale(-1.0);
            var direction = refractedDirection(player.getLookAngle(), incoming);
            if (normalizeOrZero(direction) == Vec3.ZERO) direction = player.getLookAngle();
            var mirrorPoint = player.getBoundingBox().getCenter();
            var finalDirection = direction;
            var effectKey = VectorAttackFingerprint.computeLeaseKey(
                    player.getId(), source, incoming);
            var executed = Skills.VECTOR_REDUCTION.get().executeContinuous(
                    player,
                    _ -> Math.max(1.0f, incomingDamage),
                    (_, _) -> VectorEnvironmentalFeedbackController.emitRefraction(
                            player,
                            source,
                            effectKey,
                            finalDirection,
                            mirrorPoint
                    ),
                    true
            );
            if (!executed) return false;
            player.invulnerableTime = 0;
            VectorDefenseFeedbackTickets.commitFull(player, source);
            return true;
        }

        public static boolean absorbAnomalousDamage(
                ServerPlayer player,
                DamageSource source,
                float incomingDamage
        ) {
            if (!VectorIncomingDamageCoordinator.isAnomalousDamage(incomingDamage)
                    || !canMaintain(player)
                    || VectorReflection.Server.canMaintainLinearReflectionLease(player)
                    || !canRefractSource(player, source)) {
                return false;
            }
            var attribution = VectorAttackAttributionResolver.resolve(player, source);
            var incoming = attribution.effectDirection().scale(-1.0);
            if (normalizeOrZero(incoming) == Vec3.ZERO) incoming = player.getLookAngle().scale(-1.0);
            var direction = refractedDirection(player.getLookAngle(), incoming);
            if (normalizeOrZero(direction) == Vec3.ZERO) direction = player.getLookAngle();
            var effectKey = VectorAttackFingerprint.computeLeaseKey(
                    player.getId(), source, incoming);
            VectorEnvironmentalFeedbackController.emitRefraction(
                    player,
                    source,
                    effectKey,
                    direction,
                    player.getBoundingBox().getCenter()
            );
            player.invulnerableTime = 0;
            VectorDefenseFeedbackTickets.commitFull(player, source);
            return true;
        }

        public static boolean tryRefractLinearAttack(
                ServerPlayer player,
                float incomingDamage,
                Vec3 mirrorPoint,
                Vec3 incomingDirection
        ) {
            return tryRefractLinearAttack(player, incomingDamage, mirrorPoint, incomingDirection, true);
        }

        public static boolean tryRefractLinearAttack(
                ServerPlayer player,
                float incomingDamage,
                Vec3 mirrorPoint,
                Vec3 incomingDirection,
                boolean emitFeedback
        ) {
            if (!isActive(player)
                    || VectorReflection.Server.canMaintainLinearReflectionLease(player)
                    || !(incomingDamage > 0.0f)
                    || !Float.isFinite(incomingDamage)
                    || !isFiniteVector(mirrorPoint)
                    || normalizeOrZero(incomingDirection) == Vec3.ZERO) {
                return false;
            }
            var skill = Skills.VECTOR_REDUCTION.get();
            return skill.executeContinuous(
                    player,
                    _ -> Math.max(1.0f, incomingDamage),
                    (_, _) -> {
                        if (emitFeedback) {
                            var direction = refractedDirection(player.getLookAngle(), incomingDirection);
                            VectorReflection.Server.spawnGlowCircle(player, direction, mirrorPoint);
                            VectorReflection.Server.playReflectionSound(player);
                        }
                    },
                    true
            );
        }

        private static boolean isFiniteVector(Vec3 value) {
            return value != null
                    && Double.isFinite(value.x)
                    && Double.isFinite(value.y)
                    && Double.isFinite(value.z);
        }

        public static boolean shouldRefractProjectileFor(ServerPlayer player, Projectile projectile) {
            if (!isActive(player)
                    || VectorReflection.Server.canMaintainLinearReflectionLease(player)
                    || projectile == null
                    || projectile.isRemoved()
                    || VectorProjectileRedirects.isRedirected(projectile)) {
                return false;
            }
            var owner = projectile.getOwner();
            if (owner == player || owner != null && owner.getUUID().equals(player.getUUID())) return false;
            var velocity = projectile.getDeltaMovement();
            if (normalizeOrZero(velocity) == Vec3.ZERO) return false;
            var toPlayer = player.getBoundingBox().getCenter().subtract(projectile.position());
            return toPlayer.lengthSqr() <= 1.0E-8 || velocity.dot(toPlayer) > 0.0;
        }

        public static boolean refractProjectile(ServerPlayer player, Projectile projectile) {
            if (!shouldRefractProjectileFor(player, projectile)) return false;
            var speed = projectile.getDeltaMovement().length();
            if (!Double.isFinite(speed)) return false;
            var refracted = refractedDirection(player.getLookAngle(), projectile.getDeltaMovement())
                    .scale(Math.max(speed, 1.5) * 1.2);
            if (normalizeOrZero(refracted) == Vec3.ZERO) return false;

            var skill = Skills.VECTOR_REDUCTION.get();
            return skill.executeContinuous(player, _ -> Math.max(1.0f, (float) speed), (_, _) -> {
                VectorProjectileRedirects.mark(projectile, player, VectorRedirectKind.REFRACTION);
                projectile.setOwner(player);
                var pushDistance = Math.max(player.getBbWidth(), 0.75) + 0.5;
                projectile.setPos(player.getBoundingBox().getCenter()
                        .add(refracted.normalize().scale(pushDistance)));
                VectorProjectileStateAdapter.applyRedirect(projectile, refracted);
                VectorReflection.Server.spawnGlowCircle(player, refracted, projectile.position());
                VectorReflection.Server.playReflectionSound(player);
            }, true);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.VECTOR_REDUCTION.get();
            if (!skill.isEnabled(player)) return;
            if (!AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                    player.getUUID(), skill.getMaintenanceCost(skill.getLevel(player)), skill)) {
                Server.forceDeactivate(player);
                return;
            }
            if (!Server.isActive(player)
                    || VectorReflection.Server.canMaintainLinearReflectionLease(player)) return;
            var box = player.getBoundingBox().inflate(INTERCEPT_MARGIN);
            for (var projectile : player.level().getEntitiesOfClass(Projectile.class, box, Entity::isAlive)) {
                if (VectorProjectileInterceptionService.intercept(player, projectile)) break;
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
            return PacketTypes.VECTOR_REDUCTION_TOGGLE.get();
        }
    }
}
