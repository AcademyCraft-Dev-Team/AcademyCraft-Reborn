package org.academy.internal.common.ability.meltdowner.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
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
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import net.minecraft.util.Mth;

public final class SingleHighSpeedElectronBeam extends Skill {
    public static final String CONFIG_ATTACK_DELAY_TICKS = "attackDelayTicks";
    public static final int DEFAULT_ATTACK_DELAY_TICKS = 10;
    public static final float BASE_DAMAGE = 16.0f;
    public static final float MAX_HEALTH_DAMAGE_RATIO = 0.01f;

    public SingleHighSpeedElectronBeam() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(15)
                .iterationTicks(10)
                .maxStacks(20)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    public static int getConfiguredAttackDelayTicks(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null || server.getAcademyCraftServer() == null) {
            return DEFAULT_ATTACK_DELAY_TICKS;
        }
        var settings = server.getAcademyCraftServer().getAbilityConfig().skills
                .get(SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM);
        var configuredDelay = settings == null
                ? DEFAULT_ATTACK_DELAY_TICKS
                : settings.floatMap.getOrDefault(
                CONFIG_ATTACK_DELAY_TICKS,
                (float) DEFAULT_ATTACK_DELAY_TICKS
        );
        return Mth.clamp(Math.round(configuredDelay), 0, 20 * 60);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Handler.INSTANCE);
        var skillKeyConfig = AcademyCraftClient.Config.INSTANCE.<Client.Config>getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_SHOOT, skillKeyConfig.getKeyBinding(
                Client.KEY_NAME_SHOOT,
                InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_LEFT,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), ctx -> Client.handleKey());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MELTDOWNER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                        List.of(),
                        R.textures.single_high_speed_electron_beam_icon,
                        15,
                        45
                )
        );
        public static final String KEY_NAME_SHOOT = SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM + "_shoot";

        public static void handleKey() {
            if (!AbilitySystemClient.canUseSkill(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get()))
                return;
            MisakaNetworkClient.send(ShootPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Handler implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Handler();

                private Handler() {
                }

                @Override
                public SingleHighSpeedElectronBeam.Client.Config getDefault() {
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
        public static void handle(ShootPacket packet) {
            tryAutomatedAttack(packet.getPacketListener().getPlayer());
        }

        public static boolean tryAutomatedAttack(ServerPlayer player) {
            return Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get().executeActive(player, (context, _) -> {
                var level = player.level();
                var beam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
                var eyePos = player.getEyePosition().add(0, -0.5, 0);
                var yaw = player.getYRot();
                var pitch = player.getXRot();
                var offsetFactor = 2.0;
                var random = player.getRandom();
                var randomOffsetX = (random.nextDouble() * 1.5 - 0.75) * offsetFactor;
                var randomOffsetZ = (random.nextDouble() * 1.5 - 0.75) * offsetFactor;
                var randomOffsetY = (random.nextDouble() * 0.5 - 0.25) * offsetFactor;
                var beamDistance = 1.75;
                var yawRad = (yaw) * Mth.DEG_TO_RAD;
                var pitchRad = (pitch) * Mth.DEG_TO_RAD;
                var spawnPos = eyePos.add(
                        -Mth.sin(yawRad) * Mth.cos(pitchRad) * beamDistance,
                        -Mth.sin(pitchRad) * beamDistance,
                        Mth.cos(yawRad) * Mth.cos(pitchRad) * beamDistance
                ).add(randomOffsetX, randomOffsetY, randomOffsetZ);
                var system = AbilitySystemServer.getSystem(player);
                beam.configure(
                        player,
                        Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                        BASE_DAMAGE,
                        MAX_HEALTH_DAMAGE_RATIO,
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID()),
                        Skills.RADIATION_INTENSIFY.get().isEnabled(player),
                        DestroyBlocksSetting.canDestroyBlocks(player, Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get()),
                        context.milestone()
                );
                var delay = getConfiguredAttackDelayTicks(player);
                if (context.milestone() >= 2) delay = Math.max(0, Math.round(delay * 0.75f));
                beam.setAttackDelayTicks(delay);
                if (context.milestone() >= 2) beam.setBeamLength(60.0f);
                beam.setPos(spawnPos);
                beam.setYRot(yaw);
                beam.setXRot(pitch);
                level.addFreshEntity(beam);
                level.playSound(null, player, SoundEvents.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ShootPacket extends Packet<ServerGamePacketListenerImpl, ShootPacket> {
        public static final ShootPacket INSTANCE = new ShootPacket();
        public static final StreamCodec<ByteBuf, ShootPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ShootPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ShootPacket> getPacketType() {
            return PacketTypes.SINGLE_HIGH_SPEED_ELECTRON_BEAM_SHOOT.get();
        }
    }
}
