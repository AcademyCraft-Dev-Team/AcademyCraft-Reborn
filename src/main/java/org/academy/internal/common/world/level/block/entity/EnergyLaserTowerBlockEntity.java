package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.level.block.EnergyLaserTowerBlock;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class EnergyLaserTowerBlockEntity extends MultiBlockEntity implements WirelessUser {
    private static final int MAX_ENERGY = 500_000;
    /** Throttle client energy sync; per-tick updates made the beam flicker. */
    private static final int ENERGY_SYNC_INTERVAL = 20;

    private @Nullable BlockPos connectedNodePos;
    private int energyStored;
    private boolean beamActive;
    private @Nullable UUID beamTargetEntityUuid;
    private int energySyncCooldown;

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
        int energyBefore = be.energyStored;
        UUID targetUuid = null;
        if (bound != null) {
            int drain = 2000;
            var academy = server.getAcademyCraftServer();
            if (academy != null) {
                drain = Math.max(1, academy.getGenericConfig().misakaRelayLaserDrainPerTick);
            }
            // Gameplay feed still requires a full drain; visuals stay on while any energy remains.
            if (be.hasClearSky() && be.energyStored >= drain) {
                be.energyStored -= drain;
                registry.feed(bound);
                be.setChanged();
            }
            var entry = registry.get(bound);
            if (entry != null) {
                targetUuid = entry.entityUuid;
            }
        }
        // Continuous beam while bound, sky-clear, and buffer non-empty (not gated on drain success).
        boolean active = bound != null && be.hasClearSky() && be.energyStored > 0;
        boolean beamChanged = be.beamActive != active || !Objects.equals(be.beamTargetEntityUuid, targetUuid);
        be.beamActive = active;
        be.beamTargetEntityUuid = targetUuid;

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
        // First air cell above the 1×5 stack (top solid is main + HEIGHT - 1).
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
        return main != null && main.beamActive;
    }

    public @Nullable UUID getBeamTargetEntityUuid() {
        var main = mainEntity();
        return main == null ? null : main.beamTargetEntityUuid;
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
        // Subjects forward receiveEnergy to main (same pattern as AbilityDeveloper).
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
            // Do not sendBlockUpdated here — tick throttles energy sync to avoid beam flicker.
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
        if (connectedNodePos != null) {
            output.putLong("connected_node_pos", connectedNodePos.asLong());
        }
        if (beamTargetEntityUuid != null) {
            output.putString("beam_target_entity", beamTargetEntityUuid.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energyStored = input.getIntOr("energy_stored", 0);
        beamActive = input.getBooleanOr("beam_active", false);
        connectedNodePos = null;
        input.getLong("connected_node_pos").ifPresent(pos -> connectedNodePos = BlockPos.of(pos));
        beamTargetEntityUuid = null;
        input.getString("beam_target_entity").ifPresent(id -> {
            try {
                beamTargetEntityUuid = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                beamTargetEntityUuid = null;
            }
        });
    }
}
