package org.academy.internal.common.ability.teleport.skills.lv1;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.teleport.targeting.TeleportTargeting;

import java.util.List;
import java.util.UUID;

/** Real block collisions and entity visibility for the preview and untargeted cast destination. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ThreateningTeleportGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("threatening_teleport_targeting_function");

    @SubscribeEvent private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("threatening_teleport_targeting"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 100, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "threatening-target");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var source = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, source, cookie);
        try {
            var origin = helper.absoluteVec(new Vec3(2.5, 3, 5.5));
            source.snapTo(origin.x, origin.y, origin.z, -90, 0);
            var eye = source.getEyePosition();
            var direction = new Vec3(1, 0, 0);
            var air = ThreateningTeleport.untargetedDestination(source, eye, direction);
            helper.assertTrue(air != null && air.distanceTo(eye.add(direction.scale(16))) < 1.0e-6,
                    "An unobstructed preview must retain its exact 16-block range");
            helper.assertTrue(ThreateningTeleport.untargetedDestination(source, eye, Vec3.ZERO) == null,
                    "An invalid view must not produce a cast destination");

            var block = helper.absolutePos(new BlockPos(6, 4, 5));
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            var top = ThreateningTeleport.untargetedDestination(source, eye, direction);
            var expected = Vec3.atBottomCenterOf(block.above())
                    .add(0, source.getDimensions(Pose.STANDING).height() / 2.0, 0);
            helper.assertTrue(top != null && top.distanceTo(expected) < 1.0e-6,
                    "A low obstruction must move the preview above the block");

            for (int y = 3; y <= 8; y++) for (int z = 4; z <= 6; z++)
                helper.setBlock(6, y, z, Blocks.STONE);
            var near = ThreateningTeleport.untargetedDestination(source, eye, direction);
            helper.assertTrue(near != null && near.x < block.getX() && near.x > eye.x,
                    "A tall wall must move the preview back onto the caster's side");
            helper.assertTrue(TeleportTargeting.isSafeCenter(source, near),
                    "The adjusted destination must not overlap a collision shape");

            var target = helper.spawn(EntityTypes.COW, 10.5f, 3, 5.5f);
            target.setNoAi(true);
            helper.assertTrue(TeleportTargeting.findFirstLivingEntity(source, 64) == null,
                    "An entity behind the wall must not be locked");
            for (int y = 3; y <= 8; y++) for (int z = 4; z <= 6; z++) helper.setBlock(6, y, z, Blocks.AIR);
            helper.assertTrue(TeleportTargeting.findFirstLivingEntity(source, 64) == target,
                    "A visible entity must retain ordinary targeting");
        } finally {
            level.getServer().getPlayerList().remove(source);
        }
        helper.succeed();
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> data) { super(data); }
        @Override public void run(GameTestHelper helper) { verify(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("threatening_targeting"); }
    }
}
