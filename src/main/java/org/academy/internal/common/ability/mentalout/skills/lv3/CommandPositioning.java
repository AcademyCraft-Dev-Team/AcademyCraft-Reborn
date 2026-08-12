package org.academy.internal.common.ability.mentalout.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
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
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlDestination;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlRequest;
import org.academy.api.common.entitycontrol.MentalControlApi;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.skills.MentaloutTargeting;
import org.academy.internal.common.ability.mentalout.MentaloutConfig;
import org.academy.internal.common.ability.mentalout.MentaloutControlContext;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.skills.lv1.MentalIntervention;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/**
 * Moves every compatible roster member to the block or entity under the caster's crosshair.
 */
public final class CommandPositioning extends Skill {
    private static final int CONTROL_PRIORITY = 250;

    public CommandPositioning() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(25_000)
                .cpCost(0)
                .iterationTicks(10)
                .maxStacks(10)
                .dependsOn(Skills.MENTAL_INTERVENTION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
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
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_R,
                        InputConstants.PRESS,
                        0
                )
        ), _ -> Client.use());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    private boolean executeCommand(ServerPlayer player, float cost, Runnable action) {
        return executeActive(player, _ -> cost, (_, _) -> action.run());
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.COMMAND_POSITIONING.get(),
                        List.of(MentalIntervention.Client.SKILL_INFO),
                        R.textures.ability.mentalout.skill.precision_operation.icon,
                        28,
                        112
                )
        );
        public static final String KEY_NAME_USE = SkillNames.COMMAND_POSITIONING + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.COMMAND_POSITIONING.get())) return;
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
                    MentaloutRequestGuard.SkillUse.COMMAND_POSITIONING,
                    packet.requestSequence
            )) return;
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.COMMAND_POSITIONING.get();
            if (!skill.isEnabled(player)) {
                feedback(player, "message.academy.mentalout.skill_unavailable");
                return;
            }

            var destination = MentaloutTargeting.findSightDestination(
                    player,
                    skill.hasProficiencyMilestone(player, 2)
                            ? MentaloutTargeting.PROFICIENCY_MAX_SIGHT_RANGE
                            : MentaloutTargeting.MAX_SIGHT_RANGE
            );
            if (destination == null) {
                feedback(player, "message.academy.mentalout.command_positioning.no_destination");
                return;
            }

            var destinationEntity = destination instanceof ControlDestination.Entity(java.util.UUID uuid)
                    ? uuid
                    : null;
            var skipped = 0;
            LivingEntity protectedTarget = null;
            var subjects = new ArrayList<LivingEntity>();
            for (var subject : MentaloutControlContext.subjects(player)) {
                if (MentalControlRuntime.isProtectedTarget(subject)) {
                    if (protectedTarget == null) protectedTarget = subject;
                    skipped++;
                    continue;
                }
                if (!subject.isAlive() || subject.isRemoved() || subject.level() != player.level()
                        || subject.getUUID().equals(destinationEntity)
                        || !MentalControlApi.evaluate(subject, ControlCapability.PATH_CONTROL).supported()) {
                    skipped++;
                    continue;
                }
                subjects.add(subject);
            }
            if (subjects.isEmpty()) {
                if (protectedTarget != null) {
                    MentalControlRuntime.notifyProtectionBlocked(player, protectedTarget);
                } else {
                    feedback(player, "message.academy.mentalout.no_supported_targets");
                }
                return;
            }

            var baseCost = MentaloutConfig.commandPositioningCost(player);
            var cost = 0.0f;
            for (var subject : subjects) {
                cost += baseCost * (MentalControlRuntime.isBossCost(subject)
                        ? MentaloutConfig.bossCostMultiplier(player)
                        : 1.0f);
            }
            cost = skill.adjustProficiencyCost(
                    player, SkillProficiencyProfile.CostKind.DYNAMIC, cost);

            var handles = new ArrayList<AutoCloseable>();
            var applied = new int[1];
            var failed = new int[1];
            var formationIndex = new int[1];
            var cast = skill.executeCommand(player, cost, () -> {
                for (var subject : subjects) {
                    try {
                        var subjectDestination = formationDestination(
                                destination, subject, formationIndex[0]++, subjects.size(),
                                skill.hasProficiencyMilestone(player, 3));
                        handles.add(MentalControlApi.apply(new ControlRequest(
                                player,
                                subject,
                                skill.getKey(),
                                CONTROL_PRIORITY,
                                Long.MAX_VALUE,
                                List.of(new ControlDirective.MoveTo(subjectDestination))
                        )));
                        applied[0]++;
                    } catch (RuntimeException exception) {
                        failed[0]++;
                    }
                }
            });
            if (!cast) {
                feedback(player, "message.academy.mentalout.insufficient_cp");
                return;
            }
            feedback(
                    player,
                    "message.academy.mentalout.command_positioning.applied",
                    applied[0],
                    skipped,
                    failed[0]
            );
            if (protectedTarget != null) {
                MentalControlRuntime.notifyProtectionBlocked(player, protectedTarget);
            }
        }

        private static ControlDestination formationDestination(
                ControlDestination destination,
                net.minecraft.world.entity.LivingEntity subject,
                int index,
                int count,
                boolean enabled
        ) {
            if (!enabled || !(destination instanceof ControlDestination.Position position)
                    || count <= 1) return destination;
            var spacing = Math.max(1.25, subject.getBbWidth() + 0.5);
            var radius = Math.max(1.5, count * spacing / (Math.PI * 2.0));
            var angle = Math.PI * 2.0 * index / count;
            var offset = new net.minecraft.world.phys.Vec3(
                    Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            return new ControlDestination.Position(position.dimension(), position.value().add(offset));
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
            return PacketTypes.COMMAND_POSITIONING_USE.get();
        }
    }
}
