package org.academy.internal.common.structure.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBackend;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.NativeStructureRuntime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Real chunk, inventory, scheduled tick and handle-lifecycle verification without manual tickers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructureGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("native_structure_space_function");
    private static final BlockPos CHEST = new BlockPos(-8, 80, -8);
    private static final BlockPos OUTPUT = new BlockPos(-12, 80, -12);
    private static final BlockPos HOPPER = OUTPUT.above();
    private static final BlockPos FURNACE = HOPPER.above();
    private static final BlockPos POWER = new BlockPos(-6, 80, -12);
    private static final BlockPos REPEATER = POWER.east();
    private static final BlockPos LAMP = REPEATER.east();
    private static final List<BlockPos> FIXTURE_CELLS = List.of(
            CHEST, FURNACE, HOPPER, OUTPUT, POWER, REPEATER, LAMP, REPEATER.below());
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;

    private NativeStructureGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> NativeSpaceTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("native_structure_space"),
                new NativeSpaceTest(new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 6000, 0, true,
                        Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel host;
        private final StructureBackend backend;
        private final BlockPos hostDecoy;
        private StructureHandle a;
        private StructureHandle b;
        private CompletableFuture<Void> threadCheck;
        private boolean finished;

        private Scenario(GameTestHelper helper) {
            this.helper = helper;
            host = helper.getLevel();
            backend = StructureSpaces.backend(host.getServer());
            hostDecoy = helper.absolutePos(new BlockPos(3, 2, 3));
        }

        private void start() {
            helper.runAtTickTime(5990, () -> guarded(() -> {
                throw new AssertionError("Native structure scenario did not finish before its deadline");
            }));
            guarded(() -> {
                var fixtures = host.getServer().overworld().getDataStorage().computeIfAbsent(FixtureIndex.TYPE);
                var pose = StructurePose.identity(Vec3.atLowerCornerOf(hostDecoy.subtract(CHEST)));
                a = fixture(fixtures, 0, pose);
                b = fixture(fixtures, 1, pose);
                await(() -> a.lifecycle() == StructureLifecycle.ACTIVE
                        && b.lifecycle() == StructureLifecycle.ACTIVE, 150, this::prepare);
            });
        }

        private StructureHandle fixture(FixtureIndex fixtures, int index, StructurePose pose) {
            if (fixtures.references.size() > index) {
                return backend.load(fixtures.references.get(index)).orElseThrow(() ->
                        new AssertionError("Saved native GameTest fixture must remain loadable"));
            }
            var handle = backend.createEmpty(host, pose).orElseThrow(() ->
                    new AssertionError("Native GameTest needs two persistent storage slots"));
            fixtures.references.add(handle.reference());
            fixtures.setDirty();
            return handle;
        }

        private void prepare() {
            var levelA = storage(a);
            var levelB = storage(b);
            check(levelA != levelB && levelA != host && levelB != host,
                    "Each structure must have its own ServerLevel separate from the visible host");
            check(a.reference().dimension().equals(host.dimension().identifier()),
                    "Public reference must identify the host, not the storage dimension");
            check(!a.reference().equals(b.reference()), "Spaces must have distinct identities");
            for (var position : FIXTURE_CELLS) {
                clearFixtureCell(levelA, position);
                clearFixtureCell(levelB, position);
            }
            clearFixtureCell(host, hostDecoy);
            host.setBlock(hostDecoy, Blocks.CHEST.defaultBlockState(), QUIET_FLAGS);
            var decoy = (ChestBlockEntity) host.getBlockEntity(hostDecoy);
            check(decoy != null, "Host fixture must have a real chest");
            decoy.setItem(0, new ItemStack(Items.EMERALD, 11));
            decoy.setChanged();

            var originalTag = chestData(levelA, CHEST, Items.DIAMOND, 7).blockEntityData();
            check(originalTag != null, "Chest fixture must contain complete NBT");
            var replacement = new StructureBlockData(Blocks.CHEST.defaultBlockState(), originalTag);
            var expectedTag = originalTag.copy();
            originalTag.putString("id", "minecraft:furnace");
            check(expectedTag.equals(replacement.blockEntityData()),
                    "StructureBlockData must detach the constructor's mutable NBT");
            put(a, CHEST, replacement);
            put(b, CHEST, chestData(levelB, CHEST, Items.GOLD_INGOT, 13));
            var read = a.world().read(CHEST);
            var alias = read.blockEntityData();
            check(alias != null, "Live chest read must contain NBT");
            alias.putString("id", "minecraft:furnace");
            check(read.equals(a.world().read(CHEST)), "Returned NBT must not alias a live BE or prior read");

            var liveChest = (ChestBlockEntity) levelA.getBlockEntity(CHEST);
            check(liveChest != null && liveChest.getLevel() == levelA
                            && liveChest.getBlockPos().equals(CHEST),
                    "Negative local coordinates must address a real BE in its own level");
            liveChest.setItem(0, new ItemStack(Items.DIAMOND, 8));
            liveChest.setChanged();
            check(a.world().compareAndSet(CHEST, read, new StructureBlockData(Blocks.STONE.defaultBlockState()))
                            == StructureWriteResult.CONFLICT,
                    "Unchanged state with changed inventory must reject stale CAS");
            put(a, CHEST, chestData(levelA, CHEST, Items.DIAMOND, 7));
            check(a.world().compareAndSet(new BlockPos(-33, 80, 0),
                            new StructureBlockData(Blocks.AIR.defaultBlockState()), replacement)
                            == StructureWriteResult.OUT_OF_BOUNDS,
                    "CAS must reject positions outside the allocated local grid");

            var beforePoseChange = a.world().read(CHEST);
            var pose = StructurePose.fromYawAroundPivot(
                    Vec3.atLowerCornerOf(hostDecoy).add(21, 4, -7), 90, new Vec3(0.5, 0, 0.5));
            a.setPose(pose);
            check(a.pose().equals(pose) && a.world().read(CHEST).equals(beforePoseChange),
                    "Changing world pose must not move or renumber local contents");
            var center = Vec3.atCenterOf(CHEST);
            check(pose.worldToLocalPoint(pose.localToWorldPoint(center)).distanceToSqr(center) < 1.0E-12,
                    "Pose must round-trip negative local coordinates");
            verifyIsolation();
            verifyReferences();
            verifyBoundaries(levelA);
            verifyTravelRestriction();
            verifyThreadRejection();

            put(a, OUTPUT, new StructureBlockData(Blocks.CHEST.defaultBlockState()));
            put(a, HOPPER, new StructureBlockData(Blocks.HOPPER.defaultBlockState()
                    .setValue(HopperBlock.FACING, Direction.DOWN)));
            put(a, FURNACE, new StructureBlockData(Blocks.FURNACE.defaultBlockState()));
            var furnace = (FurnaceBlockEntity) levelA.getBlockEntity(FURNACE);
            check(furnace != null && levelA.getBlockEntity(HOPPER) instanceof HopperBlockEntity,
                    "Machine fixture must register real vanilla block entities");
            furnace.setItem(0, new ItemStack(Items.IRON_ORE, 2));
            furnace.setItem(1, new ItemStack(Items.COAL, 1));
            furnace.setChanged();
            put(a, REPEATER.below(), new StructureBlockData(Blocks.STONE.defaultBlockState()));
            put(a, LAMP, new StructureBlockData(Blocks.REDSTONE_LAMP.defaultBlockState()));
            put(a, REPEATER, new StructureBlockData(Blocks.REPEATER.defaultBlockState()
                    .setValue(RepeaterBlock.FACING, Direction.WEST).setValue(RepeaterBlock.DELAY, 4)));
            put(a, POWER, new StructureBlockData(Blocks.REDSTONE_BLOCK.defaultBlockState()));
            verifyTicking(levelA);
            verifyTicking(levelB);
            after(20, () -> check(levelA.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT),
                    "A delayed repeater must light its lamp through native scheduled ticks"));
            after(240, () -> {
                check(count((Container) levelA.getBlockEntity(OUTPUT), Items.IRON_INGOT) == 1,
                        "Native furnace and hopper must deliver the first iron ingot");
                verifyIsolation();
            });
            after(330, () -> {
                verifyTicking(levelA);
                check(levelA.players().isEmpty(), "Storage must remain playerless beyond the idle threshold");
                put(a, POWER, new StructureBlockData(Blocks.AIR.defaultBlockState()));
            });
            after(550, this::verifyAndReload);
        }

        private void verifyReferences() {
            var ref = a.reference();
            var forgedBackend = new StructureRef(ref.id(), ref.dimension(),
                    Identifier.fromNamespaceAndPath("academy", "not_native_space"), ref.generation());
            var forgedGeneration = new StructureRef(ref.id(), ref.dimension(), ref.backend(), ref.generation() + 1);
            var otherDimension = ref.dimension().equals(Identifier.withDefaultNamespace("overworld"))
                    ? Identifier.withDefaultNamespace("the_nether") : Identifier.withDefaultNamespace("overworld");
            var forgedDimension = new StructureRef(ref.id(), otherDimension, ref.backend(), ref.generation());
            for (var forged : List.of(forgedBackend, forgedGeneration, forgedDimension)) {
                check(backend.resolve(forged).isEmpty() && backend.load(forged).isEmpty()
                                && !backend.unload(forged),
                        "Forged backend/generation/host must neither resolve nor affect the genuine structure");
            }
            check(backend.resolve(ref).orElseThrow() == a,
                    "Rejected references must leave the original active handle intact");
        }

        private void verifyBoundaries(ServerLevel level) {
            for (var inside : List.of(new BlockPos(-32, level.getMinY(), -32),
                    new BlockPos(31, level.getMaxY(), 31))) {
                var previous = a.world().read(inside);
                put(a, inside, new StructureBlockData(Blocks.DIAMOND_BLOCK.defaultBlockState()));
                check(level.getBlockState(inside).is(Blocks.DIAMOND_BLOCK),
                        "Both inclusive local corners must remain writable and readable");
                put(a, inside, previous);
            }
            var outside = List.of(new BlockPos(-33, 80, 0), new BlockPos(32, 80, 0),
                    new BlockPos(0, 80, -33), new BlockPos(0, 80, 32),
                    new BlockPos(0, level.getMinY() - 1, 0), new BlockPos(0, level.getMaxY() + 1, 0));
            for (var position : outside) {
                check(level.getBlockState(position).is(Blocks.BARRIER)
                                && level.getFluidState(position).is(Fluids.EMPTY)
                                && level.getBlockEntity(position) == null,
                        "Out-of-bounds Level reads must expose the closed, empty boundary at " + position);
                check(!level.setBlock(position, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL, 512),
                        "The real four-argument Level write must reject an out-of-bounds cell");
                check(a.world().compareAndSet(position, new StructureBlockData(Blocks.AIR.defaultBlockState()),
                                new StructureBlockData(Blocks.STONE.defaultBlockState()))
                                == StructureWriteResult.OUT_OF_BOUNDS,
                        "Public CAS must enforce the same XZ and build-height bounds");
            }

            // Use the same out-of-bounds XZ numbers in the ordinary host. Restore the temporary
            // control immediately, and never replace an existing host block entity.
            var position = new BlockPos(32, host.getMaxY(), 32);
            var original = host.getBlockState(position);
            check(host.getBlockEntity(position) == null, "Host boundary control must not replace a block entity");
            try {
                check(host.setBlock(position, Blocks.WATER.defaultBlockState(), QUIET_FLAGS, 512)
                                && host.getBlockState(position).is(Blocks.WATER)
                                && host.getFluidState(position).is(Fluids.WATER),
                        "Ordinary host state/fluid reads and writes must ignore storage bounds");
                check(host.setBlock(position, Blocks.CHEST.defaultBlockState(), QUIET_FLAGS, 512)
                                && host.getBlockEntity(position) instanceof ChestBlockEntity,
                        "Ordinary host block entity lookup must ignore storage bounds");
            } finally {
                host.removeBlockEntity(position);
                host.setBlock(position, original, QUIET_FLAGS, 512);
            }
        }

        private void verifyTravelRestriction() {
            var entity = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
            check(entity != null, "Travel fixture must create a vanilla entity");
            entity.setNoGravity(true);
            entity.setPos(Vec3.atCenterOf(hostDecoy.above(3)));
            check(host.addFreshEntity(entity), "Host must admit the travel fixture");
            var id = entity.getUUID();
            try {
                for (var slot = 0; slot < NativeStructureDimensions.SLOT_COUNT; slot++) {
                    var target = host.getServer().getLevel(NativeStructureDimensions.slotKey(slot));
                    check(target != null, "Every reserved storage dimension must be registered");
                    var before = entity.position();
                    check(entity.teleport(new TeleportTransition(target, new Vec3(0.5, 80, 0.5),
                                    Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == null,
                            "Vanilla TeleportTransition must reject all reserved storage dimensions");
                    check(entity.level() == host && !entity.isRemoved()
                                    && entity.position().equals(before) && host.getEntity(id) == entity
                                    && target.getEntity(id) == null,
                            "Rejected travel must preserve the original entity and leave no destination copy");
                    var callbacks = new int[1];
                    var source = host.getServer().createCommandSourceStack().withLevel(host)
                            .withPermission(PermissionSet.ALL_PERMISSIONS).withSuppressedOutput()
                            .withCallback((success, result) -> callbacks[0]++);
                    host.getServer().getCommands().performPrefixedCommand(source,
                            "execute in " + target.dimension().identifier() + " run tp " + id + " 0.5 80 0.5");
                    check(callbacks[0] > 0 && entity.level() == host && !entity.isRemoved()
                                    && entity.position().equals(before) && host.getEntity(id) == entity
                                    && target.getEntity(id) == null,
                            "Executed dimension-qualified /tp must also preserve the rejected source entity");
                }
                var destination = entity.position().add(1, 0, 0);
                check(entity.teleport(new TeleportTransition(host, destination, Vec3.ZERO,
                                0, 0, TeleportTransition.DO_NOTHING)) == entity
                                && entity.position().distanceToSqr(destination) < 1.0E-12,
                        "Ordinary same-host vanilla teleport must remain functional");
            } finally {
                entity.discard();
                // Clean up a destination copy too if a broken travel guard allowed a cross-world copy.
                for (var slot = 0; slot < NativeStructureDimensions.SLOT_COUNT; slot++) {
                    var target = host.getServer().getLevel(NativeStructureDimensions.slotKey(slot));
                    if (target != null && target.getEntity(id) != null) target.getEntity(id).discard();
                }
            }
        }

        private void verifyThreadRejection() {
            var handle = a;
            var access = a.world();
            var ref = a.reference();
            var pose = a.pose();
            var air = new StructureBlockData(Blocks.AIR.defaultBlockState());
            threadCheck = CompletableFuture.runAsync(() -> {
                expectIllegalState(() -> access.read(CHEST));
                expectIllegalState(() -> access.compareAndSet(CHEST, air, air));
                expectIllegalState(handle::pose);
                expectIllegalState(() -> handle.setPose(pose));
                expectIllegalState(() -> backend.createEmpty(host, pose));
                expectIllegalState(() -> backend.resolve(ref));
                expectIllegalState(() -> backend.load(ref));
                expectIllegalState(() -> backend.unload(ref));
            });
            after(100, () -> {
                check(threadCheck.isDone(), "Off-thread calls must reject immediately without server dispatch");
                threadCheck.getNow(null);
            });
        }

        private void verifyAndReload() {
            var level = storage(a);
            check(level.players().isEmpty(), "No player may keep the storage world ticking for the test");
            verifyTicking(level);
            check(count((Container) level.getBlockEntity(OUTPUT), Items.IRON_INGOT) == 2,
                    "The second iron ingot must be processed after 300 playerless ticks");
            check(((Container) level.getBlockEntity(FURNACE)).getItem(0).isEmpty()
                            && ((Container) level.getBlockEntity(FURNACE)).getItem(2).isEmpty()
                            && ((Container) level.getBlockEntity(HOPPER)).isEmpty(),
                    "Two ores must become exactly two iron ingots without duplication in transit");
            check(!level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT),
                    "Redstone scheduled ticks must still switch off the lamp after the idle threshold");
            verifyIsolation();
            var furnace = (FurnaceBlockEntity) level.getBlockEntity(FURNACE);
            furnace.setItem(0, new ItemStack(Items.IRON_ORE, 1));
            furnace.setChanged();
            after(5, this::verifyPausedWorldAndReload);
        }

        private void verifyPausedWorldAndReload() {
            var level = storage(a);
            var furnace = (FurnaceBlockEntity) level.getBlockEntity(FURNACE);
            var cookingBeforePause = furnace.saveWithFullMetadata(level.registryAccess())
                    .getInt("cooking_time_spent").orElseThrow();
            check(cookingBeforePause > 0, "The paused fixture must have an actively cooking furnace");
            put(a, POWER, new StructureBlockData(Blocks.REDSTONE_BLOCK.defaultBlockState()));
            check(level.getBlockTicks().hasScheduledTick(REPEATER, Blocks.REPEATER)
                            && !level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT),
                    "The paused fixture must contain a real pending repeater tick");
            var oldHandle = a;
            var oldWorld = a.world();
            var ref = a.reference();
            var savedPose = a.pose();
            var savedChest = a.world().read(CHEST);
            var savedOutput = a.world().read(OUTPUT);
            check(NativeStructureRuntime.backend(host.getServer()).save(ref), "Explicit native save must succeed");
            check(backend.unload(ref), "Unloading must save and deactivate the native space");
            check(oldHandle.lifecycle() == StructureLifecycle.UNLOADED && backend.resolve(ref).isEmpty(),
                    "An unloaded reference must no longer resolve as active");
            expectIllegalState(oldHandle::pose);
            expectIllegalState(() -> oldWorld.read(CHEST));
            check(oldWorld.compareAndSet(CHEST, savedChest, savedChest) == StructureWriteResult.NOT_READY,
                    "Unloaded world access must reject writes");
            var frozenFurnace = furnace.saveWithFullMetadata(level.registryAccess());
            after(60, () -> {
                check(frozenFurnace.equals(level.getBlockEntity(FURNACE)
                                .saveWithFullMetadata(level.registryAccess())),
                        "Unloading must freeze real furnace cooking, fuel and inventory state");
                check(level.getBlockTicks().hasScheduledTick(REPEATER, Blocks.REPEATER)
                                && !level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT),
                        "Unloading must preserve pending vanilla ticks without executing them");
                a = backend.load(ref).orElseThrow(() -> new AssertionError("Saved structure must reload"));
                check(a != oldHandle && a.reference().equals(ref),
                        "Reload must preserve reference identity and allocate a fresh runtime handle");
                await(() -> a.lifecycle() == StructureLifecycle.ACTIVE, 150, () -> {
                    check(a.pose().equals(savedPose) && a.world().read(CHEST).equals(savedChest)
                                    && a.world().read(OUTPUT).equals(savedOutput),
                            "Normal save/reload must preserve pose, complete chest NBT and produced inventory");
                    expectIllegalState(() -> oldWorld.read(CHEST));
                    check(oldWorld.compareAndSet(CHEST, savedChest, savedChest) == StructureWriteResult.NOT_READY,
                            "Reloading must never reactivate the old world's write capability");
                    verifyTicking(storage(a));
                    verifyIsolation();
                    after(20, () -> {
                        check(level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT),
                                "Reloading must resume the pending vanilla repeater tick");
                        check(level.getBlockEntity(FURNACE).saveWithFullMetadata(level.registryAccess())
                                        .getInt("cooking_time_spent").orElseThrow() > cookingBeforePause,
                                "Reloading must resume the real furnace ticker");
                        verifyStorageFailureReports();
                    });
                });
            });
        }

        private void verifyStorageFailureReports() {
            var server = host.getServer();
            var activeA = storage(a);
            var activeB = storage(b);
            var failedLoad = server.getLevel(NativeStructureDimensions.slotKey(2));
            var failedSave = server.getLevel(NativeStructureDimensions.slotKey(3));
            check(failedLoad != null && failedSave != null && failedLoad != activeA && failedLoad != activeB
                            && failedSave != activeA && failedSave != activeB,
                    "I/O report checks reserve slots 2 and 3 without poisoning either persistent fixture");
            var previousLoadFailure = NativeStructureStorageErrors.failed(failedLoad);
            var previousSaveFailure = NativeStructureStorageErrors.failed(failedSave);
            var position = new ChunkPos(0, 0);
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_SYNTHETIC_IO_REPORTS_BEGIN slots=2,3");
            server.reportChunkSaveFailure(new IOException("Synthetic native-space GameTest host control"),
                    new RegionStorageInfo("native_space_gametest", host.dimension(), "chunk"), position);
            check(!NativeStructureStorageErrors.failed(host) && !NativeStructureStorageErrors.failed(activeA)
                            && !NativeStructureStorageErrors.failed(activeB)
                            && NativeStructureStorageErrors.failed(failedLoad) == previousLoadFailure
                            && NativeStructureStorageErrors.failed(failedSave) == previousSaveFailure,
                    "An ordinary host I/O report must not latch any native storage dimension");

            server.reportChunkLoadFailure(new IOException("Synthetic native-space GameTest load report"),
                    new RegionStorageInfo("native_space_gametest", failedLoad.dimension(), "chunk"), position);
            check(NativeStructureStorageErrors.failed(failedLoad)
                            && NativeStructureStorageErrors.failed(failedSave) == previousSaveFailure
                            && !NativeStructureStorageErrors.failed(activeA) && !NativeStructureStorageErrors.failed(activeB),
                    "The real load-report entry must latch only its native dimension");
            var storageInfo = new RegionStorageInfo("native_space_gametest", failedSave.dimension(), "entities");
            var report = CompletableFuture.runAsync(() -> server.reportChunkSaveFailure(
                    new IOException("Synthetic native-space GameTest asynchronous save report"), storageInfo, position));
            await(report::isDone, 4000, () -> {
                report.getNow(null);
                check(NativeStructureStorageErrors.failed(failedLoad) && NativeStructureStorageErrors.failed(failedSave)
                                && !NativeStructureStorageErrors.failed(host) && !NativeStructureStorageErrors.failed(activeA)
                                && !NativeStructureStorageErrors.failed(activeB),
                        "An asynchronous entity-storage report must latch its slot without harming live fixtures");
                check(backend.createEmpty(host, StructurePose.identity(Vec3.ZERO)).isEmpty(),
                        "Allocation must reject both errored unclaimed slots while fixture slots remain owned");
                after(10, () -> {
                    check(NativeStructureStorageErrors.failed(failedLoad) && NativeStructureStorageErrors.failed(failedSave),
                            "I/O failure latches must remain set for the rest of this server session");
                    verifyIsolation();
                    AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_SYNTHETIC_IO_REPORTS_PASSED slots=2,3");
                    finish();
                });
            });
        }

        private void verifyIsolation() {
            assertInventory(storage(a), CHEST, Items.DIAMOND, 7);
            assertInventory(storage(b), CHEST, Items.GOLD_INGOT, 13);
            assertInventory(host, hostDecoy, Items.EMERALD, 11);
            for (var pos : List.of(FURNACE, HOPPER, OUTPUT, POWER, REPEATER, LAMP)) {
                check(b.world().read(pos).state().isAir(), "Native operations must not leak to another space");
            }
        }

        private void assertInventory(ServerLevel level, BlockPos position, Item item, int amount) {
            check(level.getBlockEntity(position) instanceof ChestBlockEntity,
                    "Expected an isolated real chest at " + position);
            var chest = (ChestBlockEntity) level.getBlockEntity(position);
            check(count(chest, item) == amount && chest.getItem(0).is(item)
                            && chest.getItem(0).getCount() == amount,
                    "Space/host isolation must preserve the exact fixture inventory");
            for (var slot = 1; slot < chest.getContainerSize(); slot++) {
                check(chest.getItem(slot).isEmpty(), "Inventory must not be duplicated into additional slots");
            }
        }

        private void verifyTicking(ServerLevel level) {
            var chunk = level.getChunkSource().getChunkNow(CHEST.getX() >> 4, CHEST.getZ() >> 4);
            check(chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                            && level.isPositionTickingWithEntitiesLoaded(ChunkPos.pack(CHEST)),
                    "An ACTIVE native space must retain a real block-ticking chunk with loaded entities");
        }

        private ServerLevel storage(StructureHandle handle) {
            return NativeStructureRuntime.backend(host.getServer()).storageLevel(handle.reference())
                    .orElseThrow(() -> new AssertionError("Active native space must have a storage ServerLevel"));
        }

        private void put(StructureHandle handle, BlockPos position, StructureBlockData data) {
            check(handle.world().compareAndSet(position, handle.world().read(position), data)
                            == StructureWriteResult.APPLIED,
                    "Fixture CAS must apply at " + position);
        }

        private void await(BooleanSupplier ready, int remaining, Runnable action) {
            if (ready.getAsBoolean()) {
                action.run();
            } else {
                check(remaining > 0, "Native structure scenario condition did not become ready before its deadline");
                after(1, () -> await(ready, remaining - 1, action));
            }
        }

        private void after(int ticks, Runnable action) {
            helper.runAfterDelay(ticks, () -> guarded(action));
        }

        private void guarded(Runnable action) {
            if (finished) return;
            try {
                action.run();
            } catch (Throwable failure) {
                finished = true;
                cleanup();
                helper.fail("Native structure space: " + failure);
            }
        }

        private void finish() {
            check(backend.unload(a.reference()) && backend.unload(b.reference()),
                    "Successful test must unload both spaces without deleting persistent content");
            finished = true;
            clearFixtureCell(host, hostDecoy);
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_SPACE_GAMETEST_PASSED a={} b={} nativeTicks=550",
                    a.reference(), b.reference());
            helper.succeed();
        }

        private void cleanup() {
            for (var handle : new StructureHandle[]{a, b}) {
                if (handle == null) continue;
                try {
                    backend.unload(handle.reference());
                } catch (RuntimeException failure) {
                    AcademyCraft.LOGGER.error("Cannot unload native GameTest fixture {}", handle.reference(), failure);
                }
            }
            clearFixtureCell(host, hostDecoy);
        }

        private void check(boolean condition, String message) {
            helper.assertTrue(condition, message);
        }
    }

    private static StructureBlockData chestData(ServerLevel level, BlockPos position, Item item, int count) {
        var state = Blocks.CHEST.defaultBlockState();
        var chest = new ChestBlockEntity(position, state);
        chest.setItem(0, new ItemStack(item, count));
        return new StructureBlockData(state, chest.saveWithFullMetadata(level.registryAccess()));
    }

    private static int count(Container container, Item item) {
        if (container == null) throw new AssertionError("Expected a real container");
        var count = 0;
        for (var slot = 0; slot < container.getContainerSize(); slot++) {
            var stack = container.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void clearFixtureCell(ServerLevel level, BlockPos position) {
        level.removeBlockEntity(position);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected immediate IllegalStateException");
    }

    /** Test-owned references keep reruns from consuming more persistent slots. */
    private static final class FixtureIndex extends SavedData {
        private static final Codec<StructureRef> REF_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(StructureRef::id),
                Identifier.CODEC.fieldOf("dimension").forGetter(StructureRef::dimension),
                Identifier.CODEC.fieldOf("backend").forGetter(StructureRef::backend),
                Codec.LONG.fieldOf("generation").forGetter(StructureRef::generation)
        ).apply(instance, StructureRef::new));
        private static final Codec<FixtureIndex> CODEC = REF_CODEC.listOf()
                .xmap(FixtureIndex::new, index -> List.copyOf(index.references));
        private static final SavedDataType<FixtureIndex> TYPE = new SavedDataType<>(
                AcademyCraft.academy("native_structure_gametest_fixtures"), FixtureIndex::new, CODEC);
        private final List<StructureRef> references;

        private FixtureIndex() {
            this(List.of());
        }

        private FixtureIndex(List<StructureRef> references) {
            this.references = new ArrayList<>(references);
        }
    }

    private static final class NativeSpaceTest extends GameTestInstance {
        private static final MapCodec<NativeSpaceTest> CODEC =
                TestData.CODEC.xmap(NativeSpaceTest::new, NativeSpaceTest::info);

        private NativeSpaceTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            new Scenario(helper).start();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Independent native structure chunks, ticks, isolation and save lifecycle");
        }
    }
}
