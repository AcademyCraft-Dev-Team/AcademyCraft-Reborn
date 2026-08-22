package org.academy.internal.common.ability.darkmatter.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterPhase;
import org.academy.internal.common.ability.darkmatter.DarkmatterAbsorption;
import org.academy.internal.common.ability.darkmatter.skills.lv3.DarkmatterRadiation;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class DarkmatterRepair extends Skill {
    static final float MATTER_COST = 1.0f;

    public DarkmatterRepair() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .cpCost(0)
                .iterationTicks(15)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.DARKMATTER_RADIATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Interference", "academy:darkmatter_interference"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_U,
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
                        Skills.DARKMATTER_REPAIR.get(),
                        List.of(DarkmatterRadiation.Client.SKILL_INFO),
                        R.textures.darkmatter_repair_icon,
                        180,
                        40
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.DARKMATTER_REPAIR + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.beginToggleRequest(Skills.DARKMATTER_REPAIR.get())) return;
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
        private static final Map<UUID, Integer> PRODUCTIVE_PULSES = new ConcurrentHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            toggle(packet.getPacketListener().getPlayer());
        }

        public static boolean toggle(ServerPlayer player) {
            if (player == null) return false;
            var skill = Skills.DARKMATTER_REPAIR.get();
            var before = skill.isEnabled(player);
            skill.toggle(player);
            return skill.isEnabled(player) != before;
        }

        static float maximumAbsorption(float alpha, int milestone) {
            return 2.0f + Math.max(0.0f, alpha) * 2.0f;
        }

        static float repairFraction(float alpha, int milestone) {
            var amount = 0.01f + 0.005f * Math.max(0.0f, alpha);
            return Math.clamp(milestone, 0, 3) >= 2 ? amount * 1.25f : amount;
        }

        static float bodyHeal(float beta, int milestone) {
            var amount = 0.5f + 0.5f * Math.max(0.0f, beta);
            return Math.clamp(milestone, 0, 3) >= 2 ? amount * 1.25f : amount;
        }

        static int effectReductionTicks(float beta, int milestone) {
            var amount = 20.0f + 10.0f * Math.max(0.0f, beta);
            return Math.round(Math.clamp(milestone, 0, 3) >= 2 ? amount * 1.25f : amount);
        }

        static float matterCost(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 1 ? 0.8f : MATTER_COST;
        }

        static int repairTargetCount(boolean gammaActive, int milestone) {
            return 1 + (gammaActive ? 1 : 0) + (Math.clamp(milestone, 0, 3) >= 3 ? 1 : 0);
        }

        static boolean removesHarmfulEffect(int productivePulse, int milestone) {
            return Math.clamp(milestone, 0, 3) >= 3
                    && productivePulse > 0 && productivePulse % 5 == 0;
        }

        private static void tick(ServerPlayer player) {
            if (player.tickCount % 20 == 0) tryPulse(player);
        }

        public static boolean tryPulse(ServerPlayer player) {
            if (player == null) return false;
            var skill = Skills.DARKMATTER_REPAIR.get();
            if (!skill.isEnabled(player) || !player.isAlive()) return false;
            var phase = DarkmatterPhase.weights(player);
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var missingHealth = Math.max(0.0f, player.getMaxHealth() - player.getHealth());
            var damagedEquipment = findDamagedEquipment(player);
            var harmful = player.getActiveEffects().stream()
                    .filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                    .findFirst().orElse(null);
            // Structural absorption is a result of a successful equipment repair, not an
            // independent source of work that may consume MP forever.
            var hasAlphaWork = phase.alpha() > 0.0f && !damagedEquipment.isEmpty();
            var hasBetaWork = phase.beta() > 0.0f && (missingHealth > 0.0f || harmful != null);
            if (!hasAlphaWork && !hasBetaWork) return false;

            var system = AbilitySystemServer.getSystem(player);
            var cost = matterCost(milestone);
            if (system.getDarkmatterResourceManager().getView(player).totalMatter() + 1.0e-5f < cost) {
                return false;
            }
            var productive = new boolean[1];
            var executed = skill.executeContinuous(player, _ -> 0.0f, (context, _) -> {
                if (!system.getDarkmatterResourceManager().consume(
                        player, cost, skill, skill.getIterationTicks(player))) return;
                var outputMultiplier = 1.0f;
                if (phase.gamma() > 0.0f) {
                    outputMultiplier *= DarkmatterSixWings.Server.gammaMagnitudeMultiplier(player);
                }
                var changed = false;
                if (hasBetaWork && missingHealth > 0.0f) {
                    var before = player.getHealth();
                    player.heal(Math.min(
                            bodyHeal(phase.beta(), milestone) * outputMultiplier,
                            missingHealth));
                    changed |= player.getHealth() > before;
                }
                var repaired = false;
                if (hasAlphaWork) {
                    var fraction = repairFraction(phase.alpha(), milestone) * outputMultiplier;
                    var count = repairTargetCount(phase.gamma() > 0.0f, milestone);
                    for (var index = 0; index < Math.min(count, damagedEquipment.size()); index++) {
                        repaired |= DarkmatterItemUtil.repairIntegrity(
                                damagedEquipment.get(index), fraction);
                    }
                    changed |= repaired;
                }
                if (repaired) {
                    var absorptionCap = maximumAbsorption(phase.alpha(), milestone);
                    var before = player.getAbsorptionAmount();
                    DarkmatterAbsorption.grantAtLeast(player, Math.min(
                            absorptionCap, before + 0.5f + phase.alpha() * 0.5f));
                    changed |= player.getAbsorptionAmount() > before;
                }
                var pulse = PRODUCTIVE_PULSES.getOrDefault(player.getUUID(), 0) + 1;
                if (hasBetaWork && harmful != null) {
                    if (removesHarmfulEffect(pulse, milestone)) {
                        changed |= player.removeEffect(harmful.getEffect());
                    } else {
                        changed |= shortenEffect(
                                player, harmful,
                                effectReductionTicks(phase.beta(), milestone));
                    }
                }
                if (changed) PRODUCTIVE_PULSES.put(player.getUUID(), pulse);
                productive[0] = changed;
                if (changed) player.getInventory().setChanged();
            }, true);
            return executed && productive[0];
        }

        private static List<ItemStack> findDamagedEquipment(ServerPlayer player) {
            var result = new ArrayList<ItemStack>();
            for (var stack : player.getInventory().getNonEquipmentItems()) {
                if (DarkmatterItemUtil.isNativeEquipment(stack)
                        && DarkmatterItemUtil.integrity(stack) < 0.99999f) result.add(stack);
            }
            for (var slot : new EquipmentSlot[]{
                    EquipmentSlot.OFFHAND,
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                var stack = player.getItemBySlot(slot);
                if (DarkmatterItemUtil.isNativeEquipment(stack)
                        && DarkmatterItemUtil.integrity(stack) < 0.99999f) result.add(stack);
            }
            result.sort(Comparator.comparingDouble(DarkmatterItemUtil::integrity));
            return result;
        }

        private static boolean shortenEffect(
                ServerPlayer player, MobEffectInstance effect, int ticks
        ) {
            var remaining = Math.max(0, effect.getDuration() - Math.max(0, ticks));
            if (remaining == effect.getDuration()) return false;
            var type = effect.getEffect();
            player.removeEffect(type);
            if (remaining <= 0) return true;
            player.addEffect(new MobEffectInstance(
                    type, remaining, effect.getAmplifier(), effect.isAmbient(),
                    effect.isVisible(), effect.showIcon()));
            return true;
        }

        public static int productivePulses(UUID playerId) {
            return PRODUCTIVE_PULSES.getOrDefault(playerId, 0);
        }

        private static void clearPlayer(UUID playerId) {
            PRODUCTIVE_PULSES.remove(playerId);
        }

        private static void clearAll() {
            PRODUCTIVE_PULSES.clear();
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

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            Server.clearPlayer(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                Server.clearPlayer(player.getUUID());
            }
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            Server.clearAll();
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
            return PacketTypes.DARKMATTER_REPAIR_TOGGLE.get();
        }
    }
}
