package org.academy.internal.common.ability.aeromanip.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import org.academy.api.server.team.TeamRelations;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Manually toggled, following dry-air pocket with a reversible water-suppression lease. */
public final class BreathingBubble extends Skill {
    static final int REFRESH_INTERVAL_TICKS = 10;
    private static final float BASE_COMPRESSED_AIR_COST = 8.0f;

    public BreathingBubble() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .maintenanceCost(20)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.FLOW_SENSE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
        );
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static float activationAirCost(int milestone) {
        return milestone >= 1 ? 6.0f : BASE_COMPRESSED_AIR_COST;
    }

    public static double activeRadius(int milestone) {
        return milestone >= 3 ? 3.0 : 2.0;
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.BREATHING_BUBBLE.get(),
                        List.of(FlowSense.Client.SKILL_INFO),
                        R.textures.breathing_film_icon,
                        75,
                        40
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.BREATHING_BUBBLE + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void initialize() {
            var skill = Skills.BREATHING_BUBBLE.get();
            AcademyCraftConfig.registerTypeHandler(skill.getKey(), Config.Action.INSTANCE);
            CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(skill.getKey());
            var defaultBinding = InputSystem.combo(
                    InputSystem.InputType.KEYBOARD,
                    InputConstants.KEY_U,
                    InputConstants.RELEASE,
                    InputConstants.MOD_ALT);
            var binding = CONFIG.getKeyBindingMigratingDefaults(
                    KEY_NAME_CAST,
                    defaultBinding,
                    InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H,
                            InputConstants.RELEASE, 0));
            InputSystem.addKeyBinding(KEY_NAME_CAST, binding, _ -> cast());
        }

        private static void cast() {
            if (AbilitySystemClient.getSkillData(Skills.BREATHING_BUBBLE.get()).isPresent()) {
                MisakaNetworkClient.send(CastPacket.INSTANCE);
            }
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

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

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var level = Server.ACTIVE.get(player);
            var skill = Skills.BREATHING_BUBBLE.get();
            if (level == null) {
                // Remove occupations left by the old automatic passive implementation or a saved session.
                AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                return;
            }
            if (!player.isAlive() || player.hasDisconnected() || player.level() != level || !skill.isEnabled(player)
                    || AbilitySystemServer.getSystem(player).getPlayerStatus(player.getUUID())
                    == org.academy.api.common.data.AbilityData.Status.OVERLOAD) {
                Server.stop(player);
                return;
            }
            Server.refresh(player, skill);
        }

        @SubscribeEvent
        public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.stop(player);
        }
    }

    public static final class Server {
        private static final Map<ServerPlayer, ServerLevel> ACTIVE = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (ACTIVE.containsKey(player)) {
                stop(player);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.academy.breathing_bubble.off"));
                return;
            }
            var skill = Skills.BREATHING_BUBBLE.get();
            if (!player.isAlive() || !skill.isEnabled(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            var cost = activationAirCost(skill.getEffectiveProficiencyMilestone(player));
            var air = system.getAeromanipResourceManager();
            if (air.getCurrent(player) + 1.0e-4f < cost) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.academy.aeromanip.insufficient_air", cost, air.getCurrent(player)));
                return;
            }
            if (!system.ensurePermanentOccupation(player.getUUID(), skill.getMaintenanceCost(player)
                    * AeromanipConfig.cpMultiplier(player, SkillNames.BREATHING_BUBBLE), skill)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.academy.breathing_bubble.cp_required"));
                return;
            }
            // No sustained-use lease: a dry pocket allows recovery at half the normal rate.
            if (!skill.executeActiveWithResource(player, _ -> 0.0f, _ -> cost, (_, _) -> {
                ACTIVE.put(player, player.level());
                refresh(player, skill);
                AeromanipVfx.burst(player.level(), player.getEyePosition(), activeRadius(skill.getEffectiveProficiencyMilestone(player)));
            })) {
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                return;
            }
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.academy.breathing_bubble.on"));
        }

        public static boolean isSustained(ServerPlayer player) {
            return ACTIVE.get(player) == player.level();
        }

        public static boolean protects(LivingEntity target) {
            for (var entry : ACTIVE.entrySet()) {
                var owner = entry.getKey();
                if (entry.getValue() != target.level()) continue;
                var skill = Skills.BREATHING_BUBBLE.get();
                if (target == owner) return true;
                var radius = activeRadius(skill.getEffectiveProficiencyMilestone(owner));
                if (skill.hasProficiencyMilestone(owner, 2) && isSupportedTarget(owner, target)
                        && target.getEyePosition().distanceToSqr(owner.getEyePosition()) <= radius * radius) return true;
            }
            return false;
        }

        public static void stop(ServerPlayer player) {
            var level = ACTIVE.remove(player);
            if (level != null) org.academy.api.server.world.WaterSuppression.release(level, player.getUUID());
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(), Skills.BREATHING_BUBBLE.get().getKeyString());
        }

        private static void refresh(ServerPlayer player, BreathingBubble skill) {
            var radius = activeRadius(skill.getEffectiveProficiencyMilestone(player));
            org.academy.api.server.world.WaterSuppression.refresh(player.level(), player.getUUID(), player.getEyePosition(), radius);
            player.setAirSupply(player.getMaxAirSupply());
            if (skill.hasProficiencyMilestone(player, 2)) {
                for (var target : player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(radius), target -> isSupportedTarget(player, target)
                                && target.getEyePosition().distanceToSqr(player.getEyePosition()) <= radius * radius)) {
                    target.setAirSupply(target.getMaxAirSupply());
                }
            }
            if (player.tickCount % REFRESH_INTERVAL_TICKS == 0) skill.reportActivity(player, true);
        }

        private static boolean isSupportedTarget(ServerPlayer owner, LivingEntity target) {
            if (target == owner) return true;
            if (target instanceof Player) return TeamRelations.areAllied(owner, target);
            return target instanceof TamableAnimal animal && animal.isOwnedBy(owner);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.BREATHING_BUBBLE_CAST.get();
        }
    }
}
