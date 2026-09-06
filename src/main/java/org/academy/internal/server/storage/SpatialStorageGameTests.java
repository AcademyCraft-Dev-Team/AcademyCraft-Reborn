package org.academy.internal.server.storage;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.AbilityBlockDrops;
import org.academy.internal.common.world.item.ItemDataComponents;
import org.academy.internal.common.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Exercises real loot, server persistence and NeoForge container capabilities. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SpatialStorageGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("spatial_storage_test");

    private SpatialStorageGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("spatial_storage"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 100, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> data) {
            super(data);
        }

        @Override
        public void run(GameTestHelper helper) {
            var level = helper.getLevel();
            var profile = new GameProfile(UUID.randomUUID(), "spatial-test");
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, profile);
            try {
                var unit = new ItemStack(Items.SPATIAL_STORAGE_UNIT.get());
                unit.set(ItemDataComponents.SPATIAL_STORAGE_ENABLED.get(), true);
                player.getInventory().setItem(20, unit);
                var id = SpatialStorageService.id(unit);
                var data = SpatialStorageSavedData.get(level.getServer());
                var pos = helper.absolutePos(new BlockPos(2, 2, 2));
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                helper.assertTrue(AbilityBlockDrops.destroyBlock(level, pos, true, player), "Ability break failed");
                helper.assertTrue(data.count(id) == 1, "Inventory unit did not collect the real block loot");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(),
                        "Ability loot appeared in the world");

                unit.set(ItemDataComponents.SPATIAL_STORAGE_ENABLED.get(), false);
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                AbilityBlockDrops.destroyBlock(level, pos, true, player);
                helper.assertTrue(data.count(id) == 1, "Disabled unit collected loot");
                helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(),
                        "Disabled unit suppressed ordinary drops");
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).forEach(ItemEntity::discard);
                unit.set(ItemDataComponents.SPATIAL_STORAGE_ENABLED.get(), true);
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                level.destroyBlock(pos, true, player);
                helper.assertTrue(data.count(id) == 1, "Unscoped/ordinary destruction was captured");
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).forEach(ItemEntity::discard);

                var barrelPos = helper.absolutePos(new BlockPos(4, 2, 2));
                level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
                var barrel = (Container) level.getBlockEntity(barrelPos);
                barrel.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
                AbilityBlockDrops.destroyBlock(level, barrelPos, true, player);
                helper.assertTrue(data.count(id) == 9, "Broken container contents or block loot were lost");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(barrelPos).inflate(1)).isEmpty(),
                        "Container contents escaped collection");

                var second = new ItemStack(Items.SPATIAL_STORAGE_UNIT.get());
                var secondId = SpatialStorageService.id(second);
                player.getInventory().setItem(20, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, second);
                data.store(secondId, new ItemStack(net.minecraft.world.item.Items.STONE, 12));
                level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
                barrel = (Container) level.getBlockEntity(barrelPos);
                for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                    barrel.setItem(slot, new ItemStack(net.minecraft.world.item.Items.STONE, slot == 0 ? 60 : 64));
                }
                player.setShiftKeyDown(true);
                var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(barrelPos), Direction.UP, barrelPos, false));
                Items.SPATIAL_STORAGE_UNIT.get().onItemUseFirst(second, context);
                helper.assertTrue(data.count(secondId) == 8 && barrel.getItem(0).getCount() == 64,
                        "Partial container transfer lost or duplicated items");
                Items.SPATIAL_STORAGE_UNIT.get().onItemUseFirst(second, context);
                helper.assertTrue(data.count(secondId) == 8, "Full container consumed remaining items");
                helper.assertTrue(data.count(id) == 9, "Distinct units shared contents");

                var named = new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3);
                named.set(DataComponents.CUSTOM_NAME, Component.literal("Stored components"));
                data.store(secondId, named);
                var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
                var encoded = SpatialStorageSavedData.CODEC.encodeStart(ops, data).getOrThrow();
                var loaded = SpatialStorageSavedData.CODEC.parse(ops, encoded).getOrThrow();
                helper.assertTrue(loaded.count(secondId) == 11 && loaded.count(id) == 9,
                        "Server storage round-trip changed counts");
                long namedCount = loaded.transfer(secondId, Integer.MAX_VALUE, (resource, count) ->
                        resource.get(DataComponents.CUSTOM_NAME) != null ? count : 0L);
                helper.assertTrue(namedCount == 3 && loaded.count(secondId) == 8,
                        "Serialized item components were not preserved");
                long refused = loaded.transfer(secondId, 1, (resource, count) -> 0L);
                helper.assertTrue(refused == 0 && loaded.count(secondId) == 8, "Refusal discarded stored items");
                try {
                    AbilityBlockDrops.run(player, () -> { throw new IllegalStateException("scope test"); });
                } catch (IllegalStateException expected) {
                    helper.assertTrue(AbilityBlockDrops.currentBreaker() == null, "Capture leaked after exception");
                }
                if (net.neoforged.fml.ModList.get().isLoaded("curios")
                        && net.neoforged.fml.ModList.get().isLoaded("beyonddimensions")) {
                    SpatialStorageCompatGameTests.verify(helper, player, second, secondId);
                }
                helper.succeed();
            } finally {
                player.getInventory().clearContent();
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Spatial storage integration");
        }
    }
}
