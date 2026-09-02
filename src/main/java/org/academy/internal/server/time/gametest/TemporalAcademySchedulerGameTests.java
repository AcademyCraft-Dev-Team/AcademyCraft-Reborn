package org.academy.internal.server.time.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramNodePurity;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramNodeScope;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalFieldLease;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.time.TemporalScope;
import org.academy.internal.common.ability.program.CompiledProgram;
import org.academy.internal.common.ability.program.ProgramSessionScheduler;
import org.academy.internal.common.ability.program.ServerProgramScheduler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** End-to-end validation for owner-local Academy scheduler time. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TemporalAcademySchedulerGameTests {
    private static final Identifier TEST_INSTANCE_TYPE =
            AcademyCraft.academy("temporal_academy_scheduler_function");
    private static final Identifier CATEGORY =
            AcademyCraft.academy("temporal_scheduler_test");
    private static final Identifier ENTRY_NODE =
            AcademyCraft.academy("temporal_scheduler_test_entry");
    private static final ProgramNodeType<Unit> ENTRY_TYPE = entryType();
    private static final CompiledProgram IMMEDIATE_PROGRAM = immediateProgram();

    private TemporalAcademySchedulerGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(
                Registries.TEST_INSTANCE_TYPE,
                TEST_INSTANCE_TYPE,
                () -> TemporalAcademySchedulerTestInstance.CODEC
        );
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("time/academy_scheduler_owner_pause"),
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
                AcademyCraft.academy("time_academy_scheduler_owner_pause"),
                new TemporalAcademySchedulerTestInstance(data)
        );
    }

    private static void runOwnerPauseTest(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var pausedOwner = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
        var normalOwner = helper.spawn(EntityTypes.ZOMBIE, 3, 2, 1);
        var pausedKey = new ServerProgramScheduler.SessionKey(
                pausedOwner.getUUID(),
                CATEGORY,
                UUID.randomUUID()
        );
        var normalKey = new ServerProgramScheduler.SessionKey(
                normalOwner.getUUID(),
                CATEGORY,
                UUID.randomUUID()
        );
        var pausedTermination =
                new AtomicReference<ProgramSessionScheduler.TerminationKind>();
        var normalTermination =
                new AtomicReference<ProgramSessionScheduler.TerminationKind>();
        var lease = TemporalApi.get(server).acquireField(TemporalField.pause(
                TemporalScope.entities(Set.of(pausedOwner.getUUID())),
                Set.of(TemporalChannel.ACADEMY_SCHEDULER),
                TemporalPauseSource.ACADEMY_PAUSE
        ));

        try {
            helper.assertTrue(
                    start(server, pausedKey, pausedTermination),
                    "Paused owner program session did not start"
            );
            helper.assertTrue(
                    start(server, normalKey, normalTermination),
                    "Normal owner program session did not start"
            );
            helper.runAfterDelay(2L, () -> validateOwnerIsolation(
                    helper,
                    pausedOwner,
                    normalOwner,
                    pausedKey,
                    normalKey,
                    pausedTermination,
                    normalTermination,
                    lease
            ));
        } catch (RuntimeException | Error throwable) {
            cleanup(
                    server,
                    pausedOwner,
                    normalOwner,
                    pausedKey,
                    normalKey,
                    lease
            );
            throw throwable;
        }
    }

    private static void validateOwnerIsolation(
            GameTestHelper helper,
            net.minecraft.world.entity.Entity pausedOwner,
            net.minecraft.world.entity.Entity normalOwner,
            ServerProgramScheduler.SessionKey pausedKey,
            ServerProgramScheduler.SessionKey normalKey,
            AtomicReference<ProgramSessionScheduler.TerminationKind> pausedTermination,
            AtomicReference<ProgramSessionScheduler.TerminationKind> normalTermination,
            TemporalFieldLease lease
    ) {
        var server = helper.getLevel().getServer();
        try {
            helper.assertTrue(
                    pausedTermination.get() == null,
                    "Entity-local pause did not stop its Academy program session"
            );
            helper.assertValueEqual(
                    normalTermination.get(),
                    ProgramSessionScheduler.TerminationKind.COMPLETED,
                    "Entity-local pause leaked into another owner's program session"
            );
            lease.close();
            helper.runAfterDelay(2L, () -> {
                try {
                    helper.assertValueEqual(
                            pausedTermination.get(),
                            ProgramSessionScheduler.TerminationKind.COMPLETED,
                            "Academy program session did not resume after the field closed"
                    );
                    helper.succeed();
                } finally {
                    cleanup(
                            server,
                            pausedOwner,
                            normalOwner,
                            pausedKey,
                            normalKey,
                            lease
                    );
                }
            });
        } catch (RuntimeException | Error throwable) {
            cleanup(
                    server,
                    pausedOwner,
                    normalOwner,
                    pausedKey,
                    normalKey,
                    lease
            );
            throw throwable;
        }
    }

    private static boolean start(
            net.minecraft.server.MinecraftServer server,
            ServerProgramScheduler.SessionKey key,
            AtomicReference<ProgramSessionScheduler.TerminationKind> termination
    ) {
        return ServerProgramScheduler.start(
                server,
                key,
                IMMEDIATE_PROGRAM,
                ignored -> null,
                null,
                2,
                20L,
                (_, result) -> termination.set(result.kind())
        );
    }

    private static void cleanup(
            net.minecraft.server.MinecraftServer server,
            net.minecraft.world.entity.Entity pausedOwner,
            net.minecraft.world.entity.Entity normalOwner,
            ServerProgramScheduler.SessionKey pausedKey,
            ServerProgramScheduler.SessionKey normalKey,
            TemporalFieldLease lease
    ) {
        if (lease.isActive()) lease.close();
        ServerProgramScheduler.cancel(server, pausedKey);
        ServerProgramScheduler.cancel(server, normalKey);
        pausedOwner.discard();
        normalOwner.discard();
    }

    private static CompiledProgram immediateProgram() {
        var graphNode = new ProgramGraph.Node(
                0,
                ENTRY_NODE,
                1,
                new JsonObject()
        );
        var compiledNode = new CompiledProgram.CompiledNode(
                0,
                ENTRY_NODE,
                ENTRY_TYPE,
                Unit.INSTANCE,
                ProgramNodeRole.ENTRY,
                ProgramNodeSchema.EMPTY
        );
        return new CompiledProgram(
                new ProgramGraph(List.of(graphNode), List.of()),
                0,
                Map.of(0, compiledNode),
                List.of(),
                Map.of(),
                Map.of()
        );
    }

    private static ProgramNodeType<Unit> entryType() {
        return new ProgramNodeType<>() {
            @Override
            public Codec<Unit> configurationCodec() {
                return MapCodec.unit(Unit.INSTANCE).codec();
            }

            @Override
            public int schemaVersion() {
                return 1;
            }

            @Override
            public ProgramNodeSchema schema(Unit configuration) {
                return ProgramNodeSchema.EMPTY;
            }

            @Override
            public ProgramNodeRole role() {
                return ProgramNodeRole.ENTRY;
            }

            @Override
            public ProgramNodePurity purity() {
                return ProgramNodePurity.STATE;
            }

            @Override
            public ProgramNodeScope scope() {
                return ProgramNodeScope.COMMON;
            }
        };
    }

    private enum Unit {
        INSTANCE
    }

    private static final class TemporalAcademySchedulerTestInstance
            extends GameTestInstance {
        private static final MapCodec<TemporalAcademySchedulerTestInstance> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TestData.CODEC.forGetter(
                                TemporalAcademySchedulerTestInstance::info
                        )
                ).apply(instance, TemporalAcademySchedulerTestInstance::new));

        private TemporalAcademySchedulerTestInstance(
                TestData<Holder<TestEnvironmentDefinition<?>>> info
        ) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            runOwnerPauseTest(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Temporal Academy scheduler owner pause");
        }
    }
}
