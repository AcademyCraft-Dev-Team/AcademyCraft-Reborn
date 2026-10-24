package org.academy.internal.common.structure.space;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;

import java.util.List;

/** Registry-backed policy checks: captured blocks use vanilla tools instead of material allowlists. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructureMiningPolicyGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("native_structure_mining_policy_function");

    private NativeStructureMiningPolicyGameTests() {}

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("native_structure_mining_policy"),
                new Instance(new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                        40, 0, true, Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void verify(GameTestHelper helper) {
        var iron = Blocks.IRON_BLOCK.defaultBlockState();
        var pick = new ItemStack(Items.IRON_PICKAXE);
        var wood = new ItemStack(Items.WOODEN_PICKAXE);
        helper.assertTrue(NativeStructureMiningPolicy.canMine(pick, iron),
                "Client selection must accept the legacy iron block with an audited pickaxe");
        helper.assertTrue(NativeStructureMiningPolicy.canMine(iron, null),
                "The authoritative path must accept the same iron block without a block entity");
        helper.assertTrue(NativeStructureMiningPolicy.canMine(ItemStack.EMPTY, iron)
                        && NativeStructureMiningPolicy.canMine(wood, iron),
                "An inadequate tool may slowly break iron; tool adequacy belongs to vanilla harvesting");
        helper.assertTrue(iron.requiresCorrectToolForDrops() && pick.isCorrectToolForDrops(iron)
                        && !wood.isCorrectToolForDrops(iron) && !ItemStack.EMPTY.isCorrectToolForDrops(iron),
                "Adding the target must retain vanilla correct-tool requirements for iron drops");
        float hardness = iron.getDestroySpeed(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(hardness > 0 && NativeStructureMiningProgress.increment(
                        hardness, pick.getDestroySpeed(iron), true) > NativeStructureMiningProgress.increment(
                        hardness, wood.getDestroySpeed(iron), false),
                "Iron must retain timed mining and the wrong-tool progress penalty");

        var decorated = pick.copy();
        decorated.setDamageValue(1);
        decorated.set(DataComponents.CUSTOM_NAME, Component.literal("Audited iron mining fixture"));
        helper.assertTrue(NativeStructureMiningPolicy.canMine(decorated, iron),
                "Ordinary wear and presentation components must remain supported");
        var altered = pick.copy();
        altered.set(DataComponents.MAX_DAMAGE, 10_000);
        helper.assertTrue(NativeStructureMiningPolicy.canMine(altered, iron),
                "A valid customized tool must use its actual vanilla components");
        var removedTool = pick.copy();
        removedTool.remove(DataComponents.TOOL);
        helper.assertTrue(NativeStructureMiningPolicy.canMine(removedTool, iron),
                "A held item without tool components may still slowly mine a block");
        helper.assertTrue(!NativeStructureMiningPolicy.canMine(new ItemStack(Items.IRON_PICKAXE, 2), iron)
                        && NativeStructureMiningPolicy.canMine(new ItemStack(Items.IRON_INGOT), iron),
                "Malformed stacks remain invalid; ordinary held items are not excluded");

        var chestState = Blocks.CHEST.defaultBlockState();
        var chest = new ChestBlockEntity(BlockPos.ZERO, chestState);
        helper.assertTrue(!NativeStructureMiningPolicy.canMine(iron, chest),
                "An unexpected block entity must not be accepted for the newly supported iron state");
        helper.assertTrue(NativeStructureMiningPolicy.canMine(chestState, chest)
                        && !NativeStructureMiningPolicy.canMine(chestState, null),
                "The existing single-chest path must still require its actual block entity");
        helper.assertTrue(NativeStructureMiningPolicy.canMine(pick, chestState.setValue(ChestBlock.WATERLOGGED, true))
                        && NativeStructureMiningPolicy.canMine(pick, chestState.setValue(ChestBlock.TYPE, ChestType.LEFT))
                        && !NativeStructureMiningPolicy.canMine(pick, Blocks.WATER.defaultBlockState()),
                "Captured waterlogged/container blocks are targets, while fluid itself is not");
        chest.setRemoved();
        helper.assertTrue(!NativeStructureMiningPolicy.canMine(chestState, chest),
                "A removed block entity must not become mineable");
        for (var block : List.of(Blocks.GOLD_BLOCK, Blocks.GLASS, Blocks.DIRT, Blocks.OBSIDIAN,
                Blocks.OAK_LEAVES, Blocks.DIAMOND_BLOCK, Blocks.BEDROCK)) {
            helper.assertTrue(NativeStructureMiningPolicy.canMine(pick, block.defaultBlockState()),
                    "Selection must accept " + block + "; server hardness/game mode determines destruction");
        }
        helper.succeed();
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
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
            return Component.literal("Native structure iron mining policy regression");
        }
    }
}
