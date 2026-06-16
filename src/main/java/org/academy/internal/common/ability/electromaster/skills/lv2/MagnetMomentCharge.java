package org.academy.internal.common.ability.electromaster.skills.lv2;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.skill.LightOrb;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class MagnetMomentCharge extends Skill {
    public MagnetMomentCharge() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL2)
                .cpCost(40)
                .iterationTicks(15)
                .dependsOn(Skills.ARC_GENERATE)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_G)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))
                )
        ), Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.MAGNET_MOMENT_CHARGE + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public MagnetMomentCharge.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.MAGNET_MOMENT_CHARGE.get().executeActive(player, (ctx, actualCost) -> {
                var context = new Context(player);
                AbilitySystemServer.registerContext(context);
            });
        }
    }

    public static final class Context extends ServerContext {
        private final LightOrb orb;
        private int lifetime = 200;
        private boolean ended;

        private Context(ServerPlayer player) {
            super(player);
            var eyePos = player.getEyePosition();
            var lookDir = player.getLookAngle();
            var spawnPos = eyePos.add(lookDir.scale(2));
            orb = new LightOrb(player.level(), lifetime, 0.6f, null);
            orb.setPos(spawnPos);
            orb.setDeltaMovement(lookDir.scale(0.5));
            player.level().addFreshEntity(orb);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            lifetime--;
            if (player.hasDisconnected() || !player.isAlive() || lifetime <= 0 || orb.isRemoved()) {
                end();
                return;
            }

            var orbPos = orb.position();
            var radius = 4.0f;
            var box = orb.getBoundingBox().inflate(radius);
            var targets = level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive());

            if (level() instanceof ServerLevel serverLevel) {
                for (var target : targets) {
                    var dist = target.position().distanceTo(orbPos);
                    if (dist < radius) {
                        var pull = orbPos.subtract(target.position()).normalize().scale(0.1);
                        target.setDeltaMovement(target.getDeltaMovement().add(pull));
                        target.hurtMarked = true;
                    }
                }
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            if (!orb.isRemoved()) orb.discard();
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);
        private ActivatePacket() {}
        @Override public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.MAGNET_MOMENT_CHARGE_ACTIVATE.get();
        }
    }
}
