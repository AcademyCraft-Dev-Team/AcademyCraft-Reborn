package org.academy.internal.common.world.item;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.future.HandlePayload;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.academy.internal.common.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ImagPhaseDowsingRodItem extends Item {
    public static List<BlockPos> RENDER_TARGET_POSITIONS = new ArrayList<>();

    public ImagPhaseDowsingRodItem() {
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

        public GetLevelChunkSectionsPacket() {}

        public GetLevelChunkSectionsPacket(BlockPos playerPos) {
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

        public static final class Response implements IResponsePayload {
            public List<BlockPos> sectionsWithImagPhase;

            public Response() {
                this.sectionsWithImagPhase = new ArrayList<>();
            }

            public Response(List<BlockPos> sections) {
                this.sectionsWithImagPhase = sections;
            }

            @Override
            public void write(@NotNull FriendlyByteBuf buf) {
                FriendlyByteBufSerializers.getCollectionFriendlyByteBufSerializer(BlockPos.class).serialize(buf, sectionsWithImagPhase);
            }

            @Override
            public void read(@NotNull FriendlyByteBuf buf) {
                this.sectionsWithImagPhase = FriendlyByteBufDeserializers.getCollectionFriendlyByteBufDeserializer(BlockPos.class, ArrayList::new).deserialize(buf);
            }
        }
    }

    @SuppressWarnings("resource")
    @HandlePayload
    public static ImagPhaseDowsingRodItem.GetLevelChunkSectionsPacket.Response onGetLevelChunkSections(ImagPhaseDowsingRodItem.GetLevelChunkSectionsPacket payload) {
        Supplier<ServerGamePacketListenerImpl> supplier = payload.packetListenerSupplier;
        if (supplier == null || supplier.get() == null) {
            return new ImagPhaseDowsingRodItem.GetLevelChunkSectionsPacket.Response(new ArrayList<>());
        }
        ServerPlayer player = supplier.get().player;
        ServerLevel serverLevel = player.serverLevel();
        ServerChunkCache chunkCache = serverLevel.getChunkSource();
        List<BlockPos> sectionsWithImagPhase = new ArrayList<>();

        chunkCache.chunkMap.getChunks().forEach(chunkHolder -> {
            LevelChunk chunk = chunkHolder.getTickingChunk();
            if (chunk != null) {
                LevelChunkSection[] sections = chunk.getSections();
                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    LevelChunkSection section = sections[sectionIndex];
                    if (section != null && !section.hasOnlyAir() && section.maybeHas(blockState -> blockState.getFluidState().is(Fluids.IMAG_PHASE))) {
                        int sectionBottomY = chunk.getSectionYFromSectionIndex(sectionIndex);
                        sectionsWithImagPhase.add(new BlockPos(chunk.getPos().getMinBlockX(), sectionBottomY, chunk.getPos().getMinBlockZ()));
                    }
                }
            }
        });
        return new ImagPhaseDowsingRodItem.GetLevelChunkSectionsPacket.Response(sectionsWithImagPhase);
    }
}