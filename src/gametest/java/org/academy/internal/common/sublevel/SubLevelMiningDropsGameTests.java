package org.academy.internal.common.sublevel;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.space.NativeStructureBreakDrops;
import org.academy.internal.common.structure.space.NativeStructureDimensions;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs actual vanilla loot and global drop events in the real plot dimension. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SubLevelMiningDropsGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("sublevel_mining_drops_function");

    private SubLevelMiningDropsGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("sublevel_mining_drops"),
                new Instance(new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                        100, 0, true, Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void verify(GameTestHelper helper) {
        var host = helper.getLevel();
        var storage = host.getServer().getLevel(NativeStructureDimensions.PLOT_KEY);
        helper.assertTrue(storage != null, "The real shared plot dimension must be loaded");
        var profile = new GameProfile(UUID.randomUUID(), "mining-drops");
        var player = new ServerPlayer(host.getServer(), host, profile,
                CommonListenerCookie.createInitial(profile, false).clientInformation());
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        var plotStorage = new PlotStorage(storage, new PlotLayout(1, 200_000, 200_000, 1));
        var lease = plotStorage.allocate().orElseThrow();
        try {
            var origin = plotStorage.worldPosition(lease.plot(), BlockPos.ZERO);
            var local = new BlockPos(8, 8, 8);
            var position = plotStorage.worldPosition(lease.plot(), local);
            var world = helper.absoluteVec(new Vec3(5.5, 4.5, 5.5));
            var pose = StructurePose.identity(world.subtract(Vec3.atCenterOf(local)));
            var reference = new StructureRef(UUID.randomUUID(), host.dimension().identifier(),
                    SubLevelStructureRefs.BACKEND, 0);
            var hit = new StructureHit(new SpaceBlockRef(reference, local), UUID.randomUUID(), 0, pose,
                    Direction.UP, Vec3.atCenterOf(local), world, false);
            var storageBounds = new AABB(origin.getX(), storage.getMinY(), origin.getZ(),
                    origin.getX() + 32, storage.getMaxY() + 1.0, origin.getZ() + 32);
            for (var around : BlockPos.betweenClosed(BlockPos.containing(world).offset(-3, 0, -3),
                    BlockPos.containing(world).offset(3, 0, 3))) host.getChunkAt(around);
            var search = new AABB(world, world).inflate(3);
            for (var block : List.of(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.GLASS, Blocks.OBSIDIAN,
                    Blocks.DIAMOND_BLOCK, Blocks.DIRT, Blocks.OAK_LEAVES)) {
                helper.assertTrue(SubLevelMiningRules.canMine(player, storage, position,
                                block.defaultBlockState(), null), "Captured " + block + " must be mineable");
            }
            helper.assertTrue(!SubLevelMiningRules.canMine(player, storage, position,
                            Blocks.BEDROCK.defaultBlockState(), null),
                    "Survival must respect vanilla unbreakable hardness");
            player.getAbilities().instabuild = true;
            helper.assertTrue(SubLevelMiningRules.canMine(player, storage, position,
                            Blocks.BEDROCK.defaultBlockState(), null),
                    "Creative must be allowed to remove a captured unbreakable block");
            player.getAbilities().instabuild = false;
            for (int mode = 0; mode < 3; mode++) {
                final int scenario = mode;
                var state = Blocks.IRON_BLOCK.defaultBlockState();
                storage.setBlock(position, state, SubLevelCaptureService.TRANSACTION_FLAGS);
                int[] count = {0};
                ItemEntity[] replacement = {null};
                Consumer<BlockDropsEvent> listener = event -> {
                    if (event.getLevel() != storage || !event.getPos().equals(position)) return;
                    count[0]++;
                    helper.assertTrue(event.getState() == state && event.getBreaker() == player
                                    && event.getTool().is(Items.IRON_PICKAXE),
                            "Global event must carry actual plot coordinates, block, breaker and tool");
                    helper.assertTrue(event.getDrops().size() == 1
                                    && event.getDrops().getFirst().getItem().is(Items.IRON_BLOCK),
                            "Vanilla iron loot must reach the event before spawning");
                    if (scenario == 1) event.setCanceled(true);
                    if (scenario == 2) {
                        event.getDrops().clear();
                        replacement[0] = new ItemEntity(storage, position.getX() + 0.5,
                                position.getY() + 0.5, position.getZ() + 0.5,
                                new ItemStack(Items.DIAMOND, 3));
                        event.getDrops().add(replacement[0]);
                        event.setDroppedExperience(7);
                    }
                };
                NeoForge.EVENT_BUS.addListener(listener);
                try (var drops = NativeStructureBreakDrops.open(host, storage, hit,
                        new StructureBlockData(state), null, origin, storageBounds)) {
                    storage.setBlock(position, Blocks.AIR.defaultBlockState(),
                            SubLevelCaptureService.TRANSACTION_FLAGS);
                    state.getBlock().playerDestroy(storage, player, position, state, null,
                            player.getMainHandItem().copy());
                    helper.assertTrue(count[0] == 1, "One removal must dispatch exactly one global drop event");
                    drops.publish();
                    var items = host.getEntitiesOfClass(ItemEntity.class, search, entity -> !entity.isRemoved());
                    var orbs = host.getEntitiesOfClass(ExperienceOrb.class, search, entity -> !entity.isRemoved());
                    helper.assertTrue(items.size() == (scenario == 1 ? 0 : 1),
                            "Cancellation and replacement must determine final host drops");
                    if (scenario == 0) helper.assertTrue(items.getFirst().getItem().is(Items.IRON_BLOCK),
                            "Default iron loot must appear in the visible host");
                    if (scenario == 2) {
                        helper.assertTrue(items.getFirst() == replacement[0]
                                        && items.getFirst().getItem().getCount() == 3,
                                "Preserve the event's replacement entity and stack without duplicate spawning");
                        helper.assertTrue(orbs.stream().mapToInt(ExperienceOrb::getValue).sum() == 7,
                                "The event's modified experience must appear once in the host");
                        helper.assertTrue(replacement[0].position().distanceToSqr(world) < 0.001,
                                "Plot origin must be subtracted before applying the structure pose");
                    } else helper.assertTrue(orbs.isEmpty(), "Default/canceled iron drops must not invent XP");
                    items.forEach(ItemEntity::discard);
                    orbs.forEach(ExperienceOrb::discard);
                    helper.assertTrue(storage.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(2)).isEmpty(),
                            "Mining must not leave unreachable plot item entities");
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                    storage.setBlock(position, Blocks.AIR.defaultBlockState(),
                            SubLevelCaptureService.TRANSACTION_FLAGS);
                }
            }
        } finally {
            plotStorage.release(lease);
        }
        verifyDetachedContents(helper, player);
        helper.succeed();
    }

    private static void verifyDetachedContents(GameTestHelper helper, ServerPlayer player) {
        var runtime = SubLevelServer.runtime(helper.getLevel()).orElseThrow();
        var id = SubLevelId.random();
        var pose = StructurePose.identity(helper.absoluteVec(new Vec3(5, 5, 5)));
        var subLevel = runtime.createStaged(id, new AABB(0, 0, 0, 6, 1, 1), pose).orElseThrow();
        var storage = runtime.storage();
        var level = storage.level();
        var origin = storage.worldPosition(subLevel.plot(), BlockPos.ZERO);
        var farGrass = new BlockPos(3, 0, 0);
        var protectedGrass = new BlockPos(5, 0, 0);
        var protectedPosition = storage.worldPosition(subLevel.plot(), protectedGrass);
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var activation = SubLevelActivationRegistry.activation(id);
        var reference = SubLevelStructureRefs.of(subLevel, helper.getLevel().dimension().identifier(),
                activation.generation());
        var hit = new StructureHit(new SpaceBlockRef(reference, BlockPos.ZERO), activation.runtimeId(),
                0, pose, Direction.UP, new Vec3(0.5, 0.5, 0.5),
                pose.localToWorldPoint(new Vec3(0.5, 0.5, 0.5)), false);
        var bounds = new AABB(origin.getX(), level.getMinY(), origin.getZ(),
                origin.getX() + 32, level.getMaxY() + 1.0, origin.getZ() + 32);
        int[] grassEvents = {0};
        Consumer<BreakBlockEvent> protection = event -> {
            if (event.getLevel() == level && event.getPos().equals(protectedPosition)) event.setCanceled(true);
        };
        Consumer<BlockDropsEvent> drops = event -> {
            if (event.getLevel() == level && event.getState().is(Blocks.SHORT_GRASS)
                    && (event.getPos().equals(storage.worldPosition(subLevel.plot(), farGrass))
                    || event.getPos().equals(protectedPosition))) grassEvents[0]++;
        };
        NeoForge.EVENT_BUS.addListener(protection);
        NeoForge.EVENT_BUS.addListener(drops);
        try {
            for (var local : List.of(farGrass, protectedGrass)) {
                var center = BlockPos.containing(pose.localToWorldPoint(Vec3.atCenterOf(local)));
                for (var cell : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 0, 2))) {
                    helper.getLevel().getChunkAt(cell);
                }
            }
            storage.setBlock(subLevel.plot(), BlockPos.ZERO, state, SubLevelCaptureService.TRANSACTION_FLAGS);
            storage.setBlock(subLevel.plot(), farGrass, Blocks.SHORT_GRASS.defaultBlockState(),
                    SubLevelCaptureService.TRANSACTION_FLAGS);
            storage.setBlock(subLevel.plot(), protectedGrass, Blocks.SHORT_GRASS.defaultBlockState(),
                    SubLevelCaptureService.TRANSACTION_FLAGS);
            // Model the final solid removal without notifying distant grass, so this specifically
            // exercises the massless sweep instead of vanilla's immediate adjacent support update.
            storage.setBlock(subLevel.plot(), BlockPos.ZERO, Blocks.AIR.defaultBlockState(),
                    SubLevelCaptureService.TRANSACTION_FLAGS);
            try (var scope = NativeStructureBreakDrops.open(helper.getLevel(), level, hit,
                    new StructureBlockData(state), null, origin, bounds)) {
                SubLevelDetachedContents.dropIfMassless(runtime, subLevel, player);
                scope.publish();
            }
            helper.assertTrue(storage.blockState(subLevel.plot(), farGrass).isAir() && grassEvents[0] == 1,
                    "A nonadjacent grass cell must use the global drop path after the final solid disappears");
            helper.assertTrue(storage.blockState(subLevel.plot(), protectedGrass).is(Blocks.SHORT_GRASS),
                    "A protected decoration must survive the cleanup");
            runtime.refreshGeometry(id);
            helper.assertTrue(subLevel.state() == SubLevelState.SLEEPING
                            && !runtime.universe().contains(id) && SubLevelQueries.resolve(
                            helper.getLevel().getServer(), reference).isPresent(),
                    "Protected leftovers must stay addressable without a manufactured collision body");
            NeoForge.EVENT_BUS.unregister(protection);
            try (var scope = NativeStructureBreakDrops.open(helper.getLevel(), level, hit,
                    new StructureBlockData(state), null, origin, bounds)) {
                SubLevelDetachedContents.dropIfMassless(runtime, subLevel, player);
                scope.publish();
            }
            helper.assertTrue(storage.blockState(subLevel.plot(), protectedGrass).isAir() && grassEvents[0] == 2,
                    "After permission changes, the retained cell must be removable exactly once");
        } finally {
            NeoForge.EVENT_BUS.unregister(protection);
            NeoForge.EVENT_BUS.unregister(drops);
            runtime.remove(id);
        }
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { verify(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() {
            return Component.literal("SubLevel vanilla mining and global drop event regression");
        }
    }
}
