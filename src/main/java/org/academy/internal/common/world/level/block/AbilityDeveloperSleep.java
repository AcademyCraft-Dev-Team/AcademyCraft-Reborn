package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.mixin.common.PlayerSleepCounterAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Server-side rules for using the Ability Developer as a rest pod.
 */
@EventBusSubscriber
public final class AbilityDeveloperSleep {
    public static final int SP_RECOVERY_INTERVAL_TICKS = 20;
    private static final int SLEEP_POSITION_OFFSET = 2;

    private AbilityDeveloperSleep() {
    }

    public static void startSleeping(ServerPlayer player, AbilityDeveloperBlockEntity developer) {
        if (!player.isAlive() || player.isSleeping()) return;

        var mainDeveloper = getMainDeveloper(developer);
        if (mainDeveloper == null) return;

        var sleepPosition = getSleepPosition(mainDeveloper);
        var occupied = player.level().players().stream()
                .filter(other -> other != player && other.isSleeping())
                .anyMatch(other -> other.getSleepingPos()
                        .map(sleepPosition::equals)
                        .orElse(false));
        if (occupied) {
            player.sendOverlayMessage(Component.translatable("block.minecraft.bed.occupied"));
            return;
        }

        player.startSleeping(sleepPosition);
        ((PlayerSleepCounterAccessor) player).academy$setSleepCounter(0);
        if (canSkipTime(player)) {
            player.level().updateSleepingPlayerList();
        }
    }

    public static BlockPos getSleepPosition(AbilityDeveloperBlockEntity developer) {
        var facing = developer.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        return developer.getBlockPos().relative(facing.getOpposite(), SLEEP_POSITION_OFFSET);
    }

    public static boolean isSleepingAtDeveloper(LivingEntity sleeper) {
        if (!sleeper.isSleeping()) return false;
        var sleepingPosition = sleeper.getSleepingPos().orElse(null);
        return sleepingPosition != null
                && getDeveloperAt(sleeper.level(), sleepingPosition) != null;
    }

    public static boolean shouldRecoverSp(ServerPlayer player) {
        return player.tickCount % SP_RECOVERY_INTERVAL_TICKS == 0
                && isSleepingAtDeveloper(player);
    }

    public static void refreshNightSleepStatus(ServerPlayer player) {
        if (player.tickCount % SP_RECOVERY_INTERVAL_TICKS == 0
                && isSleepingAtDeveloper(player)
                && canSkipTime(player)) {
            player.level().updateSleepingPlayerList();
        }
    }

    public static boolean canSkipTime(ServerPlayer player) {
        var level = player.level();
        var sleepingPosition = player.getSleepingPos().orElse(player.blockPosition());
        var bedRule = level.environmentAttributes()
                .getValue(EnvironmentAttributes.BED_RULE, sleepingPosition);
        return level.dimensionType().defaultClock().isPresent() && bedRule.canSleep(level);
    }

    @Nullable
    public static AbilityDeveloperBlockEntity getDeveloperAt(Level level, BlockPos sleepingPosition) {
        if (!(level.getBlockEntity(sleepingPosition) instanceof AbilityDeveloperBlockEntity developer)) {
            return null;
        }
        var mainDeveloper = getMainDeveloper(developer);
        if (mainDeveloper == null || !getSleepPosition(mainDeveloper).equals(sleepingPosition)) {
            return null;
        }
        return mainDeveloper;
    }

    @Nullable
    private static AbilityDeveloperBlockEntity getMainDeveloper(AbilityDeveloperBlockEntity developer) {
        if (developer.isMain()) return developer;
        return developer.getMain() instanceof AbilityDeveloperBlockEntity mainDeveloper
                ? mainDeveloper
                : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void allowDeveloperSleepToContinue(CanContinueSleepingEvent event) {
        if (isSleepingAtDeveloper(event.getEntity())) {
            event.setContinueSleeping(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void enableHeldItemInteraction(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (!player.isShiftKeyDown()
                || !(player.level().getBlockEntity(event.getPos()) instanceof AbilityDeveloperBlockEntity)) {
            return;
        }

        event.setUseBlock(TriState.TRUE);
        event.setUseItem(TriState.FALSE);
    }

    @SubscribeEvent
    public static void stabilizeDeveloperSleep(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (!isSleepingAtDeveloper(player)) return;

        player.setPose(Pose.SLEEPING);
        player.setDeltaMovement(Vec3.ZERO);
        player.walkAnimation.stop();
    }
}
