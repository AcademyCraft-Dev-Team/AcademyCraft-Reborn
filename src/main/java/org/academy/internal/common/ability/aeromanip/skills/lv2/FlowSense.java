package org.academy.internal.common.ability.aeromanip.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.ability.aeromanip.FlowSensePacket;
import org.academy.internal.common.ability.aeromanip.skills.lv1.LaminarBuffer;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

/** Detects nearby living creatures from their disturbance of the local atmosphere. */
public final class FlowSense extends Skill {
    private static final int BASE_INTERVAL_TICKS = 10;
    private static final double BASE_RANGE = 24.0;

    public FlowSense() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .initiallyDisabled()
                .iterationTicks(BASE_INTERVAL_TICKS)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.LAMINAR_BUFFER)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2)));
    }

    static boolean canSensePlayer(boolean sneaking, boolean milestoneThree) {
        return !sneaking || milestoneThree;
    }

    static double sensingRange(boolean milestoneOne) {
        return milestoneOne ? 32.0 : BASE_RANGE;
    }

    static int sensingInterval(boolean milestoneTwo) {
        return milestoneTwo ? 5 : BASE_INTERVAL_TICKS;
    }

    @Override
    public void initClient() {
        FlowSensePacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_M,
                                InputConstants.RELEASE, InputConstants.MOD_ALT)),
                _ -> Client.toggle());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.FLOW_SENSE.get(),
                        List.of(LaminarBuffer.Client.SKILL_INFO),
                        R.textures.flow_sense_icon,
                        50,
                        72));
        ToggleStatusHud.Companion.registerStateProvider(Skills.FLOW_SENSE.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && AbilitySystemClient.canUseSkillSilently(Skills.FLOW_SENSE.get());
        });
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.FLOW_SENSE + "_toggle";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (AbilitySystemClient.beginToggleRequest(Skills.FLOW_SENSE.get())) {
                MisakaNetworkClient.send(TogglePacket.INSTANCE);
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

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            Skills.FLOW_SENSE.get().toggle(packet.getPacketListener().getPlayer());
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.FLOW_SENSE.get();
            if (!skill.isEnabled(player)) return;
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            if (player.level().getGameTime() % sensingInterval(milestone >= 2) != 0) return;

            var range = sensingRange(milestone >= 1)
                    * AeromanipConfig.rangeMultiplier(player, skill.getKey().getPath());
            var center = player.getBoundingBox().getCenter();
            var maximum = Math.min(
                    milestone >= 2 ? 96 : 64,
                    ProficiencyPolicy.server(player).maxBonusEntitiesPerTick());
            var targets = player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(center, center).inflate(range),
                    target -> target != player
                            && target.isAlive()
                            && !target.isSpectator()
                            && target.distanceToSqr(player) <= range * range
                            && (!(target instanceof Player targetPlayer)
                            || canSensePlayer(targetPlayer.isShiftKeyDown(), milestone >= 3)));
            var sent = 0;
            for (var target : targets) {
                if (sent++ >= maximum) break;
                var relative = target.getBoundingBox().getCenter().subtract(center);
                var direction = relative.lengthSqr() <= 1.0e-8
                        ? target.getDeltaMovement()
                        : relative.normalize();
                new FlowSensePacket(target.getId(), direction, target.getDeltaMovement().length())
                        .sendTo(player);
                sendSenseMarker(player, target);
            }
            if (!targets.isEmpty()) skill.reportActivity(player, true);
        }

        private static void sendSenseMarker(ServerPlayer observer, LivingEntity target) {
            var position = target.getBoundingBox().getCenter();
            AeromanipVfx.marker(observer, position,
                    Math.max(0.32, target.getBbWidth() * (target.isInvisible() ? 0.8 : 0.55)));
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
            return PacketTypes.FLOW_SENSE_TOGGLE.get();
        }
    }
}
