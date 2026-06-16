package org.academy.internal.common.ability.accelerator.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.VectorFieldEffectWrapper;
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

public class VectorReduction extends Skill {
    private static final int POTION_DURATION = 30;

    public VectorReduction() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL3)
                .passive()
                .maintenanceCost(75)
                .iterationTicks(0)
                .dependsOn(Skills.VECTOR_ACCEL)
        );
    }

    public float getRadius(int level) {
        if (level >= 3) return 10.0f;
        return 6.0f;
    }

    public double getSlowdownPercent(int level) {
        if (level >= 2) return 0.80;
        return 0.50;
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(VectorFieldEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_N)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::onToggle);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.VECTOR_REDUCTION + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            VectorFieldEffectWrapper.INSTANCE.trigger(
                    (float) p.getX(), (float) p.getY(), (float) p.getZ(),
                    6, 6, 0.8f, 0.3f, 0.5f, 0.9f, 5.0f);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public VectorReduction.Client.Config getDefault() {
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
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.VECTOR_REDUCTION.get().toggle(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private static final double PROJECTILE_SLOW_FACTOR = 0.1;

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.VECTOR_REDUCTION.get();
            if (!skill.isEnabled(player)) return;
            var level = skill.getLevel(player);
            var radius = skill.getRadius(level);
            var slowdown = skill.getSlowdownPercent(level);

            var box = player.getBoundingBox().inflate(radius);

            var livingTargets = player.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive());
            for (var target : livingTargets) {
                var distance = target.distanceTo(player);
                var factor = 1.0 - slowdown * (1.0 - distance / radius);
                target.setDeltaMovement(target.getDeltaMovement().scale(factor));

                if (level >= 1) {
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, POTION_DURATION, 0, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, POTION_DURATION, 0, false, false));
                }
            }

            var projectileTargets = player.level().getEntitiesOfClass(Projectile.class, box,
                    Entity::isAlive);
            for (var proj : projectileTargets) {
                proj.setDeltaMovement(proj.getDeltaMovement().scale(PROJECTILE_SLOW_FACTOR));
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.VECTOR_REDUCTION_TOGGLE.get();
        }
    }
}
