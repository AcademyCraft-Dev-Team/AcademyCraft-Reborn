package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.sublevel.SubLevelCaptureService;
import org.academy.internal.common.sublevel.SubLevelCaptureJournal;
import org.academy.internal.common.sublevel.SubLevelRestoreService;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStaticizeJournal;
import org.academy.mixin.common.PersistentEntitySectionManagerAccessor;
import org.academy.mixin.common.ServerLevelEntityManagerAccessor;

import java.util.List;

/** Real plot inventory, allocation reuse, and far-chunk entity conservation assertions. */
final class SubLevelContentSmoke {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SUPPRESS_DROPS;

    private SubLevelContentSmoke() {}

    static void run(MinecraftServer server) {
        var runtime = SubLevelServer.runtime(server).orElseThrow();
        var host = runtime.host();
        var player = server.getPlayerList().getPlayers().getFirst();
        var origin = player.blockPosition().offset(0, 40, 0);
        var target = origin.offset(-48, 0, 0);
        var middle = new BlockPos(5, 0, 5);
        var far = new BlockPos(23, 0, 23);
        var sourceCells = List.of(origin, origin.offset(middle), origin.offset(far));
        for (var pos : sourceCells) {
            host.getChunkAt(pos);
            host.getChunkAt(target.offset(pos.subtract(origin)));
            host.setBlock(target.offset(pos.subtract(origin)), Blocks.AIR.defaultBlockState(), FLAGS);
        }
        placeChest(host, origin, new ItemStack(Items.EMERALD, 13));
        placeChest(host, origin.offset(far), new ItemStack(Items.DIAMOND, 7));
        host.setBlock(origin.offset(middle), Blocks.IRON_BLOCK.defaultBlockState(), FLAGS);
        var sourceBounds = new AABB(origin).expandTowards(23, 0, 23).inflate(1);
        int originalDrops = host.getEntitiesOfClass(ItemEntity.class, sourceBounds).size();
        var capture = SubLevelCaptureService.capture(host, runtime, sourceCells);
        var body = runtime.get(capture.id()).orElseThrow();
        var plot = body.plot();
        for (var position : sourceCells) check(host.getBlockState(position).isAir(), "capture source remains");
        check(host.getEntitiesOfClass(ItemEntity.class, sourceBounds).size() == originalDrops,
                "capture duplicated container contents as source drops");
        checkChest(runtime.storage().blockEntity(plot, BlockPos.ZERO), Items.EMERALD, 13, "captured first chest");
        checkChest(runtime.storage().blockEntity(plot, far), Items.DIAMOND, 7, "captured far chest");

        // Deliberately omit geometry/view refresh: restore must use current plot content.
        runtime.storage().setBlock(plot, middle, Blocks.GOLD_BLOCK.defaultBlockState(), FLAGS);
        var plotPoint = Vec3.atCenterOf(runtime.storage().worldPosition(plot, far)).add(0, 2, 0);
        var item = new ItemEntity(runtime.storage().level(), plotPoint.x, plotPoint.y, plotPoint.z,
                new ItemStack(Items.REDSTONE, 3));
        item.setNoGravity(true);
        var plotLevel = runtime.storage().level();
        var plotManager = ((ServerLevelEntityManagerAccessor) plotLevel).academy$getEntityManager();
        var hostManager = ((ServerLevelEntityManagerAccessor) host).academy$getEntityManager();
        var plotBlock = BlockPos.containing(plotPoint);
        var plotChunk = new ChunkPos(plotBlock.getX() >> 4, plotBlock.getZ() >> 4);
        var destination = Vec3.atCenterOf(target.offset(far)).add(0, 2, 0);
        var hostBlock = BlockPos.containing(destination);
        var hostChunk = new ChunkPos(hostBlock.getX() >> 4, hostBlock.getZ() >> 4);
        var plotStatus = entitySections(plotLevel).getOrCreateSection(SectionPos.asLong(BlockPos.containing(plotPoint))).getStatus();
        var hostStatus = entitySections(host).getOrCreateSection(SectionPos.asLong(BlockPos.containing(destination))).getStatus();
        var itemId = item.getUUID();
        Entity migrated;
        try {
            // Reproduce the same-tick ticket transition deterministically in both dimensions.
            // Spatial and UUID queries cannot see HIDDEN sections, though addFreshEntity succeeds.
            plotManager.updateChunkStatus(plotChunk, Visibility.HIDDEN);
            hostManager.updateChunkStatus(hostChunk, Visibility.HIDDEN);
            check(plotLevel.addFreshEntity(item), "could not add hidden far-chunk plot item");
            check(plotLevel.getEntity(itemId) == null, "hidden fixture unexpectedly became visible");
            check(runtime.storage().entities(plot).stream().anyMatch(entity -> entity.getUUID().equals(itemId)),
                    "plot snapshot omitted a hidden entity");
            var restored = SubLevelRestoreService.staticize(host, runtime, capture.id(), target);
            check(restored.succeeded() && restored.blocks() == 3, "staticize failed: " + restored.detail());
            migrated = entitySections(host).getExistingSectionsInChunk(hostChunk.pack())
                    .flatMap(section -> section.getEntities())
                    .filter(entity -> entity.getUUID().equals(itemId)).findFirst().orElse(null);
            check(entitySections(plotLevel).getExistingSectionsInChunk(plotChunk.pack())
                    .flatMap(section -> section.getEntities()).noneMatch(entity -> entity.getUUID().equals(itemId)),
                    "entity remains in hidden released plot");
        } finally {
            plotManager.updateChunkStatus(plotChunk, plotStatus);
            hostManager.updateChunkStatus(hostChunk, hostStatus);
        }
        checkChest(host.getBlockEntity(target), Items.EMERALD, 13, "staticized first chest");
        checkChest(host.getBlockEntity(target.offset(far)), Items.DIAMOND, 7, "staticized far chest");
        check(host.getBlockState(target.offset(middle)).is(Blocks.GOLD_BLOCK), "staticize used stale view state");
        check(migrated instanceof ItemEntity, "far-chunk entity was left in released plot");
        check(migrated.position().distanceTo(Vec3.atCenterOf(target.offset(far)).add(0, 2, 0)) < 1.0E-6,
                "far-chunk entity mapped to wrong host coordinate");
        check(((ItemEntity) migrated).getItem().is(Items.REDSTONE)
                && ((ItemEntity) migrated).getItem().getCount() == 3, "migrated item contents changed");
        check(runtime.storage().level().getEntity(itemId) == null, "entity remains in both dimensions");

        host.setBlock(origin, Blocks.STONE.defaultBlockState(), FLAGS);
        var reused = SubLevelCaptureService.capture(host, runtime, List.of(origin));
        var reusedBody = runtime.get(reused.id()).orElseThrow();
        check(reusedBody.plot().equals(plot), "fixture did not reuse the freed plot");
        check(runtime.storage().blockState(plot, BlockPos.ZERO).is(Blocks.STONE), "new plot cell differs");
        check(runtime.storage().blockEntity(plot, BlockPos.ZERO) == null, "old first chest entity survived reuse");
        check(runtime.storage().blockState(plot, middle).isAir(), "old middle block survived reuse");
        check(runtime.storage().blockState(plot, far).isAir(), "old far block survived reuse");
        check(runtime.storage().blockEntity(plot, far) == null, "old far chest entity survived reuse");
        check(runtime.storage().entities(plot).stream().noneMatch(entity -> entity.getUUID().equals(itemId)),
                "migrated entity survived plot reuse");
        runtime.remove(reused.id());
        runtime.persist();
        migrated.discard();
        for (var pos : sourceCells) host.setBlock(target.offset(pos.subtract(origin)), Blocks.AIR.defaultBlockState(), FLAGS);
        captureAllocationFailure(server);
        AcademyCraft.LOGGER.info("SUBLEVEL_CONTENT_MATRIX_PASSED inventory=true source_drops=0 live_plot_state=true"
                + " reused_plot={} stale_blocks=0 stale_block_entities=0 far_chunk_entity=true hidden_entity=true", plot);
    }

    @SuppressWarnings("unchecked")
    private static EntitySectionStorage<Entity> entitySections(ServerLevel level) {
        var manager = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
        return ((PersistentEntitySectionManagerAccessor<Entity>) (Object) manager).academy$getSectionStorage();
    }

    private static void captureAllocationFailure(MinecraftServer server) {
        var runtime = SubLevelServer.runtime(server).orElseThrow();
        var host = runtime.host();
        var nativeOrigin = runtime.universe().world().origin();
        // Valid Minecraft coordinates outside the native body's documented +/-1e6 domain:
        // allocation fails after journal begin, without a production-only test hook.
        var source = BlockPos.containing(nativeOrigin.x() + 1_000_002, 100, nativeOrigin.z());
        host.getChunkAt(source);
        host.setBlock(source, Blocks.IRON_BLOCK.defaultBlockState(), FLAGS);
        var journal = SubLevelStaticizeJournal.journal(server).orElseThrow();
        int recordsBefore = journal.records().size();
        int allocatedBefore = runtime.storage().allocated();
        boolean rejected = false;
        try {
            SubLevelCaptureService.capture(host, runtime, List.of(source));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "out-of-domain body unexpectedly allocated");
        check(host.getBlockState(source).is(Blocks.IRON_BLOCK), "failed capture changed source");
        check(runtime.storage().allocated() == allocatedBefore, "failed capture leaked plot lease");
        check(journal.records().size() == recordsBefore, "completed capture rollback retained journal");
        host.setBlock(source, Blocks.GOLD_BLOCK.defaultBlockState(), FLAGS);
        SubLevelCaptureJournal.recover(server, runtime);
        check(host.getBlockState(source).is(Blocks.GOLD_BLOCK), "recovery overwrote a post-rollback source edit");
        host.setBlock(source, Blocks.AIR.defaultBlockState(), FLAGS);
        AcademyCraft.LOGGER.info("SUBLEVEL_CAPTURE_ROLLBACK_PASSED after_journal_begin=true retired=true subsequent_edit_preserved=true");
    }

    private static void placeChest(ServerLevel level, BlockPos position, ItemStack stack) {
        check(level.setBlock(position, Blocks.CHEST.defaultBlockState(), FLAGS), "could not place fixture chest");
        var chest = (ChestBlockEntity) level.getBlockEntity(position);
        check(chest != null, "fixture chest lacks block entity");
        chest.setItem(0, stack);
        chest.setChanged();
    }

    private static void checkChest(net.minecraft.world.level.block.entity.BlockEntity entity,
                                   net.minecraft.world.item.Item item, int count, String label) {
        check(entity instanceof ChestBlockEntity, label + " missing");
        var stack = ((ChestBlockEntity) entity).getItem(0);
        check(stack.is(item) && stack.getCount() == count, label + " inventory changed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SubLevel content conservation: " + message);
    }
}
