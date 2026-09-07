package org.academy.internal.common.ability.accelerator.skills.lv5;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.channel.embedded.EmbeddedChannel;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.WingControlIntent;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Live server motion regression: packet aggregation must preserve every former directional push. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class WingFlightNetworkGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("wing_network_function");
    @SubscribeEvent private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }
    @SubscribeEvent private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("wing_network"), new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("wing_network_motion"), new Instance(new TestData<>(environment,
                Identifier.withDefaultNamespace("empty"), 100, 0, true, Rotation.NONE, false, 1, 1, false, 16), "motion"));
    }
    private static void runMotion(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "wing-network");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        var players = helper.getLevel().getServer().getPlayerList();
        players.placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded();
        var boostTicks = new HashMap<UUID, Long>();
        try {
            for (int buttons : new int[]{0, 1, 2, 4, 8, 5, 9, 6, 10, 16}) {
                for (float pitch : new float[]{-70, 0, 65}) {
                    var input = new WingControlIntent(buttons, 123, pitch);
                    for (Vec3 velocity : new Vec3[]{new Vec3(.3, .1, -.7), new Vec3(-.4, .6, .2)}) {
                        player.setDeltaMovement(velocity);
                        if (buttons == 0) WingFlightSupport.applyControl(player, StormWing.State.KEEP, 123, pitch, boostTicks);
                        else if (buttons == 16) WingFlightSupport.applyControl(player, StormWing.State.BOOST, 123, pitch, boostTicks);
                        else {
                            if ((buttons & 1) != 0) WingFlightSupport.applyControl(player, StormWing.State.FRONT, 123, pitch, boostTicks);
                            if ((buttons & 2) != 0) WingFlightSupport.applyControl(player, StormWing.State.BACK, 123, pitch, boostTicks);
                            if ((buttons & 4) != 0) WingFlightSupport.applyControl(player, StormWing.State.LEFT, 123, pitch, boostTicks);
                            if ((buttons & 8) != 0) WingFlightSupport.applyControl(player, StormWing.State.RIGHT, 123, pitch, boostTicks);
                        }
                        var expected = player.getDeltaMovement();
                        player.setDeltaMovement(velocity);
                        WingFlightSupport.applyHeldControl(player, input, boostTicks);
                        helper.assertTrue(expected.distanceToSqr(player.getDeltaMovement()) < 1e-16,
                                "Aggregated flight must preserve direction, diagonal pushes and KEEP damping");
                    }
                }
            }
            helper.succeed();
        } finally { players.remove(player); }
    }
    private static final class Instance extends GameTestInstance {
        static final MapCodec<Instance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TestData.CODEC.forGetter(Instance::info), Codec.STRING.fieldOf("scenario").forGetter(value -> value.scenario)
        ).apply(instance, Instance::new));
        final String scenario;
        Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info, String scenario) { super(info); this.scenario = scenario; }
        @Override public void run(GameTestHelper helper) { runMotion(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Wing input aggregation regression"); }
    }
}
