package org.academy.internal.common.ability.meltdowner.skills.lv2;

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
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
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

public class ElectronBarrier extends Skill {
    public ElectronBarrier() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL2)
                .passive()
                .maintenanceCost(120)
                .iterationTicks(0)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_B)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT)))
                )
        ), Client::onToggle);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.ELECTRON_BARRIER + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public ElectronBarrier.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.ELECTRON_BARRIER.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) {
                var ctx = CONTEXT_MAP.remove(player);
                if (ctx != null) ctx.end();
                return;
            }
            if (CONTEXT_MAP.containsKey(player)) return;
            var context = new Context(player);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }
    }

    public static final class Context extends ServerContext {
        private static final float DAMAGE = 4.0f;
        private static final float RADIUS = 3.0f;
        private boolean ended;

        private Context(ServerPlayer player) {
            super(player);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.ELECTRON_BARRIER.get();
            if (!skill.isEnabled(player) || !player.isAlive() || player.hasDisconnected()) {
                end();
                return;
            }

            var lookDir = player.getLookAngle();
            var center = player.getEyePosition().add(lookDir.scale(1.5));
            var box = player.getBoundingBox().move(lookDir.scale(1.5)).inflate(RADIUS);
            var targets = level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive());

            if (level() instanceof ServerLevel serverLevel) {
                for (var target : targets) {
                    var toTarget = target.position().subtract(center);
                    var distance = toTarget.length();
                    if (distance < RADIUS + target.getBbWidth() / 2.0) {
                        target.hurtServer(serverLevel, player.damageSources().magic(), DAMAGE);
                        target.setRemainingFireTicks(40);
                        var knockback = toTarget.normalize().scale(1.5);
                        target.setDeltaMovement(knockback);
                        target.hurtMarked = true;
                    }
                }
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.CONTEXT_MAP.remove(player);
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);
        private TogglePacket() {}
        @Override public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.ELECTRON_BARRIER_TOGGLE.get();
        }
    }
}
