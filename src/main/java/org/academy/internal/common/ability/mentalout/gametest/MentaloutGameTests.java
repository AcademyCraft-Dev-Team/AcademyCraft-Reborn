package org.academy.internal.common.ability.mentalout.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlHandle;
import org.academy.api.common.entitycontrol.ControlRequest;
import org.academy.api.common.entitycontrol.MentalControlApi;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;

import java.util.List;
import java.util.Locale;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MentaloutGameTests {
    private static final Identifier TEST_ENVIRONMENT = AcademyCraft.academy("mentalout");
    private static final Identifier TEST_INSTANCE_TYPE = AcademyCraft.academy("mentalout_function");
    private static final Identifier SOURCE = AcademyCraft.academy("gametest_mental_control");
    private static final Identifier IMPRESSION_GUARD_SOURCE = AcademyCraft.academy("impression_guard_target");

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
        var environment = event.registerEnvironment(
                TEST_ENVIRONMENT,
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        for (var scenario : Scenario.values()) {
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

    @SuppressWarnings("removal")
    private static ServerPlayer createController(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var position = helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(
                new net.minecraft.core.BlockPos(1, 2, 1)
        ));
        player.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
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
        MOB_FORCE_TARGET("mob_force_target", 40) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var cow = helper.spawn(EntityTypes.COW, 3, 2, 1);
                zombie.setPersistenceRequired();
                cow.setPersistenceRequired();
                var handle = forceTarget(controller, zombie, cow, 30L);

                helper.runAtTickTime(5L, () -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(zombie) == cow,
                            "Zombie lost the effective forced target"
                    );
                    helper.assertTrue(zombie.getTarget() == cow, "Zombie did not adopt the forced target");
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
        MOB_FREEZE_RECOVERY("mob_freeze_recovery", 50) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var zombie = helper.spawn(EntityTypes.ZOMBIE, 1, 2, 1);
                var villager = helper.spawn(EntityTypes.VILLAGER, 12, 2, 1);
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
        IMPRESSION_STRICT_HOSTILITY_CHAIN("impression_strict_hostility_chain", 200) {
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
        WARDEN_FORCE_TARGET("warden_force_target", 40) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var warden = helper.spawn(EntityTypes.WARDEN, 1, 2, 1);
                var cow = helper.spawn(EntityTypes.COW, 3, 2, 1);
                warden.setPersistenceRequired();
                cow.setPersistenceRequired();
                var handle = forceTarget(controller, warden, cow, 30L);

                helper.runAtTickTime(5L, () -> {
                    helper.assertTrue(
                            MentalControlRuntime.getForcedTarget(warden) == cow,
                            "Warden lost the effective forced target"
                    );
                    helper.assertTrue(
                            warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == cow,
                            "Warden brain did not retain ATTACK_TARGET"
                    );
                    finish(helper, controller, handle);
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
        DRAGON_PHASE_CONTROL("dragon_phase_control", 50) {
            @Override
            void run(GameTestHelper helper) {
                var controller = createController(helper);
                var dragon = helper.spawn(EntityTypes.ENDER_DRAGON, 1, 8, 1);
                var cow = helper.spawn(EntityTypes.COW, 3, 2, 1);
                cow.setPersistenceRequired();
                var handle = forceTarget(controller, dragon, cow, 35L);

                helper.runAtTickTime(5L, () -> {
                    helper.assertValueEqual(
                            dragon.getPhaseManager().getCurrentPhase().getPhase(),
                            EnderDragonPhase.STRAFE_PLAYER,
                            "Dragon forced-target phase"
                    );
                    handle.close();
                    helper.assertValueEqual(
                            dragon.getPhaseManager().getCurrentPhase().getPhase(),
                            EnderDragonPhase.HOLDING_PATTERN,
                            "Dragon release phase"
                    );
                    helper.getLevel().getServer().getPlayerList().remove(controller);
                    helper.succeed();
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
