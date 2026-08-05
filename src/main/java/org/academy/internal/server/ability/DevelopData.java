package org.academy.internal.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.DevState;
import org.academy.api.common.ability.DevelopAction;
import org.academy.api.common.wireless.WirelessUser;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class DevelopData {
    private final UUID playerId;
    private @Nullable DevelopAction action;
    private @Nullable BlockPos developerPos;
    private DevState state;
    private float progress;
    private int elapsedTicks;

    public DevelopData(UUID playerId) {
        this.playerId = playerId;
        state = DevState.IDLE;
        progress = 0;
        elapsedTicks = 0;
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

    public @Nullable BlockPos getDeveloperPos() {
        return developerPos;
    }

    public boolean isDeveloping() {
        return state == DevState.DEVELOPING;
    }

    public void start(DevelopAction action, BlockPos developerPos) {
        this.action = action;
        this.developerPos = developerPos;
        state = DevState.DEVELOPING;
        progress = 0;
        elapsedTicks = 0;
    }

    public void tick(ServerPlayer player) {
        if (state != DevState.DEVELOPING || action == null || developerPos == null) return;

        elapsedTicks++;
        progress = (float) elapsedTicks / action.getTotalTicks();

        if (elapsedTicks >= action.getTotalTicks()) {
            progress = 1.0f;
            var developer = resolveDeveloper(player);
            if (developer == null) return;
            try {
                action.onComplete(player, developer);
                state = DevState.DONE;
            } catch (Exception e) {
                state = DevState.FAILED;
            }
        }
    }

    private @Nullable WirelessUser resolveDeveloper(ServerPlayer player) {
        if (developerPos == null) return null;
        var be = player.level().getBlockEntity(developerPos);
        if (be instanceof WirelessUser user) return user;
        return null;
    }

    public void reset() {
        action = null;
        developerPos = null;
        state = DevState.IDLE;
        progress = 0;
        elapsedTicks = 0;
    }

    public void abort() {
        if (state == DevState.DEVELOPING) {
            action = null;
            developerPos = null;
            progress = 0;
            elapsedTicks = 0;
            state = DevState.FAILED;
        }
    }
}
