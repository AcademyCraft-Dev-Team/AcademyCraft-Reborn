package org.academy.internal.server.time.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalFieldLease;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.time.TemporalScope;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** End-to-end validation for NeoForge tick-event interoperability. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TemporalTickEventCompatibilityGameTests {
    private static final Identifier TEST_INSTANCE_TYPE = AcademyCraft.academy(
            "temporal_tick_event_compatibility_function"
    );

    private TemporalTickEventCompatibilityGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(
                Registries.TEST_INSTANCE_TYPE,
                TEST_INSTANCE_TYPE,
                () -> TemporalTickEventCompatibilityTestInstance.CODEC
        );
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("time/tick_event_compatibility"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        var data = new TestData<>(
                environment,
                Identifier.withDefaultNamespace("empty"),
                60,
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
                AcademyCraft.academy("time_tick_event_compatibility"),
                new TemporalTickEventCompatibilityTestInstance(data)
        );
    }

    private static void runCompatibilityTest(GameTestHelper helper) {
        var target = helper.spawn(EntityTypes.ARMOR_STAND, 1, 2, 1);
        new CompatibilitySession(helper, target).start();
    }

    private static final class CompatibilitySession {
        private final GameTestHelper helper;
        private final Entity target;
        private final TickEventProbe probe;
        @Nullable
        private TemporalFieldLease activeLease;
        private boolean probeRegistered;
        private int entityPreAtPause;
        private int entityPostAtPause;
        private int serverPreAtPause;
        private int levelPreAtPause;
        private int entityPreAtResume;
        private int entityPreAtAcceleration;
        private int entityPostAtAcceleration;

        private CompatibilitySession(GameTestHelper helper, Entity target) {
            this.helper = helper;
            this.target = target;
            probe = new TickEventProbe(target);
        }

        private void start() {
            NeoForge.EVENT_BUS.register(probe);
            probeRegistered = true;
            helper.runAtTickTime(59L, this::cleanup);
            helper.startSequence()
                    .thenWaitUntil(this::awaitBaseline)
                    .thenExecute(() -> guarded(this::beginPause));
        }

        private void awaitBaseline() {
            helper.assertTrue(
                    probe.serverPreTicks > 0 && probe.levelPreTicks > 0,
                    "NeoForge server or level tick events were not dispatched"
            );
            helper.assertTrue(
                    probe.entityPreTicks > 0 && probe.entityPostTicks > 0,
                    "NeoForge entity tick events were not dispatched"
            );
        }

        private void beginPause() {
            activeLease = TemporalApi.get(helper.getLevel().getServer())
                    .acquireField(TemporalField.pause(
                            TemporalScope.dimension(
                                    helper.getLevel().dimension()
                            ),
                            TemporalChannel.worldSimulation(),
                            TemporalPauseSource.ACADEMY_PAUSE
                    ));
            entityPreAtPause = probe.entityPreTicks;
            entityPostAtPause = probe.entityPostTicks;
            serverPreAtPause = probe.serverPreTicks;
            levelPreAtPause = probe.levelPreTicks;
            helper.runAfterDelay(3L, () -> guarded(this::validatePause));
        }

        private void validatePause() {
            helper.assertTrue(
                    probe.serverPreTicks > serverPreAtPause,
                    "ServerTickEvent stopped during temporal pause"
            );
            helper.assertTrue(
                    probe.levelPreTicks > levelPreAtPause,
                    "LevelTickEvent stopped during temporal pause"
            );
            helper.assertValueEqual(
                    probe.entityPreTicks,
                    entityPreAtPause,
                    "EntityTickEvent.Pre bypassed temporal pause"
            );
            helper.assertValueEqual(
                    probe.entityPostTicks,
                    entityPostAtPause,
                    "EntityTickEvent.Post bypassed temporal pause"
            );
            closeActiveLease();
            entityPreAtResume = probe.entityPreTicks;
            helper.runAfterDelay(3L, () -> guarded(this::validateResume));
        }

        private void validateResume() {
            helper.assertTrue(
                    probe.entityPreTicks > entityPreAtResume,
                    "EntityTickEvent did not resume after temporal pause"
            );
            activeLease = TemporalApi.get(helper.getLevel().getServer())
                    .acquireField(new TemporalField(
                            TemporalScope.entities(Set.of(target.getUUID())),
                            Set.of(TemporalChannel.ENTITY),
                            2.0D,
                            TemporalPauseSource.ACADEMY_PAUSE
                    ));
            probe.cancelEntityTicks = true;
            entityPreAtAcceleration = probe.entityPreTicks;
            entityPostAtAcceleration = probe.entityPostTicks;
            helper.runAfterDelay(
                    3L,
                    () -> guarded(this::validateAccelerationAndCancellation)
            );
        }

        private void validateAccelerationAndCancellation() {
            helper.assertTrue(
                    probe.entityPreTicks - entityPreAtAcceleration >= 5,
                    "Accelerated logical ticks did not dispatch EntityTickEvent.Pre"
            );
            helper.assertValueEqual(
                    probe.entityPostTicks,
                    entityPostAtAcceleration,
                    "Canceled accelerated tick dispatched EntityTickEvent.Post"
            );
            cleanup();
            helper.succeed();
        }

        private void guarded(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | Error throwable) {
                cleanup();
                throw throwable;
            }
        }

        private void cleanup() {
            closeActiveLease();
            if (probeRegistered) {
                NeoForge.EVENT_BUS.unregister(probe);
                probeRegistered = false;
            }
            target.discard();
        }

        private void closeActiveLease() {
            if (activeLease == null) return;
            if (activeLease.isActive()) activeLease.close();
            activeLease = null;
        }
    }

    private static final class TickEventProbe {
        private final UUID targetId;
        private final net.minecraft.server.level.ServerLevel targetLevel;
        private int serverPreTicks;
        private int levelPreTicks;
        private int entityPreTicks;
        private int entityPostTicks;
        private boolean cancelEntityTicks;

        private TickEventProbe(Entity target) {
            targetId = target.getUUID();
            targetLevel = (net.minecraft.server.level.ServerLevel) target.level();
        }

        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Pre event) {
            if (event.getServer() == targetLevel.getServer()) serverPreTicks++;
        }

        @SubscribeEvent
        public void onLevelTick(LevelTickEvent.Pre event) {
            if (event.getLevel() == targetLevel) levelPreTicks++;
        }

        @SubscribeEvent
        public void onEntityTickPre(EntityTickEvent.Pre event) {
            if (!event.getEntity().getUUID().equals(targetId)) return;
            entityPreTicks++;
            if (cancelEntityTicks) event.setCanceled(true);
        }

        @SubscribeEvent
        public void onEntityTickPost(EntityTickEvent.Post event) {
            if (event.getEntity().getUUID().equals(targetId)) entityPostTicks++;
        }
    }

    private static final class TemporalTickEventCompatibilityTestInstance
            extends GameTestInstance {
        private static final MapCodec<TemporalTickEventCompatibilityTestInstance>
                CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TestData.CODEC.forGetter(
                                TemporalTickEventCompatibilityTestInstance::info
                        )
                ).apply(
                        instance,
                        TemporalTickEventCompatibilityTestInstance::new
                ));

        private TemporalTickEventCompatibilityTestInstance(
                TestData<Holder<TestEnvironmentDefinition<?>>> info
        ) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            runCompatibilityTest(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Temporal tick event compatibility"
                    .toLowerCase(Locale.ROOT));
        }
    }
}
