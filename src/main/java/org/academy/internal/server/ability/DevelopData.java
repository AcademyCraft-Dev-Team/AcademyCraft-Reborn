package org.academy.internal.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.DevState;
import org.academy.api.common.ability.DevelopAction;
import org.academy.api.common.wireless.WirelessUser;

import java.util.UUID;

public class DevelopData {
    private final UUID playerId;
    private DevelopAction action;
    private BlockPos developerPos;
    private DevState state;
    private float progress;
    private int elapsedTicks;

    public DevelopData(UUID playerId) {
        this.playerId = playerId;
        this.state = DevState.IDLE;
        this.progress = 0;
        this.elapsedTicks = 0;
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

    public BlockPos getDeveloperPos() {
        return developerPos;
    }

    public boolean isDeveloping() {
        return state == DevState.DEVELOPING;
    }

    public void start(DevelopAction action, BlockPos developerPos) {
        this.action = action;
        this.developerPos = developerPos;
        this.state = DevState.DEVELOPING;
        this.progress = 0;
        this.elapsedTicks = 0;
    }

    public void tick(ServerPlayer player) {
        if (state != DevState.DEVELOPING || action == null || developerPos == null) return;

        elapsedTicks++;
        progress = (float) elapsedTicks / action.getTotalTicks();

        if (elapsedTicks >= action.getTotalTicks()) {
            progress = 1.0f;
            WirelessUser developer = resolveDeveloper(player);
            try {
                action.onComplete(player, developer);
                state = DevState.DONE;
            } catch (Exception e) {
                state = DevState.FAILED;
            }
        }
    }

    private WirelessUser resolveDeveloper(ServerPlayer player) {
        if (developerPos == null) return null;
        var be = ((ServerLevel) player.level()).getBlockEntity(developerPos);
        if (be instanceof WirelessUser user) return user;
        return null;
    }

    public void reset() {
        this.action = null;
        this.developerPos = null;
        this.state = DevState.IDLE;
        this.progress = 0;
        this.elapsedTicks = 0;
    }

    public void abort() {
        if (state == DevState.DEVELOPING) {
            this.action = null;
            this.developerPos = null;
            this.progress = 0;
            this.elapsedTicks = 0;
            this.state = DevState.FAILED;
        }
    }
}
