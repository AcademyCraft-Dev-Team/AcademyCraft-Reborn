package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;

public final class VacuumDomain extends Skill {
    static final double RADIUS = 12.0;
    private static final double MAX_TARGET_DISTANCE = 16.0;
    private static final int DURATION_TICKS = 200;
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final float DAMAGE_FRACTION = 0.05f;

    public VacuumDomain() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(100)
                .iterationTicks(100)
                .maxStacks(1)
                .dependsOn(Skills.BREATHING_FILM)
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
                                InputConstants.RELEASE,
                                0
                        )
                ),
                _ -> Client.cast()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    static boolean isInsideDomain(Vec3 center, Vec3 target) {
        return target.distanceToSqr(center) <= RADIUS * RADIUS;
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.VACUUM_DOMAIN.get(),
                        List.of(BreathingFilm.Client.SKILL_INFO),
                        R.textures.vacuum_domain_icon,
                        130,
                        72
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.VACUUM_DOMAIN + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (!AbilitySystemClient.canUseSkill(Skills.VACUUM_DOMAIN.get())) return;
            MisakaNetworkClient.send(CastPacket.INSTANCE);
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
        private static final Map<Player, Context> ACTIVE = createContextMap();

        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (ACTIVE.containsKey(player)) return;

            Skills.VACUUM_DOMAIN.get().executeActive(player, (_, _) -> {
                if (!(player.level() instanceof ServerLevel level)) return;
                var context = new Context(player, resolveTargetPoint(level, player));
                ACTIVE.put(player, context);
                AbilitySystemServer.registerContext(context);
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
    }

    public static final class Context extends ServerContext {
        private final Vec3 center;
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private int ticks;
        private boolean ended;

        private Context(ServerPlayer player, Vec3 center) {
            super(player);
            this.center = center;
            dimension = player.level().dimension();
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            ticks++;
            if (player.hasDisconnected()
                    || !player.isAlive()
                    || !player.level().dimension().equals(dimension)
                    || !Skills.VACUUM_DOMAIN.get().isEnabled(player)
                    || ticks >= DURATION_TICKS) {
                end();
                return;
            }
            if (ticks == 1 || ticks % DAMAGE_INTERVAL_TICKS == 0) {
                applyPulse();
            }
        }

        private void applyPulse() {
            var level = level();
            var box = new AABB(
                    center.subtract(RADIUS, RADIUS, RADIUS),
                    center.add(RADIUS, RADIUS, RADIUS)
            );
            var targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    box,
                    target -> isHostileTarget(player, target)
                            && isInsideDomain(center, target.getBoundingBox().getCenter())
            );
            var source = SkillDamageSource.of(player, Skills.VACUUM_DOMAIN.get());
            var power = AbilitySystemServer.getSystem(player)
                    .getPlayerAbilityPowerMultiplier(player.getUUID());
            for (var target : targets) {
                target.setAirSupply(0);
                target.invulnerableTime = 0;
                var damage = Math.max(1.0f, target.getMaxHealth() * DAMAGE_FRACTION) * power;
                target.hurtServer(level, source, damage);
            }
        }

        private static boolean isHostileTarget(ServerPlayer player, LivingEntity target) {
            if (target == player
                    || !target.isAlive()
                    || target.isRemoved()
                    || target instanceof Player) {
                return false;
            }
            if (target instanceof TamableAnimal animal && animal.isOwnedBy(player)) {
                return false;
            }
            if (player.isAlliedTo(target)) return false;
            if (target instanceof Enemy) return true;
            return target instanceof Mob mob && mob.getTarget() == player;
        }

        private void end() {
            if (ended) return;
            ended = true;
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            Server.ACTIVE.remove(player, this);
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
            return PacketTypes.VACUUM_DOMAIN_CAST.get();
        }
    }
}
