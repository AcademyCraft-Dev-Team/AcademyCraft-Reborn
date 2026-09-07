package org.academy.internal.common.ability.mentalout.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
import org.academy.api.common.ability.mentalout.MentaloutDefenseEffects;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.skills.lv2.SensoryDistortion;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class PainSuppression extends Skill {
    public static final float MAINTENANCE_CP = 40.0f;
    public static final double TRUE_RESISTANCE = 8.0;
    public static final float DAMAGE_THRESHOLD = 2.0f;
    public static final float MAX_DAMAGE_CP = 20.0f;
    public static final float NODE_X = 112.0f;
    public static final float NODE_Y = 110.0f;
    private static final Identifier RESISTANCE_ID = AcademyCraft.academy("pain_suppression_true_resistance");

    public PainSuppression() {
        super(Builder.of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(MAINTENANCE_CP)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.SENSORY_DISTORTION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Sensory Distortion", "academy:sensory_distortion")));
    }

    public static float damageCpCost(float finalDamage) {
        return MentaloutDefenseEffects.damageCpCost(finalDamage, DAMAGE_THRESHOLD, MAX_DAMAGE_CP);
    }

    @Override
    public void initClient() {
        AcademyCraftConfig.registerTypeHandler(getKey(), Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(getKey());
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE, InputSystem.combo(InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_J, InputConstants.RELEASE, InputConstants.MOD_ALT)), _ -> Client.toggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(), new AbilitySystemClient.SkillInfo(
                        Skills.PAIN_SUPPRESSION.get(), List.of(SensoryDistortion.Client.SKILL_INFO),
                        R.textures.ability.mentalout.skill.sensory_distortion.icon, NODE_X, NODE_Y));
        public static final String KEY_NAME_TOGGLE = SkillNames.PAIN_SUPPRESSION + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {}

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.PAIN_SUPPRESSION.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {}

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

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Server {
        private static final Map<DamageContainer, Float> BEFORE = new WeakHashMap<>();
        private static final Set<DamageContainer> COMPLETED = Collections.newSetFromMap(new WeakHashMap<>());

        private Server() {}

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            setActive(packet.getPacketListener().getPlayer(), !Skills.PAIN_SUPPRESSION.get()
                    .isEnabled(packet.getPacketListener().getPlayer()));
        }

        /** Server-authoritative entry shared by the key binding and future precision operations. */
        public static void setActive(ServerPlayer player, boolean active) {
            var skill = Skills.PAIN_SUPPRESSION.get();
            if (skill.isEnabled(player) != active) skill.toggle(player);
            sync(player);
        }

        public static void sync(LivingEntity entity) {
            if (!(entity instanceof ServerPlayer player)) return;
            var skill = Skills.PAIN_SUPPRESSION.get();
            var system = AbilitySystemServer.getSystem(player);
            var active = player.isAlive() && !player.isRemoved() && !player.hasDisconnected()
                    && system.getPlayerStatus(player.getUUID()) != AbilityData.Status.OVERLOAD
                    && skill.isEnabled(player);
            if (active && !system.ensurePermanentOccupation(player.getUUID(), MAINTENANCE_CP, skill)) {
                skill.toggle(player);
                active = false;
            }
            MentaloutDefenseEffects.setTrueResistance(player, RESISTANCE_ID, TRUE_RESISTANCE, active);
        }

        /** Snapshot before vanilla writes health; direct-damage declarations already carry final damage. */
        public static void beforeDamage(LivingEntity entity, DamageContainer container) {
            sync(entity);
            if (entity instanceof ServerPlayer player && Skills.PAIN_SUPPRESSION.get().isEnabled(player)) {
                BEFORE.put(container, entity.getHealth());
            }
        }

        public static void afterDamage(LivingEntity entity, DamageContainer container) {
            var before = BEFORE.remove(container);
            if (!(entity instanceof ServerPlayer player) || !COMPLETED.add(container)
                    || !Skills.PAIN_SUPPRESSION.get().isEnabled(player)) return;
            var finalDamage = before == null ? container.getNewDamage()
                    : Math.max(0.0f, before - player.getHealth());
            var cost = damageCpCost(finalDamage);
            if (cost <= 0.0f) return;
            var system = AbilitySystemServer.getSystem(player);
            if (!system.tryTimedOccupation(player.getUUID(), cost, Skills.PAIN_SUPPRESSION.get())) {
                setActive(player, false);
            } else {
                Skills.PAIN_SUPPRESSION.get().reportActivity(player, true);
                sync(player);
            }
        }

        @SubscribeEvent
        public static void onTick(PlayerTickEvent.Post event) {
            sync(event.getEntity());
        }

        @SubscribeEvent
        public static void onLeave(EntityLeaveLevelEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) clear(player);
        }

        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) clear(player);
        }

        private static void clear(ServerPlayer player) {
            MentaloutDefenseEffects.setTrueResistance(player, RESISTANCE_ID, TRUE_RESISTANCE, false);
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(), Skills.PAIN_SUPPRESSION.get().getKeyString());
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {}

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.PAIN_SUPPRESSION_TOGGLE.get();
        }
    }
}
