package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.misaka.MisakaFoodTraits;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.WanderStyle;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class MisakaForageFoodGoal extends Goal {
    private static final int SEARCH_RADIUS = 8;
    private final MisakaSisterEntity sister;
    private BlockPos targetPos;
    private int cooldown;

    public MisakaForageFoodGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
        }
        if (!sister.isAwakened()
                || sister.getWanderStyle() == WanderStyle.WAITING
                || sister.getFoodLevel() >= 10
                || cooldown > 0) {
            return false;
        }
        targetPos = findForageTarget();
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPos != null
                && sister.getWanderStyle() != WanderStyle.WAITING
                && sister.distanceToSqr(Vec3.atCenterOf(targetPos)) > 1.5;
    }

    @Override
    public void start() {
        if (targetPos != null) {
            sister.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0.8);
        }
    }

    @Override
    public void tick() {
        if (targetPos == null) {
            return;
        }
        if (sister.distanceToSqr(Vec3.atCenterOf(targetPos)) <= 2.25) {
            tryConsumeFromContainer(targetPos);
            targetPos = null;
            cooldown = 40;
        }
    }

    @Override
    public void stop() {
        sister.getNavigation().stop();
    }

    private BlockPos findForageTarget() {
        var origin = sister.blockPosition();
        List<BlockPos> favorites = new ArrayList<>();
        List<BlockPos> others = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    var pos = origin.offset(dx, dy, dz);
                    BlockEntity blockEntity = sister.level().getBlockEntity(pos);
                    if (!(blockEntity instanceof Container container)
                            || !MisakaFoodTraits.isForageContainer(blockEntity)) {
                        continue;
                    }
                    if (containsConsumable(container, true)) {
                        favorites.add(pos.immutable());
                    } else if (containsConsumable(container, false)) {
                        others.add(pos.immutable());
                    }
                }
            }
        }
        if (!favorites.isEmpty()) {
            return favorites.get(sister.getRandom().nextInt(favorites.size()));
        }
        if (!others.isEmpty()) {
            return others.get(sister.getRandom().nextInt(others.size()));
        }
        return null;
    }

    private boolean containsConsumable(Container container, boolean favoriteOnly) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            var stack = container.getItem(slot);
            if (!MisakaFoodTraits.isForageConsumable(stack)) {
                continue;
            }
            if (favoriteOnly) {
                if (MisakaFoodTraits.isFavorite(stack)) {
                    return true;
                }
            } else if (!MisakaFoodTraits.isFavorite(stack)) {
                return true;
            }
        }
        return false;
    }

    private void tryConsumeFromContainer(BlockPos pos) {
        BlockEntity blockEntity = sister.level().getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            return;
        }
        if (sister.getFoodLevel() >= 20) {
            return;
        }

        List<Integer> favoriteSlots = new ArrayList<>();
        List<Integer> otherSlots = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!MisakaFoodTraits.isForageConsumable(stack)) {
                continue;
            }
            if (MisakaFoodTraits.isFavorite(stack)) {
                favoriteSlots.add(slot);
            } else {
                otherSlots.add(slot);
            }
        }

        List<Integer> candidates = !favoriteSlots.isEmpty() ? favoriteSlots : otherSlots;
        if (candidates.isEmpty()) {
            return;
        }
        int slot = candidates.get(sister.getRandom().nextInt(candidates.size()));
        ItemStack stack = container.getItem(slot);
        ItemStack sample = stack.copyWithCount(1);
        MisakaFoodTraits.applyTo(sister, sample);
        sister.onSelfFed(sample);
        stack.shrink(1);
        container.setChanged();
    }
}
