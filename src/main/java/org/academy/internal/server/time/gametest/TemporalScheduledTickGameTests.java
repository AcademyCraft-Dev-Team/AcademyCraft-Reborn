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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.time.TemporalScope;
import org.academy.mixin.common.LevelTicksAccessor;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** End-to-end validation for scheduled-tick pause and resume rebasing. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TemporalScheduledTickGameTests {
    private static final Identifier TEST_INSTANCE_TYPE =
            AcademyCraft.academy("temporal_scheduled_tick_function");
    private static final long ORIGINAL_DELAY = 40L;
    private static final long SUB_TICK_ORDER = -23L;

    private TemporalScheduledTickGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(
                Registries.TEST_INSTANCE_TYPE,
                TEST_INSTANCE_TYPE,
                () -> TemporalScheduledTickTestInstance.CODEC
        );
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("time/scheduled_tick_pause_resume"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        var data = new TestData<>(
                environment,
                Identifier.withDefaultNamespace("empty"),
                30,
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
                AcademyCraft.academy("time_scheduled_tick_pause_resume"),
                new TemporalScheduledTickTestInstance(data)
        );
    }

    private static void runPauseResumeTest(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(1, 1, 1, Blocks.STONE);
        var blockQueue = level.getBlockTicks();
        var fluidQueue = level.getFluidTicks();
        var initialNow = level.getGameTime();
        blockQueue.schedule(new ScheduledTick<>(
                Blocks.STONE,
                position,
                initialNow + ORIGINAL_DELAY,
                TickPriority.HIGH,
                SUB_TICK_ORDER
        ));
        fluidQueue.schedule(new ScheduledTick<>(
                Fluids.WATER,
                position,
                initialNow + ORIGINAL_DELAY,
                TickPriority.HIGH,
                SUB_TICK_ORDER
        ));

        var lease = TemporalApi.get(level.getServer()).acquireField(
                TemporalField.pause(
                        TemporalScope.sphere(
                                level.dimension(),
                                Vec3.atCenterOf(position),
                                4.0D
                        ),
                        Set.of(
                                TemporalChannel.SCHEDULED_BLOCK,
                                TemporalChannel.SCHEDULED_FLUID
                        ),
                        TemporalPauseSource.ACADEMY_PAUSE
                )
        );
        try {
            var pausedBlockTick = findTick(
                    blockQueue,
                    position,
                    Blocks.STONE
            );
            var pausedFluidTick = findTick(
                    fluidQueue,
                    position,
                    Fluids.WATER
            );
            helper.assertTrue(
                    pausedBlockTick != null,
                    "Paused block tick disappeared"
            );
            helper.assertTrue(
                    pausedFluidTick != null,
                    "Paused fluid tick disappeared"
            );
            helper.assertValueEqual(
                    pausedBlockTick.triggerTick(),
                    initialNow + 1L,
                    "Pause did not park the block tick"
            );
            helper.assertValueEqual(
                    pausedFluidTick.triggerTick(),
                    initialNow + 1L,
                    "Pause did not park the fluid tick"
            );
            assertOrdering(helper, pausedBlockTick);
            assertOrdering(helper, pausedFluidTick);
        } catch (RuntimeException | Error throwable) {
            lease.close();
            throw throwable;
        }

        helper.runAfterDelay(5L, () -> {
            try {
                var pausedBlockTick = findTick(
                        blockQueue,
                        position,
                        Blocks.STONE
                );
                var pausedFluidTick = findTick(
                        fluidQueue,
                        position,
                        Fluids.WATER
                );
                helper.assertTrue(
                        pausedBlockTick != null,
                        "Block tick executed during temporal pause"
                );
                helper.assertTrue(
                        pausedFluidTick != null,
                        "Fluid tick executed during temporal pause"
                );
                assertOrdering(helper, pausedBlockTick);
                assertOrdering(helper, pausedFluidTick);

                lease.close();
                var resumedAt = level.getGameTime();
                var resumedBlockTick = findTick(
                        blockQueue,
                        position,
                        Blocks.STONE
                );
                var resumedFluidTick = findTick(
                        fluidQueue,
                        position,
                        Fluids.WATER
                );
                helper.assertTrue(
                        resumedBlockTick != null,
                        "Resumed block tick disappeared"
                );
                helper.assertTrue(
                        resumedFluidTick != null,
                        "Resumed fluid tick disappeared"
                );
                helper.assertValueEqual(
                        resumedBlockTick.triggerTick(),
                        resumedAt + ORIGINAL_DELAY,
                        "Resume did not restore the block delay"
                );
                helper.assertValueEqual(
                        resumedFluidTick.triggerTick(),
                        resumedAt + ORIGINAL_DELAY,
                        "Resume did not restore the fluid delay"
                );
                assertOrdering(helper, resumedBlockTick);
                assertOrdering(helper, resumedFluidTick);
                helper.succeed();
            } finally {
                if (lease.isActive()) lease.close();
                var area = BoundingBox.fromCorners(position, position);
                blockQueue.clearArea(area);
                fluidQueue.clearArea(area);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> ScheduledTick<T> findTick(
            LevelTicks<T> queue,
            BlockPos position,
            T expectedType
    ) {
        var containers = ((LevelTicksAccessor<T>) queue)
                .academy$getAllContainers();
        for (var container : containers.values()) {
            var found = container.getAll()
                    .filter(tick -> tick.type() == expectedType)
                    .filter(tick -> tick.pos().equals(position))
                    .findFirst();
            if (found.isPresent()) return found.get();
        }
        return null;
    }

    private static void assertOrdering(
            GameTestHelper helper,
            ScheduledTick<?> tick
    ) {
        helper.assertValueEqual(
                tick.priority(),
                TickPriority.HIGH,
                "Scheduled tick priority changed"
        );
        helper.assertValueEqual(
                tick.subTickOrder(),
                SUB_TICK_ORDER,
                "Scheduled tick sub-order changed"
        );
    }

    private static final class TemporalScheduledTickTestInstance
            extends GameTestInstance {
        private static final MapCodec<TemporalScheduledTickTestInstance> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TestData.CODEC.forGetter(TemporalScheduledTickTestInstance::info)
                ).apply(instance, TemporalScheduledTickTestInstance::new));

        private TemporalScheduledTickTestInstance(
                TestData<Holder<TestEnvironmentDefinition<?>>> info
        ) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            runPauseResumeTest(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Temporal scheduled tick pause resume"
                    .toLowerCase(Locale.ROOT));
        }
    }
}
