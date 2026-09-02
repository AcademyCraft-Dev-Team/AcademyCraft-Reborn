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
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalFieldLease;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.time.TemporalScope;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** End-to-end validation for level-wide and spatial one-shot channels. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TemporalLevelSubsystemGameTests {
    private static final Identifier TEST_INSTANCE_TYPE =
            AcademyCraft.academy("temporal_level_subsystem_function");

    private TemporalLevelSubsystemGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(
                Registries.TEST_INSTANCE_TYPE,
                TEST_INSTANCE_TYPE,
                () -> TemporalLevelSubsystemTestInstance.CODEC
        );
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("time/level_subsystem_pause_resume"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        var data = new TestData<>(
                environment,
                Identifier.withDefaultNamespace("empty"),
                40,
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
                AcademyCraft.academy("time_level_subsystem_pause_resume"),
                new TemporalLevelSubsystemTestInstance(data)
        );
    }

    private static void runPauseResumeTest(GameTestHelper helper) {
        var level = helper.getLevel();
        var inside = helper.absolutePos(new BlockPos(1, 1, 1));
        var outside = helper.absolutePos(new BlockPos(6, 1, 1));
        helper.setBlock(1, 1, 1, Blocks.SHULKER_BOX);
        helper.setBlock(6, 1, 1, Blocks.SHULKER_BOX);
        var insideBox = shulkerBox(helper, inside, "inside");
        var outsideBox = shulkerBox(helper, outside, "outside");
        var weatherData = level.getWeatherData();
        var weatherSnapshot = WeatherSnapshot.capture(level);
        level.getGameRules().set(GameRules.ADVANCE_WEATHER, true, level.getServer());
        weatherData.setClearWeatherTime(100);
        weatherData.setRainTime(1);
        weatherData.setThunderTime(1);
        weatherData.setRaining(false);
        weatherData.setThundering(false);

        var temporal = TemporalApi.get(level.getServer());
        var weatherLease = temporal.acquireField(TemporalField.pause(
                TemporalScope.dimension(level.dimension()),
                Set.of(TemporalChannel.WEATHER_AND_RAID),
                TemporalPauseSource.ACADEMY_PAUSE
        ));
        var blockEventLease = temporal.acquireField(TemporalField.pause(
                TemporalScope.sphere(
                        level.dimension(),
                        Vec3.atCenterOf(inside),
                        2.0D
                ),
                Set.of(TemporalChannel.BLOCK_EVENT),
                TemporalPauseSource.ACADEMY_PAUSE
        ));
        level.blockEvent(inside, Blocks.SHULKER_BOX, 1, 1);
        level.blockEvent(outside, Blocks.SHULKER_BOX, 1, 1);

        helper.runAfterDelay(3L, () -> {
            try {
                helper.assertValueEqual(
                        weatherData.getClearWeatherTime(),
                        100,
                        "Weather timer advanced during dimension pause"
                );
                helper.assertValueEqual(
                        insideBox.getAnimationStatus(),
                        ShulkerBoxBlockEntity.AnimationStatus.CLOSED,
                        "Block event inside the paused sphere was delivered"
                );
                helper.assertTrue(
                        outsideBox.getAnimationStatus()
                                != ShulkerBoxBlockEntity.AnimationStatus.CLOSED,
                        "Block event outside the paused sphere was suppressed"
                );
                weatherLease.close();
                blockEventLease.close();
                var resumedWeatherTime = weatherData.getClearWeatherTime();
                helper.runAfterDelay(3L, () -> finishResumeValidation(
                        helper,
                        inside,
                        outside,
                        insideBox,
                        resumedWeatherTime,
                        weatherSnapshot,
                        weatherLease,
                        blockEventLease
                ));
            } catch (RuntimeException | Error throwable) {
                cleanup(
                        level,
                        inside,
                        outside,
                        weatherSnapshot,
                        weatherLease,
                        blockEventLease
                );
                throw throwable;
            }
        });
    }

    private static void finishResumeValidation(
            GameTestHelper helper,
            BlockPos inside,
            BlockPos outside,
            ShulkerBoxBlockEntity insideBox,
            int resumedWeatherTime,
            WeatherSnapshot weatherSnapshot,
            TemporalFieldLease weatherLease,
            TemporalFieldLease blockEventLease
    ) {
        var level = helper.getLevel();
        var weatherData = level.getWeatherData();
        try {
            helper.assertTrue(
                    weatherData.getClearWeatherTime() < resumedWeatherTime,
                    "Weather timer did not resume after the field closed"
            );
            helper.assertTrue(
                    insideBox.getAnimationStatus()
                            != ShulkerBoxBlockEntity.AnimationStatus.CLOSED,
                    "Deferred block event was lost instead of resuming"
            );
        } finally {
            cleanup(
                    level,
                    inside,
                    outside,
                    weatherSnapshot,
                    weatherLease,
                    blockEventLease
            );
        }
        validateServerClockPause(helper);
    }

    private static void validateServerClockPause(GameTestHelper helper) {
        var level = helper.getLevel();
        var lease = TemporalApi.get(level.getServer()).acquireField(
                TemporalField.pause(
                        TemporalScope.save(),
                        Set.of(TemporalChannel.SERVER_CLOCK),
                        TemporalPauseSource.ACADEMY_PAUSE
                )
        );
        var pausedAt = level.getDefaultClockTime();
        helper.runAfterDelay(3L, () -> {
            try {
                helper.assertValueEqual(
                        level.getDefaultClockTime(),
                        pausedAt,
                        "Save clock advanced while its temporal channel was paused"
                );
            } finally {
                if (lease.isActive()) lease.close();
            }
            var resumedAt = level.getDefaultClockTime();
            helper.runAfterDelay(3L, () -> {
                try {
                    helper.assertTrue(
                            level.getDefaultClockTime() > resumedAt,
                            "Save clock did not resume after its field closed"
                    );
                    helper.succeed();
                } finally {
                    if (lease.isActive()) lease.close();
                }
            });
        });
    }

    private static ShulkerBoxBlockEntity shulkerBox(
            GameTestHelper helper,
            BlockPos position,
            String description
    ) {
        var blockEntity = helper.getLevel().getBlockEntity(position);
        helper.assertTrue(
                blockEntity instanceof ShulkerBoxBlockEntity,
                "Missing " + description + " shulker box block entity"
        );
        return (ShulkerBoxBlockEntity) blockEntity;
    }

    private static void cleanup(
            net.minecraft.server.level.ServerLevel level,
            BlockPos inside,
            BlockPos outside,
            WeatherSnapshot weatherSnapshot,
            TemporalFieldLease weatherLease,
            TemporalFieldLease blockEventLease
    ) {
        if (weatherLease.isActive()) weatherLease.close();
        if (blockEventLease.isActive()) blockEventLease.close();
        weatherSnapshot.restore(level);
        level.setBlockAndUpdate(inside, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(outside, Blocks.AIR.defaultBlockState());
    }

    private record WeatherSnapshot(
            int clearWeatherTime,
            int rainTime,
            int thunderTime,
            boolean raining,
            boolean thundering,
            boolean advanceWeather
    ) {
        private static WeatherSnapshot capture(
                net.minecraft.server.level.ServerLevel level
        ) {
            var data = level.getWeatherData();
            return new WeatherSnapshot(
                    data.getClearWeatherTime(),
                    data.getRainTime(),
                    data.getThunderTime(),
                    data.isRaining(),
                    data.isThundering(),
                    level.getGameRules().get(GameRules.ADVANCE_WEATHER)
            );
        }

        private void restore(net.minecraft.server.level.ServerLevel level) {
            var data = level.getWeatherData();
            data.setClearWeatherTime(clearWeatherTime);
            data.setRainTime(rainTime);
            data.setThunderTime(thunderTime);
            data.setRaining(raining);
            data.setThundering(thundering);
            level.getGameRules().set(
                    GameRules.ADVANCE_WEATHER,
                    advanceWeather,
                    level.getServer()
            );
        }
    }

    private static final class TemporalLevelSubsystemTestInstance
            extends GameTestInstance {
        private static final MapCodec<TemporalLevelSubsystemTestInstance> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TestData.CODEC.forGetter(TemporalLevelSubsystemTestInstance::info)
                ).apply(instance, TemporalLevelSubsystemTestInstance::new));

        private TemporalLevelSubsystemTestInstance(
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
            return Component.literal("Temporal level subsystem pause resume"
                    .toLowerCase(Locale.ROOT));
        }
    }
}
