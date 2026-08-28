package org.academy.internal.common.ability.aeromanip.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.AeromanipResourceManager;
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
import java.util.WeakHashMap;

/** Toggleable Lv4 creative flight with speed progression at every proficiency milestone. */
public final class Flight extends Skill {
    private static final Identifier FLIGHT_SOURCE = AcademyCraft.academy(SkillNames.FLIGHT);
    private static final float DEFAULT_FLYING_SPEED = 0.05f;

    public Flight() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.HIGH_SPEED_JET)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4)));
    }

    static float flyingSpeed(int milestone) {
        return switch (Math.max(0, Math.min(3, milestone))) {
            case 0 -> 0.035f;
            case 1 -> 0.05f;
            case 2 -> 0.065f;
            default -> 0.08f;
        };
    }

    static boolean shouldConsumeCompressedAir(boolean flying, Vec3 movement) {
        return flying && movement != null && movement.lengthSqr() > 1.0e-4;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_R,
                InputConstants.RELEASE,
                InputConstants.MOD_ALT);
        var binding = Client.CONFIG.getKeyBindingMigratingDefaults(
                Client.KEY_NAME_TOGGLE,
                defaultBinding,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_F,
                        InputConstants.RELEASE, InputConstants.MOD_ALT));
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, binding, _ -> Client.toggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilitySystemClient.addSkillInfo(
                        AbilityCategories.AEROMANIP.get(),
                        new AbilitySystemClient.SkillInfo(
                                Skills.FLIGHT.get(),
                                List.of(HighSpeedJet.Client.SKILL_INFO),
                                R.textures.flight_icon,
                                120,
                                136));
        public static final String KEY_NAME_TOGGLE = SkillNames.FLIGHT + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.FLIGHT.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
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

    public static final class Server {
        private static final Map<ServerPlayer, AeromanipResourceManager.UsageLease> USAGE_LEASES =
                new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.FLIGHT.get().toggle(player);
            refreshFlightPermission(player);
        }

        public static void refreshFlightPermission(ServerPlayer player) {
            if (player != null) syncFlight(player,
                    Skills.FLIGHT.get().isEnabled(player)
                            && player.isAlive() && !player.hasDisconnected());
        }

        private static void syncFlight(ServerPlayer player, boolean enabled) {
            SkillFlightController.setSource(player, FLIGHT_SOURCE, enabled);
            if (DarkmatterSixWings.Server.isActive(player)) return;
            var targetSpeed = enabled
                    ? flyingSpeed(Skills.FLIGHT.get().getEffectiveProficiencyMilestone(player))
                    : DEFAULT_FLYING_SPEED;
            if (Math.abs(player.getAbilities().getFlyingSpeed() - targetSpeed) > 1.0e-5f) {
                player.getAbilities().setFlyingSpeed(targetSpeed);
                player.onUpdateAbilities();
            }
        }

        private static void openUsageLease(ServerPlayer player) {
            USAGE_LEASES.computeIfAbsent(player, current ->
                    AbilitySystemServer.getSystem(current)
                            .getAeromanipResourceManager().beginUse(current));
        }

        private static void closeUsageLease(ServerPlayer player) {
            var lease = USAGE_LEASES.remove(player);
            if (lease != null) lease.close();
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.FLIGHT.get();
            var enabled = skill.isEnabled(player)
                    && player.isAlive() && !player.hasDisconnected();
            if (enabled) {
                enabled = AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                        player.getUUID(),
                        skill.getMaintenanceCost(player)
                                * AeromanipConfig.cpMultiplier(player, SkillNames.FLIGHT),
                        skill);
            }
            if (enabled) {
                var moving = shouldConsumeCompressedAir(
                        player.getAbilities().flying, player.getDeltaMovement());
                if (moving) {
                    Server.openUsageLease(player);
                    var interval = Math.max(1, Math.round(AeromanipConfig.skillFloat(
                            player, SkillNames.FLIGHT, "compressedAirIntervalTicks", 20.0f)));
                    if (player.tickCount % interval == 0) {
                        var airCost = Math.max(0.0f, AeromanipConfig.skillFloat(
                                player, SkillNames.FLIGHT, "compressedAirPerInterval", 2.0f));
                        skill.executeContinuousWithResource(
                                player, _ -> 0.0f, _ -> airCost, (_, _) -> { }, true);
                    }
                    skill.reportActivity(player, true);
                } else {
                    Server.closeUsageLease(player);
                }
            }
            if (!enabled) {
                Server.closeUsageLease(player);
                AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                        player.getUUID(), skill.getKeyString());
                if (skill.isEnabled(player)) skill.toggle(player);
            }
            Server.syncFlight(player, enabled);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket
            extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.FLIGHT_TOGGLE.get();
        }
    }
}
