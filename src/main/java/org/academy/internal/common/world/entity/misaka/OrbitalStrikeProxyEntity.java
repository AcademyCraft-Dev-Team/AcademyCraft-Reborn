package org.academy.internal.common.world.entity.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Cosmetic + damage proxy for an orbital laser strike.
 * Position is driven by {@link org.academy.internal.server.misaka.MisakaOrbitalStrikeSupport}.
 */
public final class OrbitalStrikeProxyEntity extends RenderOnlyEntity {
    private static final EntityDataAccessor<Boolean> FIRING =
            SynchedEntityData.defineId(OrbitalStrikeProxyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HYPER =
            SynchedEntityData.defineId(OrbitalStrikeProxyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> IMPACT =
            SynchedEntityData.defineId(OrbitalStrikeProxyEntity.class, EntityDataSerializers.BLOCK_POS);

    private @Nullable UUID satelliteId;

    public OrbitalStrikeProxyEntity(EntityType<? extends OrbitalStrikeProxyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(false);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FIRING, false);
        builder.define(HYPER, false);
        builder.define(IMPACT, BlockPos.ZERO);
    }

    public void configure(UUID satelliteId, boolean hyper, BlockPos impact) {
        this.satelliteId = satelliteId;
        entityData.set(HYPER, hyper);
        entityData.set(IMPACT, impact.immutable());
    }

    public @Nullable UUID getSatelliteId() {
        return satelliteId;
    }

    public boolean isFiring() {
        return entityData.get(FIRING);
    }

    public void setFiring(boolean firing) {
        entityData.set(FIRING, firing);
    }

    public boolean isHyper() {
        return entityData.get(HYPER);
    }

    public BlockPos getImpact() {
        return entityData.get(IMPACT);
    }

    public void moveToSlot(Vec3 world) {
        setPos(world.x, world.y, world.z);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            // Continuous trails + downlink beam: OrbitalStrikeProxyVfxClient.
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            // Orphan cleanup if registry lost the strike link.
            if (satelliteId != null
                    && tickCount > 5
                    && org.academy.internal.server.misaka.MisakaOrbitalStrikeSupport
                    .shouldDiscardOrphan(serverLevel.getServer(), this)) {
                discard();
            }
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // Ephemeral — never restored.
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
