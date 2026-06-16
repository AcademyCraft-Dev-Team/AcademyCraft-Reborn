package org.academy.internal.common.ability.teleport.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.client.renderer.effect.DistortionEffectWrapper;
import org.academy.internal.client.renderer.effect.ParticleEffectWrapper;
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

public class SpacialExcision extends Skill {
    public SpacialExcision() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .cpCost(0)
                .iterationTicks(60)
                .maxStacks(1)
                .dependsOn(Skills.COORDINATE_TELEPORT)
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(DistortionEffectWrapper.INSTANCE);
        RendererManager.registerEffectRenderer(ParticleEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_O)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_CONTROL)))))
        , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY = SkillNames.SPACIAL_EXCISION + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.sendPacket(ActivatePacket.INSTANCE);
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            DistortionEffectWrapper.INSTANCE.trigger(
                    (float) p.getX(), (float) p.getY(), (float) p.getZ(),
                    2.0f, 1.5f,
                    0.7f, 0.1f, 0.5f, 0.8f,
                    0.2f, 0.0f, 0.5f, 0.0f);
            var emitter = ParticleEffectWrapper.INSTANCE.createEmitter(
                    (float) p.getX(), (float) p.getY(), (float) p.getZ());
            emitter.setColor(0.6f, 0.2f, 0.8f);
            emitter.setEmissionRate(20);
            emitter.setLifetime(1.0f, 0.5f);
            emitter.setVelocity(0.8f, 0.4f);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public SpacialExcision.Client.Config getDefault() {
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
        public static void handle(ActivatePacket p) {
            var player = p.getPacketListener().getPlayer();
            Skills.SPACIAL_EXCISION.get().executeActive(player,
                    ctx -> {
                        var maxCP = AbilitySystemServer.getSystem(player).getPlayerMaxCP(player.getUUID());
                        return maxCP;
                    },
                    (ctx, actualCost) -> AbilitySystemServer.registerContext(new Context(player)));
        }
    }

    public static final class Context extends ServerContext {
        private static final int MAX_TICKS = 200;
        private static final int CHARGE_TICKS = 40;
        private static final float BASE_RADIUS = 2.0f;
        private static final float RADIUS_GROWTH = 0.05f;
        private static final float DAMAGE = 20.0f;
        private static final int EFFECT_INTERVAL = 10;

        private int ticks;
        private boolean ended;
        private boolean chargeCancelled;

        private Context(ServerPlayer p) {
            super(p);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre e) {
            ticks++;
            if (player.hasDisconnected() || !player.isAlive() || ticks >= MAX_TICKS) {
                end();
                return;
            }

            if (chargeCancelled) {
                end();
                return;
            }

            if (ticks < CHARGE_TICKS) return;

            if (ticks % EFFECT_INTERVAL == 0 && level() instanceof ServerLevel sl) {
                var center = player.position();
                var radius = BASE_RADIUS + ticks * RADIUS_GROWTH;

                var targets = sl.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(
                                center.x - radius, center.y - radius, center.z - radius,
                                center.x + radius, center.y + radius, center.z + radius),
                        target -> target != player && target.isAlive()
                                && target.position().distanceToSqr(center) <= radius * radius);

                for (var t : targets) {
                    t.hurtServer(sl, sl.damageSources().magic(), DAMAGE);
                }

                var intRadius = (int) Math.ceil(radius);
                var centerBlock = player.blockPosition();
                var radiusSq = radius * radius;
                for (var dx = -intRadius; dx <= intRadius; dx++) {
                    for (var dy = -intRadius; dy <= intRadius; dy++) {
                        for (var dz = -intRadius; dz <= intRadius; dz++) {
                            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
                            var pos = centerBlock.offset(dx, dy, dz);
                            var state = sl.getBlockState(pos);
                            if (!state.isAir() && state.getDestroySpeed(sl, pos) >= 0) {
                                sl.removeBlock(pos, false);
                            }
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public void onPlayerHurt(LivingIncomingDamageEvent ev) {
            if (ended) return;
            if (ev.getEntity() != player) return;
            if (ticks < CHARGE_TICKS) {
                chargeCancelled = true;
                ev.setCanceled(true);
                ev.setAmount(0);
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.SPACIAL_EXCISION_ACTIVATE.get();
        }
    }
}
