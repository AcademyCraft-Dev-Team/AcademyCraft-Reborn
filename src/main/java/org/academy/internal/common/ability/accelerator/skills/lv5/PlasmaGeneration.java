package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
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

import java.util.*;

public class PlasmaGeneration extends Skill {
    private static final int MAX_CHARGE_MS = 20_000;
    private static final int MIN_CHARGE_MS = 3_000;
    private static final float BASE_DAMAGE = 50f;

    public PlasmaGeneration() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL5)
                .cpCost(500)
                .iterationTicks(0)
                .maxStacks(1)
                .dependsOn(Skills.STORM_WING)
        );
    }

    public float getDamage(long chargeMs) {
        if (chargeMs < MIN_CHARGE_MS) return 0;
        var secondsOver3 = (chargeMs - MIN_CHARGE_MS) / 1000f;
        if (secondsOver3 < 12) return BASE_DAMAGE + secondsOver3 * 50;
        return BASE_DAMAGE + 12 * 50 + (secondsOver3 - 12) * 100;
    }

    protected float getExplosionRadius(long chargeMs) {
        if (chargeMs < MIN_CHARGE_MS) return 0;
        var seconds = chargeMs / 1000f;
        return Math.min(3f + seconds * 0.3f, 12f);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_CHARGE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CHARGE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_C)), GLFW.GLFW_PRESS,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_CONTROL)))))
        , Client::onChargeStart);
        InputSystem.addKeyBinding(Client.KEY_NAME_FIRE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_FIRE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_C)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_CONTROL)))))
        , Client::onFire);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_CHARGE = SkillNames.PLASMA_GENERATION + "_charge";
        public static final String KEY_NAME_FIRE = SkillNames.PLASMA_GENERATION + "_fire";
        public static Config CONFIG = new Config();
        private static long chargeStartTime;

        public static void onChargeStart() {
            chargeStartTime = System.nanoTime();
        }

        public static void onFire() {
            var elapsedMs = (System.nanoTime() - chargeStartTime) / 1_000_000f;
            if (elapsedMs < MIN_CHARGE_MS) {
                MisakaNetworkClient.send(new FirePacket(0));
                return;
            }
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                var lookVec = mc.player.getViewVector(1.0f);
                MisakaNetworkClient.send(new FirePacket((long) elapsedMs, lookVec));
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public PlasmaGeneration.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handleFire(FirePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var chargeMs = packet.chargeMs();
            if (chargeMs < MIN_CHARGE_MS) return;

            Skills.PLASMA_GENERATION.get().executeActive(player, (ctx, actualCost) -> {
                var skill = Skills.PLASMA_GENERATION.get();
                var level = player.level();
                if (!(level instanceof ServerLevel serverLevel)) return;

                var damage = skill.getDamage(chargeMs);
                var radius = skill.getExplosionRadius(chargeMs);
                var eyePos = player.getEyePosition().add(0, 16, 0);
                var dir = packet.direction();
                var targetPos = eyePos.add(dir.scale(24));

                serverLevel.explode(player, null, null, targetPos.x, targetPos.y, targetPos.z,
                        radius, false, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                var nearby = level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(
                                targetPos.add(-radius, -radius, -radius),
                                targetPos.add(radius, radius, radius)),
                        e -> e != player && e.isAlive());
                for (var target : nearby) {
                    var dist = target.distanceToSqr(targetPos);
                    var falloff = Math.max(0, 1.0 - dist / (radius * radius));
                    target.hurtServer(serverLevel, player.damageSources().explosion(player, player),
                            (float) (damage * falloff));
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class FirePacket extends Packet<ServerGamePacketListenerImpl, FirePacket> {
        private final long chargeMs;
        private final Vec3 direction;
        public static final StreamCodec<ByteBuf, FirePacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, FirePacket::chargeMs,
                ByteBufCodecs.fromCodec(Vec3.CODEC), FirePacket::direction,
                FirePacket::new);

        public FirePacket(long chargeMs) { this(chargeMs, Vec3.ZERO); }
        public FirePacket(long chargeMs, Vec3 direction) { this.chargeMs = chargeMs; this.direction = direction; }
        public long chargeMs() { return chargeMs; }
        public Vec3 direction() { return direction; }

        @Override
        public PacketType<ServerGamePacketListenerImpl, FirePacket> getPacketType() {
            return PacketTypes.PLASMA_GENERATION_FIRE.get();
        }
    }
}