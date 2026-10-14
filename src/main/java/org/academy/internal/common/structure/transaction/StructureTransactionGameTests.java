package org.academy.internal.common.structure.transaction;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBackend;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.NativeStructureRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Runs in its own isolated universe: the staged capture consumes permanent native slots, so the
 * fixture only registers under its dedicated system property. It covers the complete plain-block
 * commit, explicit pre-write rejections, and both restart recovery directions (rollback before
 * the decision, forward completion after it) against the real coordinator and journal.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class StructureTransactionGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("structure_transaction_function");
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;
    private static final BlockPos CAPTURE_BASE = new BlockPos(2, 2, 2);
    private static final BlockPos REJECTION_CELL = new BlockPos(6, 2, 2);
    private static final BlockPos SCHEDULED_CELL = new BlockPos(7, 2, 2);
    private static final BlockPos ROLLBACK_BASE = new BlockPos(2, 2, 6);
    private static final BlockPos FORWARD_BASE = new BlockPos(6, 2, 6);
    private static final BlockPos RANDOM_TICK_BASE = new BlockPos(10, 2, 10);

    private StructureTransactionGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> TransactionTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.structureTransactionTests")) return;
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("structure_transactions"),
                new TransactionTest(new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                        1200, 0, true, Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel host;
        private final MinecraftServer server;
        private final StructureBackend backend;
        private final NativeStructureRuntime runtime;
        private UUID captureId;
        private StructureRef captureRef;
        private final List<BlockPos> captureCells = new ArrayList<>();
        private final List<BlockState> captureStates = new ArrayList<>();
        private StructureRef rollbackRef;
        private final List<BlockPos> rollbackCells = new ArrayList<>();
        private UUID rollbackId;
        private StructureRef forwardRef;
        private final List<BlockPos> forwardCells = new ArrayList<>();
        private UUID forwardId;
        private UUID randomTickId;
        private StructureRef randomTickRef;
        private final List<BlockPos> randomTickCells = new ArrayList<>();
        private final List<BlockState> randomTickStates = new ArrayList<>();
        private StructureRef probeA;
        private StructureRef probeB;
        private StructureRef probeC;

        private Scenario(GameTestHelper helper) {
            this.helper = helper;
            this.host = helper.getLevel();
            this.server = host.getServer();
            this.backend = StructureSpaces.backend(server);
            this.runtime = NativeStructureRuntime.backend(server);
        }

        private void beginCapture() {
            for (int index = 0; index < 3; index++) {
                var position = hostPos(CAPTURE_BASE.offset(index, 0, 0));
                host.setBlock(position, Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
                captureCells.add(position);
                captureStates.add(host.getBlockState(position));
            }
            var result = StructureTransactions.capture(host,
                    new StructureCaptureRequest(captureCells, 16, 8, StructureCaptureRequest.ALLOW));
            require(result.pending(),
                    "Plain capture must start a staged transaction: " + result.status() + "/" + result.detail());
            captureId = result.transactionId();
        }

        private void captureCommitted() {
            var result = captureResult(captureId);
            require(result.succeeded(), "Capture still in flight: " + result.status() + "/" + result.detail());
            captureRef = orFail(result.structure(), "Committed capture must expose its structure");
        }

        private void verifyCaptureCommit() {
            var handle = orFail(backend.resolve(captureRef), "Committed target must stay resolvable");
            require(handle.lifecycle() == StructureLifecycle.ACTIVE, "Committed target must stay active");
            for (int index = 0; index < captureCells.size(); index++) {
                require(host.getBlockState(captureCells.get(index)).isAir(),
                        "Committed capture must remove its source block");
                var offset = captureCells.get(index).subtract(origin(captureCells));
                require(handle.world().read(offset).state().equals(captureStates.get(index)),
                        "Target cell must match the captured source state");
            }
            require(!journalExists(captureId), "Committed capture must recycle its journal record");
            var area = new AABB(Vec3.atLowerCornerOf(origin(captureCells)),
                    Vec3.atLowerCornerOf(origin(captureCells).offset(4, 4, 4)));
            require(host.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                    "Committed capture must not leave item drops");
            var replay = StructureTransactions.capture(host,
                    new StructureCaptureRequest(captureCells, 16, 8, StructureCaptureRequest.ALLOW));
            require(replay.status() == StructureCaptureResult.Status.REJECTED
                            && replay.detail().equals("not_in_plain_block_whitelist"),
                    "Removed source cells must never be captured again");
        }

        private void verifyRejections() {
            verifyRejected(Blocks.CHEST.defaultBlockState(), StructureCaptureRequest.ALLOW,
                    StructureCaptureResult.Status.REJECTED, "block_entity_not_supported");
            // Randomly ticking alone is admitted now; non-whitelisted farmland and vegetation
            // still fail closed on the curated set.
            verifyRejected(Blocks.FARMLAND.defaultBlockState(), StructureCaptureRequest.ALLOW,
                    StructureCaptureResult.Status.REJECTED, "not_in_plain_block_whitelist");
            verifyRejected(Blocks.SHORT_GRASS.defaultBlockState(), StructureCaptureRequest.ALLOW,
                    StructureCaptureResult.Status.REJECTED, "not_in_plain_block_whitelist");
            verifyRejected(Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.WATERLOGGED, true), StructureCaptureRequest.ALLOW,
                    StructureCaptureResult.Status.REJECTED, "waterlogged_not_supported");
            verifyRejected(Blocks.WATER.defaultBlockState(), StructureCaptureRequest.ALLOW,
                    StructureCaptureResult.Status.REJECTED, "fluid_not_supported");
            verifyRejected(Blocks.BEDROCK.defaultBlockState(), StructureCaptureRequest.ALLOW,
                    StructureCaptureResult.Status.REJECTED, "not_in_plain_block_whitelist");
            verifyRejected(Blocks.OAK_PLANKS.defaultBlockState(), (level, position) -> false,
                    StructureCaptureResult.Status.PERMISSION_DENIED, "permission_denied");
            verifyScheduledTickRejection();
            var empty = StructureTransactions.capture(host,
                    new StructureCaptureRequest(List.of(), 16, 8, StructureCaptureRequest.ALLOW));
            require(empty.status() == StructureCaptureResult.Status.EMPTY, "Empty selections must be rejected");
            require(!journalExists(empty.transactionId()), "Rejected captures must not journal");
            var tooLarge = StructureTransactions.capture(host, new StructureCaptureRequest(captureCells, 1, 8,
                    StructureCaptureRequest.ALLOW));
            require(tooLarge.status() == StructureCaptureResult.Status.TOO_LARGE,
                    "Block budgets must be enforced before any write");
            var span = StructureTransactions.capture(host, new StructureCaptureRequest(
                    List.of(hostPos(CAPTURE_BASE), hostPos(CAPTURE_BASE.offset(0, 0, 3))),
                    16, 2, StructureCaptureRequest.ALLOW));
            require(span.status() == StructureCaptureResult.Status.SPAN_TOO_LARGE,
                    "Span budgets must be enforced before any write");
        }

        /**
         * Randomly ticking surface land is admitted by policy: the fixture commits grass over
         * dirt through the real staged transaction, then releases the slot again so the later
         * capacity fixture still sees exactly one free container slot.
         */
        private void beginRandomTickCapture() {
            // Random growth could mutate the source between snapshot and copy; the fixture's own
            // isolation makes the disabled rule safe to keep.
            require(Blocks.GRASS_BLOCK.defaultBlockState().isRandomlyTicking(),
                    "Random-tick fixture must use a randomly ticking block");
            host.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
            var grass = hostPos(RANDOM_TICK_BASE);
            var dirt = hostPos(RANDOM_TICK_BASE.below());
            host.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), QUIET_FLAGS);
            host.setBlock(dirt, Blocks.DIRT.defaultBlockState(), QUIET_FLAGS);
            randomTickCells.add(dirt);
            randomTickCells.add(grass);
            randomTickStates.add(host.getBlockState(dirt));
            randomTickStates.add(host.getBlockState(grass));
            var result = StructureTransactions.capture(host,
                    new StructureCaptureRequest(randomTickCells, 16, 8, StructureCaptureRequest.ALLOW));
            require(result.pending(), "Random-tick land capture must start a staged transaction: "
                    + result.status() + "/" + result.detail());
            randomTickId = result.transactionId();
        }

        private void randomTickCaptureCommitted() {
            var result = captureResult(randomTickId);
            require(result.succeeded(), "Random-tick land capture still in flight: "
                    + result.status() + "/" + result.detail());
            randomTickRef = orFail(result.structure(),
                    "Committed random-tick capture must expose its structure");
        }

        private void verifyRandomTickCapture() {
            var handle = orFail(backend.resolve(randomTickRef), "Random-tick target must stay resolvable");
            require(handle.lifecycle() == StructureLifecycle.ACTIVE, "Random-tick target must stay active");
            var origin = origin(randomTickCells);
            for (int index = 0; index < randomTickCells.size(); index++) {
                var position = randomTickCells.get(index);
                require(host.getBlockState(position).isAir(),
                        "Committed random-tick capture must remove its source block at " + position);
                require(handle.world().read(position.subtract(origin)).state().equals(randomTickStates.get(index)),
                        "Random-tick target cell must match the captured source state at " + position);
            }
            require(!journalExists(randomTickId), "Committed random-tick capture must recycle its journal record");
            for (int index = 0; index < randomTickCells.size(); index++) {
                var offset = randomTickCells.get(index).subtract(origin);
                require(handle.world().compareAndSet(offset, new StructureBlockData(randomTickStates.get(index)),
                                new StructureBlockData(Blocks.AIR.defaultBlockState())) == StructureWriteResult.APPLIED,
                        "Random-tick fixture must empty its committed target");
            }
            require(runtime.unload(randomTickRef), "Random-tick fixture must unload its emptied target");
            require(runtime.discardEmpty(randomTickRef), "Random-tick fixture must release its permanent slot");
            host.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 3, server);
        }

        private void verifyScheduledTickRejection() {
            var scheduled = hostPos(SCHEDULED_CELL);
            host.setBlock(scheduled, Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
            host.getBlockTicks().schedule(new ScheduledTick<>(Blocks.STONE, scheduled,
                    host.getGameTime() + 20, 0));
            require(host.getBlockTicks().hasScheduledTick(scheduled, Blocks.STONE),
                    "Scheduled tick fixture must be observable");
            var result = StructureTransactions.capture(host,
                    new StructureCaptureRequest(List.of(scheduled), 16, 8, StructureCaptureRequest.ALLOW));
            require(result.status() == StructureCaptureResult.Status.REJECTED
                            && result.detail().equals("scheduled_tick_pending")
                            && host.getBlockState(scheduled).is(Blocks.STONE),
                    "Pending scheduled ticks must be rejected explicitly, not dropped");
            host.setBlock(scheduled, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
        }

        private void verifyRejected(BlockState state, BiPredicate<ServerLevel, BlockPos> permission,
                                    StructureCaptureResult.Status status, String expectedDetail) {
            var position = hostPos(REJECTION_CELL);
            host.setBlock(position, state, QUIET_FLAGS);
            var placed = host.getBlockState(position);
            var result = StructureTransactions.capture(host,
                    new StructureCaptureRequest(List.of(position), 16, 8, permission));
            require(result.status() == status && result.detail().equals(expectedDetail),
                    "Expected " + status + "/" + expectedDetail + ", got " + result.status() + "/" + result.detail());
            require(host.getBlockState(position).equals(placed),
                    "Rejected captures must preserve the source block");
            require(!journalExists(result.transactionId()), "Rejected captures must not journal");
            host.removeBlockEntity(position);
            host.setBlock(position, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
        }

        private void allocateRollbackTarget() {
            for (int index = 0; index < 2; index++) {
                var position = hostPos(ROLLBACK_BASE.offset(0, 0, index));
                host.setBlock(position, Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
                rollbackCells.add(position);
            }
            rollbackId = UUID.randomUUID();
            rollbackRef = allocateTarget(rollbackCells, rollbackId);
        }

        private void allocateForwardTarget() {
            for (int index = 0; index < 2; index++) {
                var position = hostPos(FORWARD_BASE.offset(index, 0, 0));
                host.setBlock(position, Blocks.OAK_LOG.defaultBlockState(), QUIET_FLAGS);
                forwardCells.add(position);
            }
            forwardId = UUID.randomUUID();
            forwardRef = allocateTarget(forwardCells, forwardId);
        }

        private StructureRef allocateTarget(List<BlockPos> cells, UUID transactionId) {
            var origin = origin(cells);
            return orFail(NativeStructureRuntime.backend(server).createEmpty(host,
                            StructurePose.identity(Vec3.atLowerCornerOf(origin))),
                    "Recovery fixture requires an unused native slot " + transactionId).reference();
        }

        private void seedRollbackJournal() {
            writeSeededContent(rollbackRef, rollbackCells);
            persistSeededRecord(rollbackId, rollbackRef, rollbackCells, StructureTransactionJournal.Stage.COPIED);
        }

        private void seedForwardJournal() {
            writeSeededContent(forwardRef, forwardCells);
            persistSeededRecord(forwardId, forwardRef, forwardCells, StructureTransactionJournal.Stage.COMMIT_DECIDED);
        }

        /**
         * Seeds real copied content through the target's own write path and then a durable journal
         * record, exactly as an interrupted session would have left them: source intact, target
         * filled, no commit outcome recorded for the rollback case and a decided commit for the
         * forward case.
         */
        private void writeSeededContent(StructureRef reference, List<BlockPos> cells) {
            var handle = orFail(backend.resolve(reference), "Seeded target must stay resolvable");
            var origin = origin(cells);
            for (var position : cells) {
                var state = host.getBlockState(position);
                var write = handle.world().compareAndSet(position.subtract(origin),
                        new StructureBlockData(Blocks.AIR.defaultBlockState()), new StructureBlockData(state));
                require(write == StructureWriteResult.APPLIED, "Seeded target write must apply: " + write);
            }
        }

        private void persistSeededRecord(UUID transactionId, StructureRef reference, List<BlockPos> cells,
                                         StructureTransactionJournal.Stage stage) {
            var journalCells = new ArrayList<StructureTransactionJournal.Cell>(cells.size());
            for (var position : cells) {
                journalCells.add(new StructureTransactionJournal.Cell(position,
                        encodeState(host.getBlockState(position))));
            }
            try {
                newJournal().persist(new StructureTransactionJournal.Record(transactionId, NativeStructureRuntime.ID,
                        host.dimension().identifier(), origin(cells), stage, reference.id(), journalCells, null));
            } catch (IOException failure) {
                fail("Recovery fixture journal must persist: " + failure);
            }
        }

        private void rollbackTargetActive() {
            require(orFail(backend.resolve(rollbackRef), "Rollback target vanished")
                    .lifecycle() == StructureLifecycle.ACTIVE, "Rollback target must activate before seeding");
        }

        private void forwardTargetActive() {
            require(orFail(backend.resolve(forwardRef), "Forward target vanished")
                    .lifecycle() == StructureLifecycle.ACTIVE, "Forward target must activate before seeding");
        }

        private void rollbackResolved() {
            require(!journalExists(rollbackId), "Rollback recovery did not resolve its journal record");
        }

        private void verifyRollbackRecovery() {
            for (var position : rollbackCells) {
                require(host.getBlockState(position).is(Blocks.STONE),
                        "Pre-decision recovery must keep every source block");
            }
            require(backend.resolve(rollbackRef).isEmpty(),
                    "Rolled-back target must be deactivated and released, not left interactive");
        }

        private void forwardResolved() {
            require(!journalExists(forwardId), "Forward recovery did not resolve its journal record");
        }

        private void verifyForwardRecovery() {
            var storage = orFail(runtime.storageLevel(forwardRef), "Forward fixture storage must stay addressable");
            for (var position : forwardCells) {
                require(host.getBlockState(position).isAir(),
                        "Decided recovery must complete the committed source removal");
                require(storage.getBlockState(position.subtract(origin(forwardCells))).is(Blocks.OAK_LOG),
                        "Decided recovery must preserve the copied target content");
            }
        }

        private void allocateRecyclingProbes() {
            // The rolled-back target released its slot, so two more allocations must still fit.
            probeA = orFail(runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)),
                    "Recycling probe A requires the rolled-back slot to be reusable").reference();
            probeB = orFail(runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)),
                    "Recycling probe B requires a free container slot").reference();
        }

        private void recyclingProbesActive() {
            require(orFail(backend.resolve(probeA), "Probe A vanished").lifecycle() == StructureLifecycle.ACTIVE
                            && orFail(backend.resolve(probeB), "Probe B vanished").lifecycle()
                            == StructureLifecycle.ACTIVE,
                    "Recycling probes must activate before release");
        }

        private void verifyCapacityRecycling() {
            require(runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)).isEmpty(),
                    "The container must not exceed its permanent slot budget");
            require(runtime.unload(probeA), "An activated empty probe must unload before release");
            require(runtime.discardEmpty(probeA), "An unloaded empty structure must release its slot");
            probeC = orFail(runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)),
                    "A released slot must be reusable for a new structure").reference();
            require(backend.resolve(probeC).isPresent(), "The reused slot must hold a resolvable structure");
        }

        private void cleanup() {
            for (var position : captureCells) host.setBlock(position, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
            for (var position : rollbackCells) host.setBlock(position, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
        }

        private StructureCaptureResult captureResult(UUID transactionId) {
            return orFail(StructureTransactions.poll(server, transactionId),
                    "Capture result vanished: " + transactionId);
        }

        private CompoundTag encodeState(BlockState state) {
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, host.registryAccess());
            output.store("state", BlockState.CODEC, state);
            return output.buildResult();
        }

        private boolean journalExists(UUID transactionId) {
            return Files.exists(journalPath().resolve(transactionId + ".dat"));
        }

        private Path journalPath() {
            return server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/academy/" + StructureTransactionJournal.DIRECTORY_NAME);
        }

        private StructureTransactionJournal newJournal() {
            try {
                return new StructureTransactionJournal(journalPath());
            } catch (IOException failure) {
                fail("Fixture journal must open: " + failure);
                throw new IllegalStateException("unreachable");
            }
        }

        private BlockPos hostPos(BlockPos relative) {
            return helper.absolutePos(relative);
        }

        private static BlockPos origin(List<BlockPos> positions) {
            int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
            for (var position : positions) {
                x = Math.min(x, position.getX());
                y = Math.min(y, position.getY());
                z = Math.min(z, position.getZ());
            }
            return new BlockPos(x, y, z);
        }

        private void require(boolean condition, String message) {
            helper.assertTrue(condition, message);
        }

        private void fail(String message) {
            helper.assertTrue(false, message);
        }

        private <T> T orFail(Optional<T> value, String message) {
            helper.assertTrue(value.isPresent(), message);
            return value.orElseThrow();
        }
    }

    private static final class TransactionTest extends GameTestInstance {
        private static final MapCodec<TransactionTest> CODEC =
                TestData.CODEC.xmap(TransactionTest::new, TransactionTest::info);

        private TransactionTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            var scenario = new Scenario(helper);
            helper.startSequence()
                    .thenExecute(scenario::beginCapture)
                    .thenWaitUntil(scenario::captureCommitted)
                    .thenExecute(scenario::verifyCaptureCommit)
                    .thenExecute(scenario::verifyRejections)
                    .thenExecute(scenario::beginRandomTickCapture)
                    .thenWaitUntil(scenario::randomTickCaptureCommitted)
                    .thenExecute(scenario::verifyRandomTickCapture)
                    .thenExecute(scenario::allocateRollbackTarget)
                    .thenWaitUntil(scenario::rollbackTargetActive)
                    .thenExecute(scenario::seedRollbackJournal)
                    .thenExecute(() -> StructureCaptureCoordinator.recoverNow(helper.getLevel().getServer()))
                    .thenWaitUntil(scenario::rollbackResolved)
                    .thenExecute(scenario::verifyRollbackRecovery)
                    .thenExecute(scenario::allocateForwardTarget)
                    .thenWaitUntil(scenario::forwardTargetActive)
                    .thenExecute(scenario::seedForwardJournal)
                    .thenExecute(() -> StructureCaptureCoordinator.recoverNow(helper.getLevel().getServer()))
                    .thenWaitUntil(scenario::forwardResolved)
                    .thenExecute(scenario::verifyForwardRecovery)
                    .thenExecute(scenario::allocateRecyclingProbes)
                    .thenWaitUntil(scenario::recyclingProbesActive)
                    .thenExecute(scenario::verifyCapacityRecycling)
                    .thenExecute(scenario::cleanup)
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Staged structure capture, rejection and restart recovery");
        }
    }
}
