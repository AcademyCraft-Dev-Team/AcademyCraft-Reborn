package org.academy.internal.common.ability.darkmatter.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfx.DarkmatterSixWingsVfxClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterPhase;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.SkillFlightController;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DarkmatterSixWings extends Skill {
    private static final Map<WingStrikePair, Long> NEXT_WING_STRIKE_TICK =
            new ConcurrentHashMap<>();

    private record WingStrikePair(UUID attacker, UUID target) {
    }

    public static final float MIN_RESERVED_CP = 120.0f;
    public static final float ACTIVATION_MATTER_COST = 10.0f;
    private static final Identifier FLIGHT_SOURCE =
            AcademyCraft.academy(SkillNames.DARKMATTER_SIX_WINGS);
    private static final Identifier TRUE_RESISTANCE_MODIFIER_ID =
            AcademyCraft.academy("darkmatter_six_wings_true_resistance");

    public DarkmatterSixWings() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(MIN_RESERVED_CP)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.DARKMATTER_CREATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Genesis", "academy:darkmatter_creation"))
        );
    }

    @Override
    public float getMaintenanceCost(ServerPlayer player) {
        var maximum = AbilitySystemServer.getSystem(player).getPlayerMaxCP(player.getUUID());
        var ratio = maintenanceRatio(getEffectiveProficiencyMilestone(player));
        return Math.max(MIN_RESERVED_CP, maximum * ratio);
    }

    static float maintenanceRatio(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 1 ? 0.30f : 0.35f;
    }

    static float activationMatterCost(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 3 ? 5.0f : ACTIVATION_MATTER_COST;
    }

    @Override
    public void initClient() {
        DarkmatterSixWingsVfxClient.register();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), context -> Client.toggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_SIX_WINGS.get(),
                        List.of(DarkmatterCreation.Client.SKILL_INFO),
                        R.textures.darkmatter_six_wings_icon,
                        235,
                        72
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.DARKMATTER_SIX_WINGS + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.beginToggleRequest(Skills.DARKMATTER_SIX_WINGS.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
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
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.DARKMATTER_SIX_WINGS.get();
            if (!skill.isEnabled(player)) {
                var system = AbilitySystemServer.getSystem(player);
                var required = skill.getMaintenanceCost(player)
                        * system.getPlayerCalculationIntensity(player.getUUID());
                var activationCost = activationMatterCost(
                        skill.getEffectiveProficiencyMilestone(player));
                if (system.getPlayerAvailableCP(player.getUUID()) + 1.0e-5f < required
                        || !system.getDarkmatterResourceManager().consume(
                        player,
                        activationCost,
                        skill,
                        skill.getIterationTicks(player)
                )) return;
            }
            skill.toggle(player);
            sync(player);
            AbilitySystemServer.getSystem(player).getDarkmatterResourceManager().requestSync(player);
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.DARKMATTER_SIX_WINGS.get().isEnabled(player)
                    && player.getData(AttachmentTypes.DARKMATTER_SIX_WINGS.get());
        }

        public static float adjustCategoryCost(ServerPlayer player, Skill skill,
                                               float baseCost, float adjustedCost) {
            if (player == null || skill == null || !Float.isFinite(baseCost)
                    || !Float.isFinite(adjustedCost) || baseCost < 0.0f || adjustedCost < 0.0f
                    || skill == Skills.DARKMATTER_SIX_WINGS.get()
                    || skill.getCategory() != AbilityCategories.DARKMATTER.get()
                    || !isActive(player)
                    || !Skills.DARKMATTER_SIX_WINGS.get().hasProficiencyMilestone(player, 3)) {
                return adjustedCost;
            }
            return adjustedCategoryCost(baseCost, adjustedCost,
                    Skills.DARKMATTER_SIX_WINGS.get().getEffectiveProficiencyMilestone(player));
        }

        public static boolean debugSetActive(ServerPlayer player, boolean active) {
            var skill = Skills.DARKMATTER_SIX_WINGS.get();
            if (skill.getRuntimeData(player).isEmpty()) return false;
            if (skill.isEnabled(player) != active) skill.toggle(player);
            var system = AbilitySystemServer.getSystem(player);
            if (!active) system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            sync(player);
            system.getDarkmatterResourceManager().requestSync(player);
            return isActive(player) == active;
        }

        public static float adjustedCategoryCost(float baseCost, float adjustedCost, int milestone) {
            if (!Float.isFinite(baseCost) || !Float.isFinite(adjustedCost)
                    || baseCost < 0.0f || adjustedCost < 0.0f) return Float.NaN;
            return adjustedCost;
        }

        public static float flightSpeed(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 2 ? 0.0575f : 0.05f;
        }

        public static float flightSpeed(float betaPower, int milestone) {
            var speed = 0.05f * (1.0f + 0.08f * Math.max(0.0f, betaPower));
            return Math.clamp(milestone, 0, 3) >= 2 ? speed * 1.15f : speed;
        }

        public static double areaMultiplier(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 2 ? 1.15 : 1.0;
        }

        public static float gammaMagnitudeMultiplier(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 3 ? 1.20f : 1.0f;
        }

        public static float gammaMagnitudeMultiplier(ServerPlayer player) {
            return isActive(player) ? gammaMagnitudeMultiplier(
                    Skills.DARKMATTER_SIX_WINGS.get().getEffectiveProficiencyMilestone(player)) : 1.0f;
        }

        public static float darkmatterPenetration(float betaPower) {
            return Math.min(0.25f, Math.max(0.0f,
                    Float.isFinite(betaPower) ? betaPower : 0.0f) * 0.05f);
        }

        private static void sync(ServerPlayer player) {
            var active = Skills.DARKMATTER_SIX_WINGS.get().isEnabled(player)
                    && player.isAlive() && !player.hasDisconnected();
            var type = AttachmentTypes.DARKMATTER_SIX_WINGS.get();
            if (player.getData(type) != active) {
                player.setData(type, active);
                player.syncData(type);
            }
            SkillFlightController.setSource(player, FLIGHT_SOURCE, active);
            var flightSpeed = active
                    ? flightSpeed(DarkmatterPhase.beta(player),
                    Skills.DARKMATTER_SIX_WINGS.get().getEffectiveProficiencyMilestone(player))
                    : 0.05f;
            if (Math.abs(player.getAbilities().getFlyingSpeed() - flightSpeed) > 1.0e-5f) {
                player.getAbilities().setFlyingSpeed(flightSpeed);
                player.onUpdateAbilities();
            }
            PlayerAttributeRuntime.syncTrueResistanceModifier(
                    player, TRUE_RESISTANCE_MODIFIER_ID, 0.0, false);
        }

        private static void tick(ServerPlayer player) {
            var skill = Skills.DARKMATTER_SIX_WINGS.get();
            if (skill.isEnabled(player) && (!player.isAlive() || player.hasDisconnected())) {
                skill.toggle(player);
                AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                        player.getUUID(), skill.getKeyString());
            }
            var active = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (active) {
                active = AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                        player.getUUID(), skill.getMaintenanceCost(player), skill);
                if (!active && skill.isEnabled(player)) skill.toggle(player);
            }
            sync(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || event.isCanceled()
                    || !(event.getAmount() > 0.0f)
                    || !Server.isActive(player)) return;
            if (event.getSource().getEntity() == null && event.getSource().getDirectEntity() == null) return;
            var reduction = Math.min(0.20f, DarkmatterPhase.alpha(player) * 0.04f);
            event.setAmount(event.getAmount() * (1.0f - reduction));
        }

        @SubscribeEvent
        public static void onAttack(AttackEntityEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(event.getTarget() instanceof LivingEntity target)
                    || !DarkmatterTargeting.isAttackableBy(player, target)
                    || !Server.isActive(player)) return;
            var alpha = DarkmatterPhase.alpha(player);
            if (!(alpha > 0.0f)) return;
            var now = level.getGameTime();
            var pair = new WingStrikePair(player.getUUID(), target.getUUID());
            if (now < NEXT_WING_STRIKE_TICK.getOrDefault(pair, 0L)) return;
            NEXT_WING_STRIKE_TICK.put(pair, now + 10L);
            DarkmatterTargeting.hurt(level, target,
                    SkillDamageSource.of(player, Skills.DARKMATTER_SIX_WINGS.get()),
                    1.0f + alpha);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onDarkmatterDamageApplied(LivingDamageEvent.Pre event) {
            if (!(event.getSource() instanceof SkillDamageSource skillSource)
                    || skillSource.getSkill().getCategory() != AbilityCategories.DARKMATTER.get()
                    || !(event.getSource().getEntity() instanceof ServerPlayer player)
                    || !Server.isActive(player) || !(event.getNewDamage() > 0.0f)) return;
            var penetration = Server.darkmatterPenetration(DarkmatterPhase.beta(player));
            if (penetration > 0.0f) {
                event.setNewDamage(event.getNewDamage() / (1.0f - penetration));
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
            return PacketTypes.DARKMATTER_SIX_WINGS_TOGGLE.get();
        }
    }
}
