package org.academy.internal.common.ability.electromaster.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
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
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DamageRecursionGuard;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Comparator;

public class ElectricalContact extends Skill {
    public ElectricalContact() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .damage()
                .level(AbilityLevel.LEVEL1)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(10)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.ARC_GENERATE)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        var legacyBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD, InputConstants.KEY_H, InputConstants.PRESS, 0);
        var previousDefaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD, InputConstants.KEY_H,
                InputConstants.PRESS, InputConstants.MOD_ALT);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                InputConstants.PRESS, InputConstants.MOD_ALT);
        if (Client.CONFIG.containsKeyBinding(Client.KEY_NAME_TOGGLE)
                && (legacyBinding.equals(Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE))
                || previousDefaultBinding.equals(Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE)))) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_TOGGLE, defaultBinding);
            AcademyCraftClient.Config.INSTANCE.setConfig(key, Client.CONFIG);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE, defaultBinding),
                ctx -> Client.onToggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.ELECTRICAL_CONTACT + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.ELECTRICAL_CONTACT.get())) return;
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
        private static final int DAMAGE_INTERVAL = 20;
        private static final float DAMAGE_AMOUNT = 2.0f;
        private static final float RADIUS = 2.0f;
        private static final Object RETALIATION_RECURSION_GUARD = new Object();

        public static float calculateDamage(float abilityPower, float playerMultiplier) {
            return DAMAGE_AMOUNT * Math.max(0.0f, abilityPower) * Math.max(0.0f, playerMultiplier);
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.ELECTRICAL_CONTACT.get();
            if (!skill.isEnabled(player)) return;
            if (!AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                    player.getUUID(), skill.getMaintenanceCost(player), skill)) {
                if (skill.isEnabled(player)) skill.toggle(player);
                return;
            }
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var interval = milestone >= 2 ? 15 : DAMAGE_INTERVAL;
            if (player.level().getGameTime() % interval != 0) return;

            var level = player.level();
            var radius = milestone >= 2 ? 3.0f : RADIUS;
            var box = player.getBoundingBox().inflate(radius);
            var targets = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive() && !e.isSpectator()
                            && !player.isAlliedTo(e));

            if (!(level instanceof ServerLevel serverLevel)) return;
            var damageSource = SkillDamageSource.of(player, skill,
                    DamageTypes.ELECTRO_DAMAGE);
            var system = AbilitySystemServer.getSystem(player);
            var damage = calculateDamage(
                    system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                    system.getPlayerDamageMultiplier(player.getUUID())
            );
            for (var target : targets) {
                target.hurtServer(serverLevel, damageSource, damage);
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        target.getX(), target.getY(0.6), target.getZ(),
                        8, 0.25, 0.45, 0.25, 0.04);
            }
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (DamageRecursionGuard.isActive(RETALIATION_RECURSION_GUARD)) return;
            var skill = Skills.ELECTRICAL_CONTACT.get();
            if (skill.getLevel(player) < 1) return;
            if (!skill.isEnabled(player)) return;

            var attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity livingAttacker && livingAttacker != player
                    && !player.isAlliedTo(livingAttacker)) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    DamageRecursionGuard.runGuarded(RETALIATION_RECURSION_GUARD, () -> {
                        var system = AbilitySystemServer.getSystem(player);
                        var damage = calculateDamage(
                                system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                                system.getPlayerDamageMultiplier(player.getUUID())
                        );
                        livingAttacker.hurtServer(serverLevel,
                                SkillDamageSource.of(player, skill,
                                        DamageTypes.ELECTRO_DAMAGE),
                                damage);
                        if (skill.hasProficiencyMilestone(player, 3)) {
                            TimedSkillEffectRuntime.put(player, livingAttacker.getUUID(), skill,
                                    "conductive", 80, 1.0f);
                        }
                    });
                }
            }
        }

        @SubscribeEvent
        public static void onConductiveDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof LivingEntity target)
                    || !(event.getSource() instanceof SkillDamageSource skillSource)
                    || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                    || skillSource.getSkill().getCategory() != AbilityCategories.ELECTROMASTER.get()) return;
            var markSkill = Skills.ELECTRICAL_CONTACT.get();
            if (TimedSkillEffectRuntime.consume(attacker.getUUID(), target.getUUID(), markSkill,
                    "conductive", attacker.level().getGameTime()).isEmpty()) return;
            if (!(attacker.level() instanceof ServerLevel level)) return;
            var chained = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4.0),
                            candidate -> candidate != target && candidate != attacker && candidate.isAlive()
                                    && !attacker.isAlliedTo(candidate))
                    .stream().min(Comparator.comparingDouble(target::distanceToSqr)).orElse(null);
            if (chained != null) {
                chained.hurtServer(level, SkillDamageSource.of(attacker, skillSource.getSkill(),
                                DamageTypes.ELECTRO_DAMAGE),
                        event.getAmount() * 0.5f);
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
