package org.academy.internal.common.ability.meltdowner.skills.lv2;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.ParticleEffectWrapper;
import org.academy.internal.client.renderer.effect.TrailEffectWrapper;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.LinkedHashSet;
import java.util.Set;

public class SpreadingBlast extends Skill {
    public SpreadingBlast() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL2)
                .cpCost(50)
                .iterationTicks(6)
                .dependsOn(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM)
        );
    }

    public int getBeamCount(int level) {
        if (level >= 3) return 4;
        return 3;
    }

    public float getDamage(int level) {
        if (level >= 1) return 5.0f;
        return 4.0f;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Handler.INSTANCE);
        var skillKeyConfig = AcademyCraftClient.Config.INSTANCE.<Client.Config>getConfig(key);

        RendererManager.registerEffectRenderer(TrailEffectWrapper.INSTANCE);
        RendererManager.registerEffectRenderer(ParticleEffectWrapper.INSTANCE);

        InputSystem.addKeyBinding(Client.KEY_NAME_SHOOT, skillKeyConfig.getKeyBinding(
                Client.KEY_NAME_SHOOT,
                new InputSystem.InputPair(
                        InputSystem.InputType.MOUSE,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOUSE_BUTTON_LEFT)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT))
                        )
                )
        ), Client::handleKey);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_SHOOT = SkillNames.SPREADING_BLAST + "_shoot";

        public static void handleKey() {
            if (!org.academy.api.client.ability.AbilitySystemClient.canUseSkill(Skills.SPREADING_BLAST.get())) return;
            var p = Minecraft.getInstance().player;
            if (p != null) {
                var yaw = p.getYRot();
                var pitch = p.getXRot();
                var yawRad = Math.toRadians(yaw);
                var pitchRad = Math.toRadians(pitch);
                var eyePos = p.getEyePosition();
                var spreadAngle = 15.0;
                var beamCount = 3;
                var startAngle = -(beamCount - 1) * spreadAngle / 2.0;

                for (var i = 0; i < beamCount; i++) {
                    var offsetAngle = startAngle + i * spreadAngle;
                    var offsetYawRad = Math.toRadians(yaw + offsetAngle);
                    var dx = -Math.sin(offsetYawRad) * Math.cos(pitchRad);
                    var dy = -Math.sin(pitchRad);
                    var dz = Math.cos(offsetYawRad) * Math.cos(pitchRad);

                    var trail = TrailEffectWrapper.INSTANCE.createTrail(0.8f, 0.04f, 1.0f, 0.55f, 0.1f);
                    trail.addPoint((float) eyePos.x, (float) eyePos.y, (float) eyePos.z);
                }

                var emitter = ParticleEffectWrapper.INSTANCE.createEmitter(
                        (float) eyePos.x, (float) eyePos.y, (float) eyePos.z);
                emitter.setColor(1.0f, 0.55f, 0.1f);
                emitter.setEmissionRate(0);
                emitter.burst(15);
                emitter.setLifetime(0.8f, 0.3f);
            }
            MisakaNetworkClient.sendPacket(ShootPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Handler implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Handler();

                private Handler() {
                }

                @Override
                public SpreadingBlast.Client.Config getDefault() {
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
            var player = packet.getPacketListener().getPlayer();
            Skills.SPREADING_BLAST.get().executeActive(player, (ctx, actualCost) -> {
                var level = player.level();
                var beamCount = Skills.SPREADING_BLAST.get().getBeamCount(ctx.level());
                var damage = Skills.SPREADING_BLAST.get().getDamage(ctx.level());
                var yaw = player.getYRot();
                var pitch = player.getXRot();
                var yawRad = Math.toRadians(yaw);
                var pitchRad = Math.toRadians(pitch);
                var eyePos = player.getEyePosition();

                var spreadAngle = 15.0;
                var startAngle = -(beamCount - 1) * spreadAngle / 2.0;
                var range = 16.0;

                for (var i = 0; i < beamCount; i++) {
                    var offsetAngle = startAngle + i * spreadAngle;
                    var offsetYawRad = Math.toRadians(yaw + offsetAngle);

                    var dx = -Math.sin(offsetYawRad) * Math.cos(pitchRad);
                    var dy = -Math.sin(pitchRad);
                    var dz = Math.cos(offsetYawRad) * Math.cos(pitchRad);
                    var lookVec = new Vec3(dx, dy, dz);
                    var targetPos = eyePos.add(lookVec.scale(range));

                    var src = level.damageSources().magic();
                    LevelUtil.attackEntitiesAlongPath(level, eyePos, targetPos, 0.25f, src, damage);
                }
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
            return PacketTypes.SPREADING_BLAST_SHOOT.get();
        }
    }
}
