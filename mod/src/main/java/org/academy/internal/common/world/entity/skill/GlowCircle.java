package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class GlowCircle extends RenderOnlyEntity {
    public static final float LIFE_TICKS = 10.0f;
    private static final EntityDataAccessor<Integer> OWNER_ENTITY_ID =
            SynchedEntityData.defineId(GlowCircle.class, EntityDataSerializers.INT);

    public int ticks;

    public GlowCircle(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ENTITY_ID, -1);
    }

    public void setEffectOwner(int entityId) {
        entityData.set(OWNER_ENTITY_ID, entityId);
    }

    public int getEffectOwnerId() {
        return entityData.get(OWNER_ENTITY_ID);
    }

    public void applyVisualSnapshot(SkillVfxState.DistortionRing state) {
        if (!level().isClientSide()) throw new IllegalStateException("Visual snapshot on server");
        setPos(state.position());
        setXRot(state.xRot());
        setYRot(state.yRot());
        setEffectOwner(state.ownerEntityId());
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return false;
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
