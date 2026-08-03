package org.academy.internal.common.ability.meltdowner.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
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

import java.util.Map;
import java.util.WeakHashMap;

public class BetaParticleStream extends Skill {
    public BetaParticleStream() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(40)
                .iterationTicks(10)
                .maxStacks(1)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_CHARGE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CHARGE,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS, InputConstants.MOD_ALT))
                , ctx -> Client.onChargeStart());
        InputSystem.addKeyBinding(Client.KEY_NAME_FIRE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_FIRE,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE, InputConstants.MOD_ALT))
                , ctx -> Client.onFire());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_CHARGE = SkillNames.BETA_PARTICLE_STREAM + "_charge";
        public static final String KEY_NAME_FIRE = SkillNames.BETA_PARTICLE_STREAM + "_release";
        public static Config CONFIG = new Config();
        private static boolean charging;

        public static void onChargeStart() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (charging || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.BETA_PARTICLE_STREAM.get())) return;
            charging = true;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        public static void onFire() {
            if (!charging) return;
            charging = false;
            MisakaNetworkClient.send(MulticastPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public BetaParticleStream.Client.Config getDefault() {
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
        private static final float DAMAGE_PER_BEAM = 6.0f;
        private static final float RANGE = 16.0f;
        private static final int TICKS_PER_CHARGE = 15;
        private static final int MAX_CHARGES = 5;
        private static final Map<ServerPlayer, Long> CHARGE_START_TICKS = new WeakHashMap<>();

        public static int getCharges(long startTick, long releaseTick) {
            return Math.clamp((int) (Math.max(0, releaseTick - startTick) / TICKS_PER_CHARGE),
                    1, MAX_CHARGES);
        }

        public static float calculateDamage(float playerMultiplier) {
            return DAMAGE_PER_BEAM * Math.max(0.0f, playerMultiplier);
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.BETA_PARTICLE_STREAM.get().isEnabled(player)) return;
            CHARGE_START_TICKS.put(player, player.level().getGameTime());
        }

        @SubscribePacket
        public static void handleMulticast(MulticastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var startTick = CHARGE_START_TICKS.remove(player);
            if (startTick == null) return;
            var charges = getCharges(startTick, player.level().getGameTime());
            Skills.BETA_PARTICLE_STREAM.get().executeActive(player, (ctx, actualCost) -> {
                var level = player.level();
                if (!(level instanceof ServerLevel serverLevel)) return;

                var lookVec = player.getLookAngle();
                var eyePos = player.getEyePosition();
                var spreadAngle = 10.0 * (charges - 1) / 2.0;
                var damage = calculateDamage(ctx.system()
                        .getPlayerDamageMultiplier(player.getUUID()));
                var source = SkillDamageSource.of(player, Skills.BETA_PARTICLE_STREAM.get());

                for (var i = 0; i < charges; i++) {
                    var offsetAngle = -spreadAngle + i * (2.0 * spreadAngle / Math.max(charges - 1, 1));
                    var offsetYawRad = Math.toRadians(player.getYRot() + offsetAngle);
                    var pitchRad = Math.toRadians(player.getXRot());

                    var dx = -Math.sin(offsetYawRad) * Math.cos(pitchRad);
                    var dy = -Math.sin(pitchRad);
                    var dz = Math.cos(offsetYawRad) * Math.cos(pitchRad);
                    var dirVec = new Vec3(dx, dy, dz);
                    var targetPos = eyePos.add(dirVec.scale(RANGE));

                    LevelUtil.attackEntitiesAlongPath(
                            serverLevel, eyePos, targetPos, 0.25f, source, damage, player
                    );
                    var delta = targetPos.subtract(eyePos);
                    for (var step = 0; step <= 12; step++) {
                        var point = eyePos.add(delta.scale(step / 12.0));
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.01);
                    }
                }
            });
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
            return PacketTypes.BETA_PARTICLE_STREAM_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MulticastPacket extends Packet<ServerGamePacketListenerImpl, MulticastPacket> {
        public static final MulticastPacket INSTANCE = new MulticastPacket();
        public static final StreamCodec<ByteBuf, MulticastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private MulticastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MulticastPacket> getPacketType() {
            return PacketTypes.BETA_PARTICLE_STREAM_MULTICAST.get();
        }
    }
}
