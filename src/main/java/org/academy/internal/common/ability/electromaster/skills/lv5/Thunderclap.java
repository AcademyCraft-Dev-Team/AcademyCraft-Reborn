package org.academy.internal.common.ability.electromaster.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
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

public class Thunderclap extends Skill {
    public Thunderclap() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get()).level(AbilityLevel.LEVEL5).cpCost(0).iterationTicks(60).maxStacks(1).dependsOn(Skills.LIGHTNING_STORM));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Y)), GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
                , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY = SkillNames.THUNDERCLAP + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.send(UsePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Thunderclap.Client.Config getDefault() {
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
        public static void handle(UsePacket p) {
            var player = p.getPacketListener().getPlayer();
            Skills.THUNDERCLAP.get().executeActive(player,
                    _ -> AbilitySystemServer.getSystem(player).getPlayerMaxCP(player.getUUID()),
                    (_, _) -> {
                        if (player.level() instanceof ServerLevel l) {
                            var hit = player.pick(30, 1, false);
                            var bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, l);
                            bolt.setPos(hit.getLocation());
                            l.addFreshEntity(bolt);
                            var box = new net.minecraft.world.phys.AABB(hit.getLocation().add(-6, -6, -6), hit.getLocation().add(6, 6, 6));
                            for (var e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive()))
                                e.hurtServer(l, l.damageSources().lightningBolt(), 30);
                        }
                    });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);

        private UsePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.THUNDERCLAP_USE.get();
        }
    }
}
