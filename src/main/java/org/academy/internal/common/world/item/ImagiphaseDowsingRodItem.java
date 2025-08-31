package org.academy.internal.common.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.academy.api.common.network.FBBDeserializers;
import org.academy.api.common.network.FBBSerializers;
import org.academy.api.common.network.future.*;
import org.academy.internal.common.network.future.PayloadTypes;
import org.academy.internal.common.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ImagiphaseDowsingRodItem extends Item {
    public static List<BlockPos> RENDER_TARGET_POSITIONS = new ArrayList<>();

    public ImagiphaseDowsingRodItem(Properties properties) {
        super(properties);
    }
/*

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) {
            if (!isSelected && entity instanceof LocalPlayer localPlayer) {
                if (!(localPlayer.getItemInHand(InteractionHand.MAIN_HAND).getItem() == this
                        || localPlayer.getItemInHand(InteractionHand.OFF_HAND).getItem() == this)) {
                    RENDER_TARGET_POSITIONS.clear();
                }
            }
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            RENDER_TARGET_POSITIONS.clear();
            GetLevelChunkSectionsPacket packet = new GetLevelChunkSectionsPacket(player.blockPosition());
            AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(packet, (GetLevelChunkSectionsPacket.Response response) -> {
                if (response != null && response.sectionsWithImagPhase != null) {
                    RENDER_TARGET_POSITIONS = response.sectionsWithImagPhase;
                }
            });
        }
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }
*/

    public static final class GetLevelChunkSectionsPacket extends RequestPayload<ServerGamePacketListenerImpl, GetLevelChunkSectionsPacket.Response> {
        public BlockPos playerPos;

        public GetLevelChunkSectionsPacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public GetLevelChunkSectionsPacket(BlockPos playerPos) {
            super(null);
            this.playerPos = playerPos;
        }

        @Override
        public @NotNull PayloadType<?, Response> getExpectedResponsePayloadType() {
            return PayloadTypes.GET_LEVEL_CHUNK_SECTIONS_RESPONSE.get();
        }

        @Override
        public @NotNull PayloadType<ServerGamePacketListenerImpl, ? extends Payload<ServerGamePacketListenerImpl>> getPayloadType() {
            return PayloadTypes.GET_LEVEL_CHUNK_SECTIONS.get();
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeBlockPos(playerPos);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            playerPos = buf.readBlockPos();
        }

        public static final class Response extends ResponsePayload<ClientGamePacketListener> {
            public List<BlockPos> sectionsWithImagPhase;

            public Response(ClientGamePacketListener listener) {
                super(listener);
                this.sectionsWithImagPhase = new ArrayList<>();
            }

            public Response(List<BlockPos> sections) {
                this.sectionsWithImagPhase = sections;
            }

            @Override
            public void write(@NotNull FriendlyByteBuf buf) {
                FBBSerializers.getCollectionFriendlyByteBufSerializer(BlockPos.class).serialize(buf, sectionsWithImagPhase);
            }

            @Override
            public void read(@NotNull FriendlyByteBuf buf) {
                this.sectionsWithImagPhase = FBBDeserializers.getCollectionFriendlyByteBufDeserializer(BlockPos.class, ArrayList::new).deserialize(buf);
            }

            @Override
            public @NotNull PayloadType<ClientGamePacketListener, ? extends Payload<ClientGamePacketListener>> getPayloadType() {
                return PayloadTypes.GET_LEVEL_CHUNK_SECTIONS_RESPONSE.get();
            }
        }
    }

    @SuppressWarnings("resource")
    @HandlePayload
    public static ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response onGetLevelChunkSections(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var serverLevel = player.level();
        var chunkCache = serverLevel.getChunkSource();
        var sectionsWithImagPhase = new ArrayList<BlockPos>();

        chunkCache.chunkMap.getChunks().forEach(chunkHolder -> {
            var chunk = chunkHolder.getTickingChunk();
            if (chunk != null) {
                LevelChunkSection[] sections = chunk.getSections();
                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    LevelChunkSection section = sections[sectionIndex];
                    if (section != null && !section.hasOnlyAir() && section.maybeHas(blockState -> blockState.getFluidState().is(Fluids.IMAGIPHASE_PLASMA.get()))) {
                        int sectionBottomY = chunk.getSectionYFromSectionIndex(sectionIndex);
                        sectionsWithImagPhase.add(new BlockPos(chunk.getPos().getMinBlockX(), sectionBottomY, chunk.getPos().getMinBlockZ()));
                    }
                }
            }
        });
        return new ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response(sectionsWithImagPhase);
    }
}