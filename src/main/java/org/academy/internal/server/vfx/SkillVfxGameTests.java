package org.academy.internal.server.vfx;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.vfx.SkillVfxService;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.entity.skill.KineticShockwave;
import org.academy.internal.common.world.entity.skill.Plasma;

/** Exercises actual entity joins, packet dispatch and retirement on a dedicated game-test server. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SkillVfxGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("skill_vfx_test_function");
    private SkillVfxGameTests() {}

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("skill_vfx_environment"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("skill_vfx_lifecycle"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 100, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void check(GameTestHelper helper, boolean value, String message) {
        helper.assertTrue(value, Component.literal(message));
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            var level = helper.getLevel();
            var server = level.getServer();
            var profile = new GameProfile(UUID.randomUUID(), "vfx-observer");
            var cookie = CommonListenerCookie.createInitial(profile, false);
            var observer = new ServerPlayer(server, level, profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, observer, cookie);
            observer.connection.markClientLoaded();
            var center = helper.absoluteVec(new Vec3(4, 4, 4));
            observer.snapTo(center.x, center.y, center.z, 0, 0);
            observer.setNoGravity(true);
            var beam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
            var plasma = new Plasma(EntityTypes.PLASMA.get(), level);
            long before = SkillVfxRuntime.statistics().sentPackets();
            int activeBefore = SkillVfxRuntime.statistics().activeEffects();
            try {
                SkillVfxService.shockwave(level, center, new Vec3(0, 1, 0), 5, 2);
                check(helper, SkillVfxRuntime.statistics().sentPackets() > before, "Shockwave must dispatch an event");
                check(helper, level.getEntitiesOfClass(KineticShockwave.class, new AABB(center, center).inflate(32)).isEmpty(),
                        "Shockwave feedback must not allocate a server entity");
                check(helper, !beam.broadcastToPlayer(observer) && !plasma.broadcastToPlayer(observer),
                        "Migrated logic entities must not also use vanilla tracking");
                beam.setPos(center);
                beam.setContinuous(true);
                plasma.setPos(center.add(0, 31, 0));
                plasma.setOwnerEntityId(observer.getId());
                check(helper, level.addFreshEntity(beam) && level.addFreshEntity(plasma), "Logic entities must still join");
                helper.runAfterDelay(6, () -> {
                    try {
                        check(helper, SkillVfxRuntime.statistics().activeEffects() >= activeBefore + 2,
                                "Both logic entities must have independent visual replicas");
                        check(helper, SkillVfxRuntime.statistics().sentPackets() > before + 1,
                                "An observer must receive initial snapshots");
                        SkillVfxService.plasmaImpact(level, center, 12);
                    } finally {
                        beam.discard();
                        plasma.discard();
                    }
                    helper.runAfterDelay(2, () -> {
                        try {
                            check(helper, SkillVfxRuntime.statistics().activeEffects() == activeBefore,
                                    "Removal must retire visual replication state");
                            helper.succeed();
                        } finally { server.getPlayerList().remove(observer); }
                    });
                });
            } catch (RuntimeException | Error failure) {
                beam.discard(); plasma.discard();
                server.getPlayerList().remove(observer);
                throw failure;
            }
        }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Skill VFX replication"); }
    }
}
