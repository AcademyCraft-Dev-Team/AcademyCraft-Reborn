package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.Projectile;
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
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AirflowField;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class VacuumDomain extends Skill {
    static final double RADIUS = 12.0;
    private static final double MAX_TARGET_DISTANCE = 16.0;
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final float DAMAGE_FRACTION = 0.05f;

    public VacuumDomain() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(120)
                .iterationTicks(120)
                .maxStacks(1)
                .dependsOn(Skills.PRESSURE_LOCK)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_CAST,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_CAST,
                        InputSystem.combo(
                                InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_Y,
                                InputSystem.ANY_ACTION,
                                0
                        )
                ),
                Client::handleInput
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    static boolean isInsideDomain(Vec3 center, Vec3 target) {
        return isInsideDomain(center, target, RADIUS);
    }

    static boolean isInsideDomain(Vec3 center, Vec3 target, double radius) {
        return target.distanceToSqr(center) <= radius * radius;
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.VACUUM_DOMAIN.get(),
                        List.of(PressureLock.Client.SKILL_INFO),
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
            skill.executeActive(player, context -> skill.getCpCost(context.level())
                    * AeromanipConfig.cpMultiplier(player, SkillNames.VACUUM_DOMAIN), (_, _) -> {
                if (!(player.level() instanceof ServerLevel level)) return;
                var center = resolveTargetPoint(level, player);
                var range = RADIUS * AeromanipConfig.rangeMultiplier(player, SkillNames.VACUUM_DOMAIN);
                var field = new AirflowField(java.util.UUID.randomUUID(), player.getUUID(), level.dimension(),
                        AirflowField.Type.VACUUM, AirflowField.Shape.SPHERE, center, player.getLookAngle(),
                        range, 0.0, 1.0f, Integer.MAX_VALUE);
                AeromanipFieldManager.activate(player, skill, field, Server::tick);
            });
        }

        private static Vec3 resolveTargetPoint(ServerLevel level, ServerPlayer player) {
            var eye = player.getEyePosition();
            var look = player.getLookAngle();
            if (look.lengthSqr() <= 1.0e-6) return eye;

            var end = eye.add(look.normalize().scale(MAX_TARGET_DISTANCE));
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
            if (ticks <= 40) {
                for (var entity : level.getEntities(player, field.bounds(), Entity::isAlive)) {
                    if (!(entity instanceof Projectile)
                            || !field.contains(entity.getBoundingBox().getCenter(), entity.getBbWidth() * 0.5)) continue;
                    var velocity = entity.getDeltaMovement();
                    AeromanipTargeting.addClampedVelocity(entity, velocity.scale(-0.8));
                }
            }
            if (ticks <= 40 || ticks % DAMAGE_INTERVAL_TICKS != 0) return;
            var box = new AABB(
                    center.subtract(radius, radius, radius),
                    center.add(radius, radius, radius)
            );
            var targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    box,
                    target -> isHostileTarget(player, target)
                            && isInsideDomain(center, target.getBoundingBox().getCenter(), radius)
            );
            var source = SkillDamageSource.of(player, Skills.VACUUM_DOMAIN.get());
            var system = AbilitySystemServer.getSystem(player);
            var power = system.getPlayerAbilityPowerMultiplier(player.getUUID())
                    * system.getPlayerDamageMultiplier(player.getUUID());
            for (var target : targets) {
                if (target instanceof ServerPlayer targetPlayer && Skills.BREATHING_FILM.get().isEnabled(targetPlayer)) {
                    target.setAirSupply(target.getMaxAirSupply());
                } else {
                    target.setAirSupply(Math.max(0, target.getAirSupply() - 4));
                }
                if (ticks < 100 || ticks % 20 != 0 || isPercentDamageImmune(target)) continue;
                target.invulnerableTime = 0;
                var damage = Math.max(1.0f, target.getMaxHealth() * DAMAGE_FRACTION)
                        * AeromanipConfig.damageMultiplier(player, SkillNames.VACUUM_DOMAIN) * power;
                if (target instanceof ServerPlayer) damage = Math.min(4.0f, damage * 0.4f);
                target.hurtServer(level, source, damage);
            }
        }

        private static void spawnVisual(ServerLevel level, Vec3 center, double radius, int ticks) {
            if (ticks % 4 != 0) return;
            for (var segment = 0; segment < 24; segment++) {
                var angle = segment * Math.PI * 2.0 / 24.0 + ticks * 0.025;
                var point = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        point.x, point.y, point.z, 1, 0.08, 0.25, 0.08, 0.02);
            }
            level.sendParticles(ParticleTypes.CLOUD,
                    center.x, center.y, center.z, 6,
                    radius * 0.28, radius * 0.18, radius * 0.28, 0.0);
        }

        private static boolean isPercentDamageImmune(LivingEntity target) {
            return AeromanipTargeting.isBoss(target)
                    || target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)
                    || target instanceof AbstractGolem;
        }

        private static boolean isHostileTarget(ServerPlayer player, LivingEntity target) {
            if (target == player
                    || !target.isAlive()
                    || target.isRemoved()
                    || target instanceof Player && !AeromanipTargeting.canAffectNegatively(player, target)) {
                return false;
            }
            if (target instanceof TamableAnimal animal && animal.isOwnedBy(player)) {
                return false;
            }
            if (player.isAlliedTo(target)) return false;
            if (target instanceof Enemy) return true;
            return target instanceof Mob mob && mob.getTarget() == player;
        }

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
