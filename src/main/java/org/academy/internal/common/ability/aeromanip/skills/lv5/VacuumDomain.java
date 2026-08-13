package org.academy.internal.common.ability.aeromanip.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AirflowField;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.UUID;
import net.minecraft.util.Mth;

public final class VacuumDomain extends Skill {
    static final double RADIUS = 12.0;
    private static final double MAX_TARGET_DISTANCE = 16.0;
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final int PROJECTILE_STABILIZATION_TICKS = 40;
    private static final int VISUAL_INTERVAL_TICKS = 4;
    private static final int VISUAL_SEGMENTS = 16;
    private static final int VISUAL_RINGS = 3;
    private static final float DAMAGE_FRACTION = 0.05f;
    private static final float PERCENT_IMMUNE_DAMAGE = 1.0f;

    public VacuumDomain() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(50)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.ATMOSPHERIC_DOMINION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
        );
    }

    static boolean isInsideDomain(Vec3 center, Vec3 target) {
        return isInsideDomain(center, target, RADIUS);
    }

    static boolean isInsideDomain(Vec3 center, Vec3 target, double radius) {
        return target.distanceToSqr(center) <= radius * radius;
    }

    static int airSupplyInVacuum(boolean protectedByBreathingFilm, int maxAirSupply) {
        return protectedByBreathingFilm ? Math.max(0, maxAirSupply) : 0;
    }

    static float baseDamage(float maxHealth, boolean percentDamageImmune) {
        if (percentDamageImmune) return PERCENT_IMMUNE_DAMAGE;
        return Math.max(1.0f, Math.max(0.0f, maxHealth) * DAMAGE_FRACTION);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_Y,
                InputSystem.ANY_ACTION,
                0
        );
        var configuredBinding = Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST, defaultBinding);
        var maintainedBinding = maintainedBinding(configuredBinding);
        if (!maintainedBinding.equals(configuredBinding)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, maintainedBinding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addKeyBinding(
                Client.KEY_NAME_CAST,
                maintainedBinding,
                Client::handleInput
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.VACUUM_DOMAIN.get(),
                        List.of(),
                        R.textures.vacuum_domain_icon,
                        130,
                        72
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.VACUUM_DOMAIN + "_cast";
        public static Config CONFIG = new Config();
        private static boolean maintaining;

        private Client() {
        }

        private static void handleInput(InputSystem.BindingContext context) {
            if (context.action() == InputConstants.PRESS) {
                if (maintaining || !AbilitySystemClient.canUseSkill(Skills.VACUUM_DOMAIN.get())) return;
                maintaining = true;
                MisakaNetworkClient.send(new CastPacket(true));
            } else if (context.action() == InputConstants.RELEASE && maintaining) {
                maintaining = false;
                MisakaNetworkClient.send(new CastPacket(false));
            }
        }

        public static final class Config extends KeyBindingConfig {
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
            if (!packet.active()) {
                AeromanipFieldManager.endPlaced(player);
                return;
            }
            var skill = Skills.VACUUM_DOMAIN.get();
            skill.executeActive(player, context -> (skill.getCpCost(context.level())
                    + context.system().getPlayerMaxCP(player.getUUID()) * 0.2f)
                    * AeromanipConfig.cpMultiplier(player, SkillNames.VACUUM_DOMAIN), (context, _) -> {
                if (!(player.level() instanceof ServerLevel level)) return;
                var center = resolveTargetPoint(level, player, context.milestone());
                var baseRadius = context.milestone() >= 2 ? 14.0 : RADIUS;
                var range = baseRadius * AeromanipConfig.rangeMultiplier(player, SkillNames.VACUUM_DOMAIN);
                var field = new AirflowField(java.util.UUID.randomUUID(), player.getUUID(), level.dimension(),
                        AirflowField.Type.VACUUM, AirflowField.Shape.SPHERE, center, player.getLookAngle(),
                        range, 0.0, 1.0f, Integer.MAX_VALUE, context.milestone());
                AeromanipFieldManager.activate(player, skill, field, Server::tick);
                spawnVisual(level, center, range, 0);
            });
        }

        private static Vec3 resolveTargetPoint(ServerLevel level, ServerPlayer player, int milestone) {
            var eye = player.getEyePosition();
            var look = player.getLookAngle();
            if (look.lengthSqr() <= 1.0e-6) return eye;

            var maxDistance = milestone >= 2 ? 20.0 : MAX_TARGET_DISTANCE;
            var end = eye.add(look.normalize().scale(maxDistance));
            var blockHit = level.clip(new ClipContext(
                    eye,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            var blockPoint = blockHit.getType() == HitResult.Type.MISS
                    ? end
                    : blockHit.getLocation();
            var searchBox = new AABB(eye, blockPoint).inflate(1.0);
            var entityHit = ProjectileUtil.getEntityHitResult(
                    level,
                    player,
                    eye,
                    blockPoint,
                    searchBox,
                    entity -> entity instanceof LivingEntity
                            && entity != player
                            && entity.isAlive()
                            && !entity.isSpectator(),
                    0.3f
            );
            return entityHit != null
                    ? entityHit.getEntity().getBoundingBox().getCenter()
                    : blockPoint;
        }

        private static void tick(ServerPlayer player, AirflowField field, int ticks) {
            var level = player.level();
            var center = field.center();
            var radius = field.radius();
            spawnVisual(level, center, radius, ticks);
            var entityCap = ProficiencyPolicy.server(player).maxBonusEntitiesPerTick();
            if (ticks <= PROJECTILE_STABILIZATION_TICKS) {
                var handled = 0;
                for (var entity : level.getEntities(player, field.bounds(), Entity::isAlive)) {
                    if (handled++ >= entityCap) break;
                    if (!(entity instanceof Projectile)
                            || !field.contains(entity.getBoundingBox().getCenter(), entity.getBbWidth() * 0.5))
                        continue;
                    var velocity = entity.getDeltaMovement();
                    AeromanipTargeting.addClampedVelocity(entity, velocity.scale(-0.8));
                }
            }
            var box = new AABB(
                    center.subtract(radius, radius, radius),
                    center.add(radius, radius, radius)
            );
            var targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    box,
                    target -> canAffectTarget(player, target)
                            && isInsideDomain(center, target.getBoundingBox().getCenter(), radius)
            );
            var damageTick = shouldDealDamage(ticks);
            var source = damageTick ? SkillDamageSource.of(player, Skills.VACUUM_DOMAIN.get()) : null;
            var power = 0.0f;
            if (damageTick) {
                var system = AbilitySystemServer.getSystem(player);
                power = system.getPlayerAbilityPowerMultiplier(player.getUUID())
                        * system.getPlayerDamageMultiplier(player.getUUID());
            }
            var handled = 0;
            for (var target : targets) {
                if (handled++ >= entityCap) break;
                var protectedByBreathingFilm = target instanceof ServerPlayer targetPlayer
                        && Skills.BREATHING_FILM.get().isEnabled(targetPlayer);
                reduceAirSupply(target, protectedByBreathingFilm);
                if (field.proficiencyMilestone() >= 3) {
                    target.clearFire();
                    var pull = center.subtract(target.getBoundingBox().getCenter());
                    if (pull.lengthSqr() > 1.0e-8) {
                        AeromanipTargeting.addClampedVelocity(target, pull.normalize().scale(0.035));
                    }
                }
                if (!damageTick) continue;
                target.invulnerableTime = 0;
                var damage = baseDamage(target.getMaxHealth(), isPercentDamageImmune(target))
                        * AeromanipConfig.damageMultiplier(player, SkillNames.VACUUM_DOMAIN) * power;
                target.hurtServer(level, source, damage);
            }
            if (field.proficiencyMilestone() >= 3 && ticks % 10 == 0) {
                var removed = 0;
                for (var cloud : level.getEntitiesOfClass(AreaEffectCloud.class, box,
                        cloud -> isInsideDomain(center, cloud.position(), radius))) {
                    if (removed++ >= entityCap) break;
                    cloud.discard();
                }
            }
        }

        private static void spawnVisual(ServerLevel level, Vec3 center, double radius, int ticks) {
            if (ticks % VISUAL_INTERVAL_TICKS != 0) return;
            var phase = ticks * 0.025;
            for (var ring = 0; ring < VISUAL_RINGS; ring++) {
                for (var segment = 0; segment < VISUAL_SEGMENTS; segment++) {
                    var angle = segment * Math.PI * 2.0 / VISUAL_SEGMENTS + phase;
                    var point = boundaryPoint(center, radius, ring, angle);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true,
                            point.x, point.y, point.z, 1, 0.04, 0.04, 0.04, 0.01);
                }
            }
            level.sendParticles(ParticleTypes.GUST, true, true,
                    center.x, center.y, center.z, 2,
                    radius * 0.12, radius * 0.12, radius * 0.12, 0.0);
        }

        private static boolean isPercentDamageImmune(LivingEntity target) {
            return AeromanipTargeting.isBoss(target)
                    || target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)
                    || target instanceof AbstractGolem;
        }

        private static boolean canAffectTarget(ServerPlayer player, LivingEntity target) {
            if (target == player
                    || !target.isAlive()
                    || target.isRemoved()) {
                return false;
            }
            if (target instanceof TamableAnimal animal && animal.isOwnedBy(player)) {
                return false;
            }
            return AeromanipTargeting.canAffectNegatively(player, target);
        }

        private static void reduceAirSupply(LivingEntity target, boolean protectedByBreathingFilm) {
            var maxAirSupply = target.getMaxAirSupply();
            if (maxAirSupply <= 0) return;
            target.setAirSupply(airSupplyInVacuum(protectedByBreathingFilm, maxAirSupply));
        }

    }

    static boolean shouldDealDamage(int ticks) {
        return ticks > 0 && ticks % DAMAGE_INTERVAL_TICKS == 0;
    }

    static Vec3 boundaryPoint(Vec3 center, double radius, int ring, double angle) {
        var safeRadius = Math.max(0.0, radius);
        var cosine = Math.cos(angle) * safeRadius;
        var sine = Math.sin(angle) * safeRadius;
        return switch (Math.floorMod(ring, VISUAL_RINGS)) {
            case 0 -> center.add(cosine, 0.0, sine);
            case 1 -> center.add(cosine, sine, 0.0);
            default -> center.add(0.0, cosine, sine);
        };
    }

    static InputSystem.KeyCombination maintainedBinding(InputSystem.KeyCombination configured) {
        if (configured.action() == InputSystem.ANY_ACTION) return configured;
        return new InputSystem.KeyCombination(
                configured.type(),
                configured.keys(),
                InputSystem.ANY_ACTION,
                configured.modifiers(),
                configured.availableWhenScreen(),
                configured.unbound()
        );
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = ByteBufCodecs.BOOL.map(
                CastPacket::new,
                CastPacket::active
        );
        private final boolean active;

        private CastPacket(boolean active) {
            this.active = active;
        }

        public boolean active() {
            return active;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.VACUUM_DOMAIN_CAST.get();
        }
    }
}
