package org.academy.internal.common.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.ability.darkmatter.DarkmatterBlockProfile;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingProfile;
import org.academy.internal.common.world.level.block.DarkmatterConfigurableBlock;
import org.academy.internal.common.world.level.block.entity.DarkmatterBlockEntity;

import javax.annotation.Nullable;

/**
 * Block item carrying the per-placement physical profile selected in the shaping editor.
 */
public final class DarkmatterBlockItem extends BlockItem implements DarkmatterShapedItem {
    public DarkmatterBlockItem(DarkmatterConfigurableBlock block, Properties properties) {
        super(block, properties
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
    protected boolean updateCustomBlockEntityTag(
            BlockPos pos,
            Level level,
            @Nullable Player player,
            ItemStack stack,
            BlockState state
    ) {
        var updated = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DarkmatterBlockEntity darkmatterBlockEntity) {
            darkmatterBlockEntity.setProfile(profile(stack));
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            return true;
        }
        return updated;
    }
}
