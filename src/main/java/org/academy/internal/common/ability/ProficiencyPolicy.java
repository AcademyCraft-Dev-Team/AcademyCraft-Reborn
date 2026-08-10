package org.academy.internal.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.config.AbilityConfig;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class ProficiencyPolicy {
    public static final int HARD_MAX_AREA_TELEPORT_AXIS = 40;
    public static final int HARD_MAX_CAPTURED_PROJECTILES = 16;
    public static final int HARD_MAX_BONUS_ENTITIES_PER_TICK = 96;
    private static volatile Snapshot clientSnapshot = Snapshot.DEFAULT;

    private ProficiencyPolicy() {
    }

    public static Snapshot server(ServerPlayer player) {
        if (player == null || player.level().getServer() == null
                || player.level().getServer().getAcademyCraftServer() == null) {
            return Snapshot.DEFAULT;
        }
        return sanitize(player.level().getServer().getAcademyCraftServer()
                .getAbilityConfig().proficiency);
    }

    public static Snapshot client() {
        return clientSnapshot;
    }

    public static boolean clientHasRestriction(Skill skill) {
        if (skill == null) return false;
        var policy = clientSnapshot;
        if (!policy.enabled()) return true;
        return switch (skill.getKey().getPath()) {
            case SkillNames.MINING_BEAM -> !policy.allowMiningBeamSmelting();
            case SkillNames.AREA_TELEPORT_SETUP -> !policy.allowAreaTeleportTransforms();
            case SkillNames.AREA_TELEPORT_START -> !policy.allowAreaTeleportSwap();
            case SkillNames.MENTAL_TAKEOVER -> !policy.allowMentalTakeoverExtendedControls();
            default -> false;
        };
    }

    public static void initClient() {
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    static Snapshot sanitize(AbilityConfig.ProficiencySettings settings) {
        if (settings == null) return Snapshot.DEFAULT;
        return new Snapshot(
                settings.enabled,
                settings.allowMiningBeamSmelting,
                settings.allowAreaTeleportTransforms,
                settings.allowAreaTeleportSwap,
                settings.allowMentalTakeoverExtendedControls,
                Math.clamp(settings.maxAreaTeleportAxis, 1, HARD_MAX_AREA_TELEPORT_AXIS),
                Math.clamp(settings.maxCapturedProjectiles, 0, HARD_MAX_CAPTURED_PROJECTILES),
                Math.clamp(settings.maxBonusEntitiesPerTick, 1, HARD_MAX_BONUS_ENTITIES_PER_TICK)
        );
    }

    public record Snapshot(
            boolean enabled,
            boolean allowMiningBeamSmelting,
            boolean allowAreaTeleportTransforms,
            boolean allowAreaTeleportSwap,
            boolean allowMentalTakeoverExtendedControls,
            int maxAreaTeleportAxis,
            int maxCapturedProjectiles,
            int maxBonusEntitiesPerTick
    ) {
        public static final Snapshot DEFAULT = new Snapshot(
                true, true, true, true, true,
                HARD_MAX_AREA_TELEPORT_AXIS,
                HARD_MAX_CAPTURED_PROJECTILES,
                HARD_MAX_BONUS_ENTITIES_PER_TICK
        );
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                MisakaNetworkServer.send(player, new SyncPacket(server(player)));
            }
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void receive(SyncPacket packet) {
            clientSnapshot = packet.snapshot;
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                SyncPacket::write,
                SyncPacket::read
        );
        private final Snapshot snapshot;

        public SyncPacket(Snapshot snapshot) {
            this.snapshot = snapshot == null ? Snapshot.DEFAULT : snapshot;
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.PROFICIENCY_POLICY_SYNC.get();
        }

        private static void write(ByteBuf buf, SyncPacket packet) {
            var snapshot = packet.snapshot;
            ByteBufCodecs.BOOL.encode(buf, snapshot.enabled());
            ByteBufCodecs.BOOL.encode(buf, snapshot.allowMiningBeamSmelting());
            ByteBufCodecs.BOOL.encode(buf, snapshot.allowAreaTeleportTransforms());
            ByteBufCodecs.BOOL.encode(buf, snapshot.allowAreaTeleportSwap());
            ByteBufCodecs.BOOL.encode(buf, snapshot.allowMentalTakeoverExtendedControls());
            ByteBufCodecs.VAR_INT.encode(buf, snapshot.maxAreaTeleportAxis());
            ByteBufCodecs.VAR_INT.encode(buf, snapshot.maxCapturedProjectiles());
            ByteBufCodecs.VAR_INT.encode(buf, snapshot.maxBonusEntitiesPerTick());
        }

        private static SyncPacket read(ByteBuf buf) {
            return new SyncPacket(new Snapshot(
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)
            ));
        }
    }
}
