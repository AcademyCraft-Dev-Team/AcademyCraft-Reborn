package org.academy.internal.common.ability.accelerator.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorBlast;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.sounds.SoundEvents;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DirStrike extends Skill {
    public static final int EFFECT_RADIUS = 12;
    public static final int ATTACK_RADIUS = 12;
    private static final int AIRBORNE_RADIUS_BONUS = 6;
    private static final int EFFECT_MIN_Y_OFFSET = -3;
    private static final int EFFECT_MAX_Y_OFFSET = 5;
    private static final float BASE_DAMAGE = 12.0f;
    private static final double DIVE_SPEED = 2.5;
    private static final double GROUND_SECTOR_COS = Math.cos(Math.toRadians(45.0));

    public DirStrike() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(20)
                .iterationTicks(10)
                .maxStacks(1)
                .dependsOn(Skills.VECTOR_BLAST)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition("Vector Blast", "academy:vector_blast"))
        );
    }

    public static boolean isInsideAttackRadius(double xOffset, double zOffset) {
        return xOffset * xOffset + zOffset * zOffset <= ATTACK_RADIUS * ATTACK_RADIUS;
    }

    public static float getDamage(float abilityPower, float damageMultiplier) {
        return BASE_DAMAGE * Math.max(0.0f, abilityPower) * Math.max(0.0f, damageMultiplier);
    }

    @Override
    public void initClient() {
        DirStrikeVisualPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_PRESS,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_PRESS,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.PRESS, 0)),
                ctx -> Client.onAction(true));
        InputSystem.addKeyBinding(Client.KEY_NAME_RELEASE,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_RELEASE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.RELEASE, 0)),
                ctx -> Client.onAction(false));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DIR_STRIKE.get(),
                        List.of(VectorBlast.Client.SKILL_INFO),
                        R.textures.dir_strike_icon,
                        100,
                        110
                )
        );
        public static final String KEY_NAME_PRESS = SkillNames.DIR_STRIKE + "_press";
        public static final String KEY_NAME_RELEASE = SkillNames.DIR_STRIKE + "_release";
        public static Config CONFIG = new Config();

        private Client() {
        }

        public static void onAction(boolean pressed) {
            MisakaNetworkClient.send(new ActionPacket(pressed));
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
        private static final Set<UUID> DIVING_PLAYERS = ConcurrentHashMap.newKeySet();

        private Server() {
        }

        @SubscribePacket
        public static void onAction(ActionPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!packet.pressed()) {
                if (DIVING_PLAYERS.remove(player.getUUID())) {
                    EntityMotionGuard.runWithMotionSource(
                            player,
                            () -> player.setDeltaMovement(Vec3.ZERO)
                    );
                    player.hurtMarked = true;
                    player.fallDistance = 0.0;
                }
                return;
            }
            if (!Skills.DIR_STRIKE.get().isEnabled(player)) return;
            if (!player.onGround()) {
                DIVING_PLAYERS.add(player.getUUID());
                dive(player);
                return;
            }
            executeStrike(player, false);
        }

        private static void tick(ServerPlayer player) {
            if (!DIVING_PLAYERS.contains(player.getUUID())) return;
            if (!player.isAlive() || player.hasDisconnected()
                    || !Skills.DIR_STRIKE.get().isEnabled(player)) {
                DIVING_PLAYERS.remove(player.getUUID());
                return;
            }
            if (player.onGround() || player.verticalCollision) {
                DIVING_PLAYERS.remove(player.getUUID());
                EntityMotionGuard.runWithMotionSource(
                        player,
                        () -> player.setDeltaMovement(Vec3.ZERO)
                );
                player.hurtMarked = true;
                player.fallDistance = 0.0;
                executeStrike(player, true);
                return;
            }
            Skills.DIR_STRIKE.get().reportActivity(player, true);
            dive(player);
        }

        private static void dive(ServerPlayer player) {
            EntityMotionGuard.runWithMotionSource(
                    player,
                    () -> player.setDeltaMovement(0.0, -DIVE_SPEED, 0.0)
            );
            player.hurtMarked = true;
            player.fallDistance = 0.0;
        }

        private static void executeStrike(ServerPlayer player, boolean airborne) {
            var skill = Skills.DIR_STRIKE.get();
            skill.executeActive(player, (ctx, actualCost) -> {
                var level = player.level();
                var playerPos = player.blockPosition();
                var baseRadius = skill.hasProficiencyMilestone(player, 2) ? 14 : ATTACK_RADIUS;
                var radius = airborne ? baseRadius + AIRBORNE_RADIUS_BONUS : baseRadius;
                var look = horizontalLook(player);
                level.playSound(null, playerPos, SoundEvents.DIR_STRIKE.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                DirStrikeVisualPacket.broadcast(
                        level, player.position(), playerPos, radius, airborne, look);

                var minY = playerPos.getY() + EFFECT_MIN_Y_OFFSET;
                var maxY = playerPos.getY() + EFFECT_MAX_Y_OFFSET + 1;
                var center = player.position();
                var area = new AABB(
                        center.x - radius, minY, center.z - radius,
                        center.x + radius, maxY, center.z + radius
                );
                var damage = getDamage(
                        ctx.system().getPlayerAbilityPowerMultiplier(player.getUUID()),
                        ctx.system().getPlayerDamageMultiplier(player.getUUID())
                );
                var source = SkillDamageSource.of(
                        player,
                        skill,
                        org.academy.internal.common.world.damagesource.DamageTypes.VEC
                );
                var targets = level.getEntitiesOfClass(LivingEntity.class, area,
                        target -> target != player
                                && target.isAlive()
                                && !target.isSpectator()
                                && !player.isAlliedTo(target)
                                && target.getY() >= minY
                                && target.getY() <= maxY
                                && isInsideStrikeArea(
                                target.getX() - center.x,
                                target.getZ() - center.z,
                                radius,
                                airborne,
                                look
                        ));
                for (var target : targets) {
                    target.hurtServer(level, source, damage);
                    var velocity = target.getDeltaMovement();
                    var centerTarget = skill.hasProficiencyMilestone(player, 3)
                            && target.distanceToSqr(center) <= 16.0;
                    target.setDeltaMovement(velocity.x, centerTarget ? -0.35 : 0.5, velocity.z);
                    target.hurtMarked = true;
                    if (centerTarget) {
                        TimedSkillEffectRuntime.schedule(player, 6, () -> {
                            if (!target.isAlive() || target.level() != level) return;
                            target.hurtServer(level, source, damage * 0.4f);
                        });
                    }
                }
            });
        }

        private static boolean isInsideStrikeArea(double xOffset, double zOffset, double radius,
                                                  boolean airborne, Vec3 look) {
            var distanceSquared = xOffset * xOffset + zOffset * zOffset;
            if (distanceSquared > radius * radius) return false;
            if (airborne || distanceSquared <= 1.0e-8) return true;
            var inverseDistance = 1.0 / Math.sqrt(distanceSquared);
            return (xOffset * look.x + zOffset * look.z) * inverseDistance >= GROUND_SECTOR_COS;
        }

        private static Vec3 horizontalLook(ServerPlayer player) {
            var look = player.getLookAngle();
            var horizontal = new Vec3(look.x, 0.0, look.z);
            return horizontal.lengthSqr() <= 1.0e-8
                    ? new Vec3(0.0, 0.0, 1.0)
                    : horizontal.normalize();
        }

    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            Server.DIVING_PLAYERS.remove(event.getEntity().getUUID());
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends Packet<ServerGamePacketListenerImpl, ActionPacket> {
        public static final StreamCodec<ByteBuf, ActionPacket> CODEC = ByteBufCodecs.BOOL
                .map(ActionPacket::new, ActionPacket::pressed);
        private final boolean pressed;

        public ActionPacket(boolean pressed) {
            this.pressed = pressed;
        }

        public boolean pressed() {
            return pressed;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActionPacket> getPacketType() {
            return PacketTypes.DIR_STRIKE.get();
        }
    }
}
