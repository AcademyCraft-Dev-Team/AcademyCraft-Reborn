package org.academy.internal.common.ability.teleport.skills.lv4.area;

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
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.teleport.area.AreaTeleportState;

import java.util.List;
import java.util.UUID;

/** Live-world regressions for Area Teleport's block transaction. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AreaTeleportGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("area_teleport_function");

    private AreaTeleportGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("area_teleport_preserves_blocks_without_drops"),
                new Instance(new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                        100, 0, true, Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var sourceMin = helper.absolutePos(new BlockPos(2, 2, 2));
        var sourceMax = sourceMin.offset(3, 1, 3);
        var destinationMin = helper.absolutePos(new BlockPos(8, 2, 2));
        var destinationMax = destinationMin.offset(3, 1, 3);
        var source = new AreaTeleportState.Region(level.dimension(), sourceMin, sourceMax);
        var destination = new AreaTeleportState.Region(level.dimension(), destinationMin, destinationMax);

        var support = sourceMin.offset(0, 1, 0);
        var eastTorch = support.east();
        var southTorch = support.south();
        var chestPos = sourceMin.offset(2, 0, 2);
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(eastTorch, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(WallTorchBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(southTorch, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(WallTorchBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var chest = (Container) level.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));

        var player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "area-teleport-test"));
        helper.assertTrue(AreaTeleportStart.Server.move(level, player, source, destination,
                        AreaTeleportState.Transform.IDENTITY, false, false),
                "Area Teleport transaction failed");

        var targetSupport = destinationMin.offset(0, 1, 0);
        var targetEastTorch = targetSupport.east();
        var targetSouthTorch = targetSupport.south();
        var targetChestPos = destinationMin.offset(2, 0, 2);
        helper.assertTrue(level.getBlockState(targetEastTorch).is(Blocks.WALL_TORCH)
                        && level.getBlockState(targetEastTorch).getValue(WallTorchBlock.FACING) == Direction.EAST,
                "East-facing wall torch was not preserved");
        helper.assertTrue(level.getBlockState(targetSouthTorch).is(Blocks.WALL_TORCH)
                        && level.getBlockState(targetSouthTorch).getValue(WallTorchBlock.FACING) == Direction.SOUTH,
                "South-facing wall torch was not preserved");
        helper.assertTrue(level.getBlockEntity(targetChestPos) instanceof Container targetChest
                        && targetChest.getItem(0).is(Items.DIAMOND)
                        && targetChest.getItem(0).getCount() == 7,
                "Chest contents were not preserved exactly once");

        var affectedArea = new AABB(
                sourceMin.getX(), sourceMin.getY(), sourceMin.getZ(),
                destinationMax.getX() + 1.0, destinationMax.getY() + 1.0, destinationMax.getZ() + 1.0
        ).inflate(2.0);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, affectedArea).isEmpty(),
                "Area Teleport emitted duplicate block or container item drops");
        helper.succeed();
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> data) {
            super(data);
        }

        @Override
        public void run(GameTestHelper helper) {
            verify(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Area Teleport transaction regression");
        }
    }
}
