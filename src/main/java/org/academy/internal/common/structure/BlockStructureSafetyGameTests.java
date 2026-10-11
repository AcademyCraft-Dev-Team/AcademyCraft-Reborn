package org.academy.internal.common.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureApi;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.BlockStructureCaptureResult;
import org.academy.api.common.structure.BlockStructureRestoreResult;
import org.academy.api.common.structure.BlockStructureSettlementMode;
import org.academy.api.common.structure.BlockStructureSettlementResult;
import org.academy.api.common.structure.BlockStructureSnapshot;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;

import java.util.List;
import java.util.function.Consumer;

/** Runs transfers against real containers, entity admission events and the entity save format. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class BlockStructureSafetyGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("block_structure_safety_function");
    private static final int QUIET_UPDATE_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private BlockStructureSafetyGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> StructureSafetyTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("block_structure_safety"),
                new StructureSafetyTest(new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 100, 0, true,
                        Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void verify(GameTestHelper helper) {
        verifyContainerCaptureAndRetainedSettlement(helper);
        verifyCanceledCaptureLeavesSource(helper);
        verifyAdmissionMutationPreservesNewInventory(helper);
        verifyInvalidSecondBlockEntityRollsBack(helper);
        verifyCanceledFallingBatchRollsBack(helper);
        verifyMixedFallingSettlementPreservesInventory(helper);
        verifySnowGolemPlacementCommitsOnce(helper);
        verifyPendingTransferSaveRoundTrip(helper);
        verifyChangedAfterImageBlocksRollback(helper);
        assertNoItemDrops(helper, "All structure safety scenarios must preserve inventory without drops");
        helper.succeed();
    }

    private static void verifyContainerCaptureAndRetainedSettlement(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(2, 2, 2));
        var chest = placeChest(helper, source, new ItemStack(Items.DIAMOND, 7));
        var expectedData = chest.saveWithFullMetadata(level.registryAccess());
        var structure = capture(helper, source);
        try {
            helper.assertTrue(level.getBlockState(source).isAir(), "Capture must remove the source chest");
            assertSnapshotData(helper, structure, 0, expectedData,
                    "Capture must retain the complete chest NBT");
            assertNoItemDrops(helper, "Capturing a populated chest must not drop its contents");

            level.setBlock(source, Blocks.STONE.defaultBlockState(), QUIET_UPDATE_FLAGS);
            var retained = structure.settleToGrid();
            helper.assertTrue(retained.status() == BlockStructureSettlementResult.Status.RETAINED,
                    "Occupied settlement must report RETAINED, got " + retained.status());
            helper.assertTrue(!structure.asEntity().isRemoved()
                            && retained.placedBlocks() == 0 && retained.droppedBlocks() == 0,
                    "Occupied settlement must retain the whole structure without partial placement");
            helper.assertTrue(level.getBlockState(source).is(Blocks.STONE),
                    "Occupied settlement must preserve the blocking world block");
            assertSnapshotData(helper, structure, 0, expectedData,
                    "Retained structure must still own all seven diamonds");
            assertNoItemDrops(helper, "Failed settlement must not create an empty chest item or diamonds");

            clearCell(level, source);
            var restored = structure.restoreToGrid();
            helper.assertTrue(restored.succeeded() && restored.affectedBlocks() == 1,
                    "Unblocked restoration must succeed: " + restored.status());
            helper.assertTrue(structure.asEntity().isRemoved(),
                    "Successful restoration must remove the entity payload");
            assertChestInventory(helper, source, Items.DIAMOND, 7);
            assertNoItemDrops(helper, "Restoration must not duplicate the chest inventory as drops");
        } finally {
            structure.asEntity().discard();
            clearCell(level, source);
        }
    }

    private static void verifyCanceledCaptureLeavesSource(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(6, 2, 2));
        var chest = placeChest(helper, source, new ItemStack(Items.DIAMOND, 7));
        var expectedData = chest.saveWithFullMetadata(level.registryAccess());
        var cancellations = new int[1];
        Consumer<EntityJoinLevelEvent> rejectStructure = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof BlockStructureEntity
                    && event.getEntity().blockPosition().equals(source)) {
                cancellations[0]++;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(rejectStructure);
        try {
            var result = BlockStructureApi.capture(level, List.of(source),
                    BlockStructureCaptureOptions.defaults().withGravity(false));
            helper.assertTrue(cancellations[0] == 1,
                    "Capture fixture must reach and cancel the actual entity admission event");
            helper.assertTrue(result.status() == BlockStructureCaptureResult.Status.ENTITY_SPAWN_FAILED
                            && result.structure().isEmpty(),
                    "Canceled admission must fail capture without returning an active structure");
            assertChestInventory(helper, source, Items.DIAMOND, 7);
            helper.assertTrue(level.getBlockEntity(source).saveWithFullMetadata(level.registryAccess())
                            .equals(expectedData),
                    "Canceled admission must leave the complete source BE data unchanged");
            helper.assertTrue(level.getEntitiesOfClass(BlockStructureEntity.class,
                            new AABB(source).inflate(0.1)).isEmpty(),
                    "Canceled admission must not leave a structure entity in the world");
            assertNoItemDrops(helper, "Canceled capture must not drop source contents");
        } finally {
            NeoForge.EVENT_BUS.unregister(rejectStructure);
            clearCell(level, source);
        }
    }

    private static void verifyAdmissionMutationPreservesNewInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(6, 2, 6));
        placeChest(helper, source, new ItemStack(Items.DIAMOND, 7));
        var mutations = new int[1];
        Consumer<EntityJoinLevelEvent> changeSource = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof BlockStructureEntity
                    && event.getEntity().blockPosition().equals(source)) {
                var chest = (ChestBlockEntity) level.getBlockEntity(source);
                chest.setItem(0, new ItemStack(Items.EMERALD, 11));
                chest.setChanged();
                mutations[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(changeSource);
        try {
            var result = BlockStructureApi.capture(level, List.of(source),
                    BlockStructureCaptureOptions.defaults().withGravity(false));
            helper.assertTrue(mutations[0] == 1,
                    "Fixture must change the source inventory during actual entity admission");
            helper.assertTrue(result.status() == BlockStructureCaptureResult.Status.WORLD_CHANGE_FAILED
                            && result.structure().isEmpty(),
                    "Capture must reject a stale snapshot after another listener changes the inventory");
            assertChestInventory(helper, source, Items.EMERALD, 11);
            helper.assertTrue(level.getEntitiesOfClass(BlockStructureEntity.class,
                            new AABB(source).inflate(0.1)).isEmpty(),
                    "Rejected stale capture must remove its obsolete structure payload");
            assertNoItemDrops(helper, "Rejecting a stale snapshot must not emit either version of the inventory");
        } finally {
            NeoForge.EVENT_BUS.unregister(changeSource);
            level.getEntitiesOfClass(BlockStructureEntity.class, new AABB(source).inflate(0.1))
                    .forEach(BlockStructureEntity::discard);
            clearCell(level, source);
        }
    }

    private static void verifyInvalidSecondBlockEntityRollsBack(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = helper.absolutePos(new BlockPos(2, 2, 6));
        var second = first.east();
        var validData = placeChest(helper, first, new ItemStack(Items.DIAMOND, 7))
                .saveWithFullMetadata(level.registryAccess());
        clearCell(level, first);
        clearCell(level, second);
        var invalidData = validData.copy();
        invalidData.putString("id", "academy:missing_block_entity_for_safety_test");
        var snapshot = new BlockStructureSnapshot(List.of(
                new BlockStructureSnapshot.BlockData(BlockPos.ZERO,
                        Blocks.CHEST.defaultBlockState(), validData, BlockStructureSettlementMode.FIXED),
                new BlockStructureSnapshot.BlockData(new BlockPos(1, 0, 0),
                        Blocks.CHEST.defaultBlockState(), invalidData, BlockStructureSettlementMode.FIXED)
        ));
        var structure = spawn(helper, snapshot, first);
        try {
            var result = structure.restoreToGrid();
            helper.assertTrue(result.status() == BlockStructureRestoreResult.Status.WORLD_CHANGE_FAILED,
                    "Invalid second BE must fail restoration after a recoverable write: " + result.status());
            helper.assertTrue(!structure.asEntity().isRemoved()
                            && structure.snapshot().blockCount() == 2,
                    "Failed restoration must retain the complete two-cell payload");
            helper.assertTrue(level.getBlockState(first).isAir()
                            && level.getBlockState(second).isAir()
                            && level.getBlockEntity(first) == null
                            && level.getBlockEntity(second) == null,
                    "Rollback must remove every partially restored chest and BE");
            assertSnapshotData(helper, structure, 0, validData,
                    "Rollback must preserve the first chest inventory in the structure");
            assertSnapshotData(helper, structure, 1, invalidData,
                    "Rollback must preserve failed BE data for recovery rather than discarding it");
            assertNoItemDrops(helper, "Removing a partially restored chest must not duplicate its inventory");
        } finally {
            structure.asEntity().discard();
            clearCell(level, first);
            clearCell(level, second);
        }
    }

    private static void verifyCanceledFallingBatchRollsBack(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = helper.absolutePos(new BlockPos(10, 3, 2));
        var second = first.east();
        clearCell(level, first);
        clearCell(level, second);
        var bounds = new AABB(Vec3.atLowerCornerOf(first),
                Vec3.atLowerCornerOf(second.offset(1, 1, 1)));
        var snapshot = new BlockStructureSnapshot(List.of(
                new BlockStructureSnapshot.BlockData(BlockPos.ZERO,
                        Blocks.SAND.defaultBlockState(), null, BlockStructureSettlementMode.FALLING),
                new BlockStructureSnapshot.BlockData(new BlockPos(1, 0, 0),
                        Blocks.GRAVEL.defaultBlockState(), null, BlockStructureSettlementMode.FALLING)
        ));
        var structure = spawn(helper, snapshot, first);
        var attempts = new int[1];
        Consumer<EntityJoinLevelEvent> rejectSecondFalling = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof FallingBlockEntity
                    && bounds.contains(event.getEntity().position()) && ++attempts[0] == 2) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(rejectSecondFalling);
        try {
            structure.beginGravitySettlement();
            helper.assertTrue(attempts[0] == 2,
                    "Falling settlement fixture must cancel the second real admission");
            helper.assertTrue(!structure.asEntity().isRemoved()
                            && structure.snapshot().blockCount() == 2,
                    "Rejected falling batch must retain the complete source structure");
            helper.assertTrue(level.getEntitiesOfClass(FallingBlockEntity.class, bounds).isEmpty(),
                    "Rejected falling batch must remove the earlier admitted falling entity");
            helper.assertTrue(level.getBlockState(first).isAir() && level.getBlockState(second).isAir(),
                    "Rejected falling batch must not leave temporary world blocks behind");
            assertNoItemDrops(helper, "Rejected falling batch must not convert source blocks to items");
        } finally {
            NeoForge.EVENT_BUS.unregister(rejectSecondFalling);
            structure.asEntity().discard();
            level.getEntitiesOfClass(FallingBlockEntity.class, bounds).forEach(FallingBlockEntity::discard);
            clearCell(level, first);
            clearCell(level, second);
        }
    }

    private static void verifyMixedFallingSettlementPreservesInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var sandPosition = helper.absolutePos(new BlockPos(10, 3, 6));
        var gravelPosition = sandPosition.east();
        var chestPosition = sandPosition.east(2);
        clearCell(level, sandPosition);
        clearCell(level, gravelPosition);
        var chestData = placeChest(helper, chestPosition, new ItemStack(Items.DIAMOND, 7))
                .saveWithFullMetadata(level.registryAccess());
        clearCell(level, chestPosition);
        var bounds = new AABB(Vec3.atLowerCornerOf(sandPosition),
                Vec3.atLowerCornerOf(chestPosition.offset(1, 1, 1)));
        // A legacy or external payload may mark a container FALLING. Inventory still requires fixed placement.
        var snapshot = new BlockStructureSnapshot(List.of(
                new BlockStructureSnapshot.BlockData(BlockPos.ZERO,
                        Blocks.SAND.defaultBlockState(), null, BlockStructureSettlementMode.FALLING),
                new BlockStructureSnapshot.BlockData(new BlockPos(1, 0, 0),
                        Blocks.GRAVEL.defaultBlockState(), null, BlockStructureSettlementMode.FALLING),
                new BlockStructureSnapshot.BlockData(new BlockPos(2, 0, 0),
                        Blocks.CHEST.defaultBlockState(), chestData, BlockStructureSettlementMode.FALLING)
        ));
        var structure = spawn(helper, snapshot, sandPosition);
        try {
            helper.assertTrue(structure.snapshot().blocks().get(2).settlementMode()
                            == BlockStructureSettlementMode.FALLING,
                    "Mixed fixture must actually supply a FALLING container payload");
            structure.beginGravitySettlement();
            helper.assertTrue(structure.asEntity().isRemoved(),
                    "Successful mixed settlement must remove the source structure");
            helper.assertTrue(level.getEntitiesOfClass(BlockStructureEntity.class, bounds).isEmpty(),
                    "Successful mixed settlement must not leave a second structure payload");
            var falling = level.getEntitiesOfClass(FallingBlockEntity.class, bounds);
            helper.assertTrue(falling.size() == 2,
                    "Mixed settlement must admit exactly the sand and gravel as actual falling entities");
            helper.assertTrue(falling.stream().filter(entity -> entity.getBlockState().is(Blocks.SAND)).count() == 1
                            && falling.stream().filter(entity -> entity.getBlockState().is(Blocks.GRAVEL)).count() == 1,
                    "Falling entities must preserve one sand and one gravel without a falling chest");
            helper.assertTrue(falling.stream().allMatch(entity -> entity.blockData == null),
                    "Fixed chest inventory must not also be copied into a falling entity");
            helper.assertTrue(level.getBlockState(sandPosition).isAir()
                            && level.getBlockState(gravelPosition).isAir(),
                    "Falling cells must not also remain as static world blocks");
            assertChestInventory(helper, chestPosition, Items.DIAMOND, 7);
            helper.assertTrue(level.getBlockEntity(chestPosition).saveWithFullMetadata(level.registryAccess())
                            .equals(chestData),
                    "Mixed settlement must preserve the fixed chest's complete block-entity data");
            assertNoItemDrops(helper, "Successful mixed settlement must preserve all blocks and inventory without drops");
        } finally {
            structure.asEntity().discard();
            level.getEntitiesOfClass(FallingBlockEntity.class, bounds).forEach(FallingBlockEntity::discard);
            clearCell(level, sandPosition);
            clearCell(level, gravelPosition);
            clearCell(level, chestPosition);
        }
    }

    private static void verifySnowGolemPlacementCommitsOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var bottom = helper.absolutePos(new BlockPos(10, 2, 10));
        var middle = bottom.above();
        var pumpkin = bottom.above(2);
        clearCell(level, bottom);
        clearCell(level, middle);
        clearCell(level, pumpkin);
        var bounds = new AABB(Vec3.atLowerCornerOf(bottom),
                Vec3.atLowerCornerOf(pumpkin.offset(1, 1, 1))).inflate(1);
        helper.assertTrue(level.getEntitiesOfClass(SnowGolem.class, bounds).isEmpty(),
                "Snow-golem fixture must start without an existing golem");
        var snapshot = new BlockStructureSnapshot(List.of(
                new BlockStructureSnapshot.BlockData(BlockPos.ZERO,
                        Blocks.SNOW_BLOCK.defaultBlockState(), null, BlockStructureSettlementMode.FIXED),
                new BlockStructureSnapshot.BlockData(new BlockPos(0, 1, 0),
                        Blocks.SNOW_BLOCK.defaultBlockState(), null, BlockStructureSettlementMode.FIXED),
                new BlockStructureSnapshot.BlockData(new BlockPos(0, 2, 0),
                        Blocks.CARVED_PUMPKIN.defaultBlockState(), null, BlockStructureSettlementMode.FIXED)
        ));
        var structure = spawn(helper, snapshot, bottom);
        try {
            var restored = structure.restoreToGrid();
            helper.assertTrue(restored.succeeded() && restored.affectedBlocks() == 3,
                    "Original onPlace consuming the committed blocks must still finish restoration: " + restored);
            helper.assertTrue(structure.asEntity().isRemoved(),
                    "A committed onPlace transformation must not resurrect its source structure");
            helper.assertTrue(level.getBlockState(bottom).isAir()
                            && level.getBlockState(middle).isAir()
                            && level.getBlockState(pumpkin).isAir(),
                    "Original pumpkin onPlace must consume both snow blocks and the pumpkin");
            helper.assertTrue(level.getEntitiesOfClass(SnowGolem.class, bounds).size() == 1,
                    "Restoration must execute original onPlace exactly once and create one snow golem");
            helper.assertTrue(level.getEntitiesOfClass(BlockStructureEntity.class, bounds).isEmpty(),
                    "The golem must not coexist with a retained source structure after callback consumption");
            helper.assertTrue(!structure.restoreToGrid().succeeded(),
                    "Repeating restoration on the consumed source must be rejected");
            helper.assertTrue(level.getEntitiesOfClass(SnowGolem.class, bounds).size() == 1,
                    "A repeated restore request must not duplicate the snow golem");
            assertNoItemDrops(helper, "Snow-golem assembly must not emit the consumed structure blocks as items");
        } finally {
            structure.asEntity().discard();
            level.getEntitiesOfClass(SnowGolem.class, bounds).forEach(SnowGolem::discard);
            clearCell(level, bottom);
            clearCell(level, middle);
            clearCell(level, pumpkin);
        }
    }

    /** Tests retained recovery data through the real save/load path, not process-crash atomicity. */
    private static void verifyPendingTransferSaveRoundTrip(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(2, 2, 10));
        var attempted = helper.absolutePos(new BlockPos(5, 2, 10));
        var sourceData = placeChest(helper, source, new ItemStack(Items.DIAMOND, 7))
                .saveWithFullMetadata(level.registryAccess());
        var structure = (BlockStructureEntity) capture(helper, source);
        var restored = new BlockStructureEntity(level);
        try {
            placeChest(helper, attempted, new ItemStack(Items.EMERALD, 11));
            var transfer = new BlockStructureTransferState(false);
            transfer.beforeWrite(level, attempted);
            structure.setPendingTransfer(transfer);
            clearCell(level, attempted);

            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            structure.saveWithoutId(output);
            var saved = output.buildResult();
            helper.assertTrue(saved.contains("academy_pending_transfer"),
                    "Pending recovery before-images must be present in the entity save data");
            structure.discard();
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            helper.assertTrue(restored.pendingTransfer() != null,
                    "Entity load must retain pending recovery state");
            assertSnapshotData(helper, restored, 0, sourceData,
                    "Entity save/load must retain the original structure inventory during recovery");

            var position = restored.position();
            restored.setVelocity(new Vec3(1.0, 1.0, 1.0));
            restored.tick();
            helper.assertTrue(restored.pendingTransfer() != null && !restored.isRemoved(),
                    "Pending recovery must remain quarantined instead of replaying or deleting its payload");
            helper.assertTrue(restored.position().equals(position) && restored.velocity().lengthSqr() == 0.0
                            && !restored.isPickable() && !restored.canBeCollidedWith(structure),
                    "Pending recovery must remain immobile, unpickable and non-colliding after load");
            helper.assertTrue(restored.restoreToGrid().status()
                            == BlockStructureRestoreResult.Status.RECOVERY_REQUIRED,
                    "A quarantined structure must refuse ordinary restore requests");
            var resavedOutput = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING, level.registryAccess());
            restored.saveWithoutId(resavedOutput);
            helper.assertTrue(saved.getCompound("academy_pending_transfer").orElseThrow().equals(
                            resavedOutput.buildResult().getCompound("academy_pending_transfer").orElseThrow()),
                    "Load, tick and rejected restore must preserve every pending before-image byte");
            helper.assertTrue(level.getBlockState(source).isAir() && level.getBlockState(attempted).isAir(),
                    "Quarantined data must not automatically recreate either container");
            assertNoItemDrops(helper, "Quarantined recovery must not emit either inventory as drops");
        } finally {
            structure.discard();
            restored.discard();
            clearCell(level, source);
            clearCell(level, attempted);
        }
    }

    private static void verifyChangedAfterImageBlocksRollback(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(8, 2, 10));
        var attempted = helper.absolutePos(new BlockPos(11, 2, 10));
        var sourceData = placeChest(helper, source, new ItemStack(Items.DIAMOND, 7))
                .saveWithFullMetadata(level.registryAccess());
        clearCell(level, attempted);
        var structure = (BlockStructureEntity) capture(helper, source);
        var restored = new BlockStructureEntity(level);
        ItemEntity movedItems = null;
        try {
            var transfer = new BlockStructureTransferState(false);
            structure.setPendingTransfer(transfer);
            var flags = QUIET_UPDATE_FLAGS | Block.UPDATE_SKIP_ON_PLACE
                    | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
            transfer.writeCell(level, attempted, Blocks.CHEST.defaultBlockState(), sourceData, flags);
            assertChestInventory(helper, attempted, Items.DIAMOND, 7);
            helper.assertTrue(transfer.matchesAfterImages(level),
                    "A completed real chest write must match its expected after-image");
            var chest = (ChestBlockEntity) level.getBlockEntity(attempted);
            var expectedAfterData = chest.saveWithFullMetadata(level.registryAccess());

            // Model a synchronous external callback moving the inventory out of the written container.
            var contents = chest.removeItemNoUpdate(0);
            chest.setChanged();
            helper.assertTrue(contents.is(Items.DIAMOND) && contents.getCount() == 7 && chest.isEmpty(),
                    "The callback fixture must move all seven real diamonds out of the target chest");
            movedItems = new ItemEntity(level, attempted.getX() + 0.5,
                    attempted.getY() + 1.0, attempted.getZ() + 0.5, contents);
            helper.assertTrue(level.addFreshEntity(movedItems),
                    "The callback fixture must place the moved inventory in a real item entity");
            var changedData = chest.saveWithFullMetadata(level.registryAccess());
            helper.assertTrue(!transfer.matchesAfterImages(level),
                    "An external inventory transfer must invalidate the recorded after-image");
            helper.assertTrue(!transfer.rollback(level, flags),
                    "Rollback must refuse an externally changed target instead of recreating its inventory");
            helper.assertTrue(level.getBlockEntity(attempted) == chest
                            && changedData.equals(chest.saveWithFullMetadata(level.registryAccess()))
                            && chest.isEmpty(),
                    "Rejected rollback must not rewrite or replace the changed world container");
            helper.assertTrue(structure.pendingTransfer() == transfer && structure.isTransferPending()
                            && !structure.isRemoved() && !structure.isPickable(),
                    "Rejected rollback must retain the pending payload in quarantine");
            helper.assertTrue(structure.restoreToGrid().status()
                            == BlockStructureRestoreResult.Status.RECOVERY_REQUIRED,
                    "The quarantined snapshot must not be reactivated through an ordinary restore");
            assertSnapshotData(helper, structure, 0, sourceData,
                    "Rejected rollback must retain the complete original snapshot as recovery data");

            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            structure.saveWithoutId(output);
            var saved = output.buildResult();
            var pendingData = saved.getCompound("academy_pending_transfer").orElseThrow();
            var pendingInput = TagValueInput.create(ProblemReporter.DISCARDING,
                    level.registryAccess(), pendingData);
            helper.assertTrue(pendingInput.getIntOr("image_version", 0) == 1
                            && pendingInput.getBooleanOr("rollback_blocked", false),
                    "Saved recovery data must retain its image format and blocked-rollback state");
            var cells = pendingInput.childrenListOrEmpty("attempted").iterator();
            helper.assertTrue(cells.hasNext(), "Rejected rollback must retain its cell images");
            var cell = cells.next();
            helper.assertTrue(!cells.hasNext()
                            && cell.read("state", BlockState.CODEC).orElseThrow().isAir()
                            && cell.read("block_entity", CompoundTag.CODEC).isEmpty(),
                    "The saved before-image must remain the original empty target cell");
            helper.assertTrue(cell.read("after_state", BlockState.CODEC).orElseThrow().is(Blocks.CHEST)
                            && cell.read("after_block_entity", CompoundTag.CODEC)
                            .filter(expectedAfterData::equals).isPresent()
                            && cell.getBooleanOr("write_completed", false)
                            && !cell.getBooleanOr("rolled_back", false),
                    "The saved after-image must retain the completed seven-diamond write, not the empty callback result");

            structure.discard();
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            var position = restored.position();
            restored.setVelocity(new Vec3(1.0, 0.0, 0.0));
            restored.tick();
            helper.assertTrue(restored.pendingTransfer() != null && restored.isTransferPending()
                            && !restored.pendingTransfer().rollback(level, flags)
                            && restored.position().equals(position) && restored.velocity().lengthSqr() == 0.0
                            && !restored.isPickable() && !restored.canBeCollidedWith(structure),
                    "Save/load must preserve rollback refusal and keep the obsolete inventory snapshot isolated");
            helper.assertTrue(restored.restoreToGrid().status()
                            == BlockStructureRestoreResult.Status.RECOVERY_REQUIRED,
                    "Loading pending images must not reopen ordinary restoration");
            var resaved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            restored.saveWithoutId(resaved);
            helper.assertTrue(pendingData.equals(
                            resaved.buildResult().getCompound("academy_pending_transfer").orElseThrow()),
                    "Blocked rollback, save/load and tick must preserve every before/after-image byte");
            helper.assertTrue(level.getBlockState(source).isAir() && level.getBlockEntity(attempted) == chest
                            && changedData.equals(chest.saveWithFullMetadata(level.registryAccess())),
                    "Recovery attempts must not recreate the source or alter the externally emptied target");
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(attempted).inflate(2.0));
            helper.assertTrue(items.size() == 1 && items.getFirst() == movedItems
                            && movedItems.getItem().is(Items.DIAMOND) && movedItems.getItem().getCount() == 7,
                    "The callback's item entity must remain the only active copy of the seven diamonds");
        } finally {
            if (movedItems != null) movedItems.discard();
            structure.discard();
            restored.discard();
            clearCell(level, source);
            clearCell(level, attempted);
        }
    }

    private static ChestBlockEntity placeChest(GameTestHelper helper, BlockPos position, ItemStack contents) {
        var level = helper.getLevel();
        clearCell(level, position);
        helper.assertTrue(level.setBlock(position, Blocks.CHEST.defaultBlockState(), QUIET_UPDATE_FLAGS),
                "Chest fixture must be placeable at " + position);
        var blockEntity = level.getBlockEntity(position);
        helper.assertTrue(blockEntity instanceof ChestBlockEntity, "Chest fixture must create a real container");
        var chest = (ChestBlockEntity) blockEntity;
        chest.setItem(0, contents.copy());
        chest.setChanged();
        return chest;
    }

    private static BlockStructure capture(GameTestHelper helper, BlockPos source) {
        var result = BlockStructureApi.capture(helper.getLevel(), List.of(source),
                BlockStructureCaptureOptions.defaults().withGravity(false));
        helper.assertTrue(result.succeeded(), "Fixture capture failed: " + result.status());
        return result.structure().orElseThrow();
    }

    private static BlockStructure spawn(GameTestHelper helper, BlockStructureSnapshot snapshot, BlockPos position) {
        var result = BlockStructureApi.spawn(helper.getLevel(), snapshot,
                Vec3.atLowerCornerOf(position), false, false);
        helper.assertTrue(result.isPresent(), "Fixture structure must be admitted to the real server level");
        return result.orElseThrow();
    }

    private static void assertChestInventory(
            GameTestHelper helper, BlockPos position, net.minecraft.world.item.Item item, int count
    ) {
        var blockEntity = helper.getLevel().getBlockEntity(position);
        helper.assertTrue(blockEntity instanceof ChestBlockEntity,
                "Expected a restored chest at " + position);
        var chest = (ChestBlockEntity) blockEntity;
        helper.assertTrue(chest.getItem(0).is(item) && chest.getItem(0).getCount() == count,
                "Restored chest must contain exactly " + count + " expected items in its original slot");
        for (var slot = 1; slot < chest.getContainerSize(); slot++) {
            helper.assertTrue(chest.getItem(slot).isEmpty(), "Restoration must not duplicate inventory into other slots");
        }
    }

    private static void assertSnapshotData(
            GameTestHelper helper, BlockStructure structure, int index, CompoundTag expected, String message
    ) {
        helper.assertTrue(structure.snapshot().blocks().get(index).blockEntityData()
                .filter(expected::equals).isPresent(), message);
    }

    private static void assertNoItemDrops(GameTestHelper helper, String message) {
        var origin = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(Vec3.atLowerCornerOf(origin),
                        Vec3.atLowerCornerOf(origin.offset(16, 16, 16)))).isEmpty(), message);
    }

    private static void clearCell(ServerLevel level, BlockPos position) {
        level.removeBlockEntity(position);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), QUIET_UPDATE_FLAGS);
    }

    private static final class StructureSafetyTest extends GameTestInstance {
        private static final MapCodec<StructureSafetyTest> CODEC =
                TestData.CODEC.xmap(StructureSafetyTest::new, StructureSafetyTest::info);

        private StructureSafetyTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            verify(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Block structure capture, settlement and inventory recovery safety");
        }
    }
}
