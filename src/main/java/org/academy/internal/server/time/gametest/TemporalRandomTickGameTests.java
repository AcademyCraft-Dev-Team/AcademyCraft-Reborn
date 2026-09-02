package org.academy.internal.server.time.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** End-to-end validation for spatial random-tick pause and resume. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TemporalRandomTickGameTests {
    private static final Identifier TEST_INSTANCE_TYPE =
            AcademyCraft.academy("temporal_random_tick_function");
    private static final int RANDOM_TICK_ATTEMPTS = 4096;

    private TemporalRandomTickGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(
                Registries.TEST_INSTANCE_TYPE,
                TEST_INSTANCE_TYPE,
                () -> TemporalRandomTickTestInstance.CODEC
        );
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("time/random_tick_sphere_pause"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        var data = new TestData<>(
                environment,
                Identifier.withDefaultNamespace("empty"),
                20,
                0,
                true,
                Rotation.NONE,
                false,
                1,
                1,
                false,
                16
        );
        event.registerTest(
                AcademyCraft.academy("time_random_tick_sphere_pause"),
                new TemporalRandomTickTestInstance(data)
        );
    }

    private static void runRandomTickPauseTest(GameTestHelper helper) {
        var level = helper.getLevel();
        var inside = cube(helper, 1, 1, 1, 4);
        var outside = cube(helper, 9, 1, 9, 4);
        var allPositions = new ArrayList<BlockPos>(inside.size() + outside.size());
        allPositions.addAll(inside);
        allPositions.addAll(outside);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 7);
        for (var position : allPositions) {
            level.setBlockAndUpdate(position, leaves);
        }

        var center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        try {
            var addResult = executeCommand(
                    level,
                    center,
                    String.format(
                            Locale.ROOT,
                            "academy debug tick field add sphere %s %.3f %.3f %.3f 4 0 random_tick",
                            level.dimension().identifier(),
                            center.x,
                            center.y,
                            center.z
                    )
            );
            helper.assertValueEqual(
                    addResult,
                    1,
                    "Range pause command did not create a field"
            );
            tickChunks(level, allPositions);
            helper.assertValueEqual(
                    countLeaves(level, inside),
                    inside.size(),
                    "Spatial pause allowed an inside random tick"
            );
            helper.assertTrue(
                    countLeaves(level, outside) < outside.size(),
                    "Random ticks outside the paused sphere did not run"
            );
            helper.assertValueEqual(
                    executeCommand(level, center, "academy debug tick"),
                    1,
                    "Tick debug command did not execute"
            );
            helper.assertValueEqual(
                    executeCommand(
                            level,
                            center,
                            "academy debug tick field clear"
                    ),
                    1,
                    "Range pause command did not release its field"
            );
            tickChunks(level, inside);
            helper.assertTrue(
                    countLeaves(level, inside) < inside.size(),
                    "Random ticks did not resume after the field closed"
            );

            validateMultiEntityControls(helper, level, center);
            validateWorldScopeControls(helper, level, center);
            helper.succeed();
        } finally {
            executeCommand(level, center, "academy debug tick clear");
            for (var position : allPositions) {
                level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void validateWorldScopeControls(
            GameTestHelper helper,
            ServerLevel level,
            Vec3 commandPosition
    ) {
        var nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "Nether is unavailable for scope validation");
        helper.assertValueEqual(
                executeCommand(
                        level,
                        commandPosition,
                        "academy debug tick field add dimension minecraft:overworld 0.5 entity"
                ),
                1,
                "Dimension scale command did not create a field"
        );
        helper.assertValueEqual(
                executeCommand(
                        level,
                        commandPosition,
                        "academy debug tick field add save 2 level_clock"
                ),
                1,
                "Save scale command did not create a field"
        );

        var temporal = TemporalApi.get(level.getServer());
        helper.assertValueEqual(
                temporal.effectiveScale(level, TemporalChannel.ENTITY),
                0.5D,
                "Dimension scale did not affect its target dimension"
        );
        helper.assertValueEqual(
                temporal.effectiveScale(nether, TemporalChannel.ENTITY),
                1.0D,
                "Dimension scale leaked into another dimension"
        );
        helper.assertValueEqual(
                temporal.effectiveScale(level, TemporalChannel.LEVEL_CLOCK),
                2.0D,
                "Save scale did not affect the overworld"
        );
        helper.assertValueEqual(
                temporal.effectiveScale(nether, TemporalChannel.LEVEL_CLOCK),
                2.0D,
                "Save scale did not affect the Nether"
        );
        helper.assertValueEqual(
                executeCommand(level, commandPosition, "academy debug tick field list"),
                2,
                "Field list did not include both world-scope controls"
        );
        helper.assertValueEqual(
                executeCommand(level, commandPosition, "academy debug tick field clear"),
                2,
                "World-scope fields were not both released"
        );
    }

    private static void validateMultiEntityControls(
            GameTestHelper helper,
            ServerLevel level,
            Vec3 commandPosition
    ) {
        var first = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 7);
        var second = helper.spawn(EntityTypes.ZOMBIE, 3, 2, 7);
        var outsider = helper.spawn(EntityTypes.ZOMBIE, 5, 2, 7);
        first.addTag("academy_tick_debug_selected");
        second.addTag("academy_tick_debug_selected");
        try {
            helper.assertValueEqual(
                    executeCommand(
                            level,
                            commandPosition,
                            "academy debug tick field add entities @e[tag=academy_tick_debug_selected] 0"
                    ),
                    2,
                    "Multi-entity pause did not select both targets"
            );

            var firstTicks = first.tickCount;
            var secondTicks = second.tickCount;
            var outsiderTicks = outsider.tickCount;
            level.tickNonPassenger(first);
            level.tickNonPassenger(second);
            level.tickNonPassenger(outsider);
            helper.assertValueEqual(
                    first.tickCount,
                    firstTicks,
                    "First selected entity ignored the debug pause"
            );
            helper.assertValueEqual(
                    second.tickCount,
                    secondTicks,
                    "Second selected entity ignored the debug pause"
            );
            helper.assertTrue(
                    outsider.tickCount > outsiderTicks,
                    "Entity outside the selected set was paused"
            );

            helper.assertValueEqual(
                    executeCommand(
                            level,
                            commandPosition,
                            "academy debug tick immunity add @e[tag=academy_tick_debug_selected] academy_pause"
                    ),
                    2,
                    "Multi-entity immunity did not select both targets"
            );
            level.tickNonPassenger(first);
            level.tickNonPassenger(second);
            helper.assertTrue(
                    first.tickCount > firstTicks && second.tickCount > secondTicks,
                    "Selected entities did not bypass their Academy pause"
            );
            helper.assertValueEqual(
                    executeCommand(
                            level,
                            commandPosition,
                            "academy debug tick inspect entities @e[tag=academy_tick_debug_selected]"
                    ),
                    2,
                    "Multi-entity inspection did not cover both targets"
            );
            helper.assertValueEqual(
                    executeCommand(
                            level,
                            commandPosition,
                            "academy debug tick clear"
                    ),
                    2,
                    "Combined cleanup did not release the field and immunity group"
            );
        } finally {
            first.discard();
            second.discard();
            outsider.discard();
        }
    }

    private static int executeCommand(
            ServerLevel level,
            Vec3 position,
            String command
    ) {
        try {
            return level.getServer().getCommands().getDispatcher().execute(
                    command,
                    level.getServer().createCommandSourceStack()
                            .withLevel(level)
                            .withPosition(position)
                            .withPermission(PermissionSet.ALL_PERMISSIONS)
            );
        } catch (Exception exception) {
            throw new AssertionError(
                    "Tick debug command failed: " + command,
                    exception
            );
        }
    }

    private static List<BlockPos> cube(
            GameTestHelper helper,
            int startX,
            int startY,
            int startZ,
            int size
    ) {
        var positions = new ArrayList<BlockPos>(size * size * size);
        for (var x = 0; x < size; x++) {
            for (var y = 0; y < size; y++) {
                for (var z = 0; z < size; z++) {
                    positions.add(helper.absolutePos(new BlockPos(
                            startX + x,
                            startY + y,
                            startZ + z
                    )));
                }
            }
        }
        return positions;
    }

    private static void tickChunks(
            ServerLevel level,
            List<BlockPos> positions
    ) {
        var chunks = new HashSet<LevelChunk>();
        for (var position : positions) {
            chunks.add(level.getChunk(
                    position.getX() >> 4,
                    position.getZ() >> 4
            ));
        }
        for (var chunk : chunks) {
            level.tickChunk(chunk, RANDOM_TICK_ATTEMPTS);
        }
    }

    private static int countLeaves(
            ServerLevel level,
            List<BlockPos> positions
    ) {
        var count = 0;
        for (var position : positions) {
            if (level.getBlockState(position).is(Blocks.OAK_LEAVES)) count++;
        }
        return count;
    }

    private static final class TemporalRandomTickTestInstance
            extends GameTestInstance {
        private static final MapCodec<TemporalRandomTickTestInstance> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TestData.CODEC.forGetter(TemporalRandomTickTestInstance::info)
                ).apply(instance, TemporalRandomTickTestInstance::new));

        private TemporalRandomTickTestInstance(
                TestData<Holder<TestEnvironmentDefinition<?>>> info
        ) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            runRandomTickPauseTest(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Temporal random tick sphere pause");
        }
    }
}
