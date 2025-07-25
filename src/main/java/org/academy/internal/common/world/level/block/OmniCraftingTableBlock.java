package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.common.world.inventory.OmniCraftingMenu;
import org.academy.internal.common.world.level.block.entity.OmniCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OmniCraftingTableBlock extends BaseEntityBlock {
    public static final MapCodec<OmniCraftingTableBlock> CODEC = simpleCodec(OmniCraftingTableBlock::new);
    public static final String OMNI_CRAFTING_TABLE_SCREEN = "omni_crafting_table_screen";

    public OmniCraftingTableBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var menuProvider = getMenuProvider(state, level, pos);
            ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, OMNI_CRAFTING_TABLE_SCREEN, pos);
            return InteractionResult.CONSUME;
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof OmniCraftingTableBlockEntity omniCraftingTableBlockEntity) {
                omniCraftingTableBlockEntity.tick();
            }
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new OmniCraftingTableBlockEntity(pos, state);
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof OmniCraftingTableBlockEntity container) {
            return new SimpleMenuProvider((containerId, playerInventory, player)
                    -> new OmniCraftingMenu(
                    containerId,
                    playerInventory,
                    ContainerLevelAccess.create(level, pos),
                    container
            ),
                    Component.empty()
            );
        } else {
            return null;
        }

    }
}