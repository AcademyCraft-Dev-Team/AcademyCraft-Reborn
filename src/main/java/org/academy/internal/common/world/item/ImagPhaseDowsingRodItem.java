package org.academy.internal.common.world.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.level.material.Fluids;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans loaded chunk sections around the user for imag phase fluid.
 */
public final class ImagPhaseDowsingRodItem extends Item {
    private static final int MAX_SCAN_RADIUS_CHUNKS = 12;
    private static final int MAX_TARGET_SECTIONS = 1024;
    private static final int USE_COOLDOWN_TICKS = 20;

    public ImagPhaseDowsingRodItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS;
            var scan = scanImagPhase(serverLevel, serverPlayer);
            MisakaNetworkServer.send(serverPlayer, new SyncPacket(scan.sections()));
            if (scan.nearestFluid() == null) {
                serverPlayer.sendSystemMessage(Component.translatable(
                        "item.academy.imag_phase_dowsing_rod.no_target"
                ));
            } else {
                var target = scan.nearestFluid();
                serverPlayer.sendSystemMessage(Component.translatable(
                        "item.academy.imag_phase_dowsing_rod.nearest",
                        target.getX(), target.getY(), target.getZ()
                ));
            }
            player.getCooldowns().addCooldown(stack, USE_COOLDOWN_TICKS);
        }
        return InteractionResult.SUCCESS;
    }

    private static ScanResult scanImagPhase(ServerLevel level, ServerPlayer player) {
        var center = player.chunkPosition();
        var serverViewDistance = level.getServer().getPlayerList().getViewDistance();
        var radius = Math.min(MAX_SCAN_RADIUS_CHUNKS, Math.max(0, serverViewDistance));
        var targets = new ArrayList<BlockPos>();
        var chunkSource = level.getChunkSource();
        BlockPos nearestFluid = null;
        var nearestDistance = Double.MAX_VALUE;

        for (var chunkX = center.x() - radius; chunkX <= center.x() + radius; chunkX++) {
            for (var chunkZ = center.z() - radius; chunkZ <= center.z() + radius; chunkZ++) {
                var chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;

                var chunkPos = new ChunkPos(chunkX, chunkZ);
                var sections = chunk.getSections();
                for (var sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    var section = sections[sectionIndex];
                    if (section.hasOnlyAir() || !section.maybeHas(
                            state -> state.getFluidState().is(Fluids.IMAG_PHASE.get())
                    )) continue;

                    var sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
                    var sectionMinY = SectionPos.sectionToBlockCoord(sectionY);
                    if (targets.size() < MAX_TARGET_SECTIONS) {
                        targets.add(new BlockPos(
                                chunkPos.getMinBlockX(),
                                sectionMinY,
                                chunkPos.getMinBlockZ()
                        ));
                    }
                    for (var localY = 0; localY < 16; localY++) {
                        for (var localZ = 0; localZ < 16; localZ++) {
                            for (var localX = 0; localX < 16; localX++) {
                                if (!section.getBlockState(localX, localY, localZ)
                                        .getFluidState().is(Fluids.IMAG_PHASE.get())) continue;
                                var blockX = chunkPos.getMinBlockX() + localX;
                                var blockY = sectionMinY + localY;
                                var blockZ = chunkPos.getMinBlockZ() + localZ;
                                var distance = player.distanceToSqr(
                                        blockX + 0.5, blockY + 0.5, blockZ + 0.5
                                );
                                if (distance < nearestDistance) {
                                    nearestDistance = distance;
                                    nearestFluid = new BlockPos(blockX, blockY, blockZ);
                                }
                            }
                        }
                    }
                }
            }
        }
        return new ScanResult(List.copyOf(targets), nearestFluid);
    }

    private record ScanResult(List<BlockPos> sections, BlockPos nearestFluid) {
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buffer, packet.sections.size());
                    for (var section : packet.sections) BlockPos.STREAM_CODEC.encode(buffer, section);
                },
                buffer -> {
                    var count = ByteBufCodecs.VAR_INT.decode(buffer);
                    if (count < 0 || count > MAX_TARGET_SECTIONS) {
                        throw new IllegalArgumentException("Invalid imag phase section count: " + count);
                    }
                    var sections = new ArrayList<BlockPos>(count);
                    for (var i = 0; i < count; i++) sections.add(BlockPos.STREAM_CODEC.decode(buffer));
                    return new SyncPacket(sections);
                }
        );
        private final List<BlockPos> sections;

        public SyncPacket(List<BlockPos> sections) {
            this.sections = List.copyOf(sections);
        }

        public List<BlockPos> sections() {
            return sections;
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.IMAG_PHASE_DOWSING_SYNC.get();
        }
    }
}
