package org.academy.internal.common.ability.mentalout.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentaloutControlContext;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.skills.MentaloutTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class TargetMisidentification extends Skill {
    public TargetMisidentification() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(40)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.MENTAL_INTERVENTION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
                .devCondition(new DevCondition.DependencyCondition(
                        "Mental Intervention", "academy:mental_intervention"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_USE,
                InputSystem.combo(
                        InputSystem.InputType.MOUSE,
                        InputConstants.MOUSE_BUTTON_LEFT,
                        InputConstants.PRESS,
                        InputConstants.MOD_ALT
                )
        ), _ -> Client.use());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.TARGET_MISIDENTIFICATION.get(),
                        List.of(MentalIntervention.Client.SKILL_INFO),
                        R.textures.ability.mentalout.skill.target_misidentification.icon,
                        76,
                        40
                )
        );
        public static final String KEY_NAME_USE = SkillNames.TARGET_MISIDENTIFICATION + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.TARGET_MISIDENTIFICATION.get())) return;
            MisakaNetworkClient.send(new UsePacket(MentaloutRequestGuard.nextClientSequence()));
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
        public static void handle(UsePacket packet) {
            if (!MentaloutRequestGuard.acceptSkillUse(
                    packet.getPacketListener(),
                    MentaloutRequestGuard.SkillUse.TARGET_MISIDENTIFICATION,
                    packet.requestSequence
            )) return;
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.TARGET_MISIDENTIFICATION.get();
            if (!skill.isEnabled(player)) {
                feedback(player, "message.academy.mentalout.skill_unavailable");
                return;
            }
            var context = MentaloutControlContext.get(player);
            if (context == null || !context.hasEntries()) {
                feedback(player, "message.academy.mentalout.roster_empty");
                return;
            }
            var target = MentaloutTargeting.findLookedAtLivingExtended(player,
                    skill.hasProficiencyMilestone(player, 2) ? 80.0 : 64.0);
            if (target != null && PvpSetting.shouldPrevent(player, target)) return;
            if (target == null) {
                feedback(player, "message.academy.mentalout.invalid_target");
                return;
            }

            var clearing = context.isTargetMisidentificationTarget(target);
            var result = context.applyTargetMisidentification(target);
            if (clearing) {
                feedback(player, "message.academy.mentalout.target_misidentification.cleared");
            } else if (result.insufficientCp()) {
                feedback(player, "message.academy.mentalout.insufficient_cp");
            } else if (result.applied() <= 0) {
                feedback(player, "message.academy.mentalout.no_supported_targets");
            } else {
                feedback(
                        player,
                        "message.academy.mentalout.batch_applied",
                        result.applied(),
                        result.skipped(),
                        result.failed()
                );
            }
        }

        private static void feedback(ServerPlayer player, String key, Object... arguments) {
            player.sendOverlayMessage(Component.translatable(key, arguments));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = ByteBufCodecs.LONG.map(
                UsePacket::new,
                UsePacket::getRequestSequence
        );
        private final long requestSequence;

        public UsePacket(long requestSequence) {
            this.requestSequence = requestSequence;
        }

        public long getRequestSequence() {
            return requestSequence;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.TARGET_MISIDENTIFICATION_USE.get();
        }
    }
}
