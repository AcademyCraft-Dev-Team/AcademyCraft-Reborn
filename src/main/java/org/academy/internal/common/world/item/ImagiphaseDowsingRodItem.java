package org.academy.internal.common.world.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.future.annotation.HandleFuture;
import org.academy.api.common.network.future.packet.RequestPacket;
import org.academy.api.common.network.future.packet.ResponsePacket;
import org.academy.api.common.network.packet.PacketType;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ImagiphaseDowsingRodItem extends Item {
    public static List<BlockPos> RENDER_TARGET_POSITIONS = new ArrayList<>();

    public ImagiphaseDowsingRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (level.isClientSide()) {
            if (entity instanceof LocalPlayer localPlayer) {
                if (!(localPlayer.getMainHandItem() == stack || localPlayer.getOffhandItem() == stack)) {
                    RENDER_TARGET_POSITIONS.clear();
                }
            }
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            RENDER_TARGET_POSITIONS.clear();
            var packet = new GetLevelChunkSectionsPacket(player.blockPosition());
            AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(packet, response -> {
                if (response != null) {
                    RENDER_TARGET_POSITIONS.addAll(response.getSectionsWithImagPhase());
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    public static final class GetLevelChunkSectionsPacket extends RequestPacket<ServerGamePacketListenerImpl, GetLevelChunkSectionsPacket, ClientGamePacketListener, GetLevelChunkSectionsPacket.Response> {
        public static final StreamCodec<ByteBuf, GetLevelChunkSectionsPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                GetLevelChunkSectionsPacket::getPlayerPos,
                GetLevelChunkSectionsPacket::new
        );

        private final BlockPos playerPos;

        public GetLevelChunkSectionsPacket(BlockPos playerPos) {
            this.playerPos = playerPos;
        }

        public BlockPos getPlayerPos() {
            return playerPos;
        }

        @Override
        public PacketType<ClientGamePacketListener, Response> getResponsePacketType() {
            return PacketTypes.GET_LEVEL_CHUNK_SECTIONS_RESPONSE.get();
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, GetLevelChunkSectionsPacket> getPacketType() {
            return PacketTypes.GET_LEVEL_CHUNK_SECTIONS.get();
        }

        public static final class Response extends ResponsePacket<ClientGamePacketListener, Response> {
            public static final StreamCodec<ByteBuf, Response> CODEC = StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    Response::getSectionsWithImagPhase,
                    Response::new
            );

            private final List<BlockPos> sectionsWithImagPhase;

            public Response(List<BlockPos> sections) {
                this.sectionsWithImagPhase = sections;
            }

            public List<BlockPos> getSectionsWithImagPhase() {
                return sectionsWithImagPhase;
            }

            @Override
            public PacketType<ClientGamePacketListener, Response> getPacketType() {
                return PacketTypes.GET_LEVEL_CHUNK_SECTIONS_RESPONSE.get();
            }
        }
    }

    @SuppressWarnings("resource")
    @HandleFuture
    public static ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response onGetLevelChunkSections(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var serverLevel = player.level();
        var chunkCache = serverLevel.getChunkSource();
        var sectionsWithImagPhase = new ArrayList<BlockPos>();

        for (var chunkHolder : chunkCache.chunkMap.visibleChunkMap.values()) {
            var chunk = chunkHolder.getTickingChunk();
            if (chunk != null) {
                var sections = chunk.getSections();
                for (var i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    if (section != null && !section.hasOnlyAir() && section.maybeHas(blockState -> blockState.getFluidState().is(Fluids.IMAGIPHASE_PLASMA.get()))) {
                        int sectionBottomY = chunk.getSectionYFromSectionIndex(i);
                        sectionsWithImagPhase.add(new BlockPos(chunk.getPos().getMinBlockX(), sectionBottomY, chunk.getPos().getMinBlockZ()));
                    }
                }
            }
        }
        return new ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response(sectionsWithImagPhase);
    }
}