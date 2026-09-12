package org.academy.internal.common.world.level.block.entity;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.item.HyperNetworkRelaySatelliteItem;
import org.academy.internal.common.world.item.NetworkRelaySatelliteItem;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Launch pad: insert satellite, pick same-network laser tower, launch.
 * Seated satellite is rendered client-side when the slot is non-empty.
 */
public final class SatelliteLaunchPadBlockEntity extends BlockEntity
        implements WirelessUser, Container, GeoBlockEntity {
    private static final int MAX_ENERGY = 50_000;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private @Nullable BlockPos connectedNodePos;
    private @Nullable BlockPos selectedLaserPos;
    private int energyStored;
    private int selectableLaserCount;
    private String launchFeedbackKey = "";

    public SatelliteLaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.SATELLITE_LAUNCH_PAD.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SatelliteLaunchPadBlockEntity be) {
        // No per-tick server work; GUI refreshes on open / button press.
    }

    public boolean hasSeatedSatellite() {
        return NetworkRelaySatelliteItem.isSatellite(items.get(0));
    }

    public boolean isSeatedHyper() {
        return NetworkRelaySatelliteItem.isHyper(items.get(0));
    }

    public @Nullable BlockPos getSelectedLaserPos() {
        return selectedLaserPos;
    }

    public int getSelectableLaserCount() {
        return selectableLaserCount;
    }

    public String getLaunchFeedbackKey() {
        return launchFeedbackKey;
    }

    public AABB getRenderBoundingBox() {
        return new AABB(
                worldPosition.getX() - 1.5,
                worldPosition.getY() - 0.25,
                worldPosition.getZ() - 1.5,
                worldPosition.getX() + 2.5,
                worldPosition.getY() + 2.0,
                worldPosition.getZ() + 2.5
        );
    }

    private void setLaunchFeedback(String key) {
        launchFeedbackKey = key == null ? "" : key;
    }

    public List<BlockPos> listSelectableLasers(ServerLevel level) {
        var result = new ArrayList<BlockPos>();
        if (connectedNodePos == null) {
            return result;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, connectedNodePos);
        var registry = MisakaRelayRegistry.get(level.getServer());
        var data = WirelessNetworkData.get(level);
        for (var entry : data.getAllNodes().entrySet()) {
            var nodePos = entry.getKey();
            if (!MisakaNAT.get().resolveNetworkId(level, nodePos).equals(networkId)) {
                continue;
            }
            for (var userPos : entry.getValue().connectedUsers.keySet()) {
                if (!(level.getBlockEntity(userPos) instanceof EnergyLaserTowerBlockEntity tower)) {
                    continue;
                }
                var main = tower.mainEntity();
                if (main == null || !main.isMain()) {
                    continue;
                }
                var mainPos = main.getBlockPos().immutable();
                if (registry.laserBoundSatellite(level.dimension(), mainPos) != null) {
                    continue;
                }
                result.add(mainPos);
            }
        }
        result.sort(BlockPos::compareTo);
        return result;
    }

    public void cycleSelectedLaser(ServerLevel level) {
        var selectable = listSelectableLasers(level);
        selectableLaserCount = selectable.size();
        if (selectable.isEmpty()) {
            selectedLaserPos = null;
            markAndSync();
            return;
        }
        int idx = selectedLaserPos == null ? -1 : selectable.indexOf(selectedLaserPos);
        selectedLaserPos = selectable.get((idx + 1) % selectable.size());
        markAndSync();
    }

    public void cycleHyperDimension() {
        ItemStack stack = items.get(0);
        if (!NetworkRelaySatelliteItem.isHyper(stack)) {
            return;
        }
        var updated = stack.copy();
        var current = HyperNetworkRelaySatelliteItem.targetDimension(updated);
        var next = net.minecraft.world.level.Level.NETHER.equals(current)
                ? net.minecraft.world.level.Level.END
                : net.minecraft.world.level.Level.NETHER;
        HyperNetworkRelaySatelliteItem.setTargetDimension(updated, next);
        setItem(0, updated);
    }

    public boolean tryLaunch(ServerLevel level) {
        ItemStack stack = items.get(0);
        if (!NetworkRelaySatelliteItem.isSatellite(stack)) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.need_satellite");
            markAndSync();
            return false;
        }
        if (connectedNodePos == null || selectedLaserPos == null) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.need_laser");
            markAndSync();
            return false;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, connectedNodePos);
        var targetDim = NetworkRelaySatelliteItem.targetDimension(stack);
        boolean hyper = NetworkRelaySatelliteItem.isHyper(stack);
        if (!hyper && !net.minecraft.world.level.Level.OVERWORLD.equals(targetDim)) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.bad_dim");
            markAndSync();
            return false;
        }
        if (hyper && net.minecraft.world.level.Level.OVERWORLD.equals(targetDim)) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.bad_dim");
            markAndSync();
            return false;
        }
        var selectable = listSelectableLasers(level);
        selectableLaserCount = selectable.size();
        if (!selectable.contains(selectedLaserPos)) {
            selectedLaserPos = null;
            setLaunchFeedback("gui.academy.satellite_launch_pad.laser_gone");
            markAndSync();
            return false;
        }
        var laserBe = level.getBlockEntity(selectedLaserPos);
        if (!(laserBe instanceof EnergyLaserTowerBlockEntity tower) || !tower.canPowerSatellite()) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.laser_unready");
            markAndSync();
            return false;
        }
        boolean ok = MisakaRelayRegistry.get(level.getServer()).launch(
                level.getServer(),
                networkId,
                targetDim,
                hyper,
                selectedLaserPos,
                level.dimension(),
                worldPosition,
                level.dimension()
        );
        if (ok) {
            stack.shrink(1);
            selectedLaserPos = null;
            setLaunchFeedback("gui.academy.satellite_launch_pad.launch_ok");
            markAndSync();
        } else {
            setLaunchFeedback("gui.academy.satellite_launch_pad.launch_fail");
            markAndSync();
        }
        return ok;
    }

    public void syncLaunchSnapshot(ServerLevel level) {
        selectableLaserCount = listSelectableLasers(level).size();
        markAndSync();
    }

    private void markAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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
    public boolean acceptsWirelessEnergy() {
        return true;
    }

    @Override
    public boolean suppliesWirelessEnergy() {
        return false;
    }

    @Override
    public @Nullable BlockPos getConnectedNodePosition() {
        return connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        if (!Objects.equals(connectedNodePos, nodePos)) {
            connectedNodePos = nodePos;
            selectedLaserPos = null;
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
        return energyStored;
    }

    @Override
    public int getMaxEnergyStorage() {
        return MAX_ENERGY;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("energy_stored", energyStored);
        output.putInt("selectable_laser_count", selectableLaserCount);
        output.putString("launch_feedback", launchFeedbackKey);
        if (connectedNodePos != null) {
            output.putLong("connected_node_pos", connectedNodePos.asLong());
        }
        if (selectedLaserPos != null) {
            output.putLong("selected_laser_pos", selectedLaserPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energyStored = input.getIntOr("energy_stored", 0);
        selectableLaserCount = input.getIntOr("selectable_laser_count", 0);
        launchFeedbackKey = input.getString("launch_feedback").orElse("");
        connectedNodePos = null;
        input.getLong("connected_node_pos").ifPresent(pos -> connectedNodePos = BlockPos.of(pos));
        selectedLaserPos = null;
        input.getLong("selected_laser_pos").ifPresent(pos -> selectedLaserPos = BlockPos.of(pos));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) {
            setChanged();
            markAndSync();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }
}
