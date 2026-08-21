package org.academy.internal.common.ability.meltdowner.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.academy.internal.common.ability.meltdowner.skills.lv3.LightShield;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.entity.skill.Smoke;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class JetStrike extends Skill {
    static final double DISTANCE = 8.0;
    static final double DAMAGE_RADIUS = 3.25;
    static final float BASE_DAMAGE = 10.0f;
    static final int TRAIL_TICKS = 10;
    static final double DASH_SPEED = 3;
    static final float JET_BEAM_LENGTH = 32.0f;

    public JetStrike() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(20)
                .iterationTicks(10)
                .maxStacks(1)
                .dependsOn(Skills.LIGHT_SHIELD)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Light Shield", "academy:light_shield"))
        );
    }

    static @Nullable Vec3 normalizeDirection(Vec3 direction) {
        return direction.lengthSqr() <= 1.0e-8 ? null : direction.normalize();
    }

    static float calculateDamage(float abilityPower, float playerMultiplier) {
        return MeltdownerBeamDamage.calculate(
                BASE_DAMAGE * Math.max(0.0f, abilityPower),
                0.0f,
                0.0f,
                playerMultiplier,
                false
        );
    }

    static Vec3 calculateDashVelocity(Vec3 delta) {
        var direction = normalizeDirection(delta);
        return direction == null ? Vec3.ZERO : direction.scale(DASH_SPEED);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_USE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, 0)
        ), ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MELTDOWNER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.JET_STRIKE.get(),
                        List.of(LightShield.Client.SKILL_INFO),
                        R.textures.jet_strike_icon,
                        160,
                        90
                )
        );
        public static final String KEY_NAME_USE = SkillNames.JET_STRIKE + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            if (!AbilitySystemClient.canUseSkill(Skills.JET_STRIKE.get())) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            MisakaNetworkClient.send(DashPacket.INSTANCE);
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
        @SubscribePacket
        public static void handle(DashPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.JET_STRIKE.get();
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var direction = normalizeDirection(player.getLookAngle());
            if (direction == null) return;
            var base = player.position().add(direction.scale(milestone >= 2 ? 10.0 : DISTANCE));
            var safe = findSafe(player, base);
            var delta = safe == null ? Vec3.ZERO : safe.subtract(player.position());
            skill.executeActive(player, (context, _) -> dash(player, delta, context.milestone()));
        }

        private static void dash(ServerPlayer player, Vec3 delta, int milestone) {
            var level = player.level();
            var velocity = calculateDashVelocity(delta);
            if (delta.lengthSqr() > 1.0e-8) {
                player.resetFallDistance();
                player.setDeltaMovement(velocity.x, velocity.y + 0.15, velocity.z);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }

            var skill = Skills.JET_STRIKE.get();
            var system = AbilitySystemServer.getSystem(player);
            if (velocity.lengthSqr() > 1.0e-8) {
                spawnJetBeam(player, velocity.normalize().scale(-1.0), skill, milestone, system);
            }
            var damage = calculateDamage(
                    system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                    system.getPlayerDamageMultiplier(player.getUUID())
            );
            var source = SkillDamageSource.of(player, skill);
            var radius = milestone >= 2 ? DAMAGE_RADIUS * 1.2 : DAMAGE_RADIUS;
            var targetBox = player.getBoundingBox().move(delta).inflate(radius);
            var targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    targetBox,
                    target -> target != player && target.isAlive() && !player.isAlliedTo(target)
            );
            for (var target : targets) target.hurtServer(level, source, damage);
            if (delta.lengthSqr() > 1.0e-8) {
                AbilitySystemServer.registerContext(new TrailContext(player, milestone, damage));
            }
        }

        private static void spawnJetBeam(
                ServerPlayer player,
                Vec3 beamDirection,
                Skill skill,
                int milestone,
                AbilitySystemServer system
        ) {
            var level = player.level();
            var beam = new HighSpeedElectronBeam(
                    EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
            beam.configure(
                    player,
                    skill,
                    0.0f,
                    0.0f,
                    system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                    system.getPlayerDamageMultiplier(player.getUUID()),
                    false,
                    false,
                    milestone
            );
            beam.setIgnoredTarget(player);
            beam.setAttackDelayTicks(0);
            beam.setBeamLength(JET_BEAM_LENGTH);
            beam.setBeamScale(1.0f);
            beam.setPos(player.getBoundingBox().getCenter());
            beam.setYRot((float) Math.toDegrees(Math.atan2(-beamDirection.x, beamDirection.z)));
            beam.setXRot((float) Math.toDegrees(-Math.asin(
                    Math.clamp(beamDirection.y, -1.0, 1.0))));
            if (level.addFreshEntity(beam)) {
                level.playSound(
                        null,
                        player,
                        SoundEvents.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                        SoundSource.PLAYERS,
                        1.0f,
                        1.0f
                );
            }
        }

        private static @Nullable Vec3 findSafe(ServerPlayer player, Vec3 base) {
            var origin = player.getBoundingBox();
            int[] dx = {0, 1, -1, 0, 0, 2, -2, 1, -1};
            int[] dz = {0, 0, 0, 1, -1, 0, 0, 2, -2};
            int[] dy = {0, 1, -1, 2, -2};
            for (var offsetY : dy) {
                for (var i = 0; i < dx.length; i++) {
                    var candidate = base.add(dx[i], offsetY, dz[i]);
                    var moved = origin.move(
                            candidate.x - player.getX(),
                            candidate.y - player.getY(),
                            candidate.z - player.getZ()
                    );
                    if (player.level().noCollision(player, moved)) return candidate;
                }
            }
            return null;
        }
    }

    public static final class TrailContext extends ServerContext {
        private final ServerLevel initialLevel;
        private final int proficiencyMilestone;
        private final float damage;
        private final java.util.Set<java.util.UUID> hitTargets = new java.util.HashSet<>();
        private int ticks = TRAIL_TICKS;

        private TrailContext(ServerPlayer player, int proficiencyMilestone, float damage) {
            super(player);
            initialLevel = player.level();
            this.proficiencyMilestone = proficiencyMilestone;
            this.damage = damage;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            if (player.level() != initialLevel || player.hasDisconnected() || !player.isAlive() || ticks-- <= 0) {
                unregister();
                return;
            }
            if (ticks % 2 == 0) {
                var smoke = new Smoke(EntityTypes.SMOKE.get(), initialLevel);
                smoke.setPos(player.position().add(0, 0.5, 0));
                initialLevel.addFreshEntity(smoke);
            }
            if (proficiencyMilestone >= 3) {
                var source = SkillDamageSource.of(player, Skills.JET_STRIKE.get());
                for (var target : initialLevel.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(1.25),
                        target -> target != player && target.isAlive() && !player.isAlliedTo(target)
                                && !hitTargets.contains(target.getUUID()))) {
                    hitTargets.add(target.getUUID());
                    target.hurtServer(initialLevel, source, damage * 0.4f);
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class DashPacket extends Packet<ServerGamePacketListenerImpl, DashPacket> {
        public static final DashPacket INSTANCE = new DashPacket();
        public static final StreamCodec<ByteBuf, DashPacket> CODEC = StreamCodec.unit(INSTANCE);

        private DashPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, DashPacket> getPacketType() {
            return PacketTypes.JET_STRIKE_DASH.get();
        }
    }
}
