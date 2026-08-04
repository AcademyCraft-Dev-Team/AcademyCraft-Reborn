package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class AtmosphereShield extends Skill {
    private static final net.minecraft.resources.Identifier ATTACK_DAMAGE_MODIFIER_ID =
            AcademyCraft.academy("atmosphere_shield_attack_damage");
    private static final net.minecraft.resources.Identifier ATTACK_KNOCKBACK_MODIFIER_ID =
            AcademyCraft.academy("atmosphere_shield_attack_knockback");

    public AtmosphereShield() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(50)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.AIRFLOW_JET)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(
                                InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_N,
                                InputConstants.RELEASE,
                                InputConstants.MOD_ALT
                        )
                ),
                _ -> Client.toggle()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.ATMOSPHERE_SHIELD.get(),
                        List.of(AirflowJet.Client.SKILL_INFO),
                        R.textures.atmosphere_shield_icon,
                        20,
                        72
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.ATMOSPHERE_SHIELD + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.ATMOSPHERE_SHIELD.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
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
        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            Skills.ATMOSPHERE_SHIELD.get().toggle(packet.getPacketListener().getPlayer());
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            var skill = Skills.ATMOSPHERE_SHIELD.get();
            var enabled = skill.isEnabled(player);
            if (enabled) {
                var system = AbilitySystemServer.getSystem(player);
                enabled = player.isAlive() && !player.hasDisconnected() && system.ensurePermanentOccupation(
                        player.getUUID(),
                        skill.getMaintenanceCost(skill.getLevel(player)),
                        skill
                );
                if (!enabled && skill.isEnabled(player)) {
                    system.toggleSkill(player.getUUID(), skill.getKeyString());
                }
            }
            var power = enabled
                    ? AbilitySystemServer.getSystem(player).getPlayerAbilityPowerMultiplier(player.getUUID())
                    : 0;
            syncModifier(
                    player.getAttribute(Attributes.ATTACK_DAMAGE),
                    ATTACK_DAMAGE_MODIFIER_ID,
                    4.0 * power,
                    enabled
            );
            syncModifier(
                    player.getAttribute(Attributes.ATTACK_KNOCKBACK),
                    ATTACK_KNOCKBACK_MODIFIER_ID,
                    power,
                    enabled
            );
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
            if (!(event.getAmount() > 0.0f) || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) return;
            var skill = Skills.ATMOSPHERE_SHIELD.get();
            if (!skill.isEnabled(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            if (!system.tryTimedOccupation(player.getUUID(), 20.0f, skill, 10)) return;

            event.setAmount(0.0f);
            event.setCanceled(true);
            player.level().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK,
                    SoundSource.PLAYERS,
                    1.0f,
                    0.9f + player.getRandom().nextFloat() * 0.2f
            );
        }

        private static void syncModifier(
                AttributeInstance attribute,
                net.minecraft.resources.Identifier id,
                double amount,
                boolean enabled
        ) {
            if (attribute == null) return;
            var current = attribute.getModifier(id);
            if (!enabled) {
                if (current != null) attribute.removeModifier(id);
                return;
            }
            if (current != null && Double.compare(current.amount(), amount) == 0) return;
            if (current != null) attribute.removeModifier(id);
            attribute.addTransientModifier(new AttributeModifier(
                    id,
                    amount,
                    AttributeModifier.Operation.ADD_VALUE
            ));
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
            return PacketTypes.ATMOSPHERE_SHIELD_TOGGLE.get();
        }
    }
}
