package org.academy.internal.common.world.level.block.entity;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.inventory.SatelliteLaunchPadMenu;
import org.academy.internal.common.world.item.HyperNetworkRelaySatelliteItem;
import org.academy.internal.common.world.item.NetworkRelaySatelliteItem;
import org.academy.internal.common.world.level.block.SatelliteLaunchPadBlock;
import org.academy.internal.server.misaka.MisakaNetworkLasers;
import org.academy.internal.server.misaka.MisakaRelayOrbits;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Launch pad: stack satellite / obsidian / TNT, fill coolant tank, pick laser, launch.
 * Seated satellite is rendered client-side when the top slot is non-empty.
 */
public final class SatelliteLaunchPadBlockEntity extends BlockEntity
        implements WirelessUser, Container, GeoBlockEntity, OwnedDevice {
    public static final int SLOT_SATELLITE = 0;
    public static final int SLOT_OBSIDIAN = 1;
    public static final int SLOT_TNT = 2;
    public static final int SLOT_COUNT = 3;
    public static final int TNT_REQUIRED = 64;
    /** Three buckets. */
    public static final int WATER_CAPACITY_MB = 3000;
    public static final int WATER_BUCKET_MB = 1000;
    public static final int LAUNCH_WATER_COST_MB = 1000;
    public static final float FORCE_LAUNCH_EXPLOSION_POWER = 4.0f;

    private static final int MAX_ENERGY = 50_000;
    /** Sync-only; do not persist. Mirrors the cabin laser pick list. */
    private static final String SYNC_LASER_PICKS = "launch_laser_picks";
    private static final int LASER_PICK_LIMIT = 64;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private @Nullable BlockPos connectedNodePos;
    private @Nullable BlockPos selectedLaserPos;
    private int energyStored;
    private int waterMb;
    /** Remaining ticks of the post-launch coolant drain; 0 = idle. */
    private int coolantDrainTicksRemaining;
    /** Millibuckets still owed for the current launch drain (starts at {@link #LAUNCH_WATER_COST_MB}). */
    private int coolantDrainMbRemaining;
    private @Nullable UUID coolantDrainLauncherUuid;
    private int selectableLaserCount;
    private String launchFeedbackKey = "";
    private @Nullable UUID ownerUuid;
    private List<MisakaNetworkLasers.LaserRow> laserPickList = List.of();

    public SatelliteLaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.SATELLITE_LAUNCH_PAD.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SatelliteLaunchPadBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (be.coolantDrainTicksRemaining > 0) {
            be.tickCoolantDrain(serverLevel);
            // Pad may be destroyed mid-drain.
            if (level.getBlockEntity(pos) != be) {
                return;
            }
        }
        if (level.getGameTime() % 20L != 0L || !be.hasMenuOpen(serverLevel)) {
            return;
        }
        var before = be.laserPickList;
        be.refreshLaserPickList(serverLevel);
        if (!before.equals(be.laserPickList)) {
            be.markAndSync();
        }
    }

    private boolean hasMenuOpen(ServerLevel serverLevel) {
        for (var player : serverLevel.players()) {
            if (player.containerMenu instanceof SatelliteLaunchPadMenu menu
                    && menu.getBlockEntity() == this) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSeatedSatellite() {
        return NetworkRelaySatelliteItem.isSatellite(items.get(SLOT_SATELLITE));
    }

    public boolean isSeatedHyper() {
        return NetworkRelaySatelliteItem.isHyper(items.get(SLOT_SATELLITE));
    }

    public int getWaterMb() {
        return waterMb;
    }

    public boolean hasEnoughLaunchWater() {
        return waterMb >= LAUNCH_WATER_COST_MB;
    }

    public @Nullable BlockPos getSelectedLaserPos() {
        return selectedLaserPos;
    }

    public int getSelectableLaserCount() {
        return selectableLaserCount;
    }

    public List<MisakaNetworkLasers.LaserRow> getLaserPickList() {
        return laserPickList;
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
        return MisakaNetworkLasers.listUnbound(level, connectedNodePos);
    }

    public void cycleSelectedLaser(ServerLevel level) {
        refreshLaserPickList(level);
        if (laserPickList.isEmpty()) {
            selectedLaserPos = null;
            markAndSync();
            return;
        }
        int idx = selectedLaserPos == null ? -1 : indexOfSelected();
        selectedLaserPos = laserPickList.get((idx + 1) % laserPickList.size()).pos();
        markAndSync();
    }

    public boolean trySelectLaser(ServerLevel level, int index) {
        refreshLaserPickList(level);
        if (index < 0 || index >= laserPickList.size()) {
            return false;
        }
        selectedLaserPos = laserPickList.get(index).pos();
        markAndSync();
        return true;
    }

    private int indexOfSelected() {
        if (selectedLaserPos == null) {
            return -1;
        }
        for (int i = 0; i < laserPickList.size(); i++) {
            if (selectedLaserPos.equals(laserPickList.get(i).pos())) {
                return i;
            }
        }
        return -1;
    }

    private void refreshLaserPickList(ServerLevel level) {
        var selectable = listSelectableLasers(level);
        selectableLaserCount = selectable.size();
        laserPickList = MisakaNetworkLasers.listUnboundRows(level, connectedNodePos, LASER_PICK_LIMIT);
    }

    public void cycleHyperDimension(ServerLevel serverLevel) {
        ItemStack stack = items.get(SLOT_SATELLITE);
        if (!NetworkRelaySatelliteItem.isHyper(stack) || serverLevel == null) {
            return;
        }
        var updated = stack.copy();
        var current = HyperNetworkRelaySatelliteItem.targetDimension(updated);
        var keys = new ArrayList<>(serverLevel.getServer().levelKeys());
        keys.removeIf(net.minecraft.world.level.Level.OVERWORLD::equals);
        keys.sort(Comparator.comparing(key -> key.identifier().toString()));
        if (keys.isEmpty()) {
            return;
        }
        int idx = keys.indexOf(current);
        var next = keys.get((idx + 1 + keys.size()) % keys.size());
        HyperNetworkRelaySatelliteItem.setTargetDimension(updated, next);
        setItem(SLOT_SATELLITE, updated);
    }

    /**
     * When the pad replaces a water source on place, seed the tank with one bucket.
     */
    public void absorbPlacementWater() {
        if (waterMb > 0) {
            return;
        }
        waterMb = WATER_BUCKET_MB;
        syncWaterloggedVisual();
        markAndSync();
    }

    /**
     * Fill one bucket into the coolant tank. Returns true if the interaction was handled.
     */
    public boolean tryFillWater(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.WATER_BUCKET)) {
            return false;
        }
        if (waterMb + WATER_BUCKET_MB > WATER_CAPACITY_MB) {
            return false;
        }
        if (level == null || level.isClientSide()) {
            return true;
        }
        waterMb += WATER_BUCKET_MB;
        level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(player, GameEvent.FLUID_PLACE, worldPosition);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        }
        syncWaterloggedVisual();
        markAndSync();
        return true;
    }

    /**
     * Drain one bucket from the coolant tank. Returns true if the interaction was handled.
     */
    public boolean tryDrainWater(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.BUCKET) || stack.getCount() != 1) {
            return false;
        }
        if (waterMb < WATER_BUCKET_MB) {
            return false;
        }
        if (level == null || level.isClientSide()) {
            return true;
        }
        waterMb -= WATER_BUCKET_MB;
        level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, worldPosition);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, new ItemStack(Items.WATER_BUCKET));
        }
        syncWaterloggedVisual();
        markAndSync();
        return true;
    }

    public boolean tryLaunch(ServerLevel level, @Nullable Player launcher) {
        ItemStack satellite = items.get(SLOT_SATELLITE);
        ItemStack obsidian = items.get(SLOT_OBSIDIAN);
        ItemStack tnt = items.get(SLOT_TNT);
        if (!NetworkRelaySatelliteItem.isSatellite(satellite)) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.need_satellite");
            markAndSync();
            return false;
        }
        if (!obsidian.is(Items.OBSIDIAN) || obsidian.getCount() < 1) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.need_obsidian");
            markAndSync();
            return false;
        }
        if (!tnt.is(Items.TNT) || tnt.getCount() < TNT_REQUIRED) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.need_tnt");
            markAndSync();
            return false;
        }
        if (connectedNodePos == null || selectedLaserPos == null) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.need_laser");
            markAndSync();
            return false;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, connectedNodePos);
        var targetDim = NetworkRelaySatelliteItem.targetDimension(satellite);
        boolean hyper = NetworkRelaySatelliteItem.isHyper(satellite);
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
        if (!MisakaNetworkLasers.canPower(level, selectedLaserPos)) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.laser_unready");
            markAndSync();
            return false;
        }
        boolean safeWater = hasEnoughLaunchWater();
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
        if (!ok) {
            setLaunchFeedback("gui.academy.satellite_launch_pad.launch_fail");
            markAndSync();
            return false;
        }

        satellite.shrink(1);
        obsidian.shrink(1);
        tnt.shrink(TNT_REQUIRED);
        selectedLaserPos = null;

        beginCoolantDrain(level, launcher);
        setLaunchFeedback(safeWater
                ? "gui.academy.satellite_launch_pad.launch_ok"
                : "gui.academy.satellite_launch_pad.launch_force_armed");
        markAndSync();
        return true;
    }

    private void beginCoolantDrain(ServerLevel level, @Nullable Player launcher) {
        coolantDrainTicksRemaining = MisakaRelayOrbits.launchPadCoolantDrainTicks(
                level,
                level.getServer(),
                worldPosition
        );
        coolantDrainMbRemaining = LAUNCH_WATER_COST_MB;
        coolantDrainLauncherUuid = launcher != null ? launcher.getUUID() : null;
    }

    private void clearCoolantDrain() {
        coolantDrainTicksRemaining = 0;
        coolantDrainMbRemaining = 0;
        coolantDrainLauncherUuid = null;
    }

    /**
     * Spread {@link #LAUNCH_WATER_COST_MB} evenly over the exhaust-clearance window
     * ({@link MisakaRelayOrbits#launchPadCoolantDrainTicks}).
     * Drain may reach 0 safely; the tick that would push the tank below 0 detonates the pad.
     */
    private void tickCoolantDrain(ServerLevel level) {
        if (coolantDrainTicksRemaining <= 0 || coolantDrainMbRemaining <= 0) {
            clearCoolantDrain();
            return;
        }
        int take = (coolantDrainMbRemaining + coolantDrainTicksRemaining - 1) / coolantDrainTicksRemaining;
        coolantDrainTicksRemaining--;
        coolantDrainMbRemaining -= take;

        if (take > waterMb) {
            // Would go below 0 — empty first, then detonate (waterMb == 0 alone never explodes).
            waterMb = 0;
            syncWaterloggedVisual();
            clearCoolantDrain();
            setLaunchFeedback("gui.academy.satellite_launch_pad.launch_force_explode");
            detonatePad(level, resolveCoolantDrainLauncher(level));
            return;
        }

        waterMb -= take;
        syncWaterloggedVisual();
        if (coolantDrainTicksRemaining <= 0 || coolantDrainMbRemaining <= 0) {
            clearCoolantDrain();
        }
        markAndSync();
    }

    private @Nullable Player resolveCoolantDrainLauncher(ServerLevel level) {
        if (coolantDrainLauncherUuid == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(coolantDrainLauncherUuid);
    }

    private void detonatePad(ServerLevel level, @Nullable Player launcher) {
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.25;
        double z = worldPosition.getZ() + 0.5;
        level.explode(
                launcher,
                x,
                y,
                z,
                FORCE_LAUNCH_EXPLOSION_POWER,
                false,
                Level.ExplosionInteraction.NONE
        );
        level.destroyBlock(worldPosition, true);
    }

    public void syncLaunchSnapshot(ServerLevel level) {
        refreshLaserPickList(level);
        markAndSync();
    }

    /** Mirror tank fill into block fluid level so the vanilla water surface tracks consumption. */
    private void syncWaterloggedVisual() {
        if (level == null || level.isClientSide()) {
            return;
        }
        SatelliteLaunchPadBlock.syncWaterlogged(level, worldPosition, waterMb);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncWaterloggedVisual();
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
        output.putInt("water_mb", waterMb);
        output.putInt("coolant_drain_ticks", coolantDrainTicksRemaining);
        output.putInt("coolant_drain_mb", coolantDrainMbRemaining);
        output.putInt("selectable_laser_count", selectableLaserCount);
        output.putString("launch_feedback", launchFeedbackKey);
        if (connectedNodePos != null) {
            output.putLong("connected_node_pos", connectedNodePos.asLong());
        }
        if (selectedLaserPos != null) {
            output.putLong("selected_laser_pos", selectedLaserPos.asLong());
        }
        if (coolantDrainLauncherUuid != null) {
            output.putString("coolant_drain_launcher", coolantDrainLauncherUuid.toString());
        }
        saveOwner(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energyStored = input.getIntOr("energy_stored", 0);
        waterMb = Math.clamp(input.getIntOr("water_mb", 0), 0, WATER_CAPACITY_MB);
        coolantDrainTicksRemaining = Math.max(0, input.getIntOr("coolant_drain_ticks", 0));
        coolantDrainMbRemaining = Math.max(0, input.getIntOr("coolant_drain_mb", 0));
        coolantDrainLauncherUuid = null;
        input.getString("coolant_drain_launcher").ifPresent(id -> {
            try {
                coolantDrainLauncherUuid = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                coolantDrainLauncherUuid = null;
            }
        });
        if (coolantDrainTicksRemaining <= 0 || coolantDrainMbRemaining <= 0) {
            clearCoolantDrain();
        }
        selectableLaserCount = input.getIntOr("selectable_laser_count", 0);
        launchFeedbackKey = input.getString("launch_feedback").orElse("");
        connectedNodePos = null;
        input.getLong("connected_node_pos").ifPresent(pos -> connectedNodePos = BlockPos.of(pos));
        selectedLaserPos = null;
        input.getLong("selected_laser_pos").ifPresent(pos -> selectedLaserPos = BlockPos.of(pos));
        loadOwner(input);
        var picks = new ArrayList<MisakaNetworkLasers.LaserRow>();
        input.childrenListOrEmpty(SYNC_LASER_PICKS).stream().forEach(row ->
                row.getLong("pos").ifPresent(packed ->
                        picks.add(new MisakaNetworkLasers.LaserRow(BlockPos.of(packed), row.getBooleanOr("ready", false)))
                )
        );
        laserPickList = List.copyOf(picks);
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveWithoutMetadata(registries);
        var lasers = new ListTag();
        for (var row : laserPickList) {
            var c = new CompoundTag();
            c.putLong("pos", row.pos().asLong());
            c.putBoolean("ready", row.ready());
            lasers.add(c);
        }
        tag.put(SYNC_LASER_PICKS, lasers);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (var stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
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
        int max = maxStackForSlot(slot);
        if (stack.getCount() > max) {
            stack.setCount(max);
        }
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_SATELLITE -> NetworkRelaySatelliteItem.isSatellite(stack);
            case SLOT_OBSIDIAN -> stack.is(Items.OBSIDIAN);
            case SLOT_TNT -> stack.is(Items.TNT);
            default -> false;
        };
    }

    private static int maxStackForSlot(int slot) {
        return switch (slot) {
            case SLOT_SATELLITE -> 16;
            case SLOT_OBSIDIAN -> 1;
            case SLOT_TNT -> TNT_REQUIRED;
            default -> 64;
        };
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
