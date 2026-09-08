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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;

import java.util.List;
import java.util.UUID;

/** World-level regressions for harvesting, teleport collection and target queries. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SkillRepairGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("skill_repair_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : List.of("mining_held", "mining_program", "teleport_air", "container_full", "container_collect", "block_normals")) {
            var environment = event.registerEnvironment(AcademyCraft.academy("skill_repair/" + scenario),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(AcademyCraft.academy("skill_repair_" + scenario), new Instance(new TestData<>(
                    environment, Identifier.withDefaultNamespace("empty"), 80, 0, true,
                    Rotation.NONE, false, 1, 1, false, 16), scenario));
        }
    }

    private static void verify(GameTestHelper helper, String scenario) {
        var level = helper.getLevel();
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "skill-repair");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        try {
            var system = org.academy.api.server.ability.AbilitySystemServer.getSystem(player);
            var category = org.academy.internal.common.ability.AbilityCategories.MELTDOWNER.get();
            system.setPlayerAbilityCategory(player.getUUID(), category);
            system.setPlayerLevel(player.getUUID(), 5);
            for (var skill : category.getSkills()) system.addPlayerSkill(player, skill.getKeyString());
            var mining = org.academy.internal.common.ability.Skills.MINING_BEAM.get();
            system.setPlayerSkillProficiency(player.getUUID(), mining, 3000);
            var origin = helper.absoluteVec(new Vec3(2.5, 3.5, 5.5));
            player.snapTo(origin.x, origin.y, origin.z, -90, 0);
            if (scenario.startsWith("container_")) {
                verifyContainer(helper, player, scenario.equals("container_full"));
            } else if (scenario.equals("block_normals")) {
                verifyNormals(helper, player);
            } else if (scenario.equals("teleport_air")) {
                var eye = player.getEyePosition();
                var direction = new Vec3(1, 0, 0);
                var expected = eye.add(direction.scale(8));
                var self = org.academy.internal.common.ability.teleport.TeleportTargeting
                        .findSelfTeleportCenter(player, eye, direction, 8);
                var piercing = org.academy.internal.common.ability.teleport.TeleportTargeting
                        .findDefaultPiercingTeleportCenter(player, eye, direction, 8);
                helper.assertTrue(self != null && self.distanceTo(expected) < 1.0e-6, "Self teleport must use exact distance in air: " + self);
                helper.assertTrue(piercing != null && piercing.distanceTo(expected) < 1.0e-6, "Piercing teleport must use exact distance in air");
            } else {
                var pos = helper.absolutePos(new BlockPos(4, 3, 5));
                level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                level.setBlockAndUpdate(pos.east(), Blocks.DIAMOND_ORE.defaultBlockState());
                level.setBlockAndUpdate(pos.east(2), Blocks.BEDROCK.defaultBlockState());
                level.setBlockAndUpdate(pos.east(3), Blocks.STONE.defaultBlockState());
                if (scenario.equals("mining_program")) {
                    var runtime = new org.academy.internal.common.ability.meltdowner.program.ServerMeltdownerProgramRuntime(player);
                    var action = runtime.fireMiningBeam(
                            new org.academy.api.common.ability.program.ProgramWorldPosition(level.dimension().identifier(), origin.x, origin.y, origin.z),
                            new org.academy.api.common.ability.program.ProgramDirection(1, 0, 0), null, null, 1.0f);
                    action.validate();
                    action.apply();
                    for (var beam : level.getEntitiesOfClass(org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam.class,
                            player.getBoundingBox().inflate(16))) {
                        beam.setAttackDelayTicks(0);
                        beam.tick();
                        beam.discard();
                    }
                } else {
                    org.academy.internal.common.ability.meltdowner.skills.lv2.MiningBeam.executeMiningSegment(
                            level, new org.academy.internal.common.ability.accelerator.reflection.LinearSegment(origin, origin.add(10, 0, 0)),
                            0.25f, false, player, player);
                }
                helper.assertTrue(level.getBlockState(pos).isAir(), "Obsidian hardness must not block mining");
                helper.assertTrue(level.getBlockState(pos.east()).isAir(), "Ore must be harvested");
                helper.assertTrue(level.getBlockState(pos.east(2)).is(Blocks.BEDROCK), "Bedrock must remain unbreakable");
                helper.assertTrue(level.getBlockState(pos.east(3)).is(Blocks.STONE), "Bedrock must stop the mining beam");
                var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(4));
                helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(net.minecraft.world.item.Items.OBSIDIAN)),
                        "Mining must drop obsidian");
                helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(net.minecraft.world.item.Items.DIAMOND)),
                        "Mining must drop diamonds");
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
    private static void verifyContainer(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player, boolean full) throws Exception {
        var level = helper.getLevel();
        var system = org.academy.api.server.ability.AbilitySystemServer.getSystem(player);
        var category = org.academy.internal.common.ability.AbilityCategories.TELEPORT.get();
        system.setPlayerAbilityCategory(player.getUUID(), category);
        system.setPlayerLevel(player.getUUID(), 5);
        for (var skill : category.getSkills()) system.addPlayerSkill(player, skill.getKeyString());
        var pos = helper.absolutePos(new BlockPos(5, 3, 5));
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(pos);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 17));
        chest.setChanged();
        var inventory = player.getInventory();
        inventory.clearContent();
        if (full) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++)
                inventory.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));
        }
        var runtime = new org.academy.internal.common.ability.teleport.program.ServerTeleportProgramRuntime(player);
        var position = new org.academy.api.common.ability.program.ProgramBlockPosition(
                level.dimension().identifier(), pos.getX(), pos.getY(), pos.getZ());
        var action = runtime.teleportBlockOrItem(position, 0,
                org.academy.internal.common.ability.teleport.program.TeleportProgramNodeCatalog.BlockItemTeleportMode.COLLECT);
        if (full) {
            for (int attempt = 0; attempt < 2; attempt++) {
                boolean rejected = false;
                try { action.apply(); } catch (IllegalStateException exception) {
                    if (!exception.getMessage().contains("inventory")) throw exception;
                    rejected = true;
                }
                helper.assertTrue(rejected, "Full inventory must reject collection");
                helper.assertTrue(level.getBlockState(pos).is(Blocks.CHEST), "Failed collection must restore the chest");
                var restored = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(pos);
                helper.assertTrue(restored.getItem(0).getCount() == 17, "Failed collection must restore exactly 17 diamonds");
                for (int slot = 0; slot < inventory.getContainerSize(); slot++)
                    helper.assertTrue(inventory.getItem(slot).is(net.minecraft.world.item.Items.STONE)
                            && inventory.getItem(slot).getCount() == 64, "Failed collection must restore inventory");
            }
        } else {
            var undo = action.apply();
            helper.assertTrue(level.getBlockState(pos).isAir(), "Successful collection removes chest");
            int diamonds = 0, chests = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                if (stack.is(net.minecraft.world.item.Items.DIAMOND)) diamonds += stack.getCount();
                if (stack.is(net.minecraft.world.item.Items.CHEST)) chests += stack.getCount();
            }
            helper.assertTrue(diamonds == 17 && chests == 1, "Successful collection must include chest contents exactly once");
            undo.close();
            helper.assertTrue(inventory.isEmpty(), "Undo must restore empty inventory");
            var restored = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(restored != null && restored.getItem(0).getCount() == 17, "Undo must restore chest contents");
        }
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(2)).isEmpty(), "Container contents must never leak onto the ground");
    }

    private static void verifyNormals(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player) {
        var pos = helper.absolutePos(new BlockPos(5, 5, 5));
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var runtimes = List.<org.academy.api.common.ability.program.ProgramTargetResolver>of(
                new org.academy.internal.common.ability.accelerator.program.ServerAcceleratorProgramRuntime(player),
                new org.academy.internal.common.ability.aeromanip.program.ServerAeromanipProgramRuntime(player),
                new org.academy.internal.common.ability.darkmatter.program.ServerDarkmatterProgramRuntime(player),
                new org.academy.internal.common.ability.electromaster.program.ServerElectromasterProgramRuntime(player),
                new org.academy.internal.common.ability.meltdowner.program.ServerMeltdownerProgramRuntime(player),
                new org.academy.internal.common.ability.teleport.program.ServerTeleportProgramRuntime(player));
        for (var runtime : runtimes) {
            for (var face : net.minecraft.core.Direction.values()) {
                var center = Vec3.atCenterOf(pos).add(face.getStepX() * 2, face.getStepY() * 2, face.getStepZ() * 2);
                var normal = runtime.raycastBlockNormal(
                        new org.academy.api.common.ability.program.ProgramWorldPosition(
                                level.dimension().identifier(), center.x, center.y, center.z),
                        new org.academy.api.common.ability.program.ProgramDirection(-face.getStepX(), -face.getStepY(), -face.getStepZ()), 4);
                helper.assertTrue(normal.equals(java.util.Optional.of(new org.academy.api.common.ability.program.ProgramDirection(
                        face.getStepX(), face.getStepY(), face.getStepZ()))), runtime.getClass().getSimpleName() + " normal " + face);
            }
            player.snapTo(pos.getX() - 2.0, pos.getY() + 0.5 - player.getEyeHeight(), pos.getZ() + 0.5, -90, 0);
            player.setYHeadRot(-90);
            var normal = runtime.blockNormalFromView(player, 4);
            helper.assertTrue(normal.equals(java.util.Optional.of(new org.academy.api.common.ability.program.ProgramDirection(-1, 0, 0))),
                    runtime.getClass().getSimpleName() + " view normal " + normal + " eye " + player.getEyePosition() + " look " + player.getViewVector(1.0f));
        }
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
            verify(helper, scenario);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Skill repair regression");
        }
    }
}
