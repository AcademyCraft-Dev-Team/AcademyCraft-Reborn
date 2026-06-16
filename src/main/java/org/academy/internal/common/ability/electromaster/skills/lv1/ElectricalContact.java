package org.academy.internal.common.ability.electromaster.skills.lv1;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
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

public class ElectricalContact extends Skill {
    public ElectricalContact() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL1)
                .passive()
                .maintenanceCost(15)
                .iterationTicks(40)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_H)),
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
        public static final String KEY_NAME_TOGGLE = SkillNames.ELECTRICAL_CONTACT + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public ElectricalContact.Client.Config getDefault() {
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
            Skills.ELECTRICAL_CONTACT.get().toggle(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private static final int DAMAGE_INTERVAL = 40;
        private static final float DAMAGE_AMOUNT = 2.0f;
        private static final float RADIUS = 2.0f;

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.ELECTRICAL_CONTACT.get();
            if (!skill.isEnabled(player)) return;
            if (player.level().getGameTime() % DAMAGE_INTERVAL != 0) return;

            var level = player.level();
            var box = player.getBoundingBox().inflate(RADIUS);
            var targets = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive() && !e.isSpectator()
                        && !player.isAlliedTo(e));

            if (!(level instanceof ServerLevel serverLevel)) return;
            var damageSource = player.damageSources().lightningBolt();
            for (var target : targets) {
                target.hurtServer(serverLevel, damageSource, DAMAGE_AMOUNT);
            }
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.ELECTRICAL_CONTACT.get();
            if (skill.getLevel(player) < 1) return;
            if (!skill.isEnabled(player)) return;

            var attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity livingAttacker && livingAttacker != player) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    livingAttacker.hurtServer(serverLevel, player.damageSources().lightningBolt(), DAMAGE_AMOUNT);
                }
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
            return PacketTypes.ELECTRICAL_CONTACT_TOGGLE.get();
        }
    }
}
