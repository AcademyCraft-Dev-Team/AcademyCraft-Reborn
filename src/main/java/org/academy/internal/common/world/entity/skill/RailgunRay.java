package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.jetbrains.annotations.NotNull;

public class RailgunRay extends RenderOnlyEntity {
    public static final int defaultLifeTicks = 15;
    public int currentLifetime = defaultLifeTicks;
    public int effectTicks = 20;
    public float progress = 1f;
    public float renderProgress = 1f;

    public RailgunRay(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (effectTicks <= 0) {
            if (currentLifetime <= 0) {
                if (level() instanceof ServerLevel serverLevel) {
                    kill(serverLevel);
                }
            } else {
                currentLifetime--;
            }
        } else {
            effectTicks--;
        }
        progress = (float) currentLifetime / defaultLifeTicks;
    }
}