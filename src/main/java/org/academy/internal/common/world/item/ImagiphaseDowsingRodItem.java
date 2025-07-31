package org.academy.internal.common.world.item;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.FBBDeserializers;
import org.academy.api.common.network.FBBSerializers;
import org.academy.api.common.network.future.HandlePayload;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.academy.internal.common.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ImagiphaseDowsingRodItem extends Item {
    public static List<BlockPos> RENDER_TARGET_POSITIONS = new ArrayList<>();

    public ImagiphaseDowsingRodItem() {
        super(new Item.Properties());
    }

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

    public static final class GetLevelChunkSectionsPacket extends IRequestPayload<ServerGamePacketListenerImpl, GetLevelChunkSectionsPacket.Response> {
        public BlockPos playerPos;

        public GetLevelChunkSectionsPacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public GetLevelChunkSectionsPacket(BlockPos playerPos) {
            super(null);
            this.playerPos = playerPos;
        }

        @Override
        public Class<GetLevelChunkSectionsPacket.Response> getExpectedResponseType() {
            return GetLevelChunkSectionsPacket.Response.class;
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeBlockPos(playerPos);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            playerPos = buf.readBlockPos();
        }

        public static final class Response extends IResponsePayload<ClientPacketListener> {
            public List<BlockPos> sectionsWithImagPhase;

            public Response(ClientPacketListener listener) {
                super(listener);
                this.sectionsWithImagPhase = new ArrayList<>();
            }

            public Response(List<BlockPos> sections) {
                super(null);
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
        }
    }

    @SuppressWarnings("resource")
    @HandlePayload
    public static ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response onGetLevelChunkSections(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var serverLevel = player.serverLevel();
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