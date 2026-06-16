package org.academy.internal.common.ability.accelerator.skills.lv2;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
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

public class KineticSuperposition extends Skill {
    public KineticSuperposition() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL2)
                .cpCost(55)
                .iterationTicks(10)
                .passive()
                .maintenanceCost(55)
                .maxStacks(1)
                .dependsOn(Skills.KINETIC_ENERGY_APPLIED)
        );
    }

    public float getExtraDamage(int level) {
        if (level >= 3) return 6.0f;
        return 4.0f;
    }

    public float getHeavyWeaponBonus(int level) {
        if (level >= 2) return 0.2f;
        return 0.0f;
    }

    public int getHasteLevel(int level) {
        if (level >= 3) return 1;
        if (level >= 1) return 0;
        return -1;
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
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_P)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT))
                        )
                )
        ), Client::onToggle);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.KINETIC_SUPERPOSITION + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            MisakaNetworkClient.sendPacket(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public KineticSuperposition.Client.Config getDefault() {
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
            Skills.KINETIC_SUPERPOSITION.get().toggle(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.KINETIC_SUPERPOSITION.get();
            if (!skill.isEnabled(player)) return;
            var level = skill.getLevel(player);
            var hasteLevel = skill.getHasteLevel(level);
            if (hasteLevel >= 0) {
                var existing = player.getEffect(MobEffects.HASTE);
                if (existing == null || existing.getAmplifier() < hasteLevel || existing.getDuration() < 40) {
                    player.addEffect(new MobEffectInstance(MobEffects.HASTE, 100, hasteLevel, false, false));
                }
            }
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            var source = event.getSource();
            var attacker = source.getEntity();
            if (!(attacker instanceof ServerPlayer player)) return;
            var skill = Skills.KINETIC_SUPERPOSITION.get();
            if (!skill.isEnabled(player)) return;
            if (!source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypes.MOB_ATTACK)) return;
            var level = skill.getLevel(player);
            var extraDamage = skill.getExtraDamage(level);
            var heldItem = player.getMainHandItem().getItem();
            if (heldItem instanceof AxeItem || heldItem instanceof MaceItem || heldItem instanceof TridentItem) {
                extraDamage *= (1.0f + skill.getHeavyWeaponBonus(level));
            }
            var target = event.getEntity();
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, target.damageSources().magic(), extraDamage);
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
            return PacketTypes.KINETIC_SUPERPOSITION_TOGGLE.get();
        }
    }
}
