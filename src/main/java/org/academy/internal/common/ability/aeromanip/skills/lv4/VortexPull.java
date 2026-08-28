package org.academy.internal.common.ability.aeromanip.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeContext;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.client.ability.aeromanip.AeromanipChargeHud;
import org.academy.internal.common.ability.aeromanip.AirflowField;
import org.academy.internal.common.ability.aeromanip.skills.lv3.RejectingWind;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
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

/** Tap pulse or maintained point vortex selected by a server-authoritative charge gesture. */
public final class VortexPull extends Skill {
    private static final double TARGET_RANGE = 16.0;

    public VortexPull() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .iterationTicks(15)
                .maxStacks(5)
                .dependsOn(Skills.REJECTING_WIND)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4)));
    }

    static double baseRadius(AeromanipChargeTier tier) {
        return switch (tier) {
            case INSTANT -> 7.0;
            case HALF -> 8.0;
            case FULL -> 12.0;
        };
    }

    static float baseStrength(AeromanipChargeTier tier) {
        return switch (tier) {
            case INSTANT -> 0.85f;
            case HALF -> 0.26f;
            case FULL -> 0.42f;
        };
    }

    static int baseDuration(AeromanipChargeTier tier) {
        return switch (tier) {
            case INSTANT -> 1;
            case HALF -> 80;
            case FULL -> 120;
        };
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var binding = Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_B,
                        InputSystem.ANY_ACTION, InputConstants.MOD_ALT));
        if (binding.action() != InputSystem.ANY_ACTION) {
            binding = new InputSystem.KeyCombination(
                    binding.type(), binding.keys(), InputSystem.ANY_ACTION, binding.modifiers(),
                    binding.availableWhenScreen(), binding.unbound());
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, binding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addMaintainedKeyBinding(
                Client.KEY_NAME_CAST, binding, _ -> Client.start(), _ -> Client.stop());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.VORTEX_PULL.get(),
                        List.of(RejectingWind.Client.SKILL_INFO),
                        R.textures.vortex_pull_icon,
                        190,
                        104));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_CAST = SkillNames.VORTEX_PULL + "_cast";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void start() {
            if (AbilitySystemClient.canUseSkill(Skills.VORTEX_PULL.get())) {
                AeromanipChargeHud.begin(Skills.VORTEX_PULL.get());
                MisakaNetworkClient.send(StartPacket.INSTANCE);
            }
        }

        private static void stop() {
            AeromanipChargeHud.end(Skills.VORTEX_PULL.get());
            MisakaNetworkClient.send(StopPacket.INSTANCE);
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

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
        private static final Map<ServerPlayer, ChargeContext> CHARGES = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.VORTEX_PULL.get();
            if (CHARGES.containsKey(player) || !skill.isEnabled(player)) return;
            var context = new ChargeContext(player);
            CHARGES.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var context = CHARGES.get(packet.getPacketListener().getPlayer());
            if (context != null) context.release();
        }

        private static final class ChargeContext extends AeromanipChargeContext {
            private ChargeContext(ServerPlayer player) {
                super(player, Skills.VORTEX_PULL.get());
            }

            @Override
            protected void onReleased(AeromanipChargeTier tier, long chargeTicks) {
                var skill = Skills.VORTEX_PULL.get();
                var cp = switch (tier) {
                    case INSTANT -> 24.0f;
                    case HALF -> 38.0f;
                    case FULL -> 55.0f;
                };
                var air = switch (tier) {
                    case INSTANT -> 24.0f;
                    case HALF -> 40.0f;
                    case FULL -> 64.0f;
                };
                skill.executeActiveWithResource(
                        player,
                        _ -> cp * AeromanipConfig.cpMultiplier(player, SkillNames.VORTEX_PULL),
                        _ -> air,
                        (_, _) -> cast(player, skill, tier));
            }

            @Override
            protected void onTierReached(AeromanipChargeTier tier) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.AIRFLOW_FIELD.get(), SoundSource.PLAYERS,
                        0.65f, tier == AeromanipChargeTier.FULL ? 0.6f : 0.8f);
                AeromanipVfx.vortex(player.level(),
                        player.position().add(0.0, 0.3, 0.0),
                        tier == AeromanipChargeTier.FULL ? 1.4 : 0.82);
            }

            @Override
            protected void onChargeEnded(boolean released) {
                CHARGES.remove(player, this);
            }
        }

        private static void cast(ServerPlayer player, VortexPull skill, AeromanipChargeTier tier) {
            if (!(player.level() instanceof ServerLevel level)) return;
            var center = targetCenter(player, level);
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var radius = baseRadius(tier)
                    * (milestone >= 2 ? 1.2 : 1.0)
                    * AeromanipConfig.rangeMultiplier(player, SkillNames.VORTEX_PULL);
            var duration = Math.max(1, Math.round(baseDuration(tier)
                    * (milestone >= 2 ? 1.2f : 1.0f)
                    * AeromanipConfig.durationMultiplier(player, SkillNames.VORTEX_PULL)));
            var strength = baseStrength(tier);
            var field = new AirflowField(
                    java.util.UUID.randomUUID(), player.getUUID(), level.dimension(),
                    AirflowField.Type.VORTEX, AirflowField.Shape.SPHERE,
                    center, player.getLookAngle(), radius, 0.0,
                    strength, duration, milestone);
            var capture = new ProjectileCapture();
            if (tier == AeromanipChargeTier.INSTANT) {
                pull(player, field, 0, capture, true);
                spawnBurst(level, center, radius);
                return;
            }
            AeromanipFieldManager.activate(
                    player, skill, field,
                    (owner, activeField, age) -> pull(owner, activeField, age, capture, false),
                    (owner, activeField, age) -> capture.release(owner));
        }

        private static Vec3 targetCenter(ServerPlayer player, ServerLevel level) {
            var eye = player.getEyePosition();
            var look = player.getLookAngle().normalize();
            var end = eye.add(look.scale(TARGET_RANGE));
            var hit = level.clip(new ClipContext(
                    eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            return hit.getType() == HitResult.Type.MISS
                    ? eye.add(look.scale(12.0))
                    : hit.getLocation().subtract(look.scale(0.35));
        }

        private static void pull(
                ServerPlayer owner,
                AirflowField field,
                int age,
                ProjectileCapture capture,
                boolean pulse
        ) {
            var handled = 0;
            var cap = ProficiencyPolicy.server(owner).maxBonusEntitiesPerTick();
            for (var target : owner.level().getEntities(owner, field.bounds().inflate(1.0), Entity::isAlive)) {
                if (handled++ >= cap) break;
                if (!field.contains(target.getBoundingBox().getCenter(), target.getBbWidth() * 0.5)) continue;
                if (!(target instanceof Projectile)
                        && !AeromanipTargeting.canAffectNegatively(owner, target)) continue;
                var delta = field.center().subtract(target.getBoundingBox().getCenter());
                if (delta.lengthSqr() <= 1.0e-8) continue;
                var multiplier = AeromanipTargeting.forceMultiplier(owner, target);
                if (multiplier <= 0.0) continue;
                if (!pulse && field.proficiencyMilestone() >= 3
                        && target instanceof Projectile projectile
                        && !isFriendlyProjectile(owner, projectile)
                        && capture.capture(owner, projectile)) {
                    continue;
                }
                var acceleration = field.strength() * multiplier;
                var maxSpeed = (pulse ? 1.8 : 1.35) * multiplier;
                EntityMotionGuard.runWithMotionSource(owner, () ->
                        AeromanipTargeting.accelerateAlong(target, delta, acceleration, maxSpeed));
                target.resetFallDistance();
            }
            if (!pulse) capture.hold(field, age);
        }

        private static void spawnBurst(ServerLevel level, Vec3 center, double radius) {
            AeromanipVfx.vortex(level, center, radius);
        }

        private static boolean isFriendlyProjectile(ServerPlayer owner, Projectile projectile) {
            var projectileOwner = projectile.getOwner();
            return projectileOwner == owner
                    || projectileOwner != null && owner.isAlliedTo(projectileOwner);
        }

        private static final class ProjectileCapture {
            private final List<CapturedProjectile> projectiles = new ArrayList<>();

            private boolean capture(ServerPlayer owner, Projectile projectile) {
                projectiles.removeIf(entry -> !entry.projectile().isAlive());
                if (projectiles.stream().anyMatch(entry -> entry.projectile() == projectile)) return true;
                var maximum = Math.min(16, ProficiencyPolicy.server(owner).maxCapturedProjectiles());
                if (projectiles.size() >= maximum) return false;
                var speed = Math.max(0.1, projectile.getDeltaMovement().length());
                projectiles.add(new CapturedProjectile(projectile, speed));
                projectile.setNoGravity(true);
                projectile.setDeltaMovement(Vec3.ZERO);
                projectile.hurtMarked = true;
                return true;
            }

            private void hold(AirflowField field, int age) {
                projectiles.removeIf(entry -> !entry.projectile().isAlive());
                for (var index = 0; index < projectiles.size(); index++) {
                    var projectile = projectiles.get(index).projectile();
                    var angle = age * 0.15
                            + index * Math.PI * 2.0 / Math.max(1, projectiles.size());
                    var orbit = Math.max(1.5, field.radius() * 0.35);
                    projectile.setPos(
                            field.center().x + Math.cos(angle) * orbit,
                            field.center().y + Math.sin(angle * 0.5),
                            field.center().z + Math.sin(angle) * orbit);
                    projectile.setDeltaMovement(Vec3.ZERO);
                    projectile.hurtMarked = true;
                }
            }

            private void release(ServerPlayer owner) {
                var direction = owner.getLookAngle();
                if (direction.lengthSqr() <= 1.0e-8) direction = new Vec3(0, 0, 1);
                direction = direction.normalize();
                for (var entry : projectiles) {
                    var projectile = entry.projectile();
                    if (!projectile.isAlive()) continue;
                    projectile.setNoGravity(false);
                    projectile.setDeltaMovement(direction.scale(entry.speed() * 0.75));
                    projectile.hurtMarked = true;
                }
                projectiles.clear();
            }
        }

        private record CapturedProjectile(Projectile projectile, double speed) {
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
            return PacketTypes.VORTEX_PULL_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> {
        public static final StopPacket INSTANCE = new StopPacket();
        public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() {
            return PacketTypes.VORTEX_PULL_STOP.get();
        }
    }
}
