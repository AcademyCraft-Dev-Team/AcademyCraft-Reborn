package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.item.HyperNetworkRelaySatelliteItem;
import org.academy.internal.common.world.item.NetworkRelaySatelliteItem;
import org.academy.internal.server.world.level.storage.MisakaRelayEntry;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AerospaceSignalCabinBlockEntity extends BlockEntity implements WirelessUser, Container {
    private static final int MAX_ENERGY = 50_000;
    private static final String OPS_MANAGED_SATS = "ops_managed_sats";
    private static final String OPS_RETARGET_NETS = "ops_retarget_nets";
    private static final String OPS_REBIND_LASERS = "ops_rebind_lasers";

    public record ManagedSatRow(
            int index,
            String dimPath,
            String phase,
            boolean powered,
            boolean hyper,
            String id8,
            boolean laserBound,
            int unpoweredTicks,
            int crashTimeout,
            int forceCrashTicks
    ) {
    }

    public record RetargetNetRow(String nodeName, boolean isCurrentSatNetwork) {
    }

    public record RebindLaserRow(BlockPos pos, boolean ready) {
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private @Nullable BlockPos connectedNodePos;
    private @Nullable BlockPos selectedLaserPos;
    private int energyStored;
    private int selectedSatelliteIndex;
    /** Client-synced count of unbound lasers on the cabin's wireless topology. */
    private int selectableLaserCount;
    /** Client-synced count of satellites launched from this cabin. */
    private int managedSatelliteCount;
    /** Client-synced ops-page feedback key (empty = idle). */
    private String opsFeedbackKey = "";
    /** Client-synced satellite rows for the ops list (sync-only; not persisted). */
    private List<ManagedSatRow> managedSatelliteList = List.of();
    /** Client-synced retarget targets (unique Misaka topologies; sync-only). */
    private List<RetargetNetRow> retargetNetworkList = List.of();
    /** Client-synced unbound lasers on the cabin wireless topology (sync-only). */
    private List<RebindLaserRow> rebindLaserList = List.of();
    /** Display name of the selected satellite's current coverage network, if known. */
    private String selectedSatNetworkName = "";

    public AerospaceSignalCabinBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.AEROSPACE_SIGNAL_CABIN.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AerospaceSignalCabinBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Keep ops list power flags fresh while the cabin GUI may be open.
        if (serverLevel.getGameTime() % 20L == 0L) {
            var beforeSats = be.managedSatelliteList;
            var beforeNets = be.retargetNetworkList;
            var beforeLasers = be.rebindLaserList;
            var beforeNetName = be.selectedSatNetworkName;
            be.refreshManagedCount(serverLevel);
            be.refreshRetargetNetworks(serverLevel);
            be.refreshRebindLasers(serverLevel);
            if (!beforeSats.equals(be.managedSatelliteList)
                    || !beforeNets.equals(be.retargetNetworkList)
                    || !beforeLasers.equals(be.rebindLaserList)
                    || !Objects.equals(beforeNetName, be.selectedSatNetworkName)) {
                be.markAndSync();
            }
        }
    }

    public @Nullable BlockPos getSelectedLaserPos() {
        return selectedLaserPos;
    }

    public int getSelectableLaserCount() {
        return selectableLaserCount;
    }

    public int getSelectedSatelliteIndex() {
        return selectedSatelliteIndex;
    }

    public int getManagedSatelliteCount() {
        return managedSatelliteCount;
    }

    public String getOpsFeedbackKey() {
        return opsFeedbackKey;
    }

    public List<ManagedSatRow> getManagedSatelliteList() {
        return managedSatelliteList;
    }

    public List<RetargetNetRow> getRetargetNetworkList() {
        return retargetNetworkList;
    }

    public List<RebindLaserRow> getRebindLaserList() {
        return rebindLaserList;
    }

    public String getSelectedSatNetworkName() {
        return selectedSatNetworkName;
    }

    private void setOpsFeedback(String key) {
        opsFeedbackKey = key == null ? "" : key;
    }

    /** Satellites launched from this cabin (matched by cabin dimension + block pos). */
    private List<MisakaRelayEntry> managedEntries(ServerLevel level) {
        var list = MisakaRelayRegistry.get(level.getServer()).listByCabin(level.dimension(), worldPosition);
        list.sort(Comparator.comparing(e -> e.satelliteId));
        return list;
    }

    private void refreshManagedCount(ServerLevel level) {
        var registry = MisakaRelayRegistry.get(level.getServer());
        var list = managedEntries(level);
        managedSatelliteCount = list.size();
        if (managedSatelliteCount == 0) {
            selectedSatelliteIndex = 0;
            managedSatelliteList = List.of();
            refreshRetargetNetworks(level);
            return;
        }
        selectedSatelliteIndex = Mth.clamp(selectedSatelliteIndex, 0, managedSatelliteCount - 1);
        int crashTimeout = Math.max(1, level.getServer().getAcademyCraftServer() != null
                ? level.getServer().getAcademyCraftServer().getGenericConfig().misakaRelayCrashTicks
                : 6000);
        var rows = new ArrayList<ManagedSatRow>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var entry = list.get(i);
            var id = entry.satelliteId.toString().replace("-", "");
            var id8 = id.length() >= 8 ? id.substring(0, 8) : id;
            boolean powered = registry.isReceivingPower(entry.satelliteId);
            rows.add(new ManagedSatRow(
                    i,
                    entry.dimension.identifier().getPath(),
                    entry.phase.name(),
                    powered,
                    entry.hyper,
                    id8,
                    entry.laserBound,
                    entry.unpoweredTicks,
                    crashTimeout,
                    entry.forceCrashCountdownTicks
            ));
        }
        managedSatelliteList = List.copyOf(rows);
        refreshRetargetNetworks(level);
    }

    private void refreshRetargetNetworks(ServerLevel level) {
        var data = WirelessNetworkData.get(level);
        // networkId long -> representative node name (lexicographically smallest)
        var byNetwork = new java.util.TreeMap<Long, String>();
        for (var entry : data.getAllNodes().entrySet()) {
            var nodePos = entry.getKey();
            var name = entry.getValue().name;
            if (name == null || name.isBlank()) {
                continue;
            }
            var networkId = MisakaNAT.get().resolveNetworkId(level, nodePos);
            long key = networkId.asLong();
            byNetwork.merge(key, name, (a, b) -> a.compareToIgnoreCase(b) <= 0 ? a : b);
        }
        BlockPos selectedNet = null;
        var sats = managedEntries(level);
        if (!sats.isEmpty()) {
            int idx = Mth.clamp(selectedSatelliteIndex, 0, sats.size() - 1);
            selectedNet = sats.get(idx).networkId;
        }
        selectedSatNetworkName = "";
        if (selectedNet != null) {
            var match = byNetwork.get(selectedNet.asLong());
            if (match != null) {
                selectedSatNetworkName = match;
            } else {
                selectedSatNetworkName = selectedNet.getX() + "," + selectedNet.getY() + "," + selectedNet.getZ();
            }
        }
        var names = new ArrayList<>(byNetwork.entrySet());
        names.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getValue(), b.getValue()));
        var rows = new ArrayList<RetargetNetRow>(names.size());
        for (var e : names) {
            boolean current = selectedNet != null && selectedNet.asLong() == e.getKey();
            rows.add(new RetargetNetRow(e.getValue(), current));
        }
        retargetNetworkList = List.copyOf(rows);
    }

    private void refreshRebindLasers(ServerLevel level) {
        var selectable = listSelectableLasers(level);
        selectableLaserCount = selectable.size();
        var rows = new ArrayList<RebindLaserRow>(selectable.size());
        for (var pos : selectable) {
            boolean ready = level.getBlockEntity(pos) instanceof EnergyLaserTowerBlockEntity tower
                    && tower.canPowerSatellite();
            rows.add(new RebindLaserRow(pos.immutable(), ready));
        }
        rebindLaserList = List.copyOf(rows);
    }

    /** Refresh managed-satellite stats for the ops UI when the menu opens. */
    public void syncOpsSnapshot(ServerLevel level) {
        refreshManagedCount(level);
        refreshRetargetNetworks(level);
        refreshRebindLasers(level);
        markAndSync();
    }

    public void selectSatellite(ServerLevel level, int index) {
        refreshManagedCount(level);
        if (managedSatelliteCount == 0) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_satellites");
            markAndSync();
            return;
        }
        selectedSatelliteIndex = Mth.clamp(index, 0, managedSatelliteCount - 1);
        setOpsFeedback("");
        refreshRetargetNetworks(level);
        refreshRebindLasers(level);
        markAndSync();
    }

    /**
     * Selectable lasers on the same wireless topology: unbound main blocks.
     * Power/sky readiness is only required at launch time.
     */
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
                if (!result.contains(mainPos)) {
                    result.add(mainPos);
                }
            }
        }
        return result;
    }

    public List<BlockPos> listReadyLasers(ServerLevel level) {
        var result = new ArrayList<BlockPos>();
        for (var pos : listSelectableLasers(level)) {
            if (level.getBlockEntity(pos) instanceof EnergyLaserTowerBlockEntity tower
                    && tower.canPowerSatellite()) {
                result.add(pos);
            }
        }
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

    public boolean tryLaunch(ServerLevel level) {
        ItemStack stack = items.get(0);
        if (!NetworkRelaySatelliteItem.isSatellite(stack)) {
            return false;
        }
        if (connectedNodePos == null || selectedLaserPos == null) {
            return false;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, connectedNodePos);
        var targetDim = NetworkRelaySatelliteItem.targetDimension(stack);
        boolean hyper = NetworkRelaySatelliteItem.isHyper(stack);
        if (!hyper && !net.minecraft.world.level.Level.OVERWORLD.equals(targetDim)) {
            return false;
        }
        if (hyper && net.minecraft.world.level.Level.OVERWORLD.equals(targetDim)) {
            return false;
        }
        var selectable = listSelectableLasers(level);
        if (!selectable.contains(selectedLaserPos)) {
            return false;
        }
        var laserBe = level.getBlockEntity(selectedLaserPos);
        if (!(laserBe instanceof EnergyLaserTowerBlockEntity tower) || !tower.canPowerSatellite()) {
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
            refreshManagedCount(level);
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_launch_ok");
            markAndSync();
        }
        return ok;
    }

    public boolean tryRetarget(ServerLevel level) {
        // Legacy: retarget to the cabin's currently linked wireless topology.
        if (connectedNodePos == null) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_need_wireless");
            markAndSync();
            return false;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, connectedNodePos);
        return retargetSelectedTo(level, networkId);
    }

    public boolean tryRetargetToNetworkIndex(ServerLevel level, int networkIndex) {
        refreshManagedCount(level);
        refreshRetargetNetworks(level);
        if (networkIndex < 0 || networkIndex >= retargetNetworkList.size()) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_networks");
            markAndSync();
            return false;
        }
        var name = retargetNetworkList.get(networkIndex).nodeName();
        var nodePos = WirelessNetworkData.get(level).findNodePositionByName(name);
        if (nodePos == null) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_network_missing");
            markAndSync();
            return false;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, nodePos);
        return retargetSelectedTo(level, networkId);
    }

    private boolean retargetSelectedTo(ServerLevel level, BlockPos networkId) {
        var list = managedEntries(level);
        if (list.isEmpty()) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_satellites");
            markAndSync();
            return false;
        }
        int index = Mth.clamp(selectedSatelliteIndex, 0, list.size() - 1);
        selectedSatelliteIndex = index;
        boolean ok = MisakaRelayRegistry.get(level.getServer())
                .retargetNetwork(level.getServer(), list.get(index).satelliteId, networkId);
        setOpsFeedback(ok
                ? "gui.academy.aerospace_signal_cabin.ops_retarget_ok"
                : "gui.academy.aerospace_signal_cabin.ops_retarget_fail");
        refreshManagedCount(level);
        refreshRetargetNetworks(level);
        markAndSync();
        return ok;
    }

    /**
     * Rebind the selected satellite to an unbound energy laser tower from the ops list
     * (typically after its previous laser tower was destroyed).
     */
    public boolean tryRebindToLaserIndex(ServerLevel level, int laserIndex) {
        refreshManagedCount(level);
        refreshRebindLasers(level);
        var list = managedEntries(level);
        if (list.isEmpty()) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_satellites");
            markAndSync();
            return false;
        }
        if (connectedNodePos == null) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_need_wireless");
            markAndSync();
            return false;
        }
        var selectable = listSelectableLasers(level);
        if (laserIndex < 0 || laserIndex >= selectable.size()) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_rebind_laser_busy");
            markAndSync();
            return false;
        }
        int index = Mth.clamp(selectedSatelliteIndex, 0, list.size() - 1);
        selectedSatelliteIndex = index;
        var entry = list.get(index);
        if (entry.phase != MisakaRelayEntry.Phase.ORBIT
                && entry.phase != MisakaRelayEntry.Phase.LAUNCHING) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_rebind_fail");
            markAndSync();
            return false;
        }
        var laserPos = selectable.get(laserIndex);
        var laserBe = level.getBlockEntity(laserPos);
        if (!(laserBe instanceof EnergyLaserTowerBlockEntity tower) || !tower.canPowerSatellite()) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_rebind_laser_unready");
            markAndSync();
            return false;
        }
        boolean ok = MisakaRelayRegistry.get(level.getServer()).rebindLaser(
                level.getServer(),
                entry.satelliteId,
                laserPos,
                level.dimension()
        );
        setOpsFeedback(ok
                ? "gui.academy.aerospace_signal_cabin.ops_rebind_ok"
                : "gui.academy.aerospace_signal_cabin.ops_rebind_fail");
        refreshManagedCount(level);
        refreshRebindLasers(level);
        markAndSync();
        return ok;
    }

    /** Arm a 60s forced-crash countdown for the selected satellite. */
    public boolean tryScheduleForceCrash(ServerLevel level) {
        refreshManagedCount(level);
        var list = managedEntries(level);
        if (list.isEmpty()) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_satellites");
            markAndSync();
            return false;
        }
        int index = Mth.clamp(selectedSatelliteIndex, 0, list.size() - 1);
        selectedSatelliteIndex = index;
        var entry = list.get(index);
        boolean ok = MisakaRelayRegistry.get(level.getServer()).scheduleForceCrash(
                level.getServer(),
                entry.satelliteId,
                MisakaRelayRegistry.FORCE_CRASH_COUNTDOWN_TICKS
        );
        setOpsFeedback(ok
                ? "gui.academy.aerospace_signal_cabin.ops_force_crash_armed"
                : "gui.academy.aerospace_signal_cabin.ops_force_crash_fail");
        refreshManagedCount(level);
        markAndSync();
        return ok;
    }

    /** Cancel a pending forced-crash countdown before it begins. */
    public boolean tryCancelForceCrash(ServerLevel level) {
        refreshManagedCount(level);
        var list = managedEntries(level);
        if (list.isEmpty()) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_satellites");
            markAndSync();
            return false;
        }
        int index = Mth.clamp(selectedSatelliteIndex, 0, list.size() - 1);
        selectedSatelliteIndex = index;
        var entry = list.get(index);
        boolean ok = MisakaRelayRegistry.get(level.getServer()).cancelForceCrash(
                level.getServer(),
                entry.satelliteId
        );
        setOpsFeedback(ok
                ? "gui.academy.aerospace_signal_cabin.ops_force_crash_cancelled"
                : "gui.academy.aerospace_signal_cabin.ops_force_crash_cancel_fail");
        refreshManagedCount(level);
        markAndSync();
        return ok;
    }

    public void cycleSelected() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        refreshManagedCount(serverLevel);
        if (managedSatelliteCount == 0) {
            selectedSatelliteIndex = 0;
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_satellites");
            markAndSync();
            return;
        }
        selectedSatelliteIndex = (selectedSatelliteIndex + 1) % managedSatelliteCount;
        setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_cycle_sat");
        refreshManagedCount(serverLevel);
        markAndSync();
    }

    public void cycleHyperDimension() {
        ItemStack stack = items.get(0);
        if (!NetworkRelaySatelliteItem.isHyper(stack)) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_need_hyper");
            markAndSync();
            return;
        }
        // Copy so the open menu detects a component change and syncs the slot to the client.
        ItemStack updated = stack.copy();
        var current = HyperNetworkRelaySatelliteItem.targetDimension(updated);
        var next = net.minecraft.world.level.Level.NETHER.equals(current)
                ? net.minecraft.world.level.Level.END
                : net.minecraft.world.level.Level.NETHER;
        HyperNetworkRelaySatelliteItem.setTargetDimension(updated, next);
        setOpsFeedback(net.minecraft.world.level.Level.NETHER.equals(next)
                ? "gui.academy.aerospace_signal_cabin.ops_dim_nether"
                : "gui.academy.aerospace_signal_cabin.ops_dim_end");
        setItem(0, updated);
    }

    private void markAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public List<UUID> managedSatelliteIds(ServerLevel level) {
        var list = new ArrayList<UUID>();
        for (var entry : managedEntries(level)) {
            list.add(entry.satelliteId);
        }
        return list;
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
        output.putInt("selected_satellite", selectedSatelliteIndex);
        output.putInt("selectable_laser_count", selectableLaserCount);
        output.putInt("managed_satellite_count", managedSatelliteCount);
        output.putString("ops_feedback", opsFeedbackKey);
        // Ops list rows are sync-only (appended in getUpdateTag); do not persist to disk.
        output.putString("selected_sat_network_name", selectedSatNetworkName);
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
        selectedSatelliteIndex = input.getIntOr("selected_satellite", 0);
        selectableLaserCount = input.getIntOr("selectable_laser_count", 0);
        managedSatelliteCount = input.getIntOr("managed_satellite_count", 0);
        opsFeedbackKey = input.getString("ops_feedback").orElse("");
        selectedSatNetworkName = input.getString("selected_sat_network_name").orElse("");
        connectedNodePos = null;
        input.getLong("connected_node_pos").ifPresent(pos -> connectedNodePos = BlockPos.of(pos));
        selectedLaserPos = null;
        input.getLong("selected_laser_pos").ifPresent(pos -> selectedLaserPos = BlockPos.of(pos));
        loadOpsSnapshot(input);
    }

    private void loadOpsSnapshot(ValueInput input) {
        var satChildren = input.childrenList(OPS_MANAGED_SATS);
        if (satChildren.isEmpty()) {
            managedSatelliteList = List.of();
            retargetNetworkList = List.of();
            rebindLaserList = List.of();
            return;
        }
        var sats = new ArrayList<ManagedSatRow>();
        satChildren.get().stream().forEach(row -> sats.add(new ManagedSatRow(
                row.getIntOr("index", 0),
                row.getString("dim").orElse(""),
                row.getString("phase").orElse(""),
                row.getBooleanOr("powered", false),
                row.getBooleanOr("hyper", false),
                row.getString("id8").orElse(""),
                row.getBooleanOr("laser_bound", true),
                row.getIntOr("unpowered_ticks", 0),
                row.getIntOr("crash_timeout", 6000),
                row.getIntOr("force_crash_ticks", 0)
        )));
        managedSatelliteList = List.copyOf(sats);

        var nets = new ArrayList<RetargetNetRow>();
        input.childrenListOrEmpty(OPS_RETARGET_NETS).stream().forEach(row -> nets.add(new RetargetNetRow(
                row.getString("name").orElse(""),
                row.getBooleanOr("current", false)
        )));
        retargetNetworkList = List.copyOf(nets);

        var lasers = new ArrayList<RebindLaserRow>();
        input.childrenListOrEmpty(OPS_REBIND_LASERS).stream().forEach(row ->
                row.getLong("pos").ifPresent(packed ->
                        lasers.add(new RebindLaserRow(BlockPos.of(packed), row.getBooleanOr("ready", false)))
                )
        );
        rebindLaserList = List.copyOf(lasers);
    }

    private void appendOpsSnapshot(CompoundTag tag) {
        var sats = new ListTag();
        for (var row : managedSatelliteList) {
            var c = new CompoundTag();
            c.putInt("index", row.index());
            c.putString("dim", row.dimPath());
            c.putString("phase", row.phase());
            c.putBoolean("powered", row.powered());
            c.putBoolean("hyper", row.hyper());
            c.putString("id8", row.id8());
            c.putBoolean("laser_bound", row.laserBound());
            c.putInt("unpowered_ticks", row.unpoweredTicks());
            c.putInt("crash_timeout", row.crashTimeout());
            c.putInt("force_crash_ticks", row.forceCrashTicks());
            sats.add(c);
        }
        tag.put(OPS_MANAGED_SATS, sats);

        var nets = new ListTag();
        for (var row : retargetNetworkList) {
            var c = new CompoundTag();
            c.putString("name", row.nodeName());
            c.putBoolean("current", row.isCurrentSatNetwork());
            nets.add(c);
        }
        tag.put(OPS_RETARGET_NETS, nets);

        var lasers = new ListTag();
        for (var row : rebindLaserList) {
            var c = new CompoundTag();
            c.putLong("pos", row.pos().asLong());
            c.putBoolean("ready", row.ready());
            lasers.add(c);
        }
        tag.put(OPS_REBIND_LASERS, lasers);
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveWithoutMetadata(registries);
        appendOpsSnapshot(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
