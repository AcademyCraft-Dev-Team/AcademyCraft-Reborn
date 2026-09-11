package org.academy.internal.common.world.entity.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
            spawnClientParticles();
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

    private void spawnClientParticles() {
        RandomSource random = getRandom();
        if (isFiring()) {
            BlockPos impact = getImpact();
            double x = impact.getX() + 0.5;
            double y = impact.getY() + 1.0;
            double z = impact.getZ() + 0.5;
            for (int i = 0; i < 3; i++) {
                level().addParticle(
                        ParticleTypes.LAVA,
                        x + (random.nextDouble() - 0.5) * 1.6,
                        y + random.nextDouble() * 0.8,
                        z + (random.nextDouble() - 0.5) * 1.6,
                        0.0, 0.05, 0.0
                );
                level().addParticle(
                        ParticleTypes.FLAME,
                        x + (random.nextDouble() - 0.5) * 1.2,
                        y + random.nextDouble(),
                        z + (random.nextDouble() - 0.5) * 1.2,
                        0.0, 0.08, 0.0
                );
            }
            if (tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 0.0, 0.04, 0.0);
            }
            return;
        }
        if (tickCount % 4 == 0) {
            level().addParticle(
                    ParticleTypes.CLOUD,
                    getX() + (random.nextDouble() - 0.5) * 0.8,
                    getY() + (random.nextDouble() - 0.5) * 0.4,
                    getZ() + (random.nextDouble() - 0.5) * 0.8,
                    0.0, 0.02, 0.0
            );
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
