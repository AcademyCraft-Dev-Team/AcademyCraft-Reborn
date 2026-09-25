package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.WingControlIntent;
import org.academy.api.common.ability.WingFlightMotion;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server authority for wing input and pose, without replaying velocity packets to the pilot.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class WingFlightRuntime {
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();

    private WingFlightRuntime() {
    }

    private static final class Session {
        final Skill skill;
        final ServerLevel level;
        final WingControlIntent.Mailbox controls = new WingControlIntent.Mailbox();
        long sequence;
        int lastPlayerTick = Integer.MIN_VALUE;
        int previousButtons;

        Session(ServerPlayer player, Skill skill) {
            this.skill = skill;
            level = player.level();
        }
    }

    private static Session session(ServerPlayer player, Skill skill) {
        var session = SESSIONS.get(player);
        if (session == null || session.skill != skill || session.level != player.level()) {
            session = new Session(player, skill);
            SESSIONS.put(player, session);
        }
        return session;
    }

    public static void accept(ServerPlayer player, Skill skill, WingControlIntent input) {
        if (!skill.isEnabled(player) || !player.isAlive() || player.hasDisconnected()) return;
        var session = session(player, skill);
        session.controls.accept(++session.sequence, input, System.nanoTime());
    }

    public static void clear(ServerPlayer player, Skill skill) {
        var session = SESSIONS.get(player);
        if (session != null && session.skill == skill) SESSIONS.remove(player);
    }

    public static void tick(ServerPlayer player, Skill skill) {
        if (!skill.isEnabled(player) || !player.isAlive() || player.hasDisconnected()) {
            clear(player, skill);
            return;
        }
        if (player.isPassenger() || player.isSpectator()) return;
        var session = session(player, skill);
        if (session.lastPlayerTick == player.tickCount) return;
        session.lastPlayerTick = player.tickCount;
        var input = session.controls.sample(System.nanoTime(), player.getYRot(), player.getXRot());
        double scale = skill == Skills.STORM_WING.get() && skill.hasProficiencyMilestone(player, 2) ? 1.15 : 1;
        EntityMotionGuard.runWithMotionSource(player, () -> player.setDeltaMovement(WingFlightMotion.step(
                WingFlightMotion.afterMove(player.getDeltaMovement(), session.previousButtons), input,
                session.previousButtons, input.remainingMomentum(), scale)));
        session.previousButtons = input.buttons();
        player.resetFallDistance();
        WingFlightPose.sync(player, input.has(WingControlIntent.BOOST) ? WingFlightPose.Pose.FAST
                : input.buttons() != 0 ? WingFlightPose.Pose.SLOW : WingFlightPose.coastingPose(player));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SESSIONS.remove(player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }
}
