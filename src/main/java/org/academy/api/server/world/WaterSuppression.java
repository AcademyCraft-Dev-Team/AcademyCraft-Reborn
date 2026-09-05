package org.academy.api.server.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary, overlapping water suppression leases for skills and non-player callers.
 * Refresh each tick on the server thread. The restoration journal survives saving and chunk unloads;
 * leases do not survive a restart, so loaded blocks are restored automatically.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class WaterSuppression extends SavedData {
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
            BlockState.CODEC.fieldOf("original").forGetter(Entry::original),
            BlockState.CODEC.fieldOf("dry").forGetter(Entry::dry)
    ).apply(instance, Entry::new));
    public static final Codec<WaterSuppression> CODEC = ENTRY_CODEC.listOf().xmap(WaterSuppression::new, WaterSuppression::entries);
    public static final SavedDataType<WaterSuppression> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("suppressed_water"), () -> new WaterSuppression(List.of()), CODEC);
    private final Map<BlockPos, Entry> journal = new HashMap<>();
    private final Map<UUID, Zone> zones = new HashMap<>();

    private WaterSuppression(List<Entry> entries) {
        for (var entry : entries) journal.put(entry.pos(), entry);
    }

    private List<Entry> entries() {
        return List.copyOf(journal.values());
    }

    private static WaterSuppression get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public static void refresh(ServerLevel level, UUID source, Vec3 center, double radius) {
        if (source == null || center == null || !Double.isFinite(center.x) || !Double.isFinite(center.y)
                || !Double.isFinite(center.z) || !Double.isFinite(radius) || radius <= 0.0) return;
        var data = get(level);
        data.zones.put(source, new Zone(center, Math.min(8.0, radius), level.getGameTime() + 2));
        data.suppress(level, data.zones.get(source));
    }

    public static void release(ServerLevel level, UUID source) {
        var data = get(level);
        data.zones.remove(source);
        data.restoreUncovered(level);
    }

    public static boolean contains(ServerLevel level, BlockPos pos) {
        return get(level).covered(pos, level.getGameTime());
    }

    /** True only where ambient water was displaced, rather than in ordinary open air. */
    public static boolean suppliesAir(ServerLevel level, Vec3 position) {
        var data = get(level);
        var pos = BlockPos.containing(position);
        return data.journal.containsKey(pos) && data.covered(pos, level.getGameTime());
    }

    public static boolean inside(Vec3 center, double radius, BlockPos pos) {
        return radius > 0.0 && Double.isFinite(radius)
                && Vec3.atCenterOf(pos).distanceToSqr(center) <= radius * radius;
    }

    public static BlockState dryState(BlockState state) {
        if (!state.getFluidState().is(FluidTags.WATER)) return state;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED))
            return state.setValue(BlockStateProperties.WATERLOGGED, false);
        return state.getBlock() instanceof LiquidBlock ? Blocks.AIR.defaultBlockState() : state;
    }

    public static BlockState restorationState(BlockState current, BlockState original, BlockState dry) {
        if (current.equals(dry)) return original;
        // Interacting with a drained door or reorienting a slab must not discard its original water.
        // Preserve those new properties; a different replacement block is left untouched.
        if (current.is(dry.getBlock()) && original.hasProperty(BlockStateProperties.WATERLOGGED)
                && original.getValue(BlockStateProperties.WATERLOGGED)
                && current.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return current.setValue(BlockStateProperties.WATERLOGGED, true);
        }
        return current;
    }

    private boolean covered(BlockPos pos, long now) {
        return zones.values().stream().anyMatch(zone -> zone.expiresAt() >= now && inside(zone.center(), zone.radius(), pos));
    }

    private void suppress(ServerLevel level, Zone zone) {
        var origin = BlockPos.containing(zone.center());
        var extent = (int) Math.ceil(zone.radius());
        for (var mutable : BlockPos.betweenClosed(origin.offset(-extent, -extent, -extent), origin.offset(extent, extent, extent))) {
            var pos = mutable.immutable();
            if (!inside(zone.center(), zone.radius(), pos) || !level.hasChunkAt(pos)) continue;
            var current = level.getBlockState(pos);
            var dry = dryState(current);
            if (dry.equals(current)) continue;
            journal.putIfAbsent(pos, new Entry(pos, current, dry));
            setDirty();
            // Avoid breaking aquatic plants or changing the shape of waterlogged blocks while dry.
            level.setBlock(pos, dry, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private void restoreUncovered(ServerLevel level) {
        var iterator = journal.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next().getValue();
            if (covered(entry.pos(), level.getGameTime()) || !level.hasChunkAt(entry.pos())) continue;
            var current = level.getBlockState(entry.pos());
            var restored = restorationState(current, entry.original(), entry.dry());
            if (!restored.equals(current)) {
                level.setBlock(entry.pos(), restored, Block.UPDATE_ALL);
                var fluid = restored.getFluidState().getType();
                level.scheduleTick(entry.pos(), fluid, fluid.getTickDelay(level));
            }
            iterator.remove();
            setDirty();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (var level : event.getServer().getAllLevels()) {
            var data = get(level);
            data.zones.values().removeIf(zone -> zone.expiresAt() < level.getGameTime());
            data.restoreUncovered(level);
        }
    }

    private record Entry(BlockPos pos, BlockState original, BlockState dry) {
    }

    private record Zone(Vec3 center, double radius, long expiresAt) {
    }
}
