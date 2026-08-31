package org.academy.internal.common.ability.mentalout.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
import org.academy.api.common.entitycontrol.*;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceScreen;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentaloutControlContext;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.skills.MentaloutTargeting;
import org.academy.internal.common.ability.mentalout.skills.lv1.TargetMisidentification;
import org.academy.internal.common.ability.mentalout.skills.lv2.MentalStupor;
import org.academy.internal.common.ability.mentalout.skills.lv3.CommandPositioning;
import org.academy.internal.common.ability.mentalout.skills.lv3.ImpressionManipulation;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.world.level.storage.SkillDataSerializer;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

/** Lv5 Mentalout group-command workspace and RTS control surface. */
public final class WideAreaInterference extends Skill {
    public static final int MAX_TARGETS = 64;
    public static final double MAX_COMMAND_RANGE = 96.0;
    public static final int CONTROL_PRIORITY = 300;
    public static final int FARMING_PRIORITY = 1_000;

    static {
        // Retain only the old data codec so existing program books can be migrated.
        SkillDataSerializer.registerType(PrecisionOperation.Data.ID, PrecisionOperation.Data.class);
    }

    public WideAreaInterference() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(0)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.IMPRESSION_MANIPULATION, Skills.MENTAL_STUPOR)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Impression Manipulation", "academy:impression_manipulation"))
                .devCondition(new DevCondition.DependencyCondition(
                        "Mental Stupor", "academy:mental_stupor"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_OPEN, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_OPEN,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_EQUALS,
                        InputConstants.PRESS,
                        0
                )
        ), context -> {
            if (context.action() == InputConstants.PRESS) Client.open();
        });
        MisakaNetworkClient.NETWORK_MANAGER.register(ClientPackets.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.WIDE_AREA_INTERFERENCE.get(),
                        List.of(
                                ImpressionManipulation.Client.SKILL_INFO,
                                MentalStupor.Client.SKILL_INFO
                        ),
                        R.textures.ability.mentalout.skill.wide_area_interference.icon,
                        210,
                        80
                )
        );
        public static final String KEY_NAME_OPEN = SkillNames.WIDE_AREA_INTERFERENCE + "_open";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void open() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player == null || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.WIDE_AREA_INTERFERENCE.get())) return;
            minecraft.gui.setScreen(new WideAreaInterferenceScreen());
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
        private static final Map<UUID, Map<UUID, AutoCloseable>> POSITIONING_HANDLES = new HashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void enroll(EnrollPacket packet) {
            var listener = packet.getPacketListener();
            if (!MentaloutRequestGuard.acceptSkillUse(
                    listener,
                    MentaloutRequestGuard.SkillUse.WIDE_AREA_INTERFERENCE,
                    packet.sequence
            )) return;
            var controller = listener.getPlayer();
            if (!available(controller)) return;
            var candidates = new ArrayList<LivingEntity>();
            for (var id : packet.targets) {
                var entity = controller.level().getEntity(id);
                if (!(entity instanceof LivingEntity living) || living == controller
                        || !living.isAlive() || living.isRemoved()
                        || controller.distanceToSqr(living) > MAX_COMMAND_RANGE * MAX_COMMAND_RANGE
                        || PvpSetting.shouldPrevent(controller, living)) continue;
                candidates.add(living);
            }
            var prefiltered = packet.targets.size() - candidates.size();
            var result = candidates.isEmpty()
                    ? new MentalControlRosterApi.EnrollmentBatch(0, 0, 0, List.of())
                    : MentalControlRosterApi.enroll(controller, candidates);
            if (result.added() > 0) Skills.WIDE_AREA_INTERFERENCE.get().reportTrigger(controller);
            feedback(controller, FeedbackCode.ENROLL, result.added(),
                    result.alreadyControlled(), result.rejected() + prefiltered);
        }

        @SubscribePacket
        public static void command(CommandPacket packet) {
            var listener = packet.getPacketListener();
            if (!MentaloutRequestGuard.acceptSkillUse(
                    listener,
                    MentaloutRequestGuard.SkillUse.WIDE_AREA_INTERFERENCE,
                    packet.sequence
            )) return;
            var controller = listener.getPlayer();
            if (!available(controller)) return;
            var roster = MentaloutControlContext.subjects(controller);
            var requested = new LinkedHashSet<>(packet.targets);
            var subjects = roster.stream()
                    .filter(subject -> requested.contains(subject.getUUID()))
                    .limit(MAX_TARGETS)
                    .toList();
            if (subjects.isEmpty()) {
                feedback(controller, FeedbackCode.NO_TARGETS, 0, 0, 0);
                return;
            }
            var subjectIds = subjects.stream().map(LivingEntity::getUUID)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            var source = Skills.WIDE_AREA_INTERFERENCE.get().getKey();
            GroupControlApi.cancelSubjects(controller.getUUID(), source, subjectIds);
            cancelPositioning(controller.getUUID(), subjectIds);

            if (packet.action == Action.RELEASE) {
                var released = MentalControlRosterApi.release(controller, subjectIds);
                feedback(controller, FeedbackCode.COMMAND, released, 0, subjects.size() - released);
                return;
            }
            var context = MentaloutControlContext.get(controller);
            if (context == null) {
                feedback(controller, FeedbackCode.NO_TARGETS, 0, 0, 0);
                return;
            }
            if (packet.action == Action.STUPOR || packet.action == Action.IMPRESSION) {
                var result = packet.action == Action.STUPOR
                        ? context.toggleStupor(subjectIds) : context.toggleImpression(subjectIds);
                if (result.applied() > 0 || !result.active()) {
                    Skills.WIDE_AREA_INTERFERENCE.get().reportTrigger(controller);
                }
                feedback(controller, FeedbackCode.COMMAND, result.applied(), result.skipped(), result.failed());
                return;
            }
            if (packet.action == Action.MISIDENTIFICATION) {
                var forced = packet.entityTarget == null
                        ? null : controller.level().getEntity(packet.entityTarget);
                var targetSkill = Skills.TARGET_MISIDENTIFICATION.get();
                var targetRange = targetSkill.hasProficiencyMilestone(controller, 2)
                        ? MentaloutTargeting.PROFICIENCY_MAX_SIGHT_RANGE
                        : MentaloutTargeting.MAX_SIGHT_RANGE;
                if (!(forced instanceof LivingEntity living)
                        || PvpSetting.shouldPrevent(controller, living)
                        || !MentaloutTargeting.isValidExtendedTarget(
                        controller, living, targetRange)) {
                    feedback(controller, FeedbackCode.INVALID_TARGET, 0, 0, 0);
                    return;
                }
                var application = TargetMisidentification.applyToSubjects(
                        controller, living, subjectIds);
                var result = application.batch();
                if (application.clearing()) {
                    feedback(controller, FeedbackCode.TARGET_CLEARED, 0, 0, 0);
                    return;
                }
                if (result.insufficientCp()) {
                    feedback(controller, FeedbackCode.INSUFFICIENT_CP, 0, 0, 0);
                    return;
                }
                if (result.applied() > 0) Skills.WIDE_AREA_INTERFERENCE.get().reportTrigger(controller);
                feedback(controller, FeedbackCode.COMMAND, result.applied(), result.skipped(), result.failed());
                return;
            }
            if (outOfRange(controller, packet.first)
                    || packet.action != Action.MOVE && outOfRange(controller, packet.second)) {
                feedback(controller, FeedbackCode.OUT_OF_RANGE, 0, 0, 0);
                return;
            }

            if (packet.action == Action.MOVE) {
                var destination = new ControlDestination.Position(
                        controller.level().dimension().identifier(),
                        Vec3.atBottomCenterOf(packet.first)
                );
                var result = CommandPositioning.applyToSubjects(
                        controller,
                        subjects,
                        destination,
                        source,
                        CommandPositioning.CONTROL_PRIORITY,
                        Skills.COMMAND_POSITIONING.get().hasProficiencyMilestone(controller, 3)
                );
                if (!result.handles().isEmpty()) {
                    POSITIONING_HANDLES.computeIfAbsent(
                            controller.getUUID(), ignored -> new HashMap<>())
                            .putAll(result.handles());
                }
                if (result.applied() > 0) Skills.WIDE_AREA_INTERFERENCE.get().reportTrigger(controller);
                feedback(controller, FeedbackCode.COMMAND,
                        result.applied(), result.skipped(), result.failed());
                if (result.protectedTarget() != null) {
                    MentalControlRuntime.notifyProtectionBlocked(
                            controller, result.protectedTarget());
                }
                return;
            }

            final GroupControlCommand command;
            try {
                command = switch (packet.action) {
                    case GATHER -> new GroupControlCommand.GatherResources(new BlockWorkRegion(
                            controller.level().dimension().identifier(), packet.first, packet.second));
                    case FARM -> new GroupControlCommand.Farm(new BlockWorkRegion(
                            controller.level().dimension().identifier(), packet.first, packet.second));
                    default -> throw new IllegalArgumentException("Action has no group command");
                };
            } catch (IllegalArgumentException exception) {
                feedback(controller, FeedbackCode.INVALID_REGION, 0, 0, 0);
                return;
            }
            GroupControlObserver observer = event -> {
                if (event.status() == GroupControlTaskEvent.Status.COMPLETED) {
                    feedback(controller, FeedbackCode.TASK_COMPLETED, 0, 0, 0, event.subjectName());
                } else if (event.status() == GroupControlTaskEvent.Status.PATH_FAILED) {
                    feedback(controller, FeedbackCode.TASK_PATH_FAILED, 0, 0, 0, event.subjectName());
                }
            };
            var result = GroupControlApi.dispatch(new GroupControlRequest(
                    controller,
                    source,
                    subjects,
                    command,
                    packet.action == Action.FARM ? FARMING_PRIORITY : CONTROL_PRIORITY,
                    observer
            ));
            if (result.applied() > 0) Skills.WIDE_AREA_INTERFERENCE.get().reportTrigger(controller);
            feedback(controller, FeedbackCode.COMMAND, result.applied(),
                    result.unsupported(), result.failed());
        }

        private static boolean outOfRange(ServerPlayer controller, BlockPos position) {
            return position.distSqr(controller.blockPosition())
                    > MAX_COMMAND_RANGE * MAX_COMMAND_RANGE;
        }

        private static boolean available(ServerPlayer player) {
            return player != null && player.isAlive()
                    && Skills.WIDE_AREA_INTERFERENCE.get().isEnabled(player);
        }

        private static void cancelPositioning(UUID controllerId, Set<UUID> subjectIds) {
            var handles = POSITIONING_HANDLES.get(controllerId);
            if (handles == null) return;
            for (var subjectId : subjectIds) {
                close(handles.remove(subjectId));
            }
            if (handles.isEmpty()) POSITIONING_HANDLES.remove(controllerId);
        }

        private static void clearPositioning(UUID controllerId) {
            var handles = POSITIONING_HANDLES.remove(controllerId);
            if (handles != null) handles.values().forEach(Server::close);
        }

        private static void clearPositioning() {
            POSITIONING_HANDLES.values().forEach(handles ->
                    handles.values().forEach(Server::close));
            POSITIONING_HANDLES.clear();
        }

        private static void close(AutoCloseable handle) {
            if (handle == null) return;
            try {
                handle.close();
            } catch (Exception ignored) {
            }
        }

        private static void feedback(
                ServerPlayer player,
                FeedbackCode code,
                int applied,
                int skipped,
                int failed
        ) {
            feedback(player, code, applied, skipped, failed, "");
        }

        private static void feedback(
                ServerPlayer player,
                FeedbackCode code,
                int applied,
                int skipped,
                int failed,
                String detail
        ) {
            MisakaNetworkServer.send(player, new FeedbackPacket(code, applied, skipped, failed, detail));
        }
    }

    public static final class ClientPackets {
        private ClientPackets() {
        }

        @SubscribePacket
        public static void feedback(FeedbackPacket packet) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;
            var message = switch (packet.code) {
                case TASK_COMPLETED, TASK_PATH_FAILED ->
                        Component.translatable(packet.code.translationKey, packet.detail);
                case INVALID_TARGET, INSUFFICIENT_CP, TARGET_CLEARED ->
                        Component.translatable(packet.code.translationKey);
                default -> Component.translatable(
                        packet.code.translationKey, packet.applied, packet.skipped, packet.failed);
            };
            player.sendOverlayMessage(message);
        }
    }

    public enum Action {
        MOVE,
        MISIDENTIFICATION,
        STUPOR,
        IMPRESSION,
        GATHER,
        FARM,
        RELEASE
    }

    public enum FeedbackCode {
        ENROLL("message.academy.wide_area_interference.enroll"),
        COMMAND("message.academy.wide_area_interference.command"),
        NO_TARGETS("message.academy.wide_area_interference.no_targets"),
        OUT_OF_RANGE("message.academy.wide_area_interference.out_of_range"),
        INVALID_REGION("message.academy.wide_area_interference.invalid_region"),
        INVALID_TARGET("message.academy.mentalout.invalid_target"),
        INSUFFICIENT_CP("message.academy.mentalout.insufficient_cp"),
        TARGET_CLEARED("message.academy.mentalout.target_misidentification.cleared"),
        TASK_COMPLETED("message.academy.wide_area_interference.task_completed"),
        TASK_PATH_FAILED("message.academy.wide_area_interference.task_path_failed");

        private final String translationKey;

        FeedbackCode(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                Server.clearPositioning(player.getUUID());
            }
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            Server.clearPositioning();
        }
    }

    private static void encodeTargets(ByteBuf buf, List<UUID> targets) {
        var safe = targets.stream().distinct().limit(MAX_TARGETS).toList();
        ByteBufCodecs.VAR_INT.encode(buf, safe.size());
        safe.forEach(target -> {
            buf.writeLong(target.getMostSignificantBits());
            buf.writeLong(target.getLeastSignificantBits());
        });
    }

    private static List<UUID> decodeTargets(ByteBuf buf) {
        var size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > MAX_TARGETS) {
            throw new DecoderException("Invalid wide-area target count: " + size);
        }
        var result = new ArrayList<UUID>(size);
        for (var index = 0; index < size; index++) {
            result.add(new UUID(buf.readLong(), buf.readLong()));
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class EnrollPacket extends Packet<ServerGamePacketListenerImpl, EnrollPacket> {
        public static final StreamCodec<ByteBuf, EnrollPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.sequence);
                    encodeTargets(buf, packet.targets);
                },
                buf -> new EnrollPacket(buf.readLong(), decodeTargets(buf))
        );
        private final long sequence;
        private final List<UUID> targets;

        public EnrollPacket(long sequence, List<UUID> targets) {
            this.sequence = sequence;
            this.targets = targets.stream().distinct().limit(MAX_TARGETS).toList();
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, EnrollPacket> getPacketType() {
            return PacketTypes.WIDE_AREA_INTERFERENCE_ENROLL.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CommandPacket extends Packet<ServerGamePacketListenerImpl, CommandPacket> {
        public static final StreamCodec<ByteBuf, CommandPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.sequence);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.action.ordinal());
                    encodeTargets(buf, packet.targets);
                    BlockPos.STREAM_CODEC.encode(buf, packet.first);
                    BlockPos.STREAM_CODEC.encode(buf, packet.second);
                    buf.writeBoolean(packet.entityTarget != null);
                    if (packet.entityTarget != null) {
                        buf.writeLong(packet.entityTarget.getMostSignificantBits());
                        buf.writeLong(packet.entityTarget.getLeastSignificantBits());
                    }
                },
                buf -> new CommandPacket(
                        buf.readLong(),
                        action(ByteBufCodecs.VAR_INT.decode(buf)),
                        decodeTargets(buf),
                        BlockPos.STREAM_CODEC.decode(buf),
                        BlockPos.STREAM_CODEC.decode(buf),
                        buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null
                )
        );
        private final long sequence;
        private final Action action;
        private final List<UUID> targets;
        private final BlockPos first;
        private final BlockPos second;
        private final UUID entityTarget;

        public CommandPacket(
                long sequence,
                Action action,
                List<UUID> targets,
                BlockPos first,
                BlockPos second,
                UUID entityTarget
        ) {
            this.sequence = sequence;
            this.action = Objects.requireNonNull(action, "action");
            this.targets = targets.stream().distinct().limit(MAX_TARGETS).toList();
            this.first = Objects.requireNonNull(first, "first").immutable();
            this.second = Objects.requireNonNull(second, "second").immutable();
            this.entityTarget = entityTarget;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CommandPacket> getPacketType() {
            return PacketTypes.WIDE_AREA_INTERFERENCE_COMMAND.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class FeedbackPacket extends Packet<ClientPacketListener, FeedbackPacket> {
        public static final StreamCodec<ByteBuf, FeedbackPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.code.ordinal());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.applied);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.skipped);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.failed);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.detail);
                },
                buf -> new FeedbackPacket(
                        feedbackCode(ByteBufCodecs.VAR_INT.decode(buf)),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );
        private final FeedbackCode code;
        private final int applied;
        private final int skipped;
        private final int failed;
        private final String detail;

        public FeedbackPacket(FeedbackCode code, int applied, int skipped, int failed, String detail) {
            this.code = Objects.requireNonNull(code, "code");
            this.applied = Math.max(0, applied);
            this.skipped = Math.max(0, skipped);
            this.failed = Math.max(0, failed);
            this.detail = detail == null ? "" : detail.substring(0, Math.min(96, detail.length()));
        }

        @Override
        public PacketType<ClientPacketListener, FeedbackPacket> getPacketType() {
            return PacketTypes.WIDE_AREA_INTERFERENCE_FEEDBACK.get();
        }
    }

    private static Action action(int ordinal) {
        var values = Action.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Action.MOVE;
    }

    private static FeedbackCode feedbackCode(int ordinal) {
        var values = FeedbackCode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FeedbackCode.COMMAND;
    }
}
