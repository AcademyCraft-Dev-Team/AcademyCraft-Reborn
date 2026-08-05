package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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

public final class AtmosphereBlastGun extends Skill {
    static final double LENGTH = 8.0;
    static final double HALF_WIDTH = 1.0;
    private static final float BASE_DAMAGE = 8.0f;
    private static final double KNOCKBACK_STRENGTH = 1.8;
    private static final double KNOCKBACK_UP = 0.45;

    public AtmosphereBlastGun() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(40)
                .iterationTicks(40)
                .maxStacks(1)
                .dependsOn(Skills.ATMOSPHERE_SHIELD)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
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
                                InputSystem.InputType.MOUSE,
                                InputConstants.MOUSE_BUTTON_LEFT,
                                InputConstants.RELEASE,
                                InputConstants.MOD_ALT
                        )
                ),
                _ -> Client.cast()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    static boolean isInsideBlastVolume(Vec3 eye, Vec3 look, Vec3 target, double targetRadius) {
        if (look.lengthSqr() <= 1.0e-6) return false;
        var direction = look.normalize();
        var toTarget = target.subtract(eye);
        var forward = toTarget.dot(direction);
        if (forward < 0 || forward > LENGTH) return false;
        var lateral = toTarget.subtract(direction.scale(forward)).length();
        return lateral <= HALF_WIDTH + Math.max(0, targetRadius);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.ATMOSPHERE_BLAST_GUN.get(),
                        List.of(AtmosphereShield.Client.SKILL_INFO),
                        R.textures.atmosphere_blast_gun_icon,
                        20,
                        104
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.ATMOSPHERE_BLAST_GUN + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (!AbilitySystemClient.canUseSkill(Skills.ATMOSPHERE_BLAST_GUN.get())) return;
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
        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level() instanceof ServerLevel level)) return;

            Skills.ATMOSPHERE_BLAST_GUN.get().executeActive(player, (context, _) -> {
                var eye = player.getEyePosition();
                var look = player.getLookAngle();
                if (look.lengthSqr() <= 1.0e-6) return;

                var searchBox = new AABB(eye, eye).inflate(LENGTH);
                var targets = level.getEntitiesOfClass(
                        LivingEntity.class,
                        searchBox,
                        target -> target != player
                                && target.isAlive()
                                && !target.isSpectator()
                                && !player.isAlliedTo(target)
                                && player.hasLineOfSight(target)
                                && isInsideBlastVolume(
                                eye,
                                look,
                                target.getBoundingBox().getCenter(),
                                target.getBbWidth() * 0.5
                        )
                );

                var damage = BASE_DAMAGE
                        * context.system().getPlayerAbilityPowerMultiplier(player.getUUID())
                        * context.system().getPlayerDamageMultiplier(player.getUUID());
                var source = SkillDamageSource.of(player, Skills.ATMOSPHERE_BLAST_GUN.get());
                var direction = look.normalize();
                for (var target : targets) {
                    if (target.hurtServer(level, source, damage)) {
                        applyKnockback(target, direction, source, damage);
                    }
                }
            });
        }

        private static void applyKnockback(
                LivingEntity target,
                Vec3 look,
                DamageSource source,
                float damage
        ) {
            var horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() <= 1.0e-6) {
                horizontal = look;
            }
            horizontal = horizontal.normalize();
            target.knockback(KNOCKBACK_STRENGTH, -horizontal.x, -horizontal.z, source, damage);
            var movement = target.getDeltaMovement();
            var y = Math.max(movement.y, KNOCKBACK_UP);
            if (Double.isFinite(movement.x) && Double.isFinite(y) && Double.isFinite(movement.z)) {
                target.setDeltaMovement(movement.x, y, movement.z);
                target.hurtMarked = true;
            }
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
            return PacketTypes.ATMOSPHERE_BLAST_GUN_CAST.get();
        }
    }
}
