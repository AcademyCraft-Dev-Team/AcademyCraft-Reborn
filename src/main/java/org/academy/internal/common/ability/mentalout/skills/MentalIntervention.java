package org.academy.internal.common.ability.mentalout.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
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
import org.academy.internal.common.ability.mentalout.MentaloutRosterPackets;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class MentalIntervention extends Skill {
    public MentalIntervention() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(10)
                .iterationTicks(5)
                .maxStacks(Skill.NO_STACK_LIMIT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
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
                        InputConstants.KEY_R,
                        InputConstants.PRESS,
                        InputConstants.MOD_ALT
                )
        ), _ -> Client.use());
        MentaloutRosterPackets.initClient();
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
        MentaloutRosterPackets.initServer();
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.MENTAL_INTERVENTION.get(),
                        List.of(),
                        R.textures.ability.mentalout.skill.mental_intervention.icon,
                        28,
                        40
                )
        );
        public static final String KEY_NAME_USE = SkillNames.MENTAL_INTERVENTION + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.MENTAL_INTERVENTION.get())) return;
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
                    MentaloutRequestGuard.SkillUse.MENTAL_INTERVENTION,
                    packet.requestSequence
            )) return;
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.MENTAL_INTERVENTION.get();
            if (!skill.isEnabled(player)) {
                feedback(player, "message.academy.mentalout.skill_unavailable");
                return;
            }
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var range = milestone >= 2 ? 24.0 : milestone >= 1 ? 20.0 : 16.0;
            var selected = MentaloutTargeting.findLookedAtLivingExtended(player, range);
            var result = MentaloutControlContext.toggleTarget(player, selected);
            if (result == MentaloutControlContext.ToggleResult.ADDED
                    && milestone >= 3 && selected != null && !(selected instanceof ServerPlayer)) {
                var roster = MentaloutControlContext.subjects(player).stream()
                        .map(LivingEntity::getUUID).collect(java.util.stream.Collectors.toSet());
                var added = 0;
                for (var nearby : player.level().getEntitiesOfClass(LivingEntity.class,
                        selected.getBoundingBox().inflate(4.0), candidate ->
                                candidate != selected && !(candidate instanceof ServerPlayer)
                                        && candidate.getType() == selected.getType()
                                        && candidate.isAlive() && !roster.contains(candidate.getUUID()))) {
                    if (added >= 2) break;
                    if (MentaloutControlContext.toggleTarget(player, nearby)
                            == MentaloutControlContext.ToggleResult.ADDED) added++;
                }
            }
            switch (result) {
                case ADDED -> feedback(player, "message.academy.mentalout.mental_intervention.added");
                case REMOVED -> feedback(player, "message.academy.mentalout.mental_intervention.removed");
                case INVALID -> feedback(player, "message.academy.mentalout.invalid_target");
                case UNSUPPORTED -> feedback(player, "message.academy.mentalout.unsupported_target");
                case INSUFFICIENT_CP -> feedback(player, "message.academy.mentalout.insufficient_cp");
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
            return PacketTypes.MENTAL_INTERVENTION_USE.get();
        }
    }
}
