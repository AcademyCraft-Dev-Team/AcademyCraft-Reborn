package org.academy.internal.common.world.level.block.entity;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.academy.internal.common.world.item.LaserDesignatorItem;
import org.academy.internal.common.world.level.block.AerospaceSignalCabinBlock;
import org.academy.internal.common.world.level.block.MultiBlock;
import org.academy.internal.server.world.level.storage.MisakaNetworkGovernance;
import org.academy.internal.server.world.level.storage.MisakaRelayEntry;
import org.academy.internal.server.misaka.MisakaNetworkLasers;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AerospaceSignalCabinBlockEntity extends MultiBlockEntity
        implements WirelessUser, GeoBlockEntity, OwnedDevice {
    private static final RawAnimation SPINNING = RawAnimation.begin().thenLoop("spinning");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
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
            int forceCrashTicks,
            String strikeMode,
            int strikeCooldownTicks
    ) {
    }

    public record RetargetNetRow(String nodeName, boolean isCurrentSatNetwork) {
    }

    private @Nullable BlockPos connectedNodePos;
    private @Nullable UUID ownerUuid;
    private int energyStored;
    private int selectedSatelliteIndex;
    /** Client-synced count of unbound lasers on the cabin's wireless topology. */
    private int selectableLaserCount;
    /** Client-synced count of satellites on the connected network. */
    private int managedSatelliteCount;
    /** Client-synced ops-page feedback key (empty = idle). */
    private String opsFeedbackKey = "";
    /** Client-synced satellite rows for the ops list (sync-only; not persisted). */
    private List<ManagedSatRow> managedSatelliteList = List.of();
    /** Client-synced retarget targets (unique Misaka topologies; sync-only). */
    private List<RetargetNetRow> retargetNetworkList = List.of();
    /** Client-synced unbound lasers on the cabin wireless topology (sync-only). */
    private List<MisakaNetworkLasers.LaserRow> rebindLaserList = List.of();
    /** Display name of the selected satellite's current coverage network, if known. */
    private String selectedSatNetworkName = "";

    public AerospaceSignalCabinBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.AEROSPACE_SIGNAL_CABIN.get(), pos, state);
    }

    public @Nullable AerospaceSignalCabinBlockEntity mainEntity() {
        MultiBlockEntity main = getMain();
        return main instanceof AerospaceSignalCabinBlockEntity cabin ? cabin : null;
    }

    public AABB getRenderBoundingBox() {
        BlockPos base = isMain() ? worldPosition : (mainPos != null ? mainPos : worldPosition);
        return new AABB(
                base.getX() - 0.5,
                base.getY() - 0.5,
                base.getZ() - 0.5,
                base.getX() + 1.5,
                base.getY() + AerospaceSignalCabinBlock.HEIGHT + 0.5,
                base.getZ() + 1.5
        );
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AerospaceSignalCabinBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel) || !be.isMain()) {
            return;
        }
        be.ensureSubjectPresent(serverLevel);
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

    public List<MisakaNetworkLasers.LaserRow> getRebindLaserList() {
        return rebindLaserList;
    }

    public String getSelectedSatNetworkName() {
        return selectedSatNetworkName;
    }

    private void setOpsFeedback(String key) {
        opsFeedbackKey = key == null ? "" : key;
    }

    /**
     * Owner / op, or a player with {@link MisakaNetworkPermission#SATELLITE_MANAGE} on the cabin's
     * connected network, may run cabin ops. Null player skips the gate.
     */
    public boolean canManageOps(@Nullable Player player) {
        if (player == null || isOwner(player)) {
            return true;
        }
        return hasSatelliteManage(player);
    }

    private boolean hasSatelliteManage(@Nullable Player player) {
        if (!(player instanceof ServerPlayer) || !(level instanceof ServerLevel sl) || connectedNodePos == null) {
            return false;
        }
        var server = sl.getServer();
        if (server == null) {
            return false;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(server.overworld(), connectedNodePos);
        return MisakaNetworkGovernance.get(server)
                .hasPermission(player, networkId, MisakaNetworkPermission.SATELLITE_MANAGE);
    }

    private boolean denyOpsIfNotOwner(@Nullable Player player) {
        if (canManageOps(player)) {
            return false;
        }
        setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_permission");
        markAndSync();
        return true;
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

    /** Satellites covering the cabin's connected wireless network (empty if unlinked). */
    private List<MisakaRelayEntry> managedEntries(ServerLevel level) {
        if (connectedNodePos == null) {
            return List.of();
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, connectedNodePos);
        var list = MisakaRelayRegistry.get(level.getServer()).listByNetwork(networkId);
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
            var id8 = MisakaRelayEntry.shortId(entry.satelliteId);
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
                    entry.forceCrashCountdownTicks,
                    entry.strikeMode.name(),
                    entry.strikeCooldownTicks
            ));
        }
        managedSatelliteList = List.copyOf(rows);
        refreshRetargetNetworks(level);
    }

    private void refreshRetargetNetworks(ServerLevel level) {
        var data = WirelessNetworkData.get(level);
        // networkId -> representative node name (lexicographically smallest)
        var byNetwork = new java.util.TreeMap<UUID, String>();
        for (var entry : data.getAllNodes().entrySet()) {
            var nodePos = entry.getKey();
            var name = entry.getValue().name;
            if (name == null || name.isBlank()) {
                continue;
            }
            var networkId = MisakaNAT.get().resolveNetworkId(level, nodePos);
            byNetwork.merge(networkId, name, (a, b) -> a.compareToIgnoreCase(b) <= 0 ? a : b);
        }
        UUID selectedNet = null;
        var sats = managedEntries(level);
        if (!sats.isEmpty()) {
            int idx = Mth.clamp(selectedSatelliteIndex, 0, sats.size() - 1);
            selectedNet = sats.get(idx).networkId;
        }
        selectedSatNetworkName = "";
        if (selectedNet != null) {
            var match = byNetwork.get(selectedNet);
            if (match != null) {
                selectedSatNetworkName = match;
            } else {
                selectedSatNetworkName = selectedNet.toString();
            }
        }
        var names = new ArrayList<>(byNetwork.entrySet());
        names.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getValue(), b.getValue()));
        var rows = new ArrayList<RetargetNetRow>(names.size());
        for (var e : names) {
            boolean current = selectedNet != null && selectedNet.equals(e.getKey());
            rows.add(new RetargetNetRow(e.getValue(), current));
        }
        retargetNetworkList = List.copyOf(rows);
    }

    private void refreshRebindLasers(ServerLevel level) {
        var rows = MisakaNetworkLasers.listUnboundRows(level, connectedNodePos, Integer.MAX_VALUE);
        selectableLaserCount = rows.size();
        rebindLaserList = rows;
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
     * Power/sky readiness is only required at rebind time.
     */
    public List<BlockPos> listSelectableLasers(ServerLevel level) {
        return MisakaNetworkLasers.listUnbound(level, connectedNodePos);
    }

    public boolean tryRetarget(ServerLevel level, @Nullable Player player) {
        if (denyOpsIfNotOwner(player)) {
            return false;
        }
        // Legacy: retarget to the cabin's currently linked wireless topology.
        if (connectedNodePos == null) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_need_wireless");
            markAndSync();
            return false;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, connectedNodePos);
        return retargetSelectedTo(level, networkId);
    }

    /** @deprecated use {@link #tryRetarget(ServerLevel, Player)} */
    @Deprecated
    public boolean tryRetarget(ServerLevel level) {
        return tryRetarget(level, null);
    }

    public boolean tryRetargetToNetworkIndex(ServerLevel level, int networkIndex, @Nullable Player player) {
        if (denyOpsIfNotOwner(player)) {
            return false;
        }
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

    private boolean retargetSelectedTo(ServerLevel level, UUID networkId) {
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
    public boolean tryRebindToLaserIndex(ServerLevel level, int laserIndex, @Nullable Player player) {
        if (denyOpsIfNotOwner(player)) {
            return false;
        }
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
        if (!MisakaNetworkLasers.canPower(level, laserPos)) {
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
    public boolean tryScheduleForceCrash(ServerLevel level, Player player) {
        if (denyOpsIfNotOwner(player)) {
            return false;
        }
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
                MisakaRelayRegistry.FORCE_CRASH_COUNTDOWN_TICKS,
                player.getUUID()
        );
        setOpsFeedback(ok
                ? "gui.academy.aerospace_signal_cabin.ops_force_crash_armed"
                : "gui.academy.aerospace_signal_cabin.ops_force_crash_fail");
        refreshManagedCount(level);
        markAndSync();
        return ok;
    }

    /** Cancel a pending forced-crash countdown before it begins. */
    public boolean tryCancelForceCrash(ServerLevel level, @Nullable Player player) {
        if (denyOpsIfNotOwner(player)) {
            return false;
        }
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

    /** Bind the player's main-hand laser designator to the selected managed satellite. */
    public boolean tryBindDesignator(ServerLevel level, Player player) {
        if (denyOpsIfNotOwner(player)) {
            return false;
        }
        refreshManagedCount(level);
        var list = managedEntries(level);
        if (list.isEmpty()) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_no_satellites");
            markAndSync();
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (!LaserDesignatorItem.isDesignator(stack)) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_designator_need_item");
            markAndSync();
            return false;
        }
        int index = Mth.clamp(selectedSatelliteIndex, 0, list.size() - 1);
        selectedSatelliteIndex = index;
        var entry = list.get(index);
        if (entry.phase == MisakaRelayEntry.Phase.CRASHING) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_designator_sat_unready");
            markAndSync();
            return false;
        }
        LaserDesignatorItem.bind(stack, entry.satelliteId);
        setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_designator_bound");
        refreshManagedCount(level);
        markAndSync();
        return true;
    }

    /** Clear the satellite binding on the player's main-hand laser designator. */
    public boolean tryUnbindDesignator(ServerLevel level, Player player) {
        if (denyOpsIfNotOwner(player)) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (!LaserDesignatorItem.isDesignator(stack)) {
            setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_designator_need_item");
            markAndSync();
            return false;
        }
        LaserDesignatorItem.unbind(stack);
        setOpsFeedback("gui.academy.aerospace_signal_cabin.ops_designator_unbound");
        markAndSync();
        return true;
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

    private void markAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Places missing upper segment for legacy single-block cabins when the cell is empty. */
    private void ensureSubjectPresent(ServerLevel level) {
        var facing = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        var subjects = MultiBlock.getRotatedSubjectBlocks(
                worldPosition, facing, AerospaceSignalCabinBlock.SUBJECT_BLOCKS
        );
        boolean needsPlace = false;
        for (BlockPos subjectPos : subjects) {
            if (level.getBlockState(subjectPos).isAir()) {
                needsPlace = true;
                break;
            }
        }
        if (needsPlace) {
            MultiBlock.setSubBlocks(level, worldPosition, getBlockState(), subjects);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("radar", state -> state.setAndContinue(SPINNING)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
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
        saveOwner(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energyStored = input.getIntOr("energy_stored", 0);
        selectedSatelliteIndex = input.getIntOr("selected_satellite", 0);
        selectableLaserCount = input.getIntOr("selectable_laser_count", 0);
        managedSatelliteCount = input.getIntOr("managed_satellite_count", 0);
        opsFeedbackKey = input.getString("ops_feedback").orElse("");
        selectedSatNetworkName = input.getString("selected_sat_network_name").orElse("");
        connectedNodePos = null;
        input.getLong("connected_node_pos").ifPresent(pos -> connectedNodePos = BlockPos.of(pos));
        loadOwner(input);
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
                row.getIntOr("force_crash_ticks", 0),
                row.getString("strike_mode").orElse("IDLE"),
                row.getIntOr("strike_cooldown_ticks", 0)
        )));
        managedSatelliteList = List.copyOf(sats);

        var nets = new ArrayList<RetargetNetRow>();
        input.childrenListOrEmpty(OPS_RETARGET_NETS).stream().forEach(row -> nets.add(new RetargetNetRow(
                row.getString("name").orElse(""),
                row.getBooleanOr("current", false)
        )));
        retargetNetworkList = List.copyOf(nets);

        var lasers = new ArrayList<MisakaNetworkLasers.LaserRow>();
        input.childrenListOrEmpty(OPS_REBIND_LASERS).stream().forEach(row ->
                row.getLong("pos").ifPresent(packed ->
                        lasers.add(new MisakaNetworkLasers.LaserRow(BlockPos.of(packed), row.getBooleanOr("ready", false)))
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
            c.putString("strike_mode", row.strikeMode());
            c.putInt("strike_cooldown_ticks", row.strikeCooldownTicks());
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveWithoutMetadata(registries);
        appendOpsSnapshot(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
