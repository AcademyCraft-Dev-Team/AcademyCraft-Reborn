package org.academy.internal.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.DevState;
import org.academy.api.common.ability.DevelopAction;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

public class DevelopData {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private final UUID playerId;
    private @Nullable DevelopAction action;
    private @Nullable BlockPos developerPos;
    private @Nullable ResourceKey<Level> developerDimension;
    private DevState state = DevState.IDLE;
    private float progress;
    private int elapsedTicks;
    private int paidEnergy;
    private String message = "";

    public DevelopData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public DevState getState() {
        return state;
    }

    public float getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    public @Nullable BlockPos getDeveloperPos() {
        return developerPos;
    }

    public boolean isDeveloping() {
        return state == DevState.DEVELOPING;
    }

    public boolean start(DevelopAction action, BlockPos developerPos, ResourceKey<Level> developerDimension) {
        if (isDeveloping() || action == null || developerPos == null
                || developerDimension == null
                || action.getTotalTicks() <= 0 || action.getEnergyCost() < 0) {
            return false;
        }
        this.action = action;
        this.developerPos = developerPos.immutable();
        this.developerDimension = developerDimension;
        state = DevState.DEVELOPING;
        progress = 0.0f;
        elapsedTicks = 0;
        paidEnergy = 0;
        message = "Developing...";
        return true;
    }

    public void tick(ServerPlayer player) {
        if (!isDeveloping() || action == null || developerPos == null) return;
        if (developerDimension == null || !developerDimension.equals(player.level().dimension())) {
            fail("Wrong dimension");
            return;
        }
        var developer = resolveDeveloper(player);
        if (developer == null) {
            fail("Developer unavailable");
            return;
        }
        if (player.position().distanceToSqr(Vec3.atCenterOf(developerPos)) > 64.0) {
            fail("Too far away");
            return;
        }
        if (!action.validate(player, developer)) {
            fail("Conditions changed");
            return;
        }

        var nextElapsed = elapsedTicks + 1;
        var totalTicks = action.getTotalTicks();
        var targetPaid = targetEnergy(action.getEnergyCost(), totalTicks, nextElapsed);
        var due = targetPaid - paidEnergy;
        if (due > 0) {
            if (developer.extractEnergy(due, true) < due) {
                fail("Insufficient energy");
                return;
            }
            var extracted = developer.extractEnergy(due, false);
            if (extracted != due) {
                fail("Energy extraction failed");
                return;
            }
            paidEnergy += extracted;
        }

        elapsedTicks = nextElapsed;
        progress = Math.clamp((float) elapsedTicks / totalTicks, 0.0f, 1.0f);
        message = "Developing... " + (int) (progress * 100.0f) + "%";
        if (elapsedTicks < totalTicks) return;

        if (!action.validate(player, developer)) {
            fail("Conditions changed");
            return;
        }
        try {
            action.onComplete(player, developer);
            state = DevState.DONE;
            progress = 1.0f;
            message = "Success!";
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to complete ability development for player {}", playerId, exception);
            fail("Completion failed");
        }
    }

    static int targetEnergy(int totalCost, int totalTicks, int elapsedTicks) {
        if (totalCost <= 0 || totalTicks <= 0 || elapsedTicks <= 0) return 0;
        return (int) ((long) totalCost * Math.min(elapsedTicks, totalTicks) / totalTicks);
    }

    private @Nullable AbilityDeveloperBlockEntity resolveDeveloper(ServerPlayer player) {
        if (developerPos == null || !player.level().hasChunkAt(developerPos)) return null;
        var be = player.level().getBlockEntity(developerPos);
        if (be instanceof AbilityDeveloperBlockEntity developer && developer.isMain()) return developer;
        return null;
    }

    public void reset() {
        action = null;
        developerPos = null;
        developerDimension = null;
        state = DevState.IDLE;
        progress = 0.0f;
        elapsedTicks = 0;
        paidEnergy = 0;
        message = "";
    }

    public void abort() {
        if (isDeveloping()) fail("Cancelled");
    }

    public void fail(String reason) {
        if (!isDeveloping()) return;
        action = null;
        state = DevState.FAILED;
        message = reason == null || reason.isBlank() ? "Failed" : reason;
    }
}
