package org.academy.internal.server.ability.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AirMobility;
import org.academy.api.server.world.WaterSuppression;

import java.util.List;
import java.util.UUID;

/** Runtime checks for block restoration, fluid mixins and movement priority. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AeromanipRegressionGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("aeromanip_regression_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : List.of("water_overlap", "water_flow", "water_reload", "movement_priority")) {
            var environment = event.registerEnvironment(AcademyCraft.academy("aeromanip/" + scenario),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(AcademyCraft.academy("aeromanip_" + scenario), new Instance(new TestData<>(
                    environment, Identifier.withDefaultNamespace("empty"), 80, 0, true,
                    Rotation.NONE, false, 1, 1, false, 16), scenario));
        }
    }

    private static void overlap(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var waterlogged = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        level.setBlockAndUpdate(pos, waterlogged);
        level.setBlockAndUpdate(pos.east(), Blocks.WATER.defaultBlockState());
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        WaterSuppression.refresh(level, first, Vec3.atCenterOf(pos), 2.0);
        WaterSuppression.refresh(level, second, Vec3.atCenterOf(pos), 2.0);
        helper.assertTrue(!level.getBlockState(pos).getValue(BlockStateProperties.WATERLOGGED), Component.literal("Slab must be drained"));
        WaterSuppression.release(level, first);
        helper.assertTrue(level.getFluidState(pos).isEmpty(), Component.literal("One lease must not restore another bubble's water"));
        // A player edit made inside the bubble must survive restoration.
        level.setBlockAndUpdate(pos.east(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(BlockStateProperties.SLAB_TYPE,
                net.minecraft.world.level.block.state.properties.SlabType.TOP));
        WaterSuppression.release(level, second);
        helper.assertTrue(level.getBlockState(pos).equals(waterlogged.setValue(BlockStateProperties.SLAB_TYPE,
                net.minecraft.world.level.block.state.properties.SlabType.TOP)), Component.literal("Original waterlogged slab must return"));
        helper.assertTrue(level.getBlockState(pos.east()).is(Blocks.STONE), Component.literal("Restoration must preserve player edits"));
        helper.succeed();
    }

    private static void flow(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 3, 4));
        for (var floor : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, -1, 3)))
            level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2), Blocks.WATER.defaultBlockState());
        var lease = UUID.randomUUID();
        WaterSuppression.refresh(level, lease, Vec3.atCenterOf(pos), 1.0);
        // Exercise the actual mixin entry point before any refresh can remove incoming water.
        try {
            var spread = net.minecraft.world.level.material.FlowingFluid.class.getDeclaredMethod("spreadTo",
                    net.minecraft.world.level.LevelAccessor.class, BlockPos.class,
                    net.minecraft.world.level.block.state.BlockState.class, net.minecraft.core.Direction.class,
                    net.minecraft.world.level.material.FluidState.class);
            spread.setAccessible(true);
            spread.invoke(net.minecraft.world.level.material.Fluids.FLOWING_WATER, level, pos.east(),
                    level.getBlockState(pos.east()), net.minecraft.core.Direction.WEST,
                    net.minecraft.world.level.material.Fluids.FLOWING_WATER.defaultFluidState());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot exercise the runtime fluid-spread entry point", exception);
        }
        helper.assertTrue(level.getFluidState(pos.east()).isEmpty(), Component.literal("Fluid spread mixin must reject incoming water immediately"));
        for (var tick = 1; tick <= 15; tick++) {
            helper.runAfterDelay(tick, () -> WaterSuppression.refresh(level, lease, Vec3.atCenterOf(pos), 1.0));
        }
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(level.getFluidState(pos.east()).isEmpty(), Component.literal("Neighbouring water must not flow into the dry pocket"));
            helper.assertTrue(WaterSuppression.suppliesAir(level, Vec3.atCenterOf(pos)), Component.literal("Displaced water must count as artificial air"));
        });
        // Stop renewing: abandoned leases must restore water without a live owner.
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(level.getBlockState(pos).is(Blocks.WATER), Component.literal("Expired bubble must restore its source water"));
            helper.assertTrue(!WaterSuppression.contains(level, pos), Component.literal("Expired lease must stop blocking water"));
            helper.succeed();
        });
    }

    private static void reload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var original = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        level.setBlockAndUpdate(pos, original);
        WaterSuppression.refresh(level, UUID.randomUUID(), Vec3.atCenterOf(pos), 2.0);
        var data = level.getDataStorage().computeIfAbsent(WaterSuppression.SAVED_DATA_TYPE);
        var encoded = WaterSuppression.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data).getOrThrow();
        var reloaded = WaterSuppression.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        level.getDataStorage().set(WaterSuppression.SAVED_DATA_TYPE, reloaded);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(pos).equals(original), Component.literal("Reloaded restoration journal must restore the original slab without an owner"));
            helper.succeed();
        });
    }

    private static void movement(GameTestHelper helper) {
        var entity = helper.spawn(EntityTypes.ARMOR_STAND, 3, 4, 3);
        entity.setOnGround(false);
        entity.setDeltaMovement(1.5, -0.9, -0.75);
        AirMobility.setSupport(entity, AirMobility.HOVER, entity.getY());
        AirMobility.applySupport(entity);
        helper.assertTrue(entity.getDeltaMovement().equals(new Vec3(1.5, 0.0, -0.75)), Component.literal("Hover must retain horizontal movement"));
        AirMobility.prioritizePropulsion(entity, 20);
        entity.setDeltaMovement(1.5, -2.0, -0.75);
        AirMobility.setSupport(entity, AirMobility.SLOW_FALL, 0.0);
        AirMobility.applySupport(entity);
        helper.assertTrue(entity.getDeltaMovement().y == -2.0, Component.literal("Slow fall must not brake active propulsion"));
        helper.succeed();
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TestData.CODEC.forGetter(Instance::info),
                Codec.STRING.fieldOf("scenario").forGetter(value -> value.scenario)
        ).apply(instance, Instance::new));
        private final String scenario;

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info, String scenario) {
            super(info);
            this.scenario = scenario;
        }

        @Override
        public void run(GameTestHelper helper) {
            switch (scenario) {
                case "water_overlap" -> overlap(helper);
                case "water_flow" -> flow(helper);
                case "water_reload" -> reload(helper);
                case "movement_priority" -> movement(helper);
                default -> throw new IllegalArgumentException(scenario);
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Aeromanipulation regression");
        }
    }
}
