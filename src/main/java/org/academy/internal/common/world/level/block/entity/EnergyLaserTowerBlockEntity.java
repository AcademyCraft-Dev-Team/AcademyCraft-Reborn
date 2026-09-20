package org.academy.internal.common.world.level.block.entity;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.level.block.EnergyLaserTowerBlock;
import org.academy.internal.server.misaka.MisakaOrbitalStrikeSupport;
import org.academy.internal.server.misaka.MisakaRelayOrbits;
import org.academy.internal.server.world.level.storage.MisakaRelayEntry;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class EnergyLaserTowerBlockEntity extends MultiBlockEntity
        implements WirelessUser, GeoBlockEntity, OwnedDevice {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private static final int MAX_ENERGY = 500_000;
    /** Throttle client energy sync; per-tick updates made the beam flicker. */
    private static final int ENERGY_SYNC_INTERVAL = 20;
    /** Min world-space move before re-syncing strike aim (blocks). */
    private static final float STRIKE_AIM_SYNC_EPS = 0.35f;

    private @Nullable BlockPos connectedNodePos;
    private int energyStored;
    private boolean beamActive;
    private boolean orbiting;
    private boolean orbitHyper;
    private int orbitAngleSeed;
    private float orbitVisualY;
    private int energySyncCooldown;
    /** True while the bound satellite is on an orbital-strike path (approach/fire/return). */
    private boolean strikeAiming;
    private float strikeAimX;
    private float strikeAimY;
    private float strikeAimZ;
    private @Nullable UUID ownerUuid;

    public EnergyLaserTowerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.ENERGY_LASER_TOWER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EnergyLaserTowerBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel) || !be.isMain()) {
            return;
        }
        var server = serverLevel.getServer();
        var registry = MisakaRelayRegistry.get(server);
        UUID bound = registry.laserBoundSatellite(serverLevel.dimension(), pos);
        if (bound == null) {
            // Unbound: clear leftover beam/orbit/strike visuals once, then idle (no full next* rebuild).
            if (be.beamActive || be.orbiting || be.strikeAiming) {
                be.beamActive = false;
                be.orbiting = false;
                be.orbitHyper = false;
                be.orbitAngleSeed = 0;
                be.strikeAiming = false;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
            return;
        }
        int energyBefore = be.energyStored;
        boolean nextOrbiting = false;
        boolean nextHyper = false;
        int nextSeed = 0;
        float nextVisualY = be.orbitVisualY;
        boolean nextStrikeAiming = false;
        float nextAimX = be.strikeAimX;
        float nextAimY = be.strikeAimY;
        float nextAimZ = be.strikeAimZ;
        boolean supplying = false;
        int drain = 2000;
        int hyperMul = 2;
        var academy = server.getAcademyCraftServer();
        if (academy != null) {
            var config = academy.getGenericConfig();
            drain = Math.max(1, config.misakaRelayLaserDrainPerTick);
            hyperMul = Math.max(1, config.misakaRelayHyperDrainMultiplier);
        }
        var entry = registry.get(bound);
        if (entry != null && entry.hyper) {
            drain = (int) Math.min(Integer.MAX_VALUE, (long) drain * (long) hyperMul);
        }
        boolean acceptsFeed = registry.acceptsPowerFeed(bound);
        boolean needsRecovery = registry.needsCrashRecovery(bound);
        // Extra maintain-sized drain heals 1 crash-debt tick per tick while recovering.
        int recoveryDrain = needsRecovery ? drain : 0;
        long totalNeeded = (long) drain + (long) recoveryDrain;
        boolean skyOk = be.hasClearSky();
        if (acceptsFeed && skyOk) {
            if (needsRecovery && be.energyStored >= totalNeeded) {
                be.energyStored = (int) (be.energyStored - totalNeeded);
                registry.feed(bound, true);
                supplying = true;
                be.setChanged();
            } else if (be.energyStored >= drain) {
                be.energyStored -= drain;
                registry.feed(bound, false);
                supplying = true;
                be.setChanged();
            }
        }
        // Sky orbit marker may stay; laser beam only while actually supplying (see beamActive).
        if (entry != null
                && entry.phase == MisakaRelayEntry.Phase.ORBIT
                && entry.laserBound
                && acceptsFeed) {
            nextOrbiting = true;
            nextHyper = entry.hyper;
            nextSeed = entry.satelliteId.hashCode();
            nextVisualY = (float) MisakaRelayOrbits.visualOrbitY(serverLevel, server);
            Vec3 aim = MisakaOrbitalStrikeSupport.visualAim(serverLevel, entry);
            if (aim != null) {
                nextStrikeAiming = true;
                nextAimX = (float) aim.x;
                nextAimY = (float) aim.y;
                nextAimZ = (float) aim.z;
            }
        }
        boolean active = supplying;
        // While striking, push aim every tick so beam + sky mark track the proxy smoothly.
        boolean beamChanged = be.beamActive != active
                || be.orbiting != nextOrbiting
                || be.orbitHyper != nextHyper
                || be.orbitAngleSeed != nextSeed
                || Float.compare(be.orbitVisualY, nextVisualY) != 0
                || be.strikeAiming != nextStrikeAiming
                || (nextStrikeAiming && (
                Math.abs(be.strikeAimX - nextAimX) >= STRIKE_AIM_SYNC_EPS
                        || Math.abs(be.strikeAimY - nextAimY) >= STRIKE_AIM_SYNC_EPS
                        || Math.abs(be.strikeAimZ - nextAimZ) >= STRIKE_AIM_SYNC_EPS
                        || !be.strikeAiming));
        be.beamActive = active;
        be.orbiting = nextOrbiting;
        be.orbitHyper = nextHyper;
        be.orbitAngleSeed = nextSeed;
        be.orbitVisualY = nextVisualY;
        be.strikeAiming = nextStrikeAiming;
        if (nextStrikeAiming) {
            be.strikeAimX = nextAimX;
            be.strikeAimY = nextAimY;
            be.strikeAimZ = nextAimZ;
        }

        boolean energyDirty = be.energyStored != energyBefore;
        if (energyDirty) {
            be.setChanged();
        }
        if (be.energySyncCooldown > 0) {
            be.energySyncCooldown--;
        }
        boolean syncEnergy = energyDirty
                && (be.energySyncCooldown <= 0 || be.energyStored == 0 || energyBefore == 0);
        if (beamChanged || syncEnergy) {
            if (syncEnergy) {
                be.energySyncCooldown = ENERGY_SYNC_INTERVAL;
            }
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    public boolean hasClearSky() {
        var main = mainEntity();
        if (main == null || main.level == null) {
            return false;
        }
        return main.level.canSeeSky(main.worldPosition.above(EnergyLaserTowerBlock.HEIGHT));
    }

    public boolean canPowerSatellite() {
        var main = mainEntity();
        if (main == null) {
            return false;
        }
        return hasClearSky() && main.energyStored > 0;
    }

    public boolean isBeamActive() {
        var main = mainEntity();
        // Hide beam when the tower has no stored energy (client sync lag / empty buffer).
        return main != null && main.beamActive && main.energyStored > 0;
    }

    public boolean isOrbiting() {
        var main = mainEntity();
        return main != null && main.orbiting;
    }

    public boolean isOrbitHyper() {
        var main = mainEntity();
        return main != null && main.orbitHyper;
    }

    public int getOrbitAngleSeed() {
        var main = mainEntity();
        return main == null ? 0 : main.orbitAngleSeed;
    }

    public float getOrbitVisualY() {
        var main = mainEntity();
        return main == null ? 0.0f : main.orbitVisualY;
    }

    public boolean isStrikeAiming() {
        var main = mainEntity();
        return main != null && main.strikeAiming;
    }

    public Vec3 getStrikeAim() {
        var main = mainEntity();
        if (main == null) {
            return Vec3.ZERO;
        }
        return new Vec3(main.strikeAimX, main.strikeAimY, main.strikeAimZ);
    }

    @Nullable
    public EnergyLaserTowerBlockEntity mainEntity() {
        if (isMain() || level == null || mainPos == null) {
            return this;
        }
        return getMain() instanceof EnergyLaserTowerBlockEntity main ? main : null;
    }

    @Override
    public boolean acceptsWirelessEnergy() {
        return true;
    }

    @Override
    public boolean suppliesWirelessEnergy() {
        return false;
    }

    @Override
    public @Nullable BlockPos getConnectedNodePosition() {
        var main = mainEntity();
        return main == null ? null : main.connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        var main = mainEntity();
        if (main == null) {
            return;
        }
        if (main != this) {
            main.setConnectedNodePosition(nodePos);
            return;
        }
        if (!Objects.equals(connectedNodePos, nodePos)) {
            connectedNodePos = nodePos;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        var main = mainEntity();
        if (main == null) {
            return 0;
        }
        if (main != this) {
            return main.receiveEnergy(maxReceive, simulate);
        }
        int space = getMaxEnergyStorage() - energyStored;
        int take = Math.min(space, Math.max(0, maxReceive));
        if (!simulate && take > 0) {
            energyStored += take;
            setChanged();
        }
        return take;
    }

    @Override
    public int getEnergyStored() {
        var main = mainEntity();
        return main == null ? 0 : main.energyStored;
    }

    @Override
    public int getMaxEnergyStorage() {
        return MAX_ENERGY;
    }

    public void setEnergyStored(int energy) {
        var main = mainEntity();
        if (main == null) {
            return;
        }
        if (main != this) {
            main.setEnergyStored(energy);
            return;
        }
        int clamped = Mth.clamp(energy, 0, getMaxEnergyStorage());
        if (clamped != energyStored) {
            boolean crossedEmpty = energyStored == 0 || clamped == 0;
            energyStored = clamped;
            setChanged();
            if (level != null && !level.isClientSide() && crossedEmpty) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!isMain()) {
            return;
        }
        output.putInt("energy_stored", energyStored);
        output.putBoolean("beam_active", beamActive);
        output.putBoolean("orbiting", orbiting);
        output.putBoolean("orbit_hyper", orbitHyper);
        output.putInt("orbit_angle_seed", orbitAngleSeed);
        output.putFloat("orbit_visual_y", orbitVisualY);
        output.putBoolean("strike_aiming", strikeAiming);
        if (strikeAiming) {
            output.putFloat("strike_aim_x", strikeAimX);
            output.putFloat("strike_aim_y", strikeAimY);
            output.putFloat("strike_aim_z", strikeAimZ);
        }
        if (connectedNodePos != null) {
            output.putLong("connected_node_pos", connectedNodePos.asLong());
        }
        saveOwner(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energyStored = input.getIntOr("energy_stored", 0);
        beamActive = input.getBooleanOr("beam_active", false);
        orbiting = input.getBooleanOr("orbiting", false);
        orbitHyper = input.getBooleanOr("orbit_hyper", false);
        orbitAngleSeed = input.getIntOr("orbit_angle_seed", 0);
        orbitVisualY = input.getFloatOr("orbit_visual_y", 0.0f);
        strikeAiming = input.getBooleanOr("strike_aiming", false);
        strikeAimX = input.getFloatOr("strike_aim_x", 0.0f);
        strikeAimY = input.getFloatOr("strike_aim_y", 0.0f);
        strikeAimZ = input.getFloatOr("strike_aim_z", 0.0f);
        connectedNodePos = null;
        input.getLong("connected_node_pos").ifPresent(pos -> connectedNodePos = BlockPos.of(pos));
        loadOwner(input);
        if (level != null && level.isClientSide() && isMain()) {
            OrbitSkyHooks.sync(this);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public @Nullable UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        setChanged();
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide() && isMain()) {
            OrbitSkyHooks.remove(worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && level.isClientSide() && isMain()) {
            OrbitSkyHooks.sync(this);
        }
    }
}
