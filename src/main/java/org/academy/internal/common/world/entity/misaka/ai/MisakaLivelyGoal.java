package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.academy.internal.common.world.entity.misaka.MisakaInteractionFeedback;
import org.academy.internal.common.world.entity.misaka.MisakaPersonality;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.MobRelation;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;

import java.util.EnumSet;

public final class MisakaLivelyGoal extends Goal {
    private static final int GIFT_COOLDOWN_TICKS = 1200;
    private static final double GIFT_RANGE = 6.0;
    private final MisakaSisterEntity sister;
    private int giftCooldown;

    public MisakaLivelyGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return sister.isAwakened()
                && !sister.isStarving()
                && sister.getWanderStyle() != WanderStyle.WAITING
                && sister.getPersonality() == MisakaPersonality.LIVELY;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (sister.getNavigation().isInProgress() && sister.getRandom().nextInt(40) == 0) {
            sister.setJumping(true);
            sister.jumpFromGround();
        }
        if (giftCooldown > 0) {
            giftCooldown--;
            return;
        }
        sister.rosterRecord().ifPresent(record -> {
            for (Player player : sister.level().getEntitiesOfClass(
                    Player.class,
                    sister.getBoundingBox().inflate(GIFT_RANGE),
                    Player::isAlive
            )) {
                String name = player.getGameProfile().name();
                var relation = FavorService.relation(record, name);
                if (relation == MobRelation.BENEVOLENT || FavorService.isPrivilegePlayer(record, name)) {
                    dropGift(player);
                    giftCooldown = GIFT_COOLDOWN_TICKS;
                    return;
                }
            }
        });
    }

    private void dropGift(Player player) {
        var stack = sister.getRandom().nextBoolean()
                ? new ItemStack(Items.COOKIE)
                : new ItemStack(Items.COOKED_COD);
        var itemEntity = new ItemEntity(
                sister.level(),
                sister.getX(),
                sister.getY() + 0.5,
                sister.getZ(),
                stack
        );
        itemEntity.setDefaultPickUpDelay();
        var toward = player.position().subtract(sister.position()).normalize().scale(0.2).add(0.0, 0.1, 0.0);
        itemEntity.setDeltaMovement(toward);
        sister.level().addFreshEntity(itemEntity);
        MisakaInteractionFeedback.gift(sister);
    }
}
