package org.academy.internal.common.structure.sable.validation;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;

/** Direct backend capability probe, deliberately distinct from ACR's state-only transaction. */
public final class SablePayloadCapabilityProbe {
    private SablePayloadCapabilityProbe() {}

    public static Map<String, Object> run(ServerLevel host, BlockPos anchor) {
        var source = List.of(anchor, anchor.above(), anchor.east());
        for (var cell : source) check(host.getBlockState(cell).isAir(), "probe source occupied");
        host.setBlock(anchor, Blocks.IRON_BLOCK.defaultBlockState(), 2);
        host.setBlock(anchor.above(), Blocks.CHEST.defaultBlockState(), 2);
        var wetSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        host.setBlock(anchor.east(), wetSlab, 2);
        var chest = (ChestBlockEntity) host.getBlockEntity(anchor.above());
        check(chest != null, "missing source chest");
        chest.setItem(0, new ItemStack(Items.DIAMOND, 19));
        var expected = chest.saveWithFullMetadata(host.registryAccess());
        expected.remove("x"); expected.remove("y"); expected.remove("z");

        // This is the same Sable assembly entry used by SimAssemblyHelper in Aeronautics.
        var ship = SubLevelAssemblyHelper.assembleBlocks(host, anchor, source,
                new BoundingBox3i(anchor.getX(), anchor.getY(), anchor.getZ(),
                        anchor.getX() + 1, anchor.getY() + 1, anchor.getZ()));
        var plot = ship.getPlot().getCenterBlock();
        check(source.stream().allMatch(cell -> host.getBlockState(cell).isAir()), "source was duplicated");
        check(host.getBlockState(plot.east()).equals(wetSlab), "assembled waterlogging lost");
        var copied = host.getBlockEntity(plot.above());
        check(copied instanceof ChestBlockEntity, "assembled chest missing");
        var actual = copied.saveWithFullMetadata(host.registryAccess());
        actual.remove("x"); actual.remove("y"); actual.remove("z");
        check(expected.equals(actual), "assembled chest payload changed");
        check(host.getEntitiesOfClass(ItemEntity.class, new AABB(anchor).inflate(4)).isEmpty(), "assembly dropped contents");

        // Exercise the same transfer helper used by the suite's disassembly path.
        SubLevelAssemblyHelper.moveBlocks(host,
                new SubLevelAssemblyHelper.AssemblyTransform(plot, anchor, 0, Rotation.NONE, host),
                List.of(plot, plot.above(), plot.east()));
        check(host.getBlockState(anchor.east()).equals(wetSlab), "restored waterlogging lost");
        var restored = host.getBlockEntity(anchor.above());
        check(restored instanceof ChestBlockEntity, "restored chest missing");
        var restoredTag = restored.saveWithFullMetadata(host.registryAccess());
        restoredTag.remove("x"); restoredTag.remove("y"); restoredTag.remove("z");
        check(expected.equals(restoredTag), "restored chest payload changed");
        check(host.getEntitiesOfClass(ItemEntity.class, new AABB(anchor).inflate(4)).isEmpty(), "restoration dropped contents");
        check(host.getEntitiesOfClass(ItemEntity.class, new AABB(plot).inflate(4)).isEmpty(), "plot duplicated contents");
        var container = SubLevelContainer.getContainer(host);
        if (!ship.isRemoved()) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
        ((ChestBlockEntity) restored).clearContent();
        for (var cell : source) host.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
        return Map.of("case", "sable_payload_transfer", "passed", true, "inventory_items", 19,
                "waterlogging_round_trip", true, "full_chest_nbt_round_trip", true,
                "acr_capture_supported", false, "crash_recovery_verified", false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
