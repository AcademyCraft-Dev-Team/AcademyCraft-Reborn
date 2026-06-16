package org.academy.internal.common.ability.teleport.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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

public class Shackle extends Skill {
    public Shackle() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
                .cpCost(80)
                .iterationTicks(8)
                .maxStacks(1)
                .dependsOn(Skills.MATTER_WARP)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_S)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.SHACKLE + "_use";
        public static Config CONFIG = new Config();
        public static void onUse() { MisakaNetworkClient.send(UsePacket.INSTANCE); }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public Shackle.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private static final int SHACKLE_DURATION = 40;
        private static final int DEBUFF_DURATION = 160;

        @SubscribePacket
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.SHACKLE.get().executeActive(player, (ctx, actualCost) -> {
                var distance = LevelUtil.getValidViewDistance(player, 8);
                var targetPos = player.getEyePosition().add(player.getLookAngle().scale(distance));
                var box = new AABB(targetPos.add(-1.5, -1.5, -1.5), targetPos.add(1.5, 1.5, 1.5));
                var targets = player.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive());

                if (!targets.isEmpty()) {
                    var target = targets.getFirst();
                    target.setDeltaMovement(0, 0, 0);
                    target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DEBUFF_DURATION, 3, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, DEBUFF_DURATION, 2, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_DURATION, 1, false, false));
                    target.hurtServer(player.level(), player.damageSources().playerAttack(player), 3.0f);
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);
        private UsePacket() {}
        @Override public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.SHACKLE_USE.get();
        }
    }
}
