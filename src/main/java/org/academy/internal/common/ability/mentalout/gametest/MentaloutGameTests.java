package org.academy.internal.common.ability.mentalout.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.*;
import org.academy.internal.common.ability.mentalout.control.CubeMobMoveControlAccess;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.control.MentalPerceptionRuntime;
import org.academy.internal.common.ability.mentalout.skills.MentaloutTargeting;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MentaloutGameTests {
    private static final Identifier TEST_INSTANCE_TYPE = AcademyCraft.academy("mentalout_function");
    private static final Identifier SOURCE = AcademyCraft.academy("gametest_mental_control");
    private static final Identifier PERCEPTION_SOURCE = AcademyCraft.academy("gametest_mental_perception");
    private static final Identifier IMPRESSION_GUARD_SOURCE = AcademyCraft.academy("impression_guard_target");
    private static final AtomicInteger NEXT_CONTROLLER_ID = new AtomicInteger();

    private MentaloutGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(
                Registries.TEST_INSTANCE_TYPE,
                TEST_INSTANCE_TYPE,
                () -> MentaloutTestInstance.CODEC
        );
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : Scenario.values()) {
            // Each scenario owns its environment batch. The vanilla runner executes tests in the
            // same environment concurrently, which makes server-player and entity-ticking tests
            // interfere with one another even when their structures are spatially separated.
            var environment = event.registerEnvironment(
                    AcademyCraft.academy("mentalout/" + scenario.serializedName),
                    new TestEnvironmentDefinition.AllOf(List.of())
            );
            var data = new TestData<>(
                    environment,
                    Identifier.withDefaultNamespace("empty"),
                    scenario.maxTicks,
                    0,
                    true,
                    Rotation.NONE,
                    false,
                    1,
                    1,
                    false,
                    32
            );
            event.registerTest(
                    AcademyCraft.academy("mentalout_" + scenario.serializedName),
                    new MentaloutTestInstance(scenario, data)
            );
        }
    }

    private static ControlHandle forceTarget(
            ServerPlayer controller,
            LivingEntity subject,
            LivingEntity target,
            long duration
    ) {
        return MentalControlApi.apply(new ControlRequest(
                controller,
                subject,
                SOURCE,
                100,
                subject.level().getGameTime() + duration,
                List.of(new ControlDirective.ForceTarget(target.getUUID()))
        ));
    }

    private static ControlHandle forceTargetPermanently(
            ServerPlayer controller,
            LivingEntity subject,
            LivingEntity target
    ) {
        return MentalControlApi.apply(ControlRequest.permanent(
                controller,
                subject,
                SOURCE,
                100,
                new ControlDirective.ForceTarget(target.getUUID())
        ));
    }

    private static ControlHandle freeze(ServerPlayer controller, LivingEntity subject, long duration) {
        return MentalControlApi.apply(new ControlRequest(
                controller,
                subject,
                SOURCE,
                100,
                subject.level().getGameTime() + duration,
                List.of(new ControlDirective.FreezeAi())
        ));
    }

    private static ControlHandle impression(ServerPlayer controller, LivingEntity subject) {
        return MentalControlApi.apply(ControlRequest.permanent(
                controller,
                subject,
                SOURCE,
                100,
                new ControlDirective.ImpressionAlliance()
        ));
    }

    private static ControlHandle moveTo(
            ServerPlayer controller,
            LivingEntity subject,
            Vec3 destination
    ) {
        return MentalControlApi.apply(ControlRequest.permanent(
                controller,
                subject,
                SOURCE,
                100,
                new ControlDirective.MoveTo(new ControlDestination.Position(
                        subject.level().dimension().identifier(),
                        destination
                ))
        ));
    }

    private static ControlHandle impressionGuardTarget(
            ServerPlayer controller,
            LivingEntity subject,
            LivingEntity target,
            long duration
    ) {
        return MentalControlApi.apply(new ControlRequest(
                controller,
                subject,
                IMPRESSION_GUARD_SOURCE,
                Integer.MIN_VALUE,
                subject.level().getGameTime() + duration,
                List.of(new ControlDirective.ForceTarget(target.getUUID()))
        ));
    }

    private static ServerPlayer createController(GameTestHelper helper) {
        var profile = new GameProfile(
                UUID.randomUUID(),
                "ac-test-" + NEXT_CONTROLLER_ID.incrementAndGet()
        );
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation()
        ) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        var position = helper.absoluteVec(Vec3.atBottomCenterOf(
                new BlockPos(1, 2, 1)
        ));
        player.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        GameType.CREATIVE.updatePlayerAbilities(player.getAbilities());
        return player;
    }

    private static void finish(
            GameTestHelper helper,
            ServerPlayer controller,
            ControlHandle handle
    ) {
        handle.close();
        helper.getLevel().getServer().getPlayerList().remove(controller);
        helper.succeed();
    }

    private static void prepareArena(GameTestHelper helper) {
        for (var x = -2; x <= 16; x++) {
            for (var z = -2; z <= 4; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private enum Scenario {
        MOB_FORCE_TARGET("mob_force_target", 70) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var cow = helper.spawn(EntityTypes.COW, 3, 2, 1);
                zombie.setPersistenceRequired();
                zombie.setNoAi(true);
                cow.setPersistenceRequired();
                cow.setNoAi(true);
                var initialHealth = cow.getHealth();
                var handle = forceTarget(controller, zombie, cow, 60L);

                helper.runAtTickTime(2L, () -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(zombie) == cow,
                            "Zombie lost the effective forced target"
                    );
                    helper.assertTrue(zombie.getTarget() == cow, "Zombie did not adopt the forced target");
                    helper.assertTrue(zombie.isAggressive(),
                            "Generic forced-combat executor did not activate immediately");
                });
                helper.runAtTickTime(50L, () -> {
                    helper.assertTrue(cow.getHealth() < initialHealth,
                            "Generic forced-combat executor did not damage its target with native AI disabled");
                    finish(helper, controller, handle);
                });
            }
        },
        PLAYER_FREEZE_AND_RELATION("player_freeze_and_relation", 40) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var subject = createController(helper);
                var subjectPosition = helper.absoluteVec(Vec3.atBottomCenterOf(
                        new BlockPos(4, 2, 1)
                ));
                subject.snapTo(subjectPosition.x, subjectPosition.y, subjectPosition.z, 0.0f, 0.0f);
                var freeze = freeze(controller, subject, 8L);
                var relation = impression(controller, subject);

                helper.runAtTickTime(3L, () -> {
                    helper.assertTrue(MentalControlRuntime.isFrozen(subject),
                            "Player adapter did not expose an effective freeze lease");
                    helper.assertValueEqual(
                            MentalControlApi.allianceDecision(subject, controller),
                            AttackDecision.DENY,
                            "Player impression alliance decision"
                    );
                });
                helper.runAtTickTime(12L, () -> {
                    helper.assertTrue(freeze.isClosed(), "Player freeze did not expire");
                    helper.assertFalse(MentalControlRuntime.isFrozen(subject),
                            "Player remained frozen after lease expiry");
                    relation.close();
                    helper.getLevel().getServer().getPlayerList().remove(subject);
                    finish(helper, controller, freeze);
                });
            }
        },
        PLAYER_PATH_REQUIRES_CLIENT_READY("player_path_requires_client_ready", 55) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var subject = createController(helper);
                var subjectPosition = helper.absoluteVec(Vec3.atBottomCenterOf(
                        new BlockPos(3, 2, 1)
                ));
                subject.snapTo(subjectPosition.x, subjectPosition.y, subjectPosition.z, 0.0f, 0.0f);
                var destination = helper.absoluteVec(Vec3.atBottomCenterOf(
                        new BlockPos(8, 2, 1)
                ));
                var handle = MentalControlApi.apply(ControlRequest.permanent(
                        controller,
                        subject,
                        SOURCE,
                        250,
                        new ControlDirective.MoveTo(new ControlDestination.Position(
                                helper.getLevel().dimension().identifier(), destination))
                ));

                helper.runAtTickTime(5L, () -> helper.assertFalse(
                        handle.isClosed(), "Player path closed before the Ready deadline"));
                helper.runAtTickTime(32L, () -> {
                    helper.assertTrue(handle.isClosed(), "Unconfirmed player path did not time out");
                    helper.assertValueEqual(
                            handle.failureReason().orElse(null),
                            ControlFailureReason.CLIENT_TIMEOUT,
                            "Player path handshake failure reason"
                    );
                    helper.getLevel().getServer().getPlayerList().remove(subject);
                    finish(helper, controller, handle);
                });
            }
        },
        MOB_PERMANENT_TARGET_LIFECYCLE("mob_permanent_target_lifecycle", 220) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var cow = helper.spawn(EntityTypes.COW, 3, 2, 1);
                zombie.setPersistenceRequired();
                cow.setPersistenceRequired();
                cow.setInvulnerable(true);
                var handle = forceTargetPermanently(controller, zombie, cow);

                helper.runAtTickTime(170L, () -> {
                    helper.assertFalse(handle.isClosed(), "Permanent target lease expired without explicit release");
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(zombie) == cow,
                            "Permanent forced target was not maintained"
                    );
                    cow.setInvulnerable(false);
                    cow.kill(helper.getLevel());
                });
                helper.runAtTickTime(175L, () -> {
                    helper.assertTrue(handle.isClosed(), "Target death did not release permanent target lease");
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(zombie) == null,
                            "Dead forced target remained effective"
                    );
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                    helper.succeed();
                });
            }
        },
        SIGHT_DESTINATION_ENTITY_AND_BLOCK("sight_destination_entity_and_block", 30) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var target = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 3);
                target.setPersistenceRequired();
                controller.snapTo(controller.getX(), controller.getY(), controller.getZ(), 0.0f, 0.0f);

                helper.runAtTickTime(2L, () -> {
                    var entityHit = MentaloutTargeting.findSightDestination(
                            controller, MentaloutTargeting.MAX_SIGHT_RANGE);
                    helper.assertTrue(
                            entityHit instanceof ControlDestination.Entity(var uuid)
                                    && uuid.equals(target.getUUID()),
                            "Sight destination did not select the looked-at entity"
                    );

                    var moved = helper.absoluteVec(Vec3.atBottomCenterOf(
                            new BlockPos(12, 2, 3)
                    ));
                    target.snapTo(moved.x, moved.y, moved.z, 0.0f, 0.0f);
                    helper.setBlock(1, 3, 4, Blocks.STONE);
                });
                helper.runAtTickTime(4L, () -> {
                    var blockHit = MentaloutTargeting.findSightDestination(
                            controller, MentaloutTargeting.MAX_SIGHT_RANGE);
                    var expected = helper.absoluteVec(Vec3.atBottomCenterOf(
                            new BlockPos(1, 3, 3)
                    ));
                    helper.assertTrue(
                            blockHit instanceof ControlDestination.Position(var dimension, var value)
                                    && dimension.equals(helper.getLevel().dimension().identifier())
                                    && value.distanceToSqr(expected) < 1.0e-6,
                            "Sight destination did not return the outside face position"
                    );
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                    helper.succeed();
                });
            }
        },
        PATH_POSITION_COMPLETES_IN_RADIUS("path_position_completes_in_radius", 160) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 2, 2, 1);
                zombie.setPersistenceRequired();
                var destination = helper.absoluteVec(Vec3.atBottomCenterOf(
                        new BlockPos(4, 2, 1)
                ));
                var handle = MentalControlApi.apply(ControlRequest.permanent(
                        controller,
                        zombie,
                        SOURCE,
                        100,
                        new ControlDirective.MoveTo(new ControlDestination.Position(
                                helper.getLevel().dimension().identifier(),
                                destination
                        ))
                ));
                var closestDistanceSqr = new double[]{zombie.position().distanceToSqr(destination)};
                helper.onEachTick(() -> closestDistanceSqr[0] = Math.min(
                        closestDistanceSqr[0], zombie.position().distanceToSqr(destination)));

                helper.runAtTickTime(3L, () -> {
                    helper.assertFalse(handle.isClosed(), "Move-to lease completed outside the one-block radius");
                    helper.assertTrue(
                            zombie.position().distanceToSqr(destination) > 1.0,
                            "Controlled mob began inside the one-block arrival radius"
                    );
                });
                helper.runAtTickTime(80L, () -> {
                    helper.assertTrue(handle.isClosed(), "Move-to lease did not complete after navigation");
                    helper.assertTrue(
                            closestDistanceSqr[0] <= 1.0,
                            "Controlled mob never entered the one-block arrival radius: closestDistanceSqr="
                                    + closestDistanceSqr[0]
                    );
                    finish(helper, controller, handle);
                });
            }
        },
        PATH_CONTROL_REJECTS_VANILLA_TARGET_AND_ROUTE("path_control_rejects_vanilla_target_and_route", 180) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 4, 2, 1);
                var villager = helper.spawn(EntityTypes.VILLAGER, 1, 2, 1);
                zombie.setPersistenceRequired();
                villager.setPersistenceRequired();
                villager.setNoAi(true);
                zombie.setTarget(villager);
                zombie.getNavigation().moveTo(villager, 1.0);

                var destination = helper.absoluteVec(Vec3.atBottomCenterOf(
                        new BlockPos(10, 2, 1)
                ));
                var handle = MentalControlApi.apply(ControlRequest.permanent(
                        controller,
                        zombie,
                        SOURCE,
                        100,
                        new ControlDirective.MoveTo(new ControlDestination.Position(
                                helper.getLevel().dimension().identifier(),
                                destination
                        ))
                ));
                var minimumXWhileControlled = new double[]{zombie.getX()};
                var closestDistanceSqr = new double[]{zombie.position().distanceToSqr(destination)};
                helper.onEachTick(() -> {
                    if (handle.isClosed()) return;
                    minimumXWhileControlled[0] = Math.min(minimumXWhileControlled[0], zombie.getX());
                    closestDistanceSqr[0] = Math.min(
                            closestDistanceSqr[0], zombie.position().distanceToSqr(destination));
                });

                helper.runAtTickTime(10L, () -> {
                    helper.assertTrue(zombie.getTarget() == null,
                            "Path control retained the vanilla villager target");
                    var attackTarget = zombie.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
                    helper.assertTrue(attackTarget == null || attackTarget.isEmpty(),
                            "Path control retained a vanilla ATTACK_TARGET memory");
                });
                helper.runAtTickTime(150L, () -> {
                    helper.assertTrue(handle.isClosed(),
                            "Exclusive mental route did not complete: distanceSqr="
                                    + zombie.position().distanceToSqr(destination)
                                    + ", failure=" + handle.failureReason().orElse(null));
                    helper.assertTrue(handle.failureReason().isEmpty(),
                            "Exclusive mental route failed: " + handle.failureReason().orElse(null));
                    helper.assertTrue(closestDistanceSqr[0] <= 1.0,
                            "Zombie never reached the mental destination before the lease completed");
                    helper.assertTrue(minimumXWhileControlled[0]
                                    >= helper.absolutePos(new BlockPos(3, 2, 1)).getX(),
                            "Zombie moved back toward its vanilla target during mental navigation");
                    finish(helper, controller, handle);
                });
            }
        },
        CUBE_MOB_PATH_AND_CUBE_DAMAGE("cube_mob_path_and_cube_damage", 180) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var attacker = helper.spawn(EntityTypes.SLIME, 2, 2, 1);
                var victim = helper.spawn(EntityTypes.SLIME, 3, 2, 3);
                attacker.setSize(2, true);
                victim.setSize(2, true);
                attacker.setPersistenceRequired();
                victim.setPersistenceRequired();
                victim.setNoAi(true);
                var destination = helper.absoluteVec(Vec3.atBottomCenterOf(
                        new BlockPos(6, 2, 1)
                ));
                var pathHandle = MentalControlApi.apply(ControlRequest.permanent(
                        controller,
                        attacker,
                        SOURCE,
                        100,
                        new ControlDirective.MoveTo(new ControlDestination.Position(
                                helper.getLevel().dimension().identifier(),
                                destination
                        ))
                ));
                var closestDistanceSqr = new double[]{attacker.position().distanceToSqr(destination)};
                helper.onEachTick(() -> closestDistanceSqr[0] = Math.min(
                        closestDistanceSqr[0], attacker.position().distanceToSqr(destination)));

                helper.runAtTickTime(3L, () -> helper.assertTrue(
                        attacker.getMoveControl() instanceof CubeMobMoveControlAccess,
                        "Cube move control bridge was not applied"
                ));

                helper.runAtTickTime(170L, () -> {
                    helper.assertTrue(pathHandle.isClosed(),
                            "Slime move-to lease did not complete: distanceSqr="
                                    + attacker.position().distanceToSqr(destination)
                                    + ", failure=" + pathHandle.failureReason().orElse(null));
                    helper.assertTrue(pathHandle.failureReason().isEmpty(),
                            "Slime movement ended abnormally: distanceSqr="
                                    + attacker.position().distanceToSqr(destination)
                                    + ", failure=" + pathHandle.failureReason().orElse(null));
                    helper.assertTrue(closestDistanceSqr[0] <= 1.0,
                            "Slime never entered the one-block arrival radius: closestDistanceSqr="
                                    + closestDistanceSqr[0]);

                    var targetHandle = forceTargetPermanently(controller, attacker, victim);
                    victim.snapTo(
                            attacker.getX() + 0.25,
                            attacker.getY(),
                            attacker.getZ(),
                            victim.getYRot(),
                            victim.getXRot()
                    );
                    var oldHealth = victim.getHealth();
                    attacker.push(victim);
                    helper.assertTrue(
                            victim.getHealth() < oldHealth,
                            "Controlled slime could not damage another slime on contact"
                    );
                    targetHandle.close();
                    finish(helper, controller, pathHandle);
                });
            }
        },
        PATH_UNREACHABLE_REPORTS_FAILURE("path_unreachable_reports_failure", 80) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 6, 2, 1);
                zombie.setPersistenceRequired();
                for (var x = 9; x <= 11; x++) {
                    for (var z = 0; z <= 2; z++) {
                        if (x == 10 && z == 1) continue;
                        for (var y = 1; y <= 5; y++) {
                            helper.setBlock(x, y, z, Blocks.STONE);
                        }
                    }
                }
                helper.setBlock(10, 2, 1, Blocks.STONE);
                helper.setBlock(10, 5, 1, Blocks.STONE);
                var destination = helper.absoluteVec(Vec3.atBottomCenterOf(
                        new BlockPos(10, 2, 1)
                ));
                var handle = MentalControlApi.apply(ControlRequest.permanent(
                        controller,
                        zombie,
                        SOURCE,
                        100,
                        new ControlDirective.MoveTo(new ControlDestination.Position(
                                helper.getLevel().dimension().identifier(),
                                destination
                        ))
                ));

                helper.runAtTickTime(3L, () ->
                        helper.assertFalse(handle.isClosed(), "Unreachable path failed before three attempts"));
                helper.runAtTickTime(45L, () -> {
                    var path = zombie.getNavigation().getPath();
                    helper.assertTrue(handle.isClosed(), "Unreachable path remained active after three attempts; "
                            + "canOccupy=" + helper.getLevel().noCollision(zombie,
                            zombie.getBoundingBox().move(destination.subtract(zombie.position())))
                            + ", path=" + path + ", canReach="
                            + (path == null ? null : path.canReach()));
                    helper.assertValueEqual(
                            handle.failureReason().orElse(null),
                            ControlFailureReason.UNREACHABLE_DESTINATION,
                            "Unreachable path failure reason"
                    );
                    finish(helper, controller, handle);
                });
            }
        },
        GUARD_REACTIVE_TARGET_AND_RETURN("guard_reactive_target_and_return", 80) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                controller.setInvulnerable(true);
                var guardian = helper.spawn(EntityTypes.ZOMBIE, 3, 2, 1);
                var threat = helper.spawn(EntityTypes.SKELETON, 6, 2, 1);
                guardian.setPersistenceRequired();
                threat.setPersistenceRequired();
                threat.setInvulnerable(true);
                var threatHandle = forceTarget(controller, threat, controller, 40L);
                var handle = MentalControlApi.apply(ControlRequest.permanent(
                        controller,
                        guardian,
                        SOURCE,
                        100,
                        new ControlDirective.Guard(new ControlDestination.Entity(controller.getUUID()))
                ));

                helper.runAtTickTime(10L, () -> {
                    helper.assertFalse(handle.isClosed(), "Guard lease closed before threat selection");
                    helper.assertTrue(
                            MentalControlRuntime.effectiveDirective(guardian, ControlCapability.GUARD_CONTROL)
                                    .orElse(null) instanceof ControlDirective.Guard,
                            "Guard directive was not effective"
                    );
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(threat) == controller,
                            "Threat did not retain its forced controller target"
                    );
                    helper.assertTrue(
                            MentalControlRuntime.getGuardTarget(guardian) == threat,
                            "Guard binding did not retain the selected threat"
                    );
                    helper.assertTrue(guardian.getTarget() == threat, "Guard did not select the active threat");
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(guardian, controller),
                            AttackDecision.DENY,
                            "Guard protection decision"
                    );
                    threat.setInvulnerable(false);
                    threat.kill(helper.getLevel());
                    threatHandle.close();
                });
                helper.runAtTickTime(60L, () -> {
                    helper.assertTrue(
                            guardian.getTarget() != controller,
                            "Guard attacked its controller while returning to the anchor"
                    );
                    helper.assertTrue(
                            guardian.position().distanceToSqr(controller.position()) <= 1.0,
                            "Guard did not stop within the one-block dynamic anchor radius: distanceSqr="
                                    + guardian.position().distanceToSqr(controller.position())
                    );
                    finish(helper, controller, handle);
                });
            }
        },
        MOB_FREEZE_RECOVERY("mob_freeze_recovery", 50) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var villager = helper.spawn(EntityTypes.VILLAGER, 8, 2, 1);
                zombie.setPersistenceRequired();
                villager.setPersistenceRequired();
                var handle = freeze(controller, zombie, 8L);
                var recoveryStart = new double[2];

                helper.runAtTickTime(3L, () -> {
                    helper.assertTrue(MentalControlRuntime.isFrozen(zombie), "Zombie freeze ended too early");
                    helper.assertTrue(zombie.getNavigation().isDone(), "Frozen zombie retained a path");
                    helper.assertTrue(!zombie.isNoAi(), "Freeze must not persist through the vanilla no-AI flag");
                });
                helper.runAtTickTime(11L, () -> {
                    helper.assertFalse(MentalControlRuntime.isFrozen(zombie), "Zombie remained frozen after expiry");
                    recoveryStart[0] = zombie.getX();
                    recoveryStart[1] = zombie.getZ();
                    zombie.setTarget(villager);
                    helper.assertTrue(
                            zombie.getNavigation().moveTo(villager, 1.0),
                            "Zombie could not create a path after freeze expiry"
                    );
                });
                helper.runAtTickTime(16L, () -> {
                    var movedX = zombie.getX() - recoveryStart[0];
                    var movedZ = zombie.getZ() - recoveryStart[1];
                    helper.assertTrue(
                            movedX * movedX + movedZ * movedZ > 0.01,
                            "Zombie AI did not resume movement after freeze expiry"
                    );
                    finish(helper, controller, handle);
                });
            }
        },
        PERCEPTION_MULTI_SOURCE_OVERRIDE("perception_multi_source_override", 40) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var cow = helper.spawn(EntityTypes.COW, 3, 2, 1);
                zombie.setPersistenceRequired();
                cow.setPersistenceRequired();
                zombie.setTarget(cow);
                var first = MentalPerceptionRuntime.apply(
                        controller,
                        zombie,
                        cow,
                        PERCEPTION_SOURCE,
                        100,
                        Long.MAX_VALUE
                );
                var second = MentalPerceptionRuntime.apply(
                        controller,
                        zombie,
                        cow,
                        AcademyCraft.academy("gametest_mental_perception_secondary"),
                        100,
                        Long.MAX_VALUE
                );
                var forced = new ControlHandle[1];

                helper.runAtTickTime(3L, () -> {
                    helper.assertValueEqual(
                            MentalPerceptionApi.perceptionDecision(zombie, cow),
                            PerceptionDecision.HIDDEN,
                            "Layered perception decision"
                    );
                    helper.assertFalse(
                            zombie.getSensing().hasLineOfSight(cow),
                            "Hidden target remained visible through Mob sensing"
                    );
                    helper.assertTrue(
                            zombie.getTarget() == null,
                            "Perception mask retained the hidden natural target"
                    );
                    forced[0] = forceTarget(controller, zombie, cow, 20L);
                });
                helper.runAtTickTime(6L, () -> {
                    helper.assertValueEqual(
                            MentalPerceptionApi.perceptionDecision(zombie, cow),
                            PerceptionDecision.PASS,
                            "Explicit target did not override perception masking"
                    );
                    forced[0].close();
                    first.close();
                    helper.assertValueEqual(
                            MentalPerceptionApi.perceptionDecision(zombie, cow),
                            PerceptionDecision.HIDDEN,
                            "Closing one source removed the remaining perception mask"
                    );
                    second.close();
                    helper.assertValueEqual(
                            MentalPerceptionApi.perceptionDecision(zombie, cow),
                            PerceptionDecision.PASS,
                            "Closing every source did not restore perception"
                    );
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                    helper.succeed();
                });
            }
        },
        RELATION_FORCE_TARGET_OVERRIDE("relation_force_target_override", 40) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var skeleton = helper.spawn(EntityTypes.SKELETON, 3, 2, 1);
                zombie.setPersistenceRequired();
                skeleton.setPersistenceRequired();
                var zombieRelation = impression(controller, zombie);
                var skeletonRelation = impression(controller, skeleton);
                var forcedTarget = forceTarget(controller, zombie, skeleton, 30L);

                helper.runAtTickTime(3L, () -> {
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(zombie, controller),
                            AttackDecision.DENY,
                            "Controller alliance decision"
                    );
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(skeleton, zombie),
                            AttackDecision.DENY,
                            "Roster alliance decision"
                    );
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(zombie, skeleton),
                            AttackDecision.ALLOW,
                            "Forced target relation override"
                    );
                    forcedTarget.close();
                    skeletonRelation.close();
                    zombieRelation.close();
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                    helper.succeed();
                });
            }
        },
        IMPRESSION_GUARDIAN_HOSTILITY("impression_guardian_hostility", 40) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var guardian = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var aggressor = helper.spawn(EntityTypes.SKELETON, 3, 2, 1);
                guardian.setPersistenceRequired();
                aggressor.setPersistenceRequired();
                var relation = impression(controller, guardian);

                MentalControlRuntime.alertImpressionAllies(controller, aggressor);
                helper.runAtTickTime(3L, () -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(guardian) == aggressor,
                            "Impression-controlled mob did not become hostile to its controller's aggressor"
                    );
                    helper.assertTrue(
                            guardian.getTarget() == aggressor,
                            "Guardian mob did not adopt the derived hostile target"
                    );
                    relation.close();
                });
                helper.runAtTickTime(6L, () -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(guardian) == null,
                            "Derived guardian hostility survived impression release"
                    );
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                    helper.succeed();
                });
            }
        },
        IMPRESSION_STRICT_HOSTILITY_CHAIN("impression_strict_hostility_chain", 400) {
            @Override
            void run(GameTestHelper helper) {
                var level = helper.getLevel();
                var controller = createController(helper);
                GameType.SURVIVAL.updatePlayerAbilities(controller.getAbilities());
                controller.connection.markClientLoaded();
                var guardian = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var villager = helper.spawn(EntityTypes.VILLAGER, 4, 2, 1);
                var selfAggressor = helper.spawn(EntityTypes.SKELETON, 7, 2, 1);
                var controllerAggressor = helper.spawn(EntityTypes.PILLAGER, 10, 2, 1);
                var targetingAggressor = helper.spawn(EntityTypes.DROWNED, 13, 2, 1);
                guardian.setPersistenceRequired();
                villager.setPersistenceRequired();
                selfAggressor.setPersistenceRequired();
                controllerAggressor.setPersistenceRequired();
                targetingAggressor.setPersistenceRequired();
                villager.setInvulnerable(true);
                selfAggressor.setInvulnerable(true);
                controllerAggressor.setInvulnerable(true);
                targetingAggressor.setInvulnerable(true);
                selfAggressor.setNoAi(true);
                controllerAggressor.setNoAi(true);
                targetingAggressor.setNoAi(true);

                var relation = impression(controller, guardian);
                var explicitTarget = new ControlHandle[1];
                var controllerTarget = new ControlHandle[1];
                var newerGuardTarget = new ControlHandle[1];

                helper.startSequence().thenExecuteAfter(2, () -> {
                    guardian.setTarget(villager);
                    helper.assertTrue(
                            guardian.getTarget() == null,
                            "Impression allowed an ordinary villager target"
                    );
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(guardian, villager),
                            AttackDecision.DENY,
                            "Ordinary hostility decision"
                    );

                    helper.assertTrue(
                            guardian.hurtServer(
                                    level,
                                    level.damageSources().mobAttack(selfAggressor),
                                    2.0F
                            ),
                            "Guardian did not receive real retaliation damage"
                    );
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(guardian, selfAggressor),
                            AttackDecision.ALLOW,
                            "Damage retaliation authorization"
                    );
                }).thenWaitUntil(() -> {
                    helper.assertTrue(
                            guardian.getLastHurtByMob() == selfAggressor,
                            "Guardian did not record its real attacker"
                    );
                    helper.assertTrue(
                            guardian.getTarget() == selfAggressor,
                            "Guardian did not naturally retaliate against its attacker"
                    );
                }).thenExecute(() -> {
                    var previousHealth = controller.getHealth();
                    helper.assertTrue(
                            controller.hurtServer(
                                    level,
                                    level.damageSources().mobAttack(controllerAggressor),
                                    2.0F
                            ),
                            "Controller did not receive real guard-triggering damage"
                    );
                    helper.assertTrue(
                            controller.getHealth() < previousHealth,
                            "Controller damage event did not inflict health damage"
                    );
                }).thenWaitUntil(() -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(guardian) == controllerAggressor,
                            "Controller damage did not trigger guardian hostility"
                    );
                }).thenExecute(() -> {
                    controllerTarget[0] = forceTarget(
                            controller,
                            targetingAggressor,
                            controller,
                            20L
                    );
                    helper.assertTrue(
                            targetingAggressor.getTarget() == controller,
                            "Aggressor could not establish the controller as its final target"
                    );
                }).thenWaitUntil(() -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(guardian) == targetingAggressor,
                            "Controller target event did not replace the guardian target"
                    );
                }).thenExecute(() -> {
                    controllerTarget[0].close();
                    explicitTarget[0] = forceTarget(controller, guardian, villager, 40L);
                    newerGuardTarget[0] = impressionGuardTarget(
                            controller,
                            guardian,
                            controllerAggressor,
                            40L
                    );
                }).thenWaitUntil(() -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(guardian) == villager,
                            "Explicit ForceTarget did not override impression hostility"
                    );
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(guardian, villager),
                            AttackDecision.ALLOW,
                            "Explicit ForceTarget hostility decision"
                    );
                    helper.assertTrue(
                            guardian.getTarget() == villager,
                            "Guardian did not adopt the explicit ForceTarget"
                    );
                }).thenExecute(() -> {
                    explicitTarget[0].close();
                }).thenWaitUntil(() -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(guardian) == controllerAggressor,
                            "Newer low-priority impression guard did not resume after explicit target release"
                    );
                    helper.assertTrue(
                            guardian.getTarget() == controllerAggressor,
                            "Guardian did not resume the derived impression target"
                    );
                }).thenExecute(() -> {
                    relation.close();
                    GameType.CREATIVE.updatePlayerAbilities(controller.getAbilities());
                    villager.setInvulnerable(false);
                }).thenWaitUntil(() -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(guardian) == null,
                            "Impression guard target survived relation release"
                    );
                    helper.assertTrue(
                            newerGuardTarget[0].isClosed(),
                            "Derived impression guard handle remained active after relation release"
                    );
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(guardian, villager),
                            AttackDecision.PASS,
                            "Released impression retained a hostility restriction"
                    );
                }).thenWaitUntil(() -> {
                    helper.assertTrue(
                            guardian.getTarget() == villager,
                            "Zombie target selector did not autonomously recover after impression release"
                    );
                }).thenExecute(() -> {
                    villager.setInvulnerable(true);
                    newerGuardTarget[0].close();
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                }).thenSucceed();
            }
        },
        IMPRESSION_INTERNAL_ANGER_WHITELIST("impression_internal_anger_whitelist", 50) {
            @Override
            void run(GameTestHelper helper) {
                var level = helper.getLevel();
                var controller = createController(helper);
                var neutral = helper.spawn(EntityTypes.ZOMBIFIED_PIGLIN, 1, 2, 1);
                var brainMob = helper.spawn(EntityTypes.PIGLIN, 4, 2, 1);
                var outsider = helper.spawn(EntityTypes.VILLAGER, 7, 2, 1);
                var aggressor = helper.spawn(EntityTypes.SKELETON, 10, 2, 1);
                neutral.setPersistenceRequired();
                brainMob.setPersistenceRequired();
                outsider.setPersistenceRequired();
                aggressor.setPersistenceRequired();
                outsider.setInvulnerable(true);
                aggressor.setInvulnerable(true);
                outsider.setNoAi(true);
                aggressor.setNoAi(true);

                var neutralRelation = impression(controller, neutral);
                var brainRelation = impression(controller, brainMob);
                neutral.setPersistentAngerTarget(EntityReference.of(outsider));
                neutral.setTimeToRemainAngry(200L);
                brainMob.getBrain().setMemory(MemoryModuleType.ANGRY_AT, outsider.getUUID());
                brainMob.getBrain().setMemory(MemoryModuleType.UNIVERSAL_ANGER, true);
                brainMob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, outsider);

                helper.runAtTickTime(3L, () -> {
                    helper.assertTrue(
                            neutral.getPersistentAngerTarget() == null && !neutral.isAngry(),
                            "Impression retained unauthorized persistent anger"
                    );
                    helper.assertTrue(
                            brainMob.getBrain().getMemory(MemoryModuleType.ANGRY_AT).isEmpty(),
                            "Impression retained unauthorized ANGRY_AT memory"
                    );
                    helper.assertTrue(
                            brainMob.getBrain().getMemory(MemoryModuleType.UNIVERSAL_ANGER).isEmpty(),
                            "Impression retained universal anger"
                    );
                    helper.assertTrue(
                            brainMob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(),
                            "Impression retained an unauthorized Brain attack target"
                    );
                    helper.assertTrue(
                            neutral.hurtServer(
                                    level,
                                    level.damageSources().mobAttack(aggressor),
                                    2.0F
                            ),
                            "Neutral mob did not receive retaliation damage"
                    );
                });

                helper.runAtTickTime(7L, () -> {
                    var angerTarget = neutral.getPersistentAngerTarget();
                    helper.assertTrue(
                            angerTarget != null && angerTarget.matches(aggressor),
                            "Authorized retaliation did not become persistent anger"
                    );
                    helper.assertValueEqual(
                            MentalControlApi.attackDecision(neutral, aggressor),
                            AttackDecision.ALLOW,
                            "Authorized persistent anger decision"
                    );
                    brainRelation.close();
                    neutralRelation.close();
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                    helper.succeed();
                });
            }
        },
        DIRECT_FLYING_MOBS_PATH("direct_flying_mobs_path", 220) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var ghast = helper.spawn(EntityTypes.GHAST, 2, 10, 0);
                var phantom = helper.spawn(EntityTypes.PHANTOM, 2, 16, 1);
                var blaze = helper.spawn(EntityTypes.BLAZE, 2, 5, 3);
                var vex = helper.spawn(EntityTypes.VEX, 2, 21, 4);
                var subjects = new Mob[]{ghast, phantom, blaze, vex};
                for (var subject : subjects) subject.setPersistenceRequired();

                var destinations = new Vec3[]{
                        helper.absoluteVec(new Vec3(8.5, 10.0, 0.5)),
                        helper.absoluteVec(new Vec3(8.5, 16.0, 1.5)),
                        helper.absoluteVec(new Vec3(8.5, 8.0, 3.5)),
                        helper.absoluteVec(new Vec3(8.5, 21.0, 4.5))
                };
                var handles = new ControlHandle[subjects.length];
                var closestDistanceSqr = new double[subjects.length];
                for (var i = 0; i < subjects.length; i++) {
                    handles[i] = moveTo(controller, subjects[i], destinations[i]);
                    closestDistanceSqr[i] = subjects[i].position().distanceToSqr(destinations[i]);
                }
                helper.onEachTick(() -> {
                    for (var i = 0; i < subjects.length; i++) {
                        closestDistanceSqr[i] = Math.min(
                                closestDistanceSqr[i],
                                subjects[i].position().distanceToSqr(destinations[i])
                        );
                    }
                });

                helper.runAtTickTime(200L, () -> {
                    var names = new String[]{"Ghast", "Phantom", "Blaze", "Vex"};
                    for (var i = 0; i < subjects.length; i++) {
                        helper.assertTrue(handles[i].isClosed(), names[i]
                                + " move-to lease remained active: closestDistanceSqr="
                                + closestDistanceSqr[i] + ", failure="
                                + handles[i].failureReason().orElse(null));
                        helper.assertTrue(handles[i].failureReason().isEmpty(), names[i]
                                + " movement failed: " + handles[i].failureReason().orElse(null)
                                + ", closestDistanceSqr=" + closestDistanceSqr[i]);
                        helper.assertTrue(closestDistanceSqr[i] <= 1.0, names[i]
                                + " never entered the one-block arrival radius: closestDistanceSqr="
                                + closestDistanceSqr[i]);
                        handles[i].close();
                    }
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                    helper.succeed();
                });
            }
        },
        WITHER_FLIGHT_PATH("wither_flight_path", 180) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var wither = helper.spawn(EntityTypes.WITHER, 2, 8, 1);
                wither.setPersistenceRequired();
                wither.setInvulnerableTicks(0);
                var destination = helper.absoluteVec(new Vec3(8.5, 10.0, 1.5));
                var handle = moveTo(controller, wither, destination);
                var closestDistanceSqr = new double[]{wither.position().distanceToSqr(destination)};
                helper.onEachTick(() -> closestDistanceSqr[0] = Math.min(
                        closestDistanceSqr[0], wither.position().distanceToSqr(destination)));

                helper.runAtTickTime(160L, () -> {
                    helper.assertTrue(handle.isClosed(), "Wither move-to lease remained active: closestDistanceSqr="
                            + closestDistanceSqr[0] + ", failure=" + handle.failureReason().orElse(null));
                    helper.assertTrue(handle.failureReason().isEmpty(),
                            "Wither movement failed: " + handle.failureReason().orElse(null)
                                    + ", closestDistanceSqr=" + closestDistanceSqr[0]);
                    helper.assertTrue(closestDistanceSqr[0] <= 1.0,
                            "Wither never entered the one-block arrival radius: closestDistanceSqr="
                                    + closestDistanceSqr[0]);
                    finish(helper, controller, handle);
                });
            }
        },
        WARDEN_FORCE_TARGET("warden_force_target", 90) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var warden = helper.spawn(EntityTypes.WARDEN, 1, 2, 1);
                var target = helper.spawn(EntityTypes.WARDEN, 3, 2, 1);
                warden.setPersistenceRequired();
                target.setPersistenceRequired();
                target.setNoAi(true);
                var initialHealth = target.getHealth();
                var handle = forceTarget(controller, warden, target, 80L);

                helper.runAtTickTime(2L, () -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(warden) == target,
                            "Warden lost the effective forced target"
                    );
                    helper.assertTrue(
                            warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == target,
                            "Warden brain did not retain ATTACK_TARGET"
                    );
                    helper.assertFalse(warden.canTargetEntity(target),
                            "Test fixture no longer exercises an entity-specific target rejection");
                    helper.assertTrue(warden.isAggressive(),
                            "Generic forced-combat executor did not activate immediately");
                });
                helper.runAtTickTime(70L, () -> {
                    helper.assertTrue(target.getHealth() < initialHealth,
                            "Controlled Warden did not damage another Warden within 3.5 seconds");
                    finish(helper, controller, handle);
                });
            }
        },
        WARDEN_PATH_RELATION_AND_DIG_GUARD("warden_path_relation_and_dig_guard", 220) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var warden = helper.spawn(EntityTypes.WARDEN, 2, 2, 1);
                var cow = helper.spawn(EntityTypes.COW, 2, 2, 4);
                cow.setPersistenceRequired();
                cow.setNoAi(true);
                var destination = helper.absoluteVec(Vec3.atBottomCenterOf(
                        new BlockPos(6, 2, 1)
                ));
                var pathHandle = MentalControlApi.apply(ControlRequest.permanent(
                        controller,
                        warden,
                        SOURCE,
                        100,
                        new ControlDirective.MoveTo(new ControlDestination.Position(
                                helper.getLevel().dimension().identifier(),
                                destination
                        ))
                ));
                var closestDistanceSqr = new double[]{warden.position().distanceToSqr(destination)};
                helper.onEachTick(() -> closestDistanceSqr[0] = Math.min(
                        closestDistanceSqr[0], warden.position().distanceToSqr(destination)));

                helper.runAtTickTime(100L, () -> {
                    helper.assertTrue(pathHandle.isClosed(), "Warden move-to lease did not complete: distanceSqr="
                            + warden.position().distanceToSqr(destination)
                            + ", failure=" + pathHandle.failureReason().orElse(null));
                    helper.assertTrue(
                            closestDistanceSqr[0] <= 1.0,
                            "Warden never entered the one-block arrival radius: closestDistanceSqr="
                                    + closestDistanceSqr[0]
                    );

                    var relation = impression(controller, warden);
                    warden.increaseAngerAt(cow, 150, false);
                    var oldHealth = cow.getHealth();
                    var hurt = cow.hurtServer(
                            helper.getLevel(),
                            helper.getLevel().damageSources().sonicBoom(warden),
                            10.0F
                    );
                    helper.assertFalse(hurt, "Impression-controlled Warden damaged an unauthorized creature");
                    helper.assertValueEqual(cow.getHealth(), oldHealth, "Unauthorized Warden damage changed health");

                    warden.getBrain().eraseMemory(MemoryModuleType.DIG_COOLDOWN);
                    warden.setPose(Pose.DIGGING);
                    var relationStart = warden.position();
                    helper.runAtTickTime(170L, () -> {
                        helper.assertTrue(warden.isAlive() && !warden.isRemoved(),
                                "Controlled Warden disappeared through its digging activity");
                        helper.assertFalse(warden.hasPose(Pose.DIGGING),
                                "Controlled Warden remained in the digging pose");
                        helper.assertTrue(warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(),
                                "Impression-controlled Warden retained its special AI attack target");
                        helper.assertTrue(warden.position().distanceToSqr(relationStart) < 1.0,
                                "Impression-controlled Warden followed its original special AI route");
                        relation.close();
                        finish(helper, controller, pathHandle);
                    });
                });
            }
        },
        WITHER_THREE_HEAD_TARGET("wither_three_head_target", 50) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var wither = helper.spawn(EntityTypes.WITHER, 1, 4, 1);
                var cow = helper.spawn(EntityTypes.COW, 3, 2, 1);
                wither.setPersistenceRequired();
                wither.setInvulnerableTicks(0);
                cow.setPersistenceRequired();
                var handle = forceTarget(controller, wither, cow, 35L);

                helper.runAtTickTime(5L, () -> {
                    for (var head = 0; head < 3; head++) {
                        helper.assertValueEqual(
                                wither.getAlternativeTarget(head),
                                cow.getId(),
                                "Wither head " + head + " target"
                        );
                    }
                    finish(helper, controller, handle);
                });
            }
        },
        DRAGON_PHASE_CONTROL("dragon_phase_control", 400) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var dragon = helper.spawn(EntityTypes.ENDER_DRAGON, 1, 20, 1);
                var cow = helper.spawn(EntityTypes.COW, 3, 2, 1);
                cow.setPersistenceRequired();
                var handle = forceTarget(controller, dragon, cow, 35L);
                var destination = helper.absoluteVec(new Vec3(8.5, 20.0, 1.5));
                var moveHandle = new ControlHandle[1];
                var closestDistanceSqr = new double[]{dragon.position().distanceToSqr(destination)};
                helper.onEachTick(() -> closestDistanceSqr[0] = Math.min(
                        closestDistanceSqr[0], dragon.position().distanceToSqr(destination)));

                helper.runAtTickTime(5L, () -> {
                    helper.assertValueEqual(
                            dragon.getPhaseManager().getCurrentPhase().getPhase(),
                            EnderDragonPhase.STRAFE_PLAYER,
                            "Dragon forced-target phase"
                    );
                    handle.close();
                    moveHandle[0] = moveTo(controller, dragon, destination);
                });
                helper.runAtTickTime(360L, () -> {
                    helper.assertTrue(moveHandle[0] != null && moveHandle[0].isClosed(),
                            "Dragon move-to lease remained active: closestDistanceSqr="
                                    + closestDistanceSqr[0] + ", failure="
                                    + (moveHandle[0] == null ? null
                                    : moveHandle[0].failureReason().orElse(null))
                                    + ", alive=" + dragon.isAlive()
                                    + ", noAi=" + dragon.isNoAi()
                                    + ", phase=" + dragon.getPhaseManager().getCurrentPhase().getPhase()
                                    + ", movement=" + dragon.getDeltaMovement());
                    helper.assertTrue(moveHandle[0].failureReason().isEmpty(),
                            "Dragon movement failed: " + moveHandle[0].failureReason().orElse(null));
                    helper.assertTrue(closestDistanceSqr[0] <= 1.0,
                            "Dragon never entered the one-block arrival radius: closestDistanceSqr="
                                    + closestDistanceSqr[0]);
                    var viewHandle = MentalControlApi.apply(ControlRequest.permanent(
                            controller,
                            dragon,
                            SOURCE,
                            100,
                            new ControlDirective.LookAt(cow.getUUID())
                    ));
                    helper.runAtTickTime(370L, () -> {
                        var delta = cow.getEyePosition().subtract(dragon.getEyePosition());
                        var expectedYaw = (float) (Mth.atan2(delta.z, delta.x) * 180.0 / Mth.PI) - 90.0F;
                        helper.assertTrue(Mth.degreesDifferenceAbs(
                                        dragon.getYRot(), expectedYaw) <= 5.0F,
                                "Dragon view control was overwritten by its phase AI");
                        viewHandle.close();
                        moveHandle[0].close();
                        helper.getLevel().getServer().getPlayerList().remove(controller);
                        helper.succeed();
                    });
                });
            }
        };

        private static final Codec<Scenario> CODEC = Codec.STRING.comapFlatMap(
                name -> {
                    for (var scenario : values()) {
                        if (scenario.serializedName.equals(name)) return DataResult.success(scenario);
                    }
                    return DataResult.error(() -> "Unknown Mentalout GameTest scenario: " + name);
                },
                scenario -> scenario.serializedName
        );

        private final String serializedName;
        private final int maxTicks;

        Scenario(String serializedName, int maxTicks) {
            this.serializedName = serializedName;
            this.maxTicks = maxTicks;
        }

        abstract void run(GameTestHelper helper);
    }

    private static final class MentaloutTestInstance extends GameTestInstance {
        private static final MapCodec<MentaloutTestInstance> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Scenario.CODEC.fieldOf("scenario").forGetter(test -> test.scenario),
                        TestData.CODEC.forGetter(MentaloutTestInstance::info)
                ).apply(instance, MentaloutTestInstance::new)
        );

        private final Scenario scenario;

        private MentaloutTestInstance(
                Scenario scenario,
                TestData<Holder<TestEnvironmentDefinition<?>>> info
        ) {
            super(info);
            this.scenario = scenario;
        }

        @Override
        public void run(GameTestHelper helper) {
            prepareArena(helper);
            scenario.run(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Mentalout " + scenario.name().toLowerCase(Locale.ROOT));
        }
    }
}
