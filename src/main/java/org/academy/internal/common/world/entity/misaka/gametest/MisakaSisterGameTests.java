package org.academy.internal.common.world.entity.misaka.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.common.world.entity.misaka.ai.MisakaCropGrazeGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaForageFoodGoal;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MisakaSisterGameTests {
    private static final Identifier TEST_INSTANCE_TYPE = AcademyCraft.academy("misaka_sister_function");

    private MisakaSisterGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(
                Registries.TEST_INSTANCE_TYPE,
                TEST_INSTANCE_TYPE,
                () -> MisakaSisterTestInstance.CODEC
        );
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        assertMskAnchors();
        register(event, "spawn", "Unawakened spawn smoke", MisakaSisterGameTests::runSpawnSmoke);
        register(event, "starving", "Starving not enemy", MisakaSisterGameTests::runStarvingNotEnemy);
        register(event, "tower_awaken", "Tower awaken", MisakaSisterGameTests::runTowerAwaken);
        register(event, "waiting", "Waiting wander style", MisakaSisterGameTests::runWaitingStyle);
        register(event, "reconstruction", "Reconstruction clamp", MisakaSisterGameTests::runReconstructionClamp);
        register(event, "monster_aggro", "Monster aggro", MisakaSisterGameTests::runMonsterAggro);
        register(event, "starve_zero", "Starve at zero food", MisakaSisterGameTests::runStarveZero);
        register(event, "promax_ladder", "Promax cap ladder", MisakaSisterGameTests::runPromaxLadder);
        register(event, "daily_decay", "Daily perception decay", MisakaSisterGameTests::runDailyDecay);
        register(event, "crop_graze", "Crop graze when hungry", MisakaSisterGameTests::runCropGraze);
        register(event, "forage_food", "Forage favorite from copper chest", MisakaSisterGameTests::runForageFood);
        register(event, "unload_favor", "Roster favor without entity", MisakaSisterGameTests::runUnloadFavor);
        register(event, "bind_reject", "Bind reject reconstruction", MisakaSisterGameTests::runBindReject);
        register(event, "sleep_wake", "Sleep wakes at day", MisakaSisterGameTests::runSleepWake);
    }

    private static void register(
            RegisterGameTestsEvent event,
            String name,
            String description,
            Consumer<GameTestHelper> runner
    ) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("misaka/sister/" + name),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        var data = new TestData<>(
                environment,
                Identifier.withDefaultNamespace("empty"),
                100,
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
                AcademyCraft.academy("misaka_sister_" + name),
                new MisakaSisterTestInstance(description, runner, data)
        );
    }

    static void assertMskAnchors() {
        if (Math.abs(MisakaComputeContribution.mskPerSecond(100) - 100f) > 0.001f
                || Math.abs(MisakaComputeContribution.mskPerSecond(110) - 120f) > 0.001f
                || Math.abs(MisakaComputeContribution.mskPerSecond(200) - 400f) > 0.001f) {
            throw new IllegalStateException("Misaka MSk anchors drifted from plan values");
        }
    }

    private static void runSpawnSmoke(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.assertFalse(sister.isAwakened(), "Rescued sister should start unawakened");
        helper.assertTrue(sister.canBeLeashed(), "Unawakened sister must be leashable");
        helper.assertTrue(sister.canBeSeenAsEnemy(), "Unawakened non-starving sister should be attackable");
        helper.assertValueEqual(20, sister.getFoodLevel(), "Unawakened food must stay frozen at full");
        helper.assertTrue(sister.getTarget() == null, "Unawakened sister must not enter combat");
        helper.succeed();
    }

    private static void runStarvingNotEnemy(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            record.starving = true;
            sister.bindToRecord(record);
            helper.assertTrue(sister.isStarving(), "Sister should be starving");
            helper.assertFalse(sister.canBeSeenAsEnemy(), "Starving sister must not be seen as enemy");
            helper.succeed();
        });
    }

    private static void runTowerAwaken(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var server = helper.getLevel().getServer();
            var record = sister.rosterRecord().orElseThrow();
            long gameTime = helper.getLevel().getGameTime();
            PerceptionService.awakenWithTower(record, "feeder", gameTime);
            MisakaSisterRoster.get(server).modify(record.misakaUuid, sisterRecord -> {
                sisterRecord.awakened = record.awakened;
                sisterRecord.perception = record.perception;
                sisterRecord.awakeWindowEndGameTime = record.awakeWindowEndGameTime;
                sisterRecord.favorByPlayerName.putAll(record.favorByPlayerName);
            });
            sister.bindToRecord(record);
            helper.assertTrue(sister.isAwakened(), "Tower should awaken sister");
            helper.assertValueEqual(1, record.perception, "Awakened perception starts at 1");
            helper.assertValueEqual(1, record.favorByPlayerName.get("feeder"), "Feeder gains +1 favor");
            helper.assertValueEqual(
                    gameTime + PerceptionService.AWAKE_WINDOW_TICKS,
                    record.awakeWindowEndGameTime,
                    "Awake witness window should last 1000 ticks"
            );
            helper.succeed();
        });
    }

    private static void runReconstructionClamp(GameTestHelper helper) {
        var leader = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        var follower = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 3, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var server = helper.getLevel().getServer();
            var roster = MisakaSisterRoster.get(server);
            var leaderRecord = leader.rosterRecord().orElseThrow();
            var followerRecord = follower.rosterRecord().orElseThrow();
            BlockPos node = new BlockPos(8, 64, 8);
            roster.modify(leaderRecord.misakaUuid, rec -> {
                rec.awakened = true;
                rec.perception = 105;
                rec.highTierUnlocked = true;
                rec.networkNodePos = node;
            });
            roster.modify(followerRecord.misakaUuid, rec -> {
                rec.awakened = true;
                rec.perception = 98;
                rec.perceptionCap = PerceptionService.PROMAX_CAP;
                rec.networkNodePos = node;
            });
            leader.bindToRecord(roster.get(leaderRecord.misakaUuid).orElseThrow());
            follower.bindToRecord(roster.get(followerRecord.misakaUuid).orElseThrow());
            var clampTarget = roster.get(followerRecord.misakaUuid).orElseThrow();
            helper.assertTrue(
                    MisakaNAT.get().hasReconstructionWork(server, node, followerRecord.misakaUuid),
                    "Same-network sister above 101 should block second reconstruction"
            );
            helper.assertFalse(
                    PerceptionService.tryBreakLimit(server, clampTarget),
                    "Promax must fail while another sister holds reconstruction work"
            );
            PerceptionService.gain(server, clampTarget, 5);
            helper.assertValueEqual(100, clampTarget.perception, "Gain across 101 must clamp to 100");
            helper.succeed();
        });
    }

    private static void runMonsterAggro(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            record.starving = false;
            sister.bindToRecord(record);
            sister.getFoodData().setFoodLevel(10);
            sister.syncFoodLevel();
            helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 2, 1, 1);
        });
        helper.runAtTickTime(60L, () -> {
            var zombie = helper.getLevel().getEntitiesOfClass(Mob.class, sister.getBoundingBox().inflate(8.0))
                    .stream()
                    .filter(mob -> mob instanceof Enemy)
                    .findFirst()
                    .orElse(null);
            helper.assertTrue(zombie != null, "Zombie should spawn near sister");
            helper.assertTrue(zombie.getTarget() == sister, "Zombie should target non-starving sister");
            helper.succeed();
        });
    }

    private static void runStarveZero(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            sister.bindToRecord(record);
            sister.getFoodData().setFoodLevel(0);
            sister.syncFoodLevel();
        });
        helper.runAtTickTime(5L, () -> {
            helper.assertTrue(sister.isStarving(), "Sister should be starving at food level 0");
            helper.assertFalse(sister.canBeSeenAsEnemy(), "Starving sister must not be seen as enemy");
            sister.setHealth(0.5f);
        });
        helper.runAtTickTime(10L, () -> {
            helper.assertTrue(sister.getHealth() >= 1.0f, "Starving sister HP must stay at least 1");
            var record = sister.rosterRecord().orElseThrow();
            helper.assertTrue(record.starving, "Roster starving flag should sync from entity");
            helper.succeed();
        });
    }

    private static void runPromaxLadder(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var server = helper.getLevel().getServer();
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            record.perception = 90;
            sister.bindToRecord(record);
            helper.assertTrue(PerceptionService.tryBreakLimit(server, record), "Promax should break base cap");
            helper.assertValueEqual(PerceptionService.PROMAX_CAP, record.perceptionCap, "Cap should rise to 110");
            helper.assertValueEqual(100, record.perception, "Promax grants +10 perception");
            PerceptionService.gain(server, record, 5);
            helper.assertTrue(record.highTierUnlocked, "Crossing 101 unlocks high tier");
            helper.assertValueEqual(PerceptionService.HIGH_TIER_CAP, record.perceptionCap, "Cap should rise to 200");
            helper.succeed();
        });
    }

    private static void runWaitingStyle(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            record.wanderStyle = WanderStyle.WAITING;
            sister.bindToRecord(record);
            sister.setWanderStyle(WanderStyle.WAITING);
            helper.assertValueEqual(
                    WanderStyle.WAITING.ordinal(),
                    sister.getWanderStyle().ordinal(),
                    "Waiting style should sync to entity"
            );
            var followGoal = sister.followGoal();
            var wanderGoal = sister.networkWanderGoal();
            helper.assertFalse(followGoal != null && followGoal.canUse(), "Follow must not run while waiting");
            helper.assertFalse(wanderGoal != null && wanderGoal.canUse(), "Wander must not run while waiting");
            helper.succeed();
        });
    }

    private static void runDailyDecay(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            record.perception = 100;
            record.highTierUnlocked = false;
            PerceptionService.applyDailyDecay(record);
            helper.assertValueEqual(94, record.perception, "p=100 daily loss should be 1+5=6");

            record.perception = 105;
            record.highTierUnlocked = true;
            PerceptionService.applyDailyDecay(record);
            helper.assertTrue(record.perception >= 101, "highTierUnlocked floor must stay at 101");
            helper.assertValueEqual(101, record.perception, "105 with loss 6 floors at 101");

            record.perception = 40;
            record.highTierUnlocked = false;
            record.promaxUsed = true;
            PerceptionService.applyDailyDecay(record);
            helper.assertValueEqual(37, record.perception, "promax without high tier must not use 101 floor");
            helper.succeed();
        });
    }

    private static void runCropGraze(GameTestHelper helper) {
        helper.setBlock(1, 0, 1, Blocks.FARMLAND);
        helper.setBlock(1, 1, 1, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 2, 1);
        helper.runAtTickTime(1L, () -> {
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            sister.bindToRecord(record);
            sister.getFoodData().setFoodLevel(5);
            sister.syncFoodLevel();
            var cropPos = helper.absolutePos(new BlockPos(1, 1, 1));
            sister.snapTo(cropPos.getX() + 0.5, cropPos.getY() + 1.0, cropPos.getZ() + 0.5, 0.0f, 0.0f);
            var goal = new MisakaCropGrazeGoal(sister);
            helper.assertTrue(goal.canUse(), "Hungry sister with no container food should graze");
            goal.start();
            goal.tick();
            helper.assertValueEqual(
                    0,
                    helper.getBlockState(new BlockPos(1, 1, 1)).getValue(CropBlock.AGE),
                    "Mature crop should reset to age 0 after graze"
            );
            helper.assertTrue(sister.getFoodLevel() >= 9, "Crop graze should restore 4 food");
            helper.succeed();
        });
    }

    private static void runForageFood(GameTestHelper helper) {
        helper.setBlock(2, 1, 1, Blocks.COPPER_CHEST.weathering().unaffected());
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var chestPos = helper.absolutePos(new BlockPos(2, 1, 1));
            var be = helper.getLevel().getBlockEntity(chestPos);
            helper.assertTrue(be instanceof net.minecraft.world.Container, "Copper chest must be a container");
            var container = (net.minecraft.world.Container) be;
            container.setItem(0, new ItemStack(Items.COOKED_BEEF));
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            sister.bindToRecord(record);
            sister.getFoodData().setFoodLevel(8);
            sister.syncFoodLevel();
            sister.snapTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 0.0f, 0.0f);
            var goal = new MisakaForageFoodGoal(sister);
            helper.assertTrue(goal.canUse(), "Hungry sister should forage copper chest food");
            goal.start();
            for (int i = 0; i < 5; i++) {
                goal.tick();
            }
            helper.assertTrue(sister.getFoodLevel() > 8, "Foraging should raise food level");
            helper.assertTrue(container.getItem(0).isEmpty(), "Chest food should be consumed");
            helper.succeed();
        });
    }

    private static void runUnloadFavor(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var server = helper.getLevel().getServer();
            var roster = MisakaSisterRoster.get(server);
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            var uuid = record.misakaUuid;
            sister.discard();
            roster.modify(uuid, rec -> rec.favorByPlayerName.put("offline", 7));
            var stored = roster.get(uuid).orElseThrow();
            helper.assertValueEqual(7, stored.favorByPlayerName.get("offline"), "Roster favor must update without loaded entity");
            helper.succeed();
        });
    }

    private static void runBindReject(GameTestHelper helper) {
        var leader = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        var follower = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 3, 1, 1);
        helper.runAtTickTime(1L, () -> {
            var server = helper.getLevel().getServer();
            var roster = MisakaSisterRoster.get(server);
            var leaderRecord = leader.rosterRecord().orElseThrow();
            var followerRecord = follower.rosterRecord().orElseThrow();
            BlockPos node = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertTrue(
                    org.academy.internal.server.world.level.storage.WirelessNetworkData
                            .get(helper.getLevel())
                            .registerNode(node, "misaka_test_node", "pw", 16, 8),
                    "Test wireless node must register"
            );
            roster.modify(leaderRecord.misakaUuid, rec -> {
                rec.awakened = true;
                rec.perception = 105;
                rec.highTierUnlocked = true;
                rec.networkNodePos = node;
            });
            roster.modify(followerRecord.misakaUuid, rec -> {
                rec.awakened = true;
                rec.perception = 110;
                rec.perceptionCap = PerceptionService.HIGH_TIER_CAP;
                rec.highTierUnlocked = true;
                rec.networkNodePos = null;
            });
            helper.assertFalse(
                    MisakaNAT.get().bindSisterToNode(helper.getLevel(), followerRecord.misakaUuid, node),
                    "Bind must reject second reconstruction work on same network"
            );
            helper.assertTrue(
                    roster.get(followerRecord.misakaUuid).orElseThrow().networkNodePos == null,
                    "Rejected bind must leave networkNodePos null"
            );
            helper.succeed();
        });
    }

    private static void runSleepWake(GameTestHelper helper) {
        var sister = helper.spawn(EntityTypes.MISAKA_SISTER.get(), 1, 1, 1);
        helper.setBlock(1, 1, 1, Blocks.BED.red());
        helper.runAtTickTime(1L, () -> {
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = true;
            sister.bindToRecord(record);
            var bed = helper.absolutePos(new BlockPos(1, 1, 1));
            sister.startSleeping(bed);
            helper.assertTrue(sister.isSleeping(), "Sister should start sleeping");
            sister.setWanderStyle(WanderStyle.WAITING);
        });
        helper.runAtTickTime(3L, () -> {
            helper.assertFalse(sister.isSleeping(), "WAITING style should wake sleeping sister");
            helper.succeed();
        });
    }

    private static final class MisakaSisterTestInstance extends GameTestInstance {
        private static final MapCodec<MisakaSisterTestInstance> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(TestData.CODEC.forGetter(MisakaSisterTestInstance::info))
                        .apply(instance, data -> new MisakaSisterTestInstance(
                                "Misaka sister",
                                MisakaSisterGameTests::runSpawnSmoke,
                                data
                        ))
        );

        private final String description;
        private final Consumer<GameTestHelper> runner;

        private MisakaSisterTestInstance(
                String description,
                Consumer<GameTestHelper> runner,
                TestData<Holder<TestEnvironmentDefinition<?>>> info
        ) {
            super(info);
            this.description = description;
            this.runner = runner;
        }

        @Override
        public void run(GameTestHelper helper) {
            runner.accept(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal(description);
        }
    }
}
