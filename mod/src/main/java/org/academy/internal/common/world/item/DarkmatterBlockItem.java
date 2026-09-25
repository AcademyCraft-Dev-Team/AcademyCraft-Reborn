package org.academy.internal.common.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.ability.darkmatter.DarkmatterBlockProfile;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingProfile;
import org.academy.internal.common.world.level.block.DarkmatterConfigurableBlock;
import org.academy.internal.common.world.level.block.entity.DarkmatterBlockEntity;


/**
 * Block item carrying the per-placement physical profile selected in the shaping editor.
 */
public final class DarkmatterBlockItem extends BlockItem implements DarkmatterShapedItem {
    public DarkmatterBlockItem(DarkmatterConfigurableBlock block, Properties properties) {
        super(block, DarkmatterNativeItemSupport.enchantableProperties(properties)
                .component(ItemDataComponents.DARKMATTER_SHAPING_PROFILE.get(),
                        DarkmatterShapingProfile.DEFAULT)
                .component(ItemDataComponents.DARKMATTER_BLOCK_PROFILE.get(),
                        DarkmatterBlockProfile.DEFAULT));
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.BLOCK;
    }

    @Override
    public boolean usesDarkmatterIntegrity() {
        return false;
    }

    public static DarkmatterBlockProfile profile(ItemStack stack) {
        return stack.getOrDefault(ItemDataComponents.DARKMATTER_BLOCK_PROFILE.get(),
                DarkmatterBlockProfile.DEFAULT);
    }

    public static void setProfile(ItemStack stack, DarkmatterBlockProfile profile) {
        stack.set(ItemDataComponents.DARKMATTER_BLOCK_PROFILE.get(), profile);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState placementState) {
        boolean placed = super.placeBlock(context, placementState);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (placed && !level.isClientSide() && level.getBlockEntity(pos) instanceof DarkmatterBlockEntity darkmatterBlockEntity) {
            darkmatterBlockEntity.setProfile(profile(context.getItemInHand()));
            level.sendBlockUpdated(pos, placementState, placementState, Block.UPDATE_ALL);
        }
        return placed;
    }
}
