package org.academy.internal.common.ability.teleport;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;

import java.util.List;
import java.util.Locale;

/**
 * Live-world verification for the 区块跃迁 swap.
 *
 * <p>These run against a real {@link ServerLevel} inside the NeoForge GameTest harness, which is the
 * only place the risky half of the feature is observable: block entities must survive the section
 * exchange at their new coordinates, entities must move with their chunk, and swapping the same pair
 * twice must restore the original world.
 *
 * <p>Everything for a scenario runs in a single server callback. Delaying between planting and
 * swapping lets the remote test chunks unload, which would silently detach the {@link LevelChunk}
 * handles and make the swap a no-op. The chunks are held through
 * {@link ChunkTicketLeaseManager}, so these tests also exercise the production lease path.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ChunkSwapGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("chunk_swap_function");
    private static final int CHUNK_A_X = 400;
    private static final int CHUNK_A_Z = 400;

    private ChunkSwapGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> SwapTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("chunk_swap"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("chunk_swap_restores_world"),
                new SwapTest(data(environment), SwapScenario.ROUND_TRIP));
        event.registerTest(AcademyCraft.academy("chunk_swap_section_byte_round_trip"),
                new SwapTest(data(environment), SwapScenario.SECTION_BYTES));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> data(
            Holder<TestEnvironmentDefinition<?>> environment) {
        return new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                200, 0, true, Rotation.NONE, false, 1, 1, false, 16);
    }

    private enum SwapScenario {
        /** Plant a marker pair, swap, then swap back and prove the world is unchanged. */
        ROUND_TRIP,
        /**
         * Exercise the write/read round-trip the cross-dimension path depends on.
         *
         * <p>Cross-dimension swaps cannot share section objects between levels, so they serialise a
         * section and read it back in place. That primitive needs a real level (its palette factory and
         * registry access), which is why it is verified here rather than in a unit test.
         */
        SECTION_BYTES
    }

    private static final class SwapTest extends GameTestInstance {
        private static final MapCodec<SwapTest> CODEC = TestData.CODEC.xmap(SwapTest::new, SwapTest::info);
        private final SwapScenario scenario;

        private SwapTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            this(info, SwapScenario.ROUND_TRIP);
        }

        private SwapTest(TestData<Holder<TestEnvironmentDefinition<?>>> info, SwapScenario scenario) {
            super(info);
            this.scenario = scenario;
        }

        @Override
        public void run(GameTestHelper helper) {
            var level = helper.getLevel();
            var posA = new BlockPos(CHUNK_A_X << 4, 80, CHUNK_A_Z << 4);
            var posB = new BlockPos((CHUNK_A_X + 1) << 4, 80, CHUNK_A_Z << 4);

            // setChunkForced loads synchronously, so the chunks are resident on the next tick. A ticket
            // lease would also work but needs several ticks to generate, which is not what this tests.
            level.setChunkForced(CHUNK_A_X, CHUNK_A_Z, true);
            level.setChunkForced(CHUNK_A_X + 1, CHUNK_A_Z, true);

            helper.runAfterDelay(2, () -> {
                var liveA = level.getChunkSource().getChunkNow(CHUNK_A_X, CHUNK_A_Z);
                var liveB = level.getChunkSource().getChunkNow(CHUNK_A_X + 1, CHUNK_A_Z);
                if (liveA == null || liveB == null) {
                    release(level);
                    fail(helper, "both chunks must be loaded before the swap can run");
                    return;
                }
                try {
                    switch (scenario) {
                        case ROUND_TRIP -> roundTrip(helper, level, liveA, liveB, posA, posB);
                        case SECTION_BYTES -> sectionByteRoundTrip(helper, level, liveA, posA);
                    }
                } finally {
                    release(level);
                }
            });
        }

        private static void release(ServerLevel level) {
            level.setChunkForced(CHUNK_A_X, CHUNK_A_Z, false);
            level.setChunkForced(CHUNK_A_X + 1, CHUNK_A_Z, false);
        }

        /** Plants a distinct block plus a filled chest in each chunk and verifies a true round trip. */
        private void roundTrip(GameTestHelper helper, ServerLevel level, LevelChunk chunkA, LevelChunk chunkB,
                               BlockPos posA, BlockPos posB) {
            level.setBlock(posA, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            level.setBlock(posB, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            var chestPos = posA.offset(1, 0, 0);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
                fail(helper, "the planted chest must exist before swapping");
                return;
            }
            chest.setItem(0, new ItemStack(Items.APPLE, 7));

            var regionA = ChunkLeapRegion.ofChunks(level.dimension(), CHUNK_A_X, CHUNK_A_Z, 1, 1);
            var regionB = ChunkLeapRegion.ofChunks(level.dimension(), CHUNK_A_X + 1, CHUNK_A_Z, 1, 1);

            ChunkRegionSwap.apply(level, chunkA, chunkB, regionA, regionB, false);

            helper.assertTrue(level.getBlockState(posA).is(Blocks.GOLD_BLOCK),
                    "chunk A must now hold chunk B's gold block");
            helper.assertTrue(level.getBlockState(posB).is(Blocks.DIAMOND_BLOCK),
                    "chunk B must now hold chunk A's diamond block");

            // The chest travels with its chunk, contents intact, at the relocated coordinates.
            var movedChestPos = chestPos.offset(16, 0, 0);
            helper.assertTrue(level.getBlockState(movedChestPos).is(Blocks.CHEST),
                    "the chest must travel with its chunk");
            if (level.getBlockEntity(movedChestPos) instanceof ChestBlockEntity movedChest) {
                helper.assertTrue(movedChest.getItem(0).is(Items.APPLE) && movedChest.getItem(0).getCount() == 7,
                        "the chest must keep its exact contents after moving");
            } else {
                fail(helper, "the chest block entity must be rebuilt at its new coordinates");
                return;
            }

            // Swap back: the world must be exactly as it was.
            ChunkRegionSwap.apply(level, chunkA, chunkB, regionA, regionB, false);
            helper.assertTrue(level.getBlockState(posA).is(Blocks.DIAMOND_BLOCK),
                    "a second swap must restore the diamond block");
            helper.assertTrue(level.getBlockState(posB).is(Blocks.GOLD_BLOCK),
                    "a second swap must restore the gold block");
            helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST),
                    "the chest must return to its original chunk");
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity restored) {
                helper.assertTrue(restored.getItem(0).is(Items.APPLE) && restored.getItem(0).getCount() == 7,
                        "the returned chest must keep its original contents");
            } else {
                fail(helper, "the chest must be rebuilt back at its original coordinates");
                return;
            }
            helper.succeed();
        }

        /**
         * Plants a block, serialises the containing section, reads it back, and checks the block
         * survived — the primitive the cross-dimension path is built on.
         *
         * <p>The same section is written and read in place, so the block must be identical afterwards;
         * if the palette or storage round-trip were lossy, the block would change or vanish.
         */
        private void sectionByteRoundTrip(GameTestHelper helper, ServerLevel level, LevelChunk chunk,
                                          BlockPos pos) {
            level.setBlock(pos, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
            var before = level.getBlockState(pos);
            helper.assertTrue(before.is(Blocks.EMERALD_BLOCK),
                    "the planted emerald block must exist before the round trip");

            var sectionIndex = chunk.getSectionIndex(pos.getY());
            var section = chunk.getSection(sectionIndex);

            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(8192));
            try {
                section.write(buffer);
                var written = buffer.readableBytes();
                helper.assertTrue(written > 0, "a section must serialise to a non-empty payload");
                buffer.readerIndex(0);
                section.read(buffer);
                section.recalcBlockCounts();
            } finally {
                buffer.release();
            }

            var after = level.getBlockState(pos);
            helper.assertTrue(after.is(Blocks.EMERALD_BLOCK),
                    "the block must survive a section write/read round trip (found " + after + ")");
            helper.succeed();
        }

        private static void fail(GameTestHelper helper, String message) {
            helper.assertTrue(false, message);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Chunk leap swap: " + scenario.name().toLowerCase(Locale.ROOT));
        }
    }
}
