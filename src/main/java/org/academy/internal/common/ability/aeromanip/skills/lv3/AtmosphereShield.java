package org.academy.internal.common.ability.aeromanip.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.*;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.ability.aeromanip.skills.lv2.BreathingBubble;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class AtmosphereShield extends Skill {
    private static final float[] REDUCTION = {0.20f, 0.28f, 0.35f};
    private static final Identifier ATTACK_KNOCKBACK_MODIFIER_ID =
            AcademyCraft.academy("atmosphere_shield_attack_knockback");
    private static final Identifier TRUE_RESISTANCE_MODIFIER_ID =
            AcademyCraft.academy("atmosphere_shield_true_resistance");
    private static final Map<UUID, StoppedProjectile> STOPPED_PROJECTILES = new HashMap<>();

    public AtmosphereShield() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.BREATHING_BUBBLE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
        );
    }

    static float effectAirCost(float configured) {
        return Float.isFinite(configured) ? Math.max(0.0f, configured) : 8.0f;
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
                        List.of(BreathingBubble.Client.SKILL_INFO),
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
            if (!AbilitySystemClient.beginToggleRequest(Skills.ATMOSPHERE_SHIELD.get())) return;
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
        private static final Set<ServerPlayer> ACTIVE =
                Collections.newSetFromMap(new WeakHashMap<>());

        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.ATMOSPHERE_SHIELD.get().toggle(player);
            if (!Skills.ATMOSPHERE_SHIELD.get().isEnabled(player)) stop(player);
        }

        public static boolean isActive(ServerPlayer player) {
            return player != null && ACTIVE.contains(player)
                    && Skills.ATMOSPHERE_SHIELD.get().isEnabled(player);
        }

        private static void start(ServerPlayer player) {
            ACTIVE.add(player);
        }

        private static void stop(ServerPlayer player) {
            ACTIVE.remove(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private static final Set<ServerPlayer> BOOSTED_ATTACKERS =
                Collections.newSetFromMap(new WeakHashMap<>());
        private static final Map<ServerPlayer, Integer> DEFENSE_DEPTH = new WeakHashMap<>();

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
                        skill.getMaintenanceCost(player)
                                * AeromanipConfig.cpMultiplier(player, SkillNames.ATMOSPHERE_SHIELD),
                        skill
                );
                if (enabled) Server.start(player);
                if (!enabled) {
                    Server.stop(player);
                    system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                    if (skill.isEnabled(player)) {
                        system.toggleSkill(player.getUUID(), skill.getKeyString());
                    }
                }
            } else {
                Server.stop(player);
            }
            if (!enabled) clearTransientEffects(player);
            if (enabled && Server.isActive(player)) {
                stopNearbyProjectiles(player);
                if (player.level().getGameTime() % 12 == 0) {
                    AeromanipVfx.ring(player.level(),
                            player.position().add(0.0, player.getBbHeight() * 0.45, 0.0), 1.05);
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
            if (!(event.getAmount() > 0.0f)) return;
            var skill = Skills.ATMOSPHERE_SHIELD.get();
            if (!Server.isActive(player)) return;
            if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
                if (tryStopProjectile(player, projectile, skill)) event.setCanceled(true);
                return;
            }
            if (event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) return;
            if (!canAffordEffectAir(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            var immunityThreshold = skill.hasProficiencyMilestone(player, 3) ? 6.0f : 4.0f;
            var lowDamageCost = skill.adjustProficiencyCost(player, SkillProficiencyProfile.CostKind.DYNAMIC, 10.0f);
            if (event.getAmount() < immunityThreshold && system.tryTimedOccupation(
                    player.getUUID(), lowDamageCost, skill, 5
            ) && tryConsumeEffectAir(player)) {
                event.setCanceled(true);
                return;
            }
            var level = Math.max(0, Math.min(2, skill.getLevel(player)));
            var reduction = REDUCTION[level];
            if (skill.hasProficiencyMilestone(player, 3)) reduction = Math.min(0.5f, reduction + 0.1f);
            var prevented = event.getAmount() * reduction;
            var defenseCost = skill.adjustProficiencyCost(player, SkillProficiencyProfile.CostKind.DYNAMIC,
                    Math.min(30.0f, 4.0f + prevented * 2.0f)
                            * AeromanipConfig.cpMultiplier(player, SkillNames.ATMOSPHERE_SHIELD));
            if (!system.tryTimedOccupation(player.getUUID(),
                    defenseCost,
                    skill, 5)) return;
            if (!tryConsumeEffectAir(player)) return;
            beginDefenseResistance(player);
            event.setAmount(event.getAmount() - prevented);
            player.level().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK,
                    SoundSource.PLAYERS,
                    1.0f,
                    0.9f + player.getRandom().nextFloat() * 0.2f
            );
        }

        @SubscribeEvent
        public static void onDamageApplied(LivingDamageEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) endDefenseResistance(player);
        }

        @SubscribeEvent
        public static void onAttack(AttackEntityEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !event.getTarget().isAlive() || !Server.isActive(player)) return;
            if (!tryConsumeEffectAir(player)) {
                clearAttackBoost(player);
                return;
            }
            var power = AbilitySystemServer.getSystem(player)
                    .getPlayerAbilityPowerMultiplier(player.getUUID());
            syncModifier(
                    player.getAttribute(Attributes.ATTACK_KNOCKBACK),
                    ATTACK_KNOCKBACK_MODIFIER_ID,
                    0.5 * power,
                    true
            );
            BOOSTED_ATTACKERS.add(player);
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            for (var player : List.copyOf(BOOSTED_ATTACKERS)) clearAttackBoost(player);
            for (var player : List.copyOf(DEFENSE_DEPTH.keySet())) clearDefenseResistance(player);
            var now = event.getServer().overworld().getGameTime();
            STOPPED_PROJECTILES.values().removeIf(ticket -> ticket.expiresAt() < now);
        }

        private static void stopNearbyProjectiles(ServerPlayer player) {
            var handled = 0;
            var cap = ProficiencyPolicy.server(player).maxBonusEntitiesPerTick();
            for (var projectile : player.level().getEntitiesOfClass(
                    Projectile.class,
                    player.getBoundingBox().inflate(1.0),
                    projectile -> projectile.isAlive() && projectile.getOwner() != player
            )) {
                if (handled++ >= cap) break;
                tryStopProjectile(player, projectile, Skills.ATMOSPHERE_SHIELD.get());
            }
        }

        private static boolean tryStopProjectile(
                ServerPlayer player,
                Projectile projectile,
                Skill skill
        ) {
            var now = player.level().getGameTime();
            var ticket = STOPPED_PROJECTILES.get(projectile.getUUID());
            var alreadyStopped = ticket != null
                    && ticket.ownerId().equals(player.getUUID())
                    && ticket.expiresAt() >= now;
            if (!alreadyStopped && !tryConsumeEffectAir(player)) return false;
            if (!alreadyStopped) {
                STOPPED_PROJECTILES.put(projectile.getUUID(),
                        new StoppedProjectile(player.getUUID(), now + 20));
                expireStoppedProjectile(player, projectile, skill);
            }
            projectile.setDeltaMovement(Vec3.ZERO);
            projectile.hurtMarked = true;
            return true;
        }

        private static boolean canAffordEffectAir(ServerPlayer player) {
            var cost = configuredEffectAirCost(player);
            return AbilitySystemServer.getSystem(player).getAeromanipResourceManager()
                    .getCurrent(player) + 1.0e-4f >= cost;
        }

        private static boolean tryConsumeEffectAir(ServerPlayer player) {
            var consumed = AbilitySystemServer.getSystem(player).getAeromanipResourceManager()
                    .tryConsume(player, configuredEffectAirCost(player));
            if (consumed) Skills.ATMOSPHERE_SHIELD.get().reportActivity(player, true);
            return consumed;
        }

        private static float configuredEffectAirCost(ServerPlayer player) {
            return effectAirCost(AeromanipConfig.skillFloat(
                    player, SkillNames.ATMOSPHERE_SHIELD,
                    "compressedAirPerEffect", 8.0f));
        }

        private static void beginDefenseResistance(ServerPlayer player) {
            DEFENSE_DEPTH.merge(player, 1, Integer::sum);
            PlayerAttributeRuntime.syncTrueResistanceModifier(
                    player, TRUE_RESISTANCE_MODIFIER_ID, 6.0, true);
        }

        private static void endDefenseResistance(ServerPlayer player) {
            var depth = DEFENSE_DEPTH.getOrDefault(player, 0);
            if (depth <= 1) {
                clearDefenseResistance(player);
            } else {
                DEFENSE_DEPTH.put(player, depth - 1);
            }
        }

        private static void clearDefenseResistance(ServerPlayer player) {
            DEFENSE_DEPTH.remove(player);
            PlayerAttributeRuntime.syncTrueResistanceModifier(
                    player, TRUE_RESISTANCE_MODIFIER_ID, 0.0, false);
        }

        private static void clearAttackBoost(ServerPlayer player) {
            BOOSTED_ATTACKERS.remove(player);
            syncModifier(
                    player.getAttribute(Attributes.ATTACK_KNOCKBACK),
                    ATTACK_KNOCKBACK_MODIFIER_ID,
                    0.0,
                    false
            );
        }

        private static void clearTransientEffects(ServerPlayer player) {
            clearAttackBoost(player);
            clearDefenseResistance(player);
        }

        private static void expireStoppedProjectile(ServerPlayer player, Projectile projectile, Skill skill) {
            if (!skill.hasProficiencyMilestone(player, 2)) return;
            var now = player.level().getGameTime();
            if (TimedSkillEffectRuntime.get(player.getUUID(), projectile.getUUID(), skill,
                    "stopped_projectile", now).isPresent()) return;
            if (!TimedSkillEffectRuntime.put(player, projectile.getUUID(), skill,
                    "stopped_projectile", 20, 1.0f)) return;
            TimedSkillEffectRuntime.schedule(player, 20, () -> {
                if (projectile.isAlive()) projectile.discard();
            });
        }

        private static void syncModifier(
                AttributeInstance attribute,
                Identifier id,
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

    private record StoppedProjectile(UUID ownerId, long expiresAt) {
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
