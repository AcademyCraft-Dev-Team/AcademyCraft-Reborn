package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.jetbrains.annotations.NotNull;

public class GlowCircle extends RenderOnlyEntity {
    public static final float LIFE_TICKS = 20.0f;

    public int ticks;

    public GlowCircle(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();

        if (ticks > LIFE_TICKS) {
            discard();
        }

        ticks++;
    }
}