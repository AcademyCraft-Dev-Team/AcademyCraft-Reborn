package org.academy.internal.common.ability.mentalout.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.List;
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
import org.academy.internal.common.ability.mentalout.skills.lv1.MentalIntervention;
import org.academy.internal.common.ability.mentalout.skills.lv1.TargetMisidentification;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class ImpressionManipulation extends Skill {
    public ImpressionManipulation() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(0)
                .iterationTicks(0)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.MENTAL_INTERVENTION, Skills.TARGET_MISIDENTIFICATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Mental Intervention", "academy:mental_intervention"))
                .devCondition(new DevCondition.DependencyCondition(
                        "Target Misidentification", "academy:target_misidentification"))
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
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_H,
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
                        Skills.IMPRESSION_MANIPULATION.get(),
                        List.of(
                                MentalIntervention.Client.SKILL_INFO,
                                TargetMisidentification.Client.SKILL_INFO
                        ),
                        R.textures.ability.mentalout.skill.impression_manipulation.icon,
                        76,
                        112
                )
        );
        public static final String KEY_NAME_USE = SkillNames.IMPRESSION_MANIPULATION + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.IMPRESSION_MANIPULATION.get())) return;
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
                    MentaloutRequestGuard.SkillUse.IMPRESSION_MANIPULATION,
                    packet.requestSequence
            )) return;
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.IMPRESSION_MANIPULATION.get();
            if (!skill.isEnabled(player)) {
                feedback(player, "message.academy.mentalout.skill_unavailable");
                return;
            }
            var context = MentaloutControlContext.get(player);
            if (context == null || !context.hasEntries()) {
                feedback(player, "message.academy.mentalout.roster_empty");
                return;
            }
            var wasActive = context.isImpressionEnabled();
            var result = context.toggleImpression();
            if (result.insufficientCp()) {
                feedback(player, "message.academy.mentalout.insufficient_cp");
            } else if (wasActive) {
                feedback(player, "message.academy.mentalout.impression_manipulation.cleared");
            } else if (!result.active()) {
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
            return PacketTypes.IMPRESSION_MANIPULATION_USE.get();
        }
    }
}
