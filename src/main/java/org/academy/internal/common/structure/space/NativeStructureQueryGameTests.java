package org.academy.internal.common.structure.space;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.Unpooled;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBackend;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructurePresentationStatus;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.internal.common.network.NativeStructureUsePacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Run in its own test universe: persistent fixtures consume at most two permanent slots. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructureQueryGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("native_structure_query_function");
    private static final BlockPos CELL = new BlockPos(-8, 80, -8);
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;

    private NativeStructureQueryGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> QueryTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.nativeStructureQueryTests")) return;
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("native_structure_query"),
                new QueryTest(new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                        1000, 0, true, Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel host;
        private final StructureBackend backend;
        private final Vec3 center;
        private StructureHandle a;
        private StructureHandle b;
        private BlockPos hostBlocker;
        private boolean finished;

        private Scenario(GameTestHelper helper) {
            this.helper = helper;
            host = helper.getLevel();
            backend = StructureSpaces.backend(host.getServer());
            var origin = helper.absolutePos(new BlockPos(4, 0, 4));
            center = new Vec3(origin.getX() + 0.5, host.getMaxY() - 20.5, origin.getZ() + 0.5);
        }

        private void start() {
            helper.runAtTickTime(990, () -> guarded(() -> {
                throw new AssertionError("Native structure query did not finish before its deadline");
            }));
            guarded(() -> {
                var fixtures = host.getServer().overworld().getDataStorage().computeIfAbsent(FixtureIndex.TYPE);
                a = fixture(fixtures, 0);
                b = fixture(fixtures, 1);
                await(() -> active(a) && active(b), 200, this::verifyShapesAndRevisions);
            });
        }

        private StructureHandle fixture(FixtureIndex fixtures, int index) {
            if (fixtures.references.size() > index) {
                return backend.load(fixtures.references.get(index)).orElseThrow(() ->
                        new AssertionError("Saved query fixture must remain loadable"));
            }
            var handle = backend.createEmpty(host, identityAt(center)).orElseThrow(() ->
                    new AssertionError("Query test requires its own universe with two available slots"));
            fixtures.references.add(handle.reference());
            fixtures.setDirty();
            return handle;
        }

        private void verifyShapesAndRevisions() {
            clearPresentationFixture();
            clearCell(a);
            clearCell(b);
            var sine = Math.sin(0.63 / 2.0) / Math.sqrt(14.0);
            var rotation = new StructurePose(Vec3.ZERO, sine, 2 * sine, 3 * sine, Math.cos(0.63 / 2.0));
            var pose = new StructurePose(center.subtract(rotation.localToWorldPoint(Vec3.atCenterOf(CELL))),
                    rotation.rotationX(), rotation.rotationY(), rotation.rotationZ(), rotation.rotationW());
            a.setPose(pose);
            b.setPose(identityAt(center.add(20, 0, 0)));
            var stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
                    .setValue(StairBlock.HALF, Half.BOTTOM).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
            check(storage(a).setBlock(CELL, stairs, QUIET_FLAGS), "Fixture must install real vanilla stairs");

            // NORTH stairs leave the south upper half empty; an AABB-only query would falsely hit it.
            var gap = localRay(pose, new Vec3(CELL.getX() - 2, CELL.getY() + 0.75, CELL.getZ() + 0.75),
                    new Vec3(CELL.getX() + 3, CELL.getY() + 0.75, CELL.getZ() + 0.75));
            check(StructureQueries.raycast(host, gap.from(), gap.to()).isEmpty(),
                    "Rotated staircase upper-half gap must not be filled by its outer box");
            var solid = localRay(pose, new Vec3(CELL.getX() - 2, CELL.getY() + 0.25, CELL.getZ() + 0.75),
                    new Vec3(CELL.getX() + 3, CELL.getY() + 0.25, CELL.getZ() + 0.75));
            var hit = hit(solid);
            check(hit.block().structure().equals(a.reference()) && hit.block().localPosition().equals(CELL),
                    "Arbitrary quaternion query must retain the exact negative local cell");
            check(hit.localFace() == Direction.WEST && Math.abs(hit.localPoint().x - CELL.getX()) < 1.0E-6,
                    "Staircase lower-half hit must retain its local face and surface point");
            check(hit.pose().equals(pose)
                            && hit.worldPoint().distanceToSqr(pose.localToWorldPoint(hit.localPoint())) < 1.0E-10,
                    "Host hit must use the same pose as its local point");
            check(StructureQueries.validate(host, solid.from(), solid.to(), hit).succeeded(),
                    "A fresh server hit must validate");
            var buffer = Unpooled.buffer();
            try {
                NativeStructureUsePacket.CODEC.encode(buffer, new NativeStructureUsePacket(hit));
                var transmitted = NativeStructureUsePacket.CODEC.decode(buffer).hit();
                check(StructureQueries.validate(host, solid.from(), solid.to(), transmitted).succeeded(),
                        "Arbitrary rotation hit must survive the actual use-packet codec");
            } finally {
                buffer.release();
            }

            var entering = localRay(pose, new Vec3(-34, CELL.getY() + 0.25, CELL.getZ() + 0.75),
                    new Vec3(CELL.getX() + 2, CELL.getY() + 0.25, CELL.getZ() + 0.75));
            check(hit(entering).block().localPosition().equals(CELL),
                    "Entering a container from outside must not hit the virtual BARRIER boundary");

            a.setPose(new StructurePose(pose.worldOrigin().add(0.25, 0, 0),
                    pose.rotationX(), pose.rotationY(), pose.rotationZ(), pose.rotationW()));
            check(!StructureQueries.validate(host, solid.from(), solid.to(), hit).succeeded(),
                    "Changing pose must invalidate the prior hit");
            a.setPose(pose);
            var beforeRawWrite = hit(solid);
            var beforeRevision = NativeStructureChanges.revision(storage(a));
            check(storage(a).setBlock(CELL, Blocks.BRICKS.defaultBlockState(), QUIET_FLAGS),
                    "A direct Level write must alter the fixture");
            check(NativeStructureChanges.revision(storage(a)) > beforeRevision,
                    "Raw Level write must advance revision synchronously before any tick drain");
            check(!StructureQueries.validate(host, solid.from(), solid.to(), beforeRawWrite).succeeded(),
                    "A state change must invalidate the old hit within the same call stack");
            var afterRawWrite = hit(solid);
            check(afterRawWrite.geometryRevision() != beforeRawWrite.geometryRevision(),
                    "Fresh query must expose the native geometry change");
            var stableRevision = NativeStructureChanges.revision(storage(a));
            storage(a).setBlock(CELL, Blocks.BRICKS.defaultBlockState(), QUIET_FLAGS);
            check(NativeStructureChanges.revision(storage(a)) == stableRevision,
                    "Writing the identical state must not manufacture a geometry change");
            verifyOcclusionAndReload();
        }

        private void verifyOcclusionAndReload() {
            a.setPose(identityAt(center));
            b.setPose(identityAt(center.add(3, 0, 0)));
            storage(a).setBlock(CELL, Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
            storage(b).setBlock(CELL, Blocks.GOLD_BLOCK.defaultBlockState(), QUIET_FLAGS);
            var ray = worldRay(center.add(-2, 0, 0), center.add(6, 0, 0));
            var nearest = hit(ray);
            check(nearest.block().structure().equals(a.reference()),
                    "Two structures on one ray must select the nearer real surface");
            clearCell(a);
            check(hit(ray).block().structure().equals(b.reference()),
                    "Removing the near cell must reveal the farther structure immediately");
            storage(a).setBlock(CELL, Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
            nearest = hit(ray);
            var blocker = BlockPos.containing(center.add(-1, 0, 0));
            check(host.getBlockState(blocker).isAir() && host.getBlockEntity(blocker) == null,
                    "Host occlusion fixture must not replace existing content");
            hostBlocker = blocker;
            check(host.setBlock(blocker, Blocks.STONE.defaultBlockState(), QUIET_FLAGS),
                    "Host occlusion fixture must be placed");
            check(StructureQueries.raycast(host, ray.from(), ray.to()).isEmpty(),
                    "An ordinary host block must occlude both structures");
            check(!StructureQueries.validate(host, ray.from(), ray.to(), nearest).succeeded(),
                    "A new host occluder must invalidate a previously visible hit");
            host.setBlock(blocker, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
            hostBlocker = null;

            var oldHit = hit(ray);
            var oldHandle = a;
            check(backend.unload(a.reference()), "Query fixture must save and unload");
            a = backend.load(oldHandle.reference()).orElseThrow(() -> new AssertionError("Query fixture must reload"));
            check(a != oldHandle, "Reload must return a fresh handle");
            await(() -> active(a), 200, () -> {
                var fresh = hit(ray);
                check(fresh.block().structure().equals(oldHit.block().structure())
                                && !fresh.runtimeId().equals(oldHit.runtimeId()),
                        "Reload must preserve persistent identity and change the runtime identity");
                check(!StructureQueries.validate(host, ray.from(), ray.to(), oldHit).succeeded(),
                        "A pre-unload hit must remain invalid even when the same cells reload");
                check(StructureQueries.validate(host, ray.from(), ray.to(), fresh).succeeded(),
                        "The reloaded server hit must validate");
                verifyPresentationCache();
            });
        }

        private void verifyPresentationCache() {
            clearCell(b);
            a.setPose(identityAt(center));
            storage(a).setBlock(CELL, Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
            await(() -> presented(StructurePresentationStatus.VISIBLE, 1), 40, () -> {
                var before = projection().orElseThrow();
                var oldView = before.view();
                var oldRevision = before.viewState().revision();
                check(storage(a).setBlock(CELL, Blocks.BRICKS.defaultBlockState(), QUIET_FLAGS),
                        "A raw state write must change a visible fixture");
                check(!runtime().isPresented(a.reference()), "A raw write immediately makes the old view stamp stale");
                await(() -> presented(StructurePresentationStatus.VISIBLE, 1)
                        && projection().orElseThrow().view().getBlockState(CELL).is(Blocks.BRICKS), 40, () -> {
                    var current = projection().orElseThrow();
                    check(current == before && current.view() != oldView
                                    && current.viewState().revision() > oldRevision,
                            "Dirty-cell refresh must replace contents on the same runtime projection");
                    verifyViewWireAndImmutability();
                    verifyUnsupportedBlockEntity(current);
                });
            });
        }

        private void verifyUnsupportedBlockEntity(NativeStructureProjectionEntity previous) {
            var chestCell = CELL.east();
            check(storage(a).setBlock(chestCell, Blocks.TRAPPED_CHEST.defaultBlockState(), QUIET_FLAGS),
                    "Unsupported block entity fixture must be installed");
            await(() -> presented(StructurePresentationStatus.UNSUPPORTED_CONTENT, 0), 40, () -> {
                check(previous.isRemoved() && storage(a).getBlockState(CELL).is(Blocks.BRICKS)
                                && storage(a).getBlockState(chestCell).is(Blocks.TRAPPED_CHEST)
                                && storage(a).getBlockEntity(chestCell) instanceof Container chest && chest.isEmpty()
                                && active(a),
                        "An unsupported trapped chest must hide the projection without changing its live block entity");
                // This exact empty chest was just created by the fixture; no inventory is erased.
                storage(a).setBlock(chestCell, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
                await(() -> presented(StructurePresentationStatus.VISIBLE, 1), 40,
                        () -> verifyUnsupportedPresentation(projection().orElseThrow()));
            });
        }

        private void verifyUnsupportedPresentation(NativeStructureProjectionEntity previous) {
            var fluidCell = CELL.east();
            check(storage(a).setBlock(fluidCell, Blocks.WATER.defaultBlockState(), QUIET_FLAGS),
                    "Unsupported fluid fixture must be installed without scheduling a flow");
            await(() -> presented(StructurePresentationStatus.UNSUPPORTED_CONTENT, 0), 40, () -> {
                check(previous.isRemoved() && storage(a).getBlockState(CELL).is(Blocks.BRICKS)
                                && storage(a).getBlockState(fluidCell).is(Blocks.WATER) && active(a),
                        "One unsupported cell must hide the complete view while storage remains active and intact");
                storage(a).setBlock(fluidCell, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
                clearCell(a);
                await(() -> presented(StructurePresentationStatus.EMPTY, 0), 40, () -> {
                    storage(a).setBlock(CELL, Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
                    await(() -> presented(StructurePresentationStatus.VISIBLE, 1), 40, () -> {
                        var restored = projection().orElseThrow();
                        check(restored != previous && restored.viewState().runtimeId().equals(previous.viewState().runtimeId()),
                                "An empty view must recover without reallocating persistent storage or runtime identity");
                        clearCell(a);
                        verifyPresentationLimit();
                    });
                });
            });
        }

        private void verifyPresentationLimit() {
            for (var index = 0; index < NativeStructureView.MAX_CELLS; index++) {
                check(storage(a).setBlock(bulkPosition(index), Blocks.STONE.defaultBlockState(), QUIET_FLAGS),
                        "Limit fixture must install each distinct cell");
            }
            await(() -> presented(StructurePresentationStatus.VISIBLE, NativeStructureView.MAX_CELLS), 40, () -> {
                var previous = projection().orElseThrow();
                var overflow = bulkPosition(NativeStructureView.MAX_CELLS);
                check(storage(a).setBlock(overflow, Blocks.STONE.defaultBlockState(), QUIET_FLAGS),
                        "Limit fixture must retain its 4097th cell in real storage");
                await(() -> presented(StructurePresentationStatus.LIMIT_EXCEEDED, 0), 40, () -> {
                    check(previous.isRemoved() && active(a), "An oversized view must hide without unloading storage");
                    for (var index = 0; index <= NativeStructureView.MAX_CELLS; index++) {
                        check(storage(a).getBlockState(bulkPosition(index)).is(Blocks.STONE),
                                "Hiding an oversized view must preserve every real cell");
                    }
                    storage(a).setBlock(overflow, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
                    await(() -> presented(StructurePresentationStatus.VISIBLE, NativeStructureView.MAX_CELLS), 40, () -> {
                        check(projection().orElseThrow().viewState().runtimeId().equals(a.runtimeId()),
                                "A limit recovery must remain on the current runtime");
                        clearPresentationFixture();
                        await(() -> presented(StructurePresentationStatus.EMPTY, 0), 40, this::finish);
                    });
                });
            });
        }

        private boolean presented(StructurePresentationStatus expected, int count) {
            if (runtime().presentationStatus(a.reference()) != expected) return false;
            var current = projection();
            return expected == StructurePresentationStatus.VISIBLE
                    ? runtime().isPresented(a.reference()) && current.isPresent() && current.get().view().cells().size() == count
                    : current.isEmpty();
        }

        private Optional<NativeStructureProjectionEntity> projection() {
            NativeStructureProjectionEntity result = null;
            for (var entity : host.getAllEntities()) {
                if (entity instanceof NativeStructureProjectionEntity projection && !projection.isRemoved()
                        && projection.viewState().reference().equals(a.reference())) {
                    check(result == null, "A structure must never own two live host projections");
                    result = projection;
                }
            }
            return Optional.ofNullable(result);
        }

        private NativeStructureRuntime runtime() {
            return NativeStructureRuntime.backend(host.getServer());
        }

        private static BlockPos bulkPosition(int index) {
            return new BlockPos(index % 64 - 32, 32 + index / 4096, index / 64 % 64 - 32);
        }

        private void clearPresentationFixture() {
            var level = storage(a);
            var fluidCell = CELL.east();
            var previousBlockEntity = level.getBlockEntity(fluidCell);
            var emptyFixtureChest = (level.getBlockState(fluidCell).is(Blocks.CHEST)
                    || level.getBlockState(fluidCell).is(Blocks.TRAPPED_CHEST))
                    && previousBlockEntity instanceof Container chest && chest.isEmpty();
            check(emptyFixtureChest || previousBlockEntity == null
                            && (level.getBlockState(fluidCell).isAir() || level.getBlockState(fluidCell).is(Blocks.WATER)),
                    "Dedicated fluid fixture must not erase unrelated content");
            level.setBlock(fluidCell, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
            for (var index = 0; index <= NativeStructureView.MAX_CELLS; index++) {
                var position = bulkPosition(index);
                var state = level.getBlockState(position);
                check(level.getBlockEntity(position) == null && (state.isAir() || state.is(Blocks.STONE)),
                        "Dedicated bulk fixture must not erase block entity data or unrelated states");
                if (!state.isAir()) level.setBlock(position, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
            }
        }

        private void verifyViewWireAndImmutability() {
            var mutable = new BlockPos.MutableBlockPos(-3, 17, -4);
            var cell = new NativeStructureView.Cell(mutable, Blocks.FURNACE.defaultBlockState());
            var input = new ArrayList<NativeStructureView.Cell>();
            input.add(cell);
            input.add(new NativeStructureView.Cell(new BlockPos(31, -64, -32), Blocks.STONE.defaultBlockState()));
            var view = new NativeStructureView(input);
            mutable.set(31, 319, 31);
            input.clear();
            check(view.cells().size() == 2 && cell.position().equals(new BlockPos(-3, 17, -4))
                            && view.cells().getFirst().position().getY() == -64,
                    "View must defensively copy mutable positions and input lists, then sort cells canonically");
            check(view.getBlockEntity(cell.position()) == null && view.getFluidState(cell.position()).isEmpty(),
                    "A furnace model must expose neither its live block entity nor container contents");
            var reversed = new ArrayList<>(view.cells());
            Collections.reverse(reversed);
            var ordered = new NativeStructureView(reversed);
            var first = new RegistryFriendlyByteBuf(Unpooled.buffer(), storage(a).registryAccess(), ConnectionType.OTHER);
            var second = new RegistryFriendlyByteBuf(Unpooled.buffer(), storage(a).registryAccess(), ConnectionType.OTHER);
            try {
                NativeStructureView.STREAM_CODEC.encode(first, view);
                NativeStructureView.STREAM_CODEC.encode(second, ordered);
                check(first.equals(second) && first.readableBytes() <= 19,
                        "Wire cells must be state-only and independent of source map iteration order");
                var copy = NativeStructureView.STREAM_CODEC.decode(first);
                check(copy.cells().equals(view.cells()) && !first.isReadable(),
                        "Actual registered block states and negative positions must survive the wire");
                second.clear();
                second.writeVarInt(1);
                second.writeByte(0);
                second.writeShort(0);
                second.writeByte(0);
                second.writeVarInt(Integer.MAX_VALUE);
                var rejected = false;
                try { NativeStructureView.STREAM_CODEC.decode(second); }
                catch (DecoderException expected) { rejected = true; }
                check(rejected, "The decoder must reject unknown state IDs instead of silently converting to air");
            } finally {
                first.release();
                second.release();
            }
            rejectView(() -> new NativeStructureView(List.of(cell, cell)), "Duplicate cells must be rejected");
            rejectView(() -> new NativeStructureView.Cell(new BlockPos(-33, 0, 0), Blocks.STONE.defaultBlockState()),
                    "Out-of-domain negative cells must be rejected");
            rejectView(() -> new NativeStructureView.Cell(BlockPos.ZERO, Blocks.WATER.defaultBlockState()),
                    "Fluid states must never enter a state-only model view");
            for (var block : List.of(Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
                    Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.CHEST)) {
                check(block.defaultBlockState().hasBlockEntity()
                                && NativeStructureView.isSupportedState(block.defaultBlockState()),
                        "Audited vanilla state-only block entity models must remain supported");
                new NativeStructureView.Cell(BlockPos.ZERO, block.defaultBlockState());
            }
            for (var block : List.of(Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST,
                    Blocks.SHULKER_BOX, Blocks.ENCHANTING_TABLE, Blocks.CAMPFIRE, Blocks.BELL,
                    Blocks.BREWING_STAND)) {
                check(!NativeStructureView.isSupportedState(block.defaultBlockState()),
                        "Unknown or special-rendered block entities must fail closed");
                rejectView(() -> new NativeStructureView.Cell(BlockPos.ZERO, block.defaultBlockState()),
                        "Block entity renderer content must never masquerade as a complete model view");
            }
            var chest = Blocks.CHEST.defaultBlockState();
            rejectView(() -> new NativeStructureView.Cell(BlockPos.ZERO, chest.setValue(
                    net.minecraft.world.level.block.ChestBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.ChestType.LEFT)), "Double chests remain unsupported");
            rejectView(() -> new NativeStructureView.Cell(BlockPos.ZERO, chest.setValue(
                    net.minecraft.world.level.block.ChestBlock.WATERLOGGED, true)), "Waterlogged chests remain unsupported");
        }

        private void rejectView(Runnable action, String message) {
            var rejected = false;
            try { action.run(); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, message);
        }

        private StructureHit hit(Ray ray) {
            return StructureQueries.raycast(host, ray.from(), ray.to()).orElseThrow(() ->
                    new AssertionError("Expected a visible native structure surface"));
        }

        private Ray localRay(StructurePose pose, Vec3 from, Vec3 to) {
            return worldRay(pose.localToWorldPoint(from), pose.localToWorldPoint(to));
        }

        private Ray worldRay(Vec3 from, Vec3 to) {
            // Explicit test setup, not query behavior: keep the entire host ray in loaded chunks.
            var steps = (int) Math.ceil(from.distanceTo(to)) + 1;
            for (var step = 0; step <= steps; step++) {
                var position = BlockPos.containing(from.lerp(to, (double) step / steps));
                host.getChunk(position.getX() >> 4, position.getZ() >> 4);
            }
            return new Ray(from, to);
        }

        private ServerLevel storage(StructureHandle handle) {
            return NativeStructureRuntime.backend(host.getServer()).storageLevel(handle.reference()).orElseThrow();
        }

        private boolean active(StructureHandle handle) {
            return handle.lifecycle() == StructureLifecycle.ACTIVE;
        }

        private void clearCell(StructureHandle handle) {
            var level = storage(handle);
            check(level.getBlockEntity(CELL) == null, "Query fixture must never erase block entity data");
            level.setBlock(CELL, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
        }

        private void await(BooleanSupplier condition, int remaining, Runnable action) {
            if (condition.getAsBoolean()) action.run();
            else {
                check(remaining > 0, "Query fixture loading timed out");
                helper.runAfterDelay(1, () -> guarded(() -> await(condition, remaining - 1, action)));
            }
        }

        private void guarded(Runnable action) {
            if (finished) return;
            try {
                action.run();
            } catch (Throwable failure) {
                finished = true;
                cleanup();
                helper.fail("Native structure query: " + failure);
            }
        }

        private void finish() {
            clearCell(a);
            clearCell(b);
            check(backend.unload(a.reference()) && backend.unload(b.reference()),
                    "Query test must unload both permanent fixtures");
            finished = true;
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_QUERY_GAMETEST_PASSED a={} b={}", a.reference(), b.reference());
            helper.succeed();
        }

        private void cleanup() {
            if (hostBlocker != null) host.setBlock(hostBlocker, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
            for (var handle : new StructureHandle[]{a, b}) {
                if (handle == null) continue;
                try {
                    backend.unload(handle.reference());
                } catch (RuntimeException failure) {
                    AcademyCraft.LOGGER.error("Cannot unload query fixture {}", handle.reference(), failure);
                }
            }
        }

        private void check(boolean condition, String message) {
            helper.assertTrue(condition, message);
        }

        private StructurePose identityAt(Vec3 worldCenter) {
            return StructurePose.identity(worldCenter.subtract(Vec3.atCenterOf(CELL)));
        }
    }

    private record Ray(Vec3 from, Vec3 to) {
    }

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
                AcademyCraft.academy("native_structure_query_fixtures"), FixtureIndex::new, CODEC);
        private final List<StructureRef> references;

        private FixtureIndex() {
            this(List.of());
        }

        private FixtureIndex(List<StructureRef> references) {
            this.references = new ArrayList<>(references);
        }
    }

    private static final class QueryTest extends GameTestInstance {
        private static final MapCodec<QueryTest> CODEC = TestData.CODEC.xmap(QueryTest::new, QueryTest::info);

        private QueryTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override public void run(GameTestHelper helper) { new Scenario(helper).start(); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() {
            return Component.literal("Native structure host rays, real shapes, occlusion and stale-hit rejection");
        }
    }
}
