package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class GlowCircle extends RenderOnlyEntity {
    public static final float LIFE_TICKS = 10.0f;
    private static final EntityDataAccessor<Integer> OWNER_ENTITY_ID =
            SynchedEntityData.defineId(GlowCircle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REDIRECT_KIND =
            SynchedEntityData.defineId(GlowCircle.class, EntityDataSerializers.INT);

    public int ticks;

    public GlowCircle(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ENTITY_ID, -1);
        builder.define(REDIRECT_KIND, VectorRedirectKind.REFLECTION.ordinal());
    }

    public void setEffectOwner(int entityId, VectorRedirectKind kind) {
        entityData.set(OWNER_ENTITY_ID, entityId);
        entityData.set(REDIRECT_KIND, kind == null
                ? VectorRedirectKind.REFLECTION.ordinal()
                : kind.ordinal());
    }

    public int getEffectOwnerId() {
        return entityData.get(OWNER_ENTITY_ID);
    }

    public VectorRedirectKind getRedirectKind() {
        var ordinal = entityData.get(REDIRECT_KIND);
        var values = VectorRedirectKind.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : VectorRedirectKind.REFLECTION;
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
