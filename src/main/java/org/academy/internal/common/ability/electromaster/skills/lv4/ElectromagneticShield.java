package org.academy.internal.common.ability.electromaster.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.ToggleStatusHud;
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
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.api.common.ability.SkillProficiencyProfile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.electromaster.skills.lv2.MagnetManipulation;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.ElectromagneticShieldData;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Locale;
import net.minecraft.util.Mth;

public final class ElectromagneticShield extends Skill {
    static final float BASE_CAPACITY = 100.0f;
    static final float BASE_COOLING = 10.0f;
    static final float BASE_COOLING_CP_COST = 20.0f;
    private static final int COOLING_INTERVAL_TICKS = 20;
    private static final Identifier TRUE_RESISTANCE_MODIFIER_ID =
            AcademyCraft.academy("electromagnetic_shield_true_resistance");

    public ElectromagneticShield() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(40)
                .iterationTicks(15)
                .maxStacks(NO_STACK_LIMIT)
                .withCustomData(
                        ElectromagneticShieldData.ID,
                        ElectromagneticShieldData.class,
                        ElectromagneticShieldData::new
                )
                .dependsOn(Skills.MAGNET_MANIPULATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
        );
    }

    static AbsorptionResult absorbDamage(float storedDamage, float capacity, float incomingDamage) {
        var safeCapacity = Float.isFinite(capacity) ? Math.max(0, capacity) : 0;
        var safeStored = Float.isFinite(storedDamage)
                ? Mth.clamp(storedDamage, 0, safeCapacity)
                : 0;
        var safeIncoming = Float.isFinite(incomingDamage) ? Math.max(0, incomingDamage) : 0;
        var absorbed = Math.min(safeIncoming, safeCapacity - safeStored);
        return new AbsorptionResult(safeStored + absorbed, safeIncoming - absorbed);
    }

    static float coolStoredDamage(float storedDamage, float coolingAmount) {
        if (!Float.isFinite(storedDamage) || !Float.isFinite(coolingAmount)) return 0;
        return Math.max(0, storedDamage - Math.max(0, coolingAmount));
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
                                InputConstants.KEY_G,
                                InputConstants.RELEASE,
                                InputConstants.MOD_SHIFT
                        )
                ),
                _ -> Client.toggle()
        );
        ToggleStatusHud.Companion.registerDetailProvider(
                Skills.ELECTROMAGNETIC_SHIELD.get(), Client::statusText);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    record AbsorptionResult(float storedDamage, float remainingDamage) {
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.ELECTROMAGNETIC_SHIELD.get(),
                        List.of(MagnetManipulation.Client.SKILL_INFO),
                        R.textures.electromagnetic_shield_icon,
                        104,
                        80
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.ELECTROMAGNETIC_SHIELD + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.ELECTROMAGNETIC_SHIELD.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        private static String statusText() {
            var data = AbilitySystemClient.getSkillData(
                    Skills.ELECTROMAGNETIC_SHIELD.get(), ElectromagneticShieldData.class);
            if (data.isEmpty()) return "0 / 0";
            var shield = data.get();
            var remaining = Math.max(0.0f, shield.getCapacity() - shield.getAbsorbedDamage());
            return String.format(Locale.ROOT, "%.0f / %.0f", remaining, shield.getCapacity());
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

        public static boolean isActive(ServerPlayer player) {
            return Skills.ELECTROMAGNETIC_SHIELD.get().isEnabled(player);
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.ELECTROMAGNETIC_SHIELD.get();
            var wasEnabled = skill.isEnabled(player);
            skill.toggle(player);
            if (wasEnabled != skill.isEnabled(player)) {
                setShieldState(player, 0, currentCapacity(player));
            }
        }

        private static void setStoredDamage(ServerPlayer player, float value) {
            setShieldState(player, value, currentCapacity(player));
        }

        private static float currentCapacity(ServerPlayer player) {
            return BASE_CAPACITY * AbilitySystemServer.getSystem(player)
                    .getPlayerAbilityPowerMultiplier(player.getUUID());
        }

        private static void setShieldState(ServerPlayer player, float value, float capacity) {
            var skill = Skills.ELECTROMAGNETIC_SHIELD.get();
            AbilitySystemServer.getSystem(player).updatePlayerSkillData(
                    player.getUUID(),
                    skill,
                    ElectromagneticShieldData.class,
                    data -> {
                        data.setCapacity(capacity);
                        data.setAbsorbedDamage(value);
                    }
            );
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            var skill = Skills.ELECTROMAGNETIC_SHIELD.get();
            if (!skill.isEnabled(player) || event.getAmount() <= 0) return;

            var system = AbilitySystemServer.getSystem(player);
            var data = skill.<ElectromagneticShieldData>getRuntimeData(player).orElse(null);
            if (data == null) return;

            var capacity = BASE_CAPACITY * system.getPlayerAbilityPowerMultiplier(player.getUUID());
            if (skill.hasProficiencyMilestone(player, 2)) capacity *= 1.25f;
            var resolvedCapacity = capacity;
            var result = absorbDamage(data.getAbsorbedDamage(), capacity, event.getAmount());
            if (Float.compare(result.storedDamage(), data.getAbsorbedDamage()) != 0
                    || Float.compare(capacity, data.getCapacity()) != 0) {
                system.updatePlayerSkillData(
                        player.getUUID(),
                        skill,
                        ElectromagneticShieldData.class,
                        shieldData -> {
                            shieldData.setCapacity(resolvedCapacity);
                            shieldData.setAbsorbedDamage(result.storedDamage());
                        }
                );
            }

            event.setAmount(result.remainingDamage());
            if (result.remainingDamage() <= 0) {
                event.setCanceled(true);
            }
            if (skill.hasProficiencyMilestone(player, 3)
                    && data.getAbsorbedDamage() < capacity && result.storedDamage() >= capacity) {
                triggerOverloadPulse(player, event.getSource().getEntity());
            }
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            var skill = Skills.ELECTROMAGNETIC_SHIELD.get();
            var active = skill.isEnabled(player);
            PlayerAttributeRuntime.syncTrueResistanceModifier(
                    player,
                    TRUE_RESISTANCE_MODIFIER_ID,
                    2.0,
                    active
            );
            if (!active) return;

            if (player.tickCount % 5 == 0 && player.level() instanceof ServerLevel level) {
                ElectromasterArcEffects.spawnShieldArcs(level, player.position(), player.tickCount);
            }

            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            var capacity = BASE_CAPACITY * system.getPlayerAbilityPowerMultiplier(uuid);
            if (skill.hasProficiencyMilestone(player, 2)) capacity *= 1.25f;
            var syncedData = skill.<ElectromagneticShieldData>getRuntimeData(player).orElse(null);
            if (syncedData != null && Float.compare(syncedData.getCapacity(), capacity) != 0) {
                Server.setShieldState(player, syncedData.getAbsorbedDamage(), capacity);
            }
            if (!system.ensurePermanentOccupation(
                    uuid,
                    skill.getMaintenanceCost(player),
                    skill
            )) {
                system.toggleSkill(uuid, skill.getKeyString());
                Server.setStoredDamage(player, 0);
                PlayerAttributeRuntime.syncTrueResistanceModifier(
                        player,
                        TRUE_RESISTANCE_MODIFIER_ID,
                        2.0,
                        false
                );
                return;
            }

            if (player.level().getGameTime() % COOLING_INTERVAL_TICKS != 0) return;
            var data = skill.<ElectromagneticShieldData>getRuntimeData(player).orElse(null);
            if (data == null || data.getAbsorbedDamage() <= 0) return;

            var coolingCost = skill.adjustProficiencyCost(player,
                    SkillProficiencyProfile.CostKind.DYNAMIC, BASE_COOLING_CP_COST);
            if (!system.tryTimedOccupation(uuid, coolingCost, skill)) return;
            var coolingBase = skill.hasProficiencyMilestone(player, 2) ? 15.0f : BASE_COOLING;
            var cooling = coolingBase * system.getPlayerAbilityPowerMultiplier(uuid);
            Server.setStoredDamage(player, coolStoredDamage(data.getAbsorbedDamage(), cooling));
        }

        private static void triggerOverloadPulse(ServerPlayer player, net.minecraft.world.entity.Entity attacker) {
            var skill = Skills.ELECTROMAGNETIC_SHIELD.get();
            var now = player.level().getGameTime();
            if (TimedSkillEffectRuntime.get(player.getUUID(), player.getUUID(), skill,
                    "overload_pulse", now).isPresent()) return;
            TimedSkillEffectRuntime.put(player, player.getUUID(), skill, "overload_pulse", 200, 1.0f);
            var handled = 0;
            var cap = ProficiencyPolicy.server(player).maxBonusEntitiesPerTick();
            for (var target : player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(4.0),
                    target -> target != player && target.isAlive() && !player.isAlliedTo(target))) {
                if (handled++ >= cap) break;
                var direction = target.position().subtract(player.position());
                if (direction.lengthSqr() > 1.0e-8) target.push(direction.normalize().x, 0.25, direction.normalize().z);
                if (target == attacker) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
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
            return PacketTypes.ELECTROMAGNETIC_SHIELD_TOGGLE.get();
        }
    }
}
