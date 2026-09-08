package org.academy.internal.server.vfx;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.VarInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.api.server.vfx.SkillVfxService;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.network.SkillVfxPacket;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.misaka.api.common.network.packet.S2CPacket;

/** Real server dispatch to simulated connections; does not claim client rendering or TCP measurements. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SkillVfxMultiplayerGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("skill_vfx_multiplayer_function");
    private SkillVfxMultiplayerGameTests() {}

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (int count : new int[]{2, 8, 16}) {
            // Separate environments prevent nearby test batches from sharing observer traffic.
            var environment = event.registerEnvironment(AcademyCraft.academy("skill_vfx_multiplayer_environment_" + count),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(AcademyCraft.academy("skill_vfx_multiplayer_" + count), new Instance(new TestData<>(
                    environment, Identifier.withDefaultNamespace("empty"), 220, 0, true,
                    Rotation.NONE, false, 1, 1, false, 16), count));
        }
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TestData.CODEC.forGetter(Instance::info),
                Codec.INT.fieldOf("observers").forGetter(test -> test.count)).apply(instance, Instance::new));
        private final int count;
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info, int count) { super(info); this.count = count; }
        @Override public void run(GameTestHelper helper) { new Scenario(helper, count).start(); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Multiplayer VFX dispatch"); }
    }

    private static final class Observer {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        long encodedBytes;
        Observer(GameTestHelper helper, Vec3 position, int index) {
            var level = helper.getLevel();
            var profile = new GameProfile(UUID.randomUUID(), "vfx-peer-" + index);
            var cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.connection.markClientLoaded();
            player.setNoGravity(true);
            move(position);
        }
        void move(Vec3 position) { player.snapTo(position.x, position.y, position.z, 0, 0); }
        List<SkillVfxPacket> drain() {
            channel.runPendingTasks();
            var result = new ArrayList<SkillVfxPacket>();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                try {
                    if (!(message instanceof S2CPacket envelope)) continue;
                    var bytes = Unpooled.buffer();
                    try {
                        envelope.write(bytes);
                        int size = bytes.readableBytes();
                        if (VarInt.read(bytes) != PacketTypes.SKILL_VFX.get().getPacketId()) continue;
                        var packet = SkillVfxPacket.CODEC.decode(bytes);
                        if (bytes.isReadable()) throw new AssertionError("Unconsumed VFX packet bytes");
                        encodedBytes += size;
                        result.add(packet);
                    } finally { bytes.release(); }
                } finally { ReferenceCountUtil.release(message); }
            }
            return result;
        }
        void close() {
            player.level().getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final int count;
        final Vec3 center;
        final List<Observer> observers = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        final List<Plasma> stressPlasmas = new ArrayList<>();
        long beamId, plasmaId;
        HighSpeedElectronBeam beam;
        Plasma plasma;
        boolean closed;
        Scenario(GameTestHelper helper, int count) {
            this.helper = helper; this.count = count;
            center = helper.absoluteVec(new Vec3(4, 5, 4));
        }
        void check(boolean value, String message) { helper.assertTrue(value, Component.literal(message)); }
        void guarded(Runnable action) {
            try { action.run(); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void after(int ticks, Runnable action) { helper.runAfterDelay(ticks, () -> guarded(action)); }
        Observer remote() { return observers.getLast(); }
        void clearPackets() { observers.forEach(Observer::drain); }
        HighSpeedElectronBeam beam(boolean held) {
            var result = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), helper.getLevel());
            result.setPos(center);
            result.setYRot(-90);
            result.setBeamLength(400);
            result.setAttackDelayTicks(240);
            result.setHeldCharge(held);
            result.setContinuous(!held);
            entities.add(result);
            check(helper.getLevel().addFreshEntity(result), "Beam must join the server");
            return result;
        }
        Plasma plasma() {
            var result = new Plasma(EntityTypes.PLASMA.get(), helper.getLevel());
            result.setPos(center.add(0, 31, 6));
            result.setOwnerEntityId(observers.getFirst().player.getId());
            entities.add(result);
            check(helper.getLevel().addFreshEntity(result), "Plasma must join the server");
            return result;
        }
        void start() { guarded(() -> {
            for (int i = 0; i < count; i++) observers.add(new Observer(helper,
                    i == count - 1 ? center.add(0, 0, 700) : center.add(0, 0, i), i));
            clearPackets();
            SkillVfxService.shockwave(helper.getLevel(), center, new Vec3(0, 1, 0), 8, 2);
            for (int i = 0; i < count; i++) {
                var packets = observers.get(i).drain();
                check(packets.size() == (i == count - 1 ? 0 : 1), "Shockwave must reach each nearby peer exactly once");
            }
            SkillVfxService.smoke(helper.getLevel(), center, 0.5f, 80);
            SkillVfxService.slash(helper.getLevel(), center, 20, 180, 2, -1, 4);
            for (int i = 0; i < count; i++) {
                var packets = observers.get(i).drain();
                check(packets.size() == (i == count - 1 ? 0 : 2), "Pure visuals must reach nearby peers once");
                if (i != count - 1) {
                    check(packets.getFirst().state instanceof SkillVfxState.Smoke s && s.size() == 0.5f,
                            "Smoke must carry the configured size without entity tracking");
                    check(packets.getLast().state instanceof SkillVfxState.Slash s && s.lifetimeTicks() == 4,
                            "Short slash must carry its complete playback lifetime");
                }
            }
            check(helper.getLevel().getEntitiesOfClass(
                    org.academy.internal.common.world.entity.skill.Smoke.class,
                    new net.minecraft.world.phys.AABB(center, center).inflate(16)).isEmpty(),
                    "Smoke event must not create a server entity");
            check(helper.getLevel().getEntitiesOfClass(
                    org.academy.internal.common.world.entity.skill.DarkmatterCutSlash.class,
                    new net.minecraft.world.phys.AABB(center, center).inflate(16)).isEmpty(),
                    "Slash event must not create a server entity");
            beam = beam(true); plasma = plasma();
            after(6, this::initial);
        }); }
        void initial() {
            var initial = observers.getFirst().drain();
            beamId = initial.stream().filter(p -> p.state instanceof SkillVfxState.Beam).findFirst().orElseThrow().id;
            plasmaId = initial.stream().filter(p -> p.state instanceof SkillVfxState.Plasma).findFirst().orElseThrow().id;
            check(remote().drain().isEmpty(), "Distant peer must not receive either effect");
            clearPackets();
            remote().move(center.add(400, 0, 0));
            after(6, () -> {
                var packets = remote().drain();
                check(packets.stream().anyMatch(p -> p.id == beamId && p.state instanceof SkillVfxState.Beam),
                        "Observer near beam end must receive it even 400 blocks from caster");
                check(packets.stream().noneMatch(p -> p.id == plasmaId), "Distant plasma must stay unsubscribed");
                remote().move(center.add(0, 0, 8));
                after(6, this::chargeReentry);
            });
        }
        void chargeReentry() {
            var state = (SkillVfxState.Plasma) remote().drain().stream()
                    .filter(p -> p.id == plasmaId && p.state instanceof SkillVfxState.Plasma).findFirst().orElseThrow().state;
            check(state.positionAt(0).distanceToSqr(plasma.position()) < 1e-8,
                    "Late charge subscriber must receive the actual focus, not the world origin");
            clearPackets();
            remote().move(center.add(0, 0, 700));
            after(6, () -> {
                var hidden = remote().drain();
                check(hidden.stream().filter(p -> p.state instanceof SkillVfxState.End e && e.hidden()).count() == 2,
                        "Leaving range must hide both subscriptions");
                plasma.launch(observers.getFirst().player.getUUID(), center.add(200, 31, 6), 2.5,
                        0, 0, 0, false, 0);
                remote().move(center.add(0, 0, 8));
                after(6, this::flightReentry);
            });
        }
        void flightReentry() {
            check(remote().drain().stream().anyMatch(p -> p.id == plasmaId
                    && p.state instanceof SkillVfxState.Plasma s && s.launched()),
                    "Flight reentry must carry a complete launched snapshot");
            clearPackets();
            // Exercise a real fire immediately followed by removal within one server tick.
            var caster = observers.getFirst().player;
            var skill = Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get();
            var ability = org.academy.api.server.ability.AbilitySystemServer.getSystem(caster);
            ability.setPlayerAbilityCategory(caster.getUUID(), skill.getCategory());
            ability.addPlayerSkill(caster, skill.getKeyString());
            if (!skill.isEnabled(caster)) ability.toggleSkill(caster.getUUID(), skill.getKeyString());
            check(skill.isEnabled(caster), "Test caster must have an enabled beam skill");
            var flash = beam(true);
            flash.configure(caster, skill, 0, 0, 0, false, false);
            flash.setHeldCharge(false);
            flash.setAttackDelayTicks(0);
            flash.tick();
            check(flash.hasFired(), "Test flash must actually fire");
            flash.discard(); beam.discard(); plasma.discard();
            after(2, () -> {
                for (var observer : observers) {
                    var packets = observer.drain();
                    var fired = packets.stream().filter(p -> p.state instanceof SkillVfxState.Beam b && b.fired())
                            .findFirst().orElseThrow();
                    check(packets.stream().anyMatch(p -> p.id == fired.id && p.revision > fired.revision
                            && p.state instanceof SkillVfxState.End e && !e.hidden()), "Flash must arrive before its terminal event");
                    check(packets.stream().noneMatch(p -> p.state instanceof SkillVfxState.Burst b && b.plasmaImpact()),
                            "Cancelled plasma must not explode");
                }
                SkillVfxService.plasmaImpact(helper.getLevel(), center, 12);
                // Server connections may defer their flush until the end of the current tick.
                after(1, () -> {
                    for (var observer : observers) check(observer.drain().stream().filter(p -> p.state instanceof SkillVfxState.Burst b
                            && b.plasmaImpact()).count() == 1, "Impact must arrive independently after parent removal");
                    startLoad();
                });
            });
        }
        void startLoad() {
            for (int i = 0; i < count; i++) { beam(false); stressPlasmas.add(plasma()); }
            after(8, () -> {
                clearPackets();
                for (var observer : observers) observer.encodedBytes = 0;
                // 100 physical ticks of simultaneous stable beams and advancing plasma charge.
                after(1, () -> advanceCharge(1));
            });
        }
        void advanceCharge(int tick) {
            stressPlasmas.forEach(p -> p.setGatherProgress(tick / 240f));
            if (tick < 100) after(1, () -> advanceCharge(tick + 1));
            else after(1, this::finishLoad);
        }
        void finishLoad() {
            long packetsTotal = 0, bytesTotal = 0;
            for (var observer : observers) {
                var packets = observer.drain();
                Set<Long> seen = new HashSet<>();
                for (var packet : packets) seen.add(packet.id);
                check(seen.size() == count * 2, "Every simultaneous effect must receive a renewed snapshot");
                check(packets.size() <= count * 16, "Stable visual updates must remain below per-tick entity traffic");
                packetsTotal += packets.size(); bytesTotal += observer.encodedBytes;
            }
            AcademyCraft.getLogger().info("VFX_MULTIPLAYER_RESULT observers={} simultaneousEffects={} windowTicks=101 packets={} encodedBytes={} stats={}",
                    count, count * 2, packetsTotal, bytesTotal, SkillVfxRuntime.statistics());
            close(); helper.succeed();
        }
        void close() {
            if (closed) return;
            closed = true;
            entities.forEach(Entity::discard);
            observers.forEach(Observer::close);
        }
    }
}
