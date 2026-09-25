package org.academy.internal.server.ability.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.program.ServerAcceleratorProgramRuntime;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.ability.aeromanip.program.ServerAeromanipProgramRuntime;
import org.academy.internal.common.ability.darkmatter.program.ServerDarkmatterProgramRuntime;
import org.academy.internal.common.ability.electromaster.program.ServerElectromasterProgramRuntime;
import org.academy.internal.common.ability.meltdowner.program.ServerMeltdownerProgramRuntime;
import org.academy.internal.common.ability.meltdowner.skills.lv2.MiningBeam;
import org.academy.internal.common.ability.teleport.TeleportTargeting;
import org.academy.internal.common.ability.teleport.program.ServerTeleportProgramRuntime;
import org.academy.internal.common.ability.teleport.program.TeleportProgramNodeCatalog;
import org.academy.internal.common.gametest.GameTestData;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.server.ability.AbilitySystemServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * World-level regressions for harvesting, teleport collection and target queries.
 */
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
            event.registerTest(AcademyCraft.academy("skill_repair_" + scenario), new Instance(GameTestData.of(
                    environment, Identifier.withDefaultNamespace("empty"), 80, 0, true,
                    Rotation.NONE, false, 1, 1, false, 16), scenario));
        }
    }

    private static void verify(GameTestHelper helper, String scenario) {
        var level = helper.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "skill-repair");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        try {
            var system = AbilitySystemServer.getSystem(player);
            var category = AbilityCategories.MELTDOWNER.get();
            system.setPlayerAbilityCategory(player.getUUID(), category);
            system.setPlayerLevel(player.getUUID(), 5);
            for (var skill : category.getSkills()) system.addPlayerSkill(player, skill.getKeyString());
            var mining = Skills.MINING_BEAM.get();
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
                var self = TeleportTargeting
                        .findSelfTeleportCenter(player, eye, direction, 8);
                var piercing = TeleportTargeting
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
                    var runtime = new ServerMeltdownerProgramRuntime(player);
                    var action = runtime.fireMiningBeam(
                            new ProgramWorldPosition(level.dimension().identifier(), origin.x, origin.y, origin.z),
                            new ProgramDirection(1, 0, 0), null, null, 1.0f);
                    action.validate();
                    action.apply();
                    for (var beam : level.getEntitiesOfClass(HighSpeedElectronBeam.class,
                            player.getBoundingBox().inflate(16))) {
                        beam.setAttackDelayTicks(0);
                        beam.tick();
                        beam.discard();
                    }
                } else {
                    MiningBeam.executeMiningSegment(
                            level, new LinearSegment(origin, origin.add(10, 0, 0)),
                            0.25f, false, player, player);
                }
                helper.assertTrue(level.getBlockState(pos).isAir(), "Obsidian hardness must not block mining");
                helper.assertTrue(level.getBlockState(pos.east()).isAir(), "Ore must be harvested");
                helper.assertTrue(level.getBlockState(pos.east(2)).is(Blocks.BEDROCK), "Bedrock must remain unbreakable");
                helper.assertTrue(level.getBlockState(pos.east(3)).is(Blocks.STONE), "Bedrock must stop the mining beam");
                var drops = level.getEntitiesOfClass(ItemEntity.class,
                        new AABB(pos).inflate(4));
                helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(Items.OBSIDIAN)),
                        "Mining must drop obsidian");
                helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(Items.DIAMOND)),
                        "Mining must drop diamonds");
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void verifyContainer(GameTestHelper helper, ServerPlayer player, boolean full) throws Exception {
        var level = helper.getLevel();
        var system = AbilitySystemServer.getSystem(player);
        var category = AbilityCategories.TELEPORT.get();
        system.setPlayerAbilityCategory(player.getUUID(), category);
        system.setPlayerLevel(player.getUUID(), 5);
        for (var skill : category.getSkills()) system.addPlayerSkill(player, skill.getKeyString());
        var pos = helper.absolutePos(new BlockPos(5, 3, 5));
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
        chest.setChanged();
        var inventory = player.getInventory();
        inventory.clearContent();
        if (full) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++)
                inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        var runtime = new ServerTeleportProgramRuntime(player);
        var position = new ProgramBlockPosition(
                level.dimension().identifier(), pos.getX(), pos.getY(), pos.getZ());
        var action = runtime.teleportBlockOrItem(position, 0,
                TeleportProgramNodeCatalog.BlockItemTeleportMode.COLLECT);
        if (full) {
            for (int attempt = 0; attempt < 2; attempt++) {
                boolean rejected = false;
                try {
                    action.apply();
                } catch (IllegalStateException exception) {
                    if (!exception.getMessage().contains("inventory")) throw exception;
                    rejected = true;
                }
                helper.assertTrue(rejected, "Full inventory must reject collection");
                helper.assertTrue(level.getBlockState(pos).is(Blocks.CHEST), "Failed collection must restore the chest");
                var restored = (ChestBlockEntity) level.getBlockEntity(pos);
                helper.assertTrue(restored.getItem(0).getCount() == 17, "Failed collection must restore exactly 17 diamonds");
                for (int slot = 0; slot < inventory.getContainerSize(); slot++)
                    helper.assertTrue(inventory.getItem(slot).is(Items.STONE)
                            && inventory.getItem(slot).getCount() == 64, "Failed collection must restore inventory");
            }
        } else {
            var undo = action.apply();
            helper.assertTrue(level.getBlockState(pos).isAir(), "Successful collection removes chest");
            int diamonds = 0, chests = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
                if (stack.is(Items.CHEST)) chests += stack.getCount();
            }
            helper.assertTrue(diamonds == 17 && chests == 1, "Successful collection must include chest contents exactly once");
            undo.close();
            helper.assertTrue(inventory.isEmpty(), "Undo must restore empty inventory");
            var restored = (ChestBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(restored != null && restored.getItem(0).getCount() == 17, "Undo must restore chest contents");
        }
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class,
                new AABB(pos).inflate(2)).isEmpty(), "Container contents must never leak onto the ground");
    }

    private static void verifyNormals(GameTestHelper helper, ServerPlayer player) {
        var pos = helper.absolutePos(new BlockPos(5, 5, 5));
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var runtimes = List.<ProgramTargetResolver>of(
                new ServerAcceleratorProgramRuntime(player),
                new ServerAeromanipProgramRuntime(player),
                new ServerDarkmatterProgramRuntime(player),
                new ServerElectromasterProgramRuntime(player),
                new ServerMeltdownerProgramRuntime(player),
                new ServerTeleportProgramRuntime(player));
        for (var runtime : runtimes) {
            for (var face : Direction.values()) {
                var center = Vec3.atCenterOf(pos).add(face.getStepX() * 2, face.getStepY() * 2, face.getStepZ() * 2);
                var normal = runtime.raycastBlockNormal(
                        new ProgramWorldPosition(
                                level.dimension().identifier(), center.x, center.y, center.z),
                        new ProgramDirection(-face.getStepX(), -face.getStepY(), -face.getStepZ()), 4);
                helper.assertTrue(normal.equals(Optional.of(new ProgramDirection(
                        face.getStepX(), face.getStepY(), face.getStepZ()))), runtime.getClass().getSimpleName() + " normal " + face);
            }
            player.snapTo(pos.getX() - 2.0, pos.getY() + 0.5 - player.getEyeHeight(), pos.getZ() + 0.5, -90, 0);
            player.setYHeadRot(-90);
            var normal = runtime.blockNormalFromView(player, 4);
            helper.assertTrue(normal.equals(Optional.of(new ProgramDirection(-1, 0, 0))),
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
