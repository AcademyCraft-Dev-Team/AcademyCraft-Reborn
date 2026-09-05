package org.academy.internal.server.time.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** End-to-end pause, slowdown, acceleration and rollback coverage for players. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TemporalPlayerTickGameTests {
    private static final Identifier TEST_INSTANCE_TYPE = AcademyCraft.academy(
            "temporal_player_tick_function"
    );

    private TemporalPlayerTickGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(
                Registries.TEST_INSTANCE_TYPE,
                TEST_INSTANCE_TYPE,
                () -> TemporalPlayerTickTestInstance.CODEC
        );
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("time/player_tick_scaling"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        var data = new TestData<>(
                environment,
                Identifier.withDefaultNamespace("empty"),
                90,
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
                AcademyCraft.academy("time_player_tick_scaling"),
                new TemporalPlayerTickTestInstance(data)
        );
    }

    private static void runPlayerTickTest(GameTestHelper helper) {
        new Session(
                helper,
                createPlayer(helper, "time-a-"),
                createPlayer(helper, "time-b-")
        ).start();
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, String prefix) {
        var profile = new GameProfile(
                UUID.randomUUID(),
                prefix + UUID.randomUUID().toString().substring(0, 8)
        );
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation()
        );
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        var server = helper.getLevel().getServer();
        server.getConnection().getConnections().add(connection);
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
        } catch (RuntimeException | Error throwable) {
            server.getConnection().getConnections().remove(connection);
            throw throwable;
        }
        player.connection.markClientLoaded();
        player.setGameMode(GameType.SURVIVAL);
        var position = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
        player.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        return player;
    }

    private static int playTime(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
    }

    private static final class Session {
        private final GameTestHelper helper;
        private final ServerPlayer controlled;
        private final ServerPlayer reference;
        @Nullable
        private TemporalFieldLease lease;
        private int controlledTicks;
        private int controlledPlayTime;
        private int referencePlayTime;
        private boolean cleaned;

        private Session(
                GameTestHelper helper,
                ServerPlayer controlled,
                ServerPlayer reference
        ) {
            this.helper = helper;
            this.controlled = controlled;
            this.reference = reference;
        }

        private void start() {
            controlledPlayTime = playTime(controlled);
            referencePlayTime = playTime(reference);
            helper.runAtTickTime(89L, this::cleanup);
            helper.runAfterDelay(3L, () -> guarded(this::beginPause));
        }

        private void beginPause() {
            helper.assertTrue(
                    playTime(controlled) - controlledPlayTime >= 2
                            && playTime(reference) - referencePlayTime >= 2,
                    "Connected test players did not receive baseline simulation ticks"
            );
            lease = field(Set.of(controlled.getUUID()), 0.0D);
            snapshot();
            helper.runAfterDelay(4L, () -> guarded(this::validatePause));
        }

        private void validatePause() {
            helper.assertValueEqual(
                    controlled.tickCount,
                    controlledTicks,
                    "ServerPlayer.tick advanced during a hard pause"
            );
            helper.assertValueEqual(
                    playTime(controlled),
                    controlledPlayTime,
                    "ServerPlayer.doTick advanced during a hard pause"
            );
            helper.assertTrue(
                    playTime(reference) - referencePlayTime >= 3,
                    "An entity-scoped player pause leaked to another player"
            );
            helper.assertTrue(
                    controlled.connection.isAcceptingMessages(),
                    "Hard pause stopped the physical player connection"
            );
            closeLease();
            snapshot();
            helper.runAfterDelay(3L, () -> guarded(this::validateResume));
        }

        private void validateResume() {
            assertDeltas(3, "Player simulation did not resume cleanly");
            lease = field(Set.of(controlled.getUUID()), 0.5D);
            snapshot();
            helper.runAfterDelay(6L, () -> guarded(this::validateSlowdown));
        }

        private void validateSlowdown() {
            assertDeltas(3, "Half-speed player tick plan was not deterministic");
            closeLease();
            lease = field(
                    Set.of(controlled.getUUID(), reference.getUUID()),
                    2.0D
            );
            snapshot();
            helper.runAfterDelay(3L, () -> guarded(this::validateAcceleration));
        }

        private void validateAcceleration() {
            assertDeltas(6, "Accelerated player tick phases diverged");
            helper.assertValueEqual(
                    playTime(reference) - referencePlayTime,
                    6,
                    "Multi-player acceleration did not reach every selected player"
            );
            closeLease();
            snapshot();
            helper.runAfterDelay(2L, () -> guarded(this::validateRollback));
        }

        private void validateRollback() {
            assertDeltas(2, "Full-speed rollback retained stale temporal state");
            cleanup();
            helper.succeed();
        }

        private TemporalFieldLease field(Set<UUID> playerIds, double scale) {
            return TemporalApi.get(helper.getLevel().getServer()).acquireField(
                    new TemporalField(
                            TemporalScope.entities(playerIds),
                            Set.of(TemporalChannel.ENTITY),
                            scale,
                            TemporalPauseSource.ACADEMY_PAUSE
                    )
            );
        }

        private void snapshot() {
            controlledTicks = controlled.tickCount;
            controlledPlayTime = playTime(controlled);
            referencePlayTime = playTime(reference);
        }

        private void assertDeltas(int expected, String message) {
            helper.assertValueEqual(
                    controlled.tickCount - controlledTicks,
                    expected,
                    message + " (entity phase)"
            );
            helper.assertValueEqual(
                    playTime(controlled) - controlledPlayTime,
                    expected,
                    message + " (simulation phase)"
            );
        }

        private void guarded(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | Error throwable) {
                cleanup();
                throw throwable;
            }
        }

        private void closeLease() {
            if (lease == null) return;
            if (lease.isActive()) lease.close();
            lease = null;
        }

        private void cleanup() {
            if (cleaned) return;
            cleaned = true;
            closeLease();
            var server = helper.getLevel().getServer();
            var players = server.getPlayerList();
            server.getConnection().getConnections().remove(
                    controlled.connection.getConnection()
            );
            server.getConnection().getConnections().remove(
                    reference.connection.getConnection()
            );
            if (players.getPlayer(controlled.getUUID()) == controlled) {
                players.remove(controlled);
            }
            if (players.getPlayer(reference.getUUID()) == reference) {
                players.remove(reference);
            }
        }
    }

    private static final class TemporalPlayerTickTestInstance
            extends GameTestInstance {
        private static final MapCodec<TemporalPlayerTickTestInstance> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TestData.CODEC.forGetter(TemporalPlayerTickTestInstance::info)
                ).apply(instance, TemporalPlayerTickTestInstance::new));

        private TemporalPlayerTickTestInstance(
                TestData<Holder<TestEnvironmentDefinition<?>>> info
        ) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            runPlayerTickTest(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Temporal player tick scaling"
                    .toLowerCase(Locale.ROOT));
        }
    }
}
