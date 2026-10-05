package org.academy.internal.client.render.vfxgraph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.common.vfx.EffectSequence;
import org.academy.api.common.vfx.SkillVfxState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(Dist.CLIENT)
public final class SkillVfxStates {
    public interface OneShot {
        void accept(SkillVfxState state, long tick);

        void reset();
    }

    public interface Session {
        void accept(long id, SkillVfxState state, long tick);

        void tick(long tick);

        void reset();
    }

    private static final EffectSequence SEQUENCE = new EffectSequence(16384);
    private static final List<OneShot> ONE_SHOTS = new ArrayList<>();
    private static final List<Session> SESSIONS = new ArrayList<>();
    private static @Nullable ClientLevel world;
    private static long received;
    private static long clientTick;

    private SkillVfxStates() {
    }

    public static void registerOneShot(OneShot sink) {
        ONE_SHOTS.add(sink);
    }

    public static void registerSession(Session sink) {
        SESSIONS.add(sink);
    }

    public static void accept(Identifier dimension, long id, long revision, SkillVfxState state) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> accept(dimension, id, revision, state));
            return;
        }
        var level = minecraft.level;
        if (level != world) reset(level);
        if (level == null || !level.dimension().identifier().equals(dimension)) return;
        boolean terminal = state.oneShot() || (state instanceof SkillVfxState.End end && !end.hidden());
        if (!SEQUENCE.accept(id, revision, terminal)) return;
        received++;
        if (state instanceof SkillVfxState.End) {
            for (var sink : SESSIONS) sink.accept(id, state, clientTick);
            return;
        }
        if (state.oneShot()) {
            for (var sink : ONE_SHOTS) sink.accept(state, clientTick);
            return;
        }
        for (var sink : SESSIONS) sink.accept(id, state, clientTick);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != world) reset(level);
        if (level == null || Minecraft.getInstance().isPaused()) return;
        clientTick++;
        for (var sink : SESSIONS) sink.tick(clientTick);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset(null);
    }

    private static void reset(@Nullable ClientLevel level) {
        SEQUENCE.clear();
        for (var sink : ONE_SHOTS) sink.reset();
        for (var sink : SESSIONS) sink.reset();
        clientTick = 0;
        world = level;
    }

    public static long tick() {
        return clientTick;
    }

    public static long receivedPackets() {
        return received;
    }
}