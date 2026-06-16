package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
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

import java.util.LinkedHashSet;
import java.util.Set;

public class BloodflowReverse extends Skill {
    public BloodflowReverse() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL5)
                .cpCost(100)
                .iterationTicks(10)
                .maxStacks(16)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME, Client.CONFIG.getKeyBinding(Client.KEY_NAME,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.KeyInfo(
                                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                                        GLFW.GLFW_RELEASE,
                                        new LinkedHashSet<>(
                                                Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)
                                        )
                                )
                        )
                ), Client::reverseBloodflow
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME = SkillNames.BLOODFLOW_REVERSE + "_use";
        public static Config CONFIG = new Config();

        public static void reverseBloodflow() {
            MisakaNetworkClient.sendPacket(ReverseBloodflowPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public BloodflowReverse.Client.Config getDefault() {
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
        private static final String EFFECT_KEY = "bloodflow_reverse_level";

        @SubscribePacket
        public static void onAction(ReverseBloodflowPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.BLOODFLOW_REVERSE.get().executeActive(player, (ctx, actualCost) -> {
                var serverLevel = player.level();
                if (!(serverLevel instanceof ServerLevel)) return;
                var lookVec = player.getViewVector(1.0f);
                var eyePos = player.getEyePosition();
                var targetPos = eyePos.add(lookVec.scale(4.5));
                var box = new AABB(targetPos.add(-1.5, -1.5, -1.5), targetPos.add(1.5, 1.5, 1.5));

                var targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive() && !e.isSpectator());
                if (targets.isEmpty()) return;
                var target = targets.getFirst();

                var currentStacks = getBloodflowStacks(target);
                var newStacks = currentStacks + 1;
                var amplifier = Math.min(newStacks - 1, 4);
                var duration = 200;

                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, amplifier));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
                target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, amplifier));

                var hpPercent = 0.20f + newStacks * 0.02f;
                var damage = target.getMaxHealth() * hpPercent;
                target.hurtServer(serverLevel, player.damageSources().magic(), Math.max(1.0f, damage));

                setBloodflowStacks(target, newStacks);
            });
        }

        private static int getBloodflowStacks(LivingEntity entity) {
            var data = entity.getPersistentData();
            if (data.contains(EFFECT_KEY)) {
                var val = data.getInt(EFFECT_KEY);
                if (val.isPresent()) return val.get();
            }
            return 0;
        }

        private static void setBloodflowStacks(LivingEntity entity, int stacks) {
            var data = entity.getPersistentData();
            data.putInt(EFFECT_KEY, stacks);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReverseBloodflowPacket extends Packet<ServerGamePacketListenerImpl, ReverseBloodflowPacket> {
        public static final ReverseBloodflowPacket INSTANCE = new ReverseBloodflowPacket();
        public static final StreamCodec<ByteBuf, ReverseBloodflowPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ReverseBloodflowPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReverseBloodflowPacket> getPacketType() {
            return PacketTypes.REVERSE_BLOODFLOW.get();
        }
    }
}