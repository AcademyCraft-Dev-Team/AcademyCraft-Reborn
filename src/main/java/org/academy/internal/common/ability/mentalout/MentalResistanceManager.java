package org.academy.internal.common.ability.mentalout;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.mentalout.MentalResistanceClientState;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.control.MentalPerceptionRuntime;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative counterplay shared by every player-affecting Mentalout runtime. */
public final class MentalResistanceManager {
    public static final int INPUT_MASK = 0x3F;
    private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();
    private static final Map<UUID, Long> RESISTANCE_UNTIL = new HashMap<>();
    private static boolean clientInitialized;
    private static boolean serverInitialized;

    private MentalResistanceManager() {
    }

    public static synchronized void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static void markAffected(ServerPlayer controller, ServerPlayer subject, boolean takeover) {
        if (controller == null || subject == null || controller == subject
                || !controller.isAlive() || !subject.isAlive()
                || controller.level() != subject.level() || isResistant(subject)) {
            return;
        }
        var system = AbilitySystemServer.getSystem(controller);
        var controllerLevel = normalizedAbilityLevel(system.getPlayerLevel(controller.getUUID()));
        var subjectLevel = normalizedAbilityLevel(
                AbilitySystemServer.getSystem(subject).getPlayerLevel(subject.getUUID()));
        var now = subject.level().getGameTime();
        var challenge = CHALLENGES.computeIfAbsent(subject.getUUID(), _ -> new Challenge(subject));
        challenge.exposures.put(controller.getUUID(), new Exposure(
                controller.getUUID(), controllerLevel, takeover, now));
        challenge.refresh(subjectLevel, now);
    }

    public static void tick(MinecraftServer server) {
        var now = server.overworld().getGameTime();
        RESISTANCE_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (var challenge : List.copyOf(CHALLENGES.values())) {
            challenge.exposures.entrySet().removeIf(entry -> entry.getValue().lastSeenTick < now);
            var subject = server.getPlayerList().getPlayer(challenge.subjectId);
            if (subject == null || !subject.isAlive() || subject.hasDisconnected()
                    || challenge.exposures.isEmpty() || isResistant(subject)) {
                CHALLENGES.remove(challenge.subjectId, challenge);
                if (subject != null) sendInactive(subject);
                continue;
            }
            var subjectLevel = normalizedAbilityLevel(
                    AbilitySystemServer.getSystem(subject).getPlayerLevel(subject.getUUID()));
            challenge.refresh(subjectLevel, now);
        }
    }

    public static boolean isResistant(ServerPlayer subject) {
        if (subject == null) return false;
        var until = RESISTANCE_UNTIL.getOrDefault(subject.getUUID(), Long.MIN_VALUE);
        if (until <= subject.level().getGameTime()) {
            RESISTANCE_UNTIL.remove(subject.getUUID(), until);
            return false;
        }
        return true;
    }

    public static long resistanceUntil(ServerPlayer subject) {
        return isResistant(subject)
                ? RESISTANCE_UNTIL.getOrDefault(subject.getUUID(), 0L)
                : 0L;
    }

    public static int breakThreshold(int controllerLevel) {
        var level = normalizedAbilityLevel(controllerLevel);
        return level * level * 2 + 5;
    }

    public static int resistanceTicks(int controllerLevel) {
        return Math.max(0, 20 - normalizedAbilityLevel(controllerLevel) * 2) * 20;
    }

    public static int inputPoints(int edgeMask, boolean takeover) {
        var clicks = Integer.bitCount(edgeMask & INPUT_MASK);
        return clicks * (takeover ? 2 : 1);
    }

    public static int progress(ServerPlayer subject) {
        var challenge = subject == null ? null : CHALLENGES.get(subject.getUUID());
        return challenge == null ? 0 : challenge.points;
    }

    public static int threshold(ServerPlayer subject) {
        var challenge = subject == null ? null : CHALLENGES.get(subject.getUUID());
        return challenge == null ? 1 : challenge.threshold;
    }

    public static void releaseEntity(UUID entityId) {
        if (entityId == null) return;
        CHALLENGES.remove(entityId);
        RESISTANCE_UNTIL.remove(entityId);
        for (var challenge : CHALLENGES.values()) challenge.exposures.remove(entityId);
    }

    public static void clear() {
        CHALLENGES.clear();
        RESISTANCE_UNTIL.clear();
    }

    private static void acceptInput(ServerPlayer subject, long sequence, int edgeMask) {
        var challenge = CHALLENGES.get(subject.getUUID());
        if (challenge == null || !challenge.eligible || isResistant(subject)
                || sequence < 0L || sequence <= challenge.lastInputSequence) return;
        var now = subject.level().getGameTime();
        if (challenge.lastInputTick == now) return;
        var gained = inputPoints(edgeMask, challenge.takeover);
        if (gained <= 0) return;
        challenge.lastInputSequence = sequence;
        challenge.lastInputTick = now;
        challenge.points = Math.min(challenge.threshold, challenge.points + gained);
        if (challenge.points < challenge.threshold) {
            challenge.sync();
            return;
        }
        breakFree(subject, challenge);
    }

    private static void breakFree(ServerPlayer subject, Challenge challenge) {
        var controllerIds = List.copyOf(challenge.exposures.keySet());
        var level = challenge.controllerLevel;
        CHALLENGES.remove(subject.getUUID(), challenge);
        var now = subject.level().getGameTime();
        var resistanceTicks = resistanceTicks(level);
        if (resistanceTicks > 0) RESISTANCE_UNTIL.put(subject.getUUID(), now + resistanceTicks);

        PlayerControlSessionManager.breakFree(subject);
        MentaloutControlContext.releaseEffects(subject.getUUID());
        MentalIntrusionManager.releaseTarget(subject.getUUID());
        PrecisionOperationRuntime.releaseEntity(subject.level().getServer(), subject.getUUID());
        MentalPerceptionRuntime.releaseEntity(subject.getUUID());
        MentalControlRuntime.releaseBySubject(subject.level().getServer(), subject.getUUID());

        sendInactive(subject);
        subject.sendOverlayMessage(Component.translatable(
                "message.academy.mentalout.break_free.success",
                resistanceTicks / 20
        ));
        for (var controllerId : controllerIds) {
            var controller = subject.level().getServer().getPlayerList().getPlayer(controllerId);
            if (controller != null) controller.sendOverlayMessage(Component.translatable(
                    "message.academy.mentalout.break_free.controller",
                    subject.getDisplayName()
            ));
        }
    }

    private static void sendInactive(ServerPlayer subject) {
        MisakaNetworkServer.send(subject, new StatePacket(false, 0, 1, 0, false));
    }

    private static int normalizedAbilityLevel(int level) {
        return Math.clamp(level, 0, 5);
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void input(InputPacket packet) {
            acceptInput(packet.getPacketListener().getPlayer(), packet.sequence, packet.edgeMask);
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void state(StatePacket packet) {
            MentalResistanceClientState.update(
                    packet.active,
                    packet.points,
                    packet.threshold,
                    packet.controllerLevel,
                    packet.takeover
            );
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class InputPacket extends Packet<ServerGamePacketListenerImpl, InputPacket> {
        public static final StreamCodec<ByteBuf, InputPacket> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeLong(packet.sequence);
                    buffer.writeByte(packet.edgeMask);
                },
                buffer -> new InputPacket(buffer.readLong(), buffer.readUnsignedByte())
        );
        private final long sequence;
        private final int edgeMask;

        public InputPacket(long sequence, int edgeMask) {
            this.sequence = sequence;
            this.edgeMask = edgeMask & INPUT_MASK;
        }

        public long sequence() {
            return sequence;
        }

        public int edgeMask() {
            return edgeMask;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, InputPacket> getPacketType() {
            return PacketTypes.MENTAL_RESISTANCE_INPUT.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class StatePacket extends Packet<ClientPacketListener, StatePacket> {
        public static final StreamCodec<ByteBuf, StatePacket> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeBoolean(packet.active);
                    ByteBufCodecs.VAR_INT.encode(buffer, packet.points);
                    ByteBufCodecs.VAR_INT.encode(buffer, packet.threshold);
                    buffer.writeByte(packet.controllerLevel);
                    buffer.writeBoolean(packet.takeover);
                },
                buffer -> new StatePacket(
                        buffer.readBoolean(),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        buffer.readUnsignedByte(),
                        buffer.readBoolean()
                )
        );
        private final boolean active;
        private final int points;
        private final int threshold;
        private final int controllerLevel;
        private final boolean takeover;

        public StatePacket(boolean active, int points, int threshold, int controllerLevel, boolean takeover) {
            this.active = active;
            this.points = Math.max(0, points);
            this.threshold = Math.max(1, threshold);
            this.controllerLevel = normalizedAbilityLevel(controllerLevel);
            this.takeover = takeover;
        }

        public boolean active() {
            return active;
        }

        public int points() {
            return points;
        }

        public int threshold() {
            return threshold;
        }

        public int controllerLevel() {
            return controllerLevel;
        }

        public boolean takeover() {
            return takeover;
        }

        @Override
        public PacketType<ClientPacketListener, StatePacket> getPacketType() {
            return PacketTypes.MENTAL_RESISTANCE_STATE.get();
        }
    }

    private static final class Challenge {
        private final UUID subjectId;
        private final ServerPlayer subject;
        private final Map<UUID, Exposure> exposures = new HashMap<>();
        private int points;
        private int threshold = 1;
        private int controllerLevel;
        private boolean takeover;
        private boolean eligible;
        private long lastInputSequence = -1L;
        private long lastInputTick = Long.MIN_VALUE;
        private int lastSyncedHash;

        private Challenge(ServerPlayer subject) {
            this.subject = subject;
            subjectId = subject.getUUID();
        }

        private void refresh(int subjectLevel, long now) {
            controllerLevel = exposures.values().stream()
                    .filter(exposure -> exposure.lastSeenTick >= now)
                    .mapToInt(Exposure::controllerLevel)
                    .max()
                    .orElse(0);
            takeover = exposures.values().stream()
                    .anyMatch(exposure -> exposure.lastSeenTick >= now && exposure.takeover);
            threshold = breakThreshold(controllerLevel);
            points = Math.min(points, threshold);
            eligible = controllerLevel > 0 && subjectLevel >= controllerLevel;
            sync();
        }

        private void sync() {
            var hash = java.util.Objects.hash(eligible, points, threshold, controllerLevel, takeover);
            if (hash == lastSyncedHash) return;
            lastSyncedHash = hash;
            MisakaNetworkServer.send(subject, new StatePacket(
                    eligible, points, threshold, controllerLevel, takeover));
        }
    }

    private record Exposure(UUID controllerId, int controllerLevel, boolean takeover, long lastSeenTick) {
    }
}
