package org.academy.internal.common.ability.electromaster;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.ability.electromaster.MagneticFieldTuning;
import org.academy.api.server.ability.*;
import org.academy.api.server.ability.electromaster.MagneticFieldEffects;
import org.academy.api.server.ability.electromaster.MagneticLevitation;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.Map;

/** Player lifecycle adapter; reusable effects and motion remain in the public API. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MagneticFieldRuntime {
    private static final Map<Player, Flight> FLIGHTS = Skill.createContextMap();
    private MagneticFieldRuntime() {}

    public static boolean isHovering(ServerPlayer player) {
        var flight = FLIGHTS.get(player);
        return flight != null && flight.hovering;
    }

    public static boolean toggle(ServerPlayer player) {
        var current = FLIGHTS.get(player);
        if (current != null) { current.unregister(); return false; }
        if (!SkillAvailability.requestExecution(player, Skills.MAGNET_MANIPULATION.get(), false)
                || !player.isAlive() || player.isSpectator() || player.isPassenger()) return false;
        var flight = new Flight(player);
        FLIGHTS.put(player, flight);
        state(player, true, false);
        AbilitySystemServer.registerContext(flight);
        return true;
    }

    private static void state(ServerPlayer player, boolean requested, boolean hovering) {
        if (player.getData(AttachmentTypes.MAGNETIC_LEVITATION_REQUESTED) != requested) {
            player.setData(AttachmentTypes.MAGNETIC_LEVITATION_REQUESTED, requested);
            player.syncData(AttachmentTypes.MAGNETIC_LEVITATION_REQUESTED);
        }
        if (player.getData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE) != hovering) {
            player.setData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE, hovering);
            player.syncData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE);
        }
    }

    @SubscribeEvent public static void tickPassives(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MagneticFieldEffects.setPassives(player, player.isAlive()
                && SkillAvailability.isLearnedAndAvailable(player, Skills.MAGNET_MANIPULATION.get()));
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var flight = FLIGHTS.get(player);
        if (flight != null) flight.unregister();
        MagneticFieldEffects.setPassives(player, false);
    }

    public static final class Flight extends ServerContext {
        private final MagneticLevitation movement = new MagneticLevitation();
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private int activeTicks;
        private int unsupportedTicks;
        private boolean hovering;
        private boolean ended;

        Flight(ServerPlayer player) { super(player); dimension = player.level().dimension(); }

        @SubscribeEvent public void tick(ServerTickEvent.Pre event) {
            if (ended) return;
            var skill = Skills.MAGNET_MANIPULATION.get();
            if (player.hasDisconnected() || !player.isAlive() || player.isSpectator() || player.isPassenger()
                    || !dimension.equals(player.level().dimension()) || !skill.isEnabled(player)
                    || !SkillAvailability.requestExecution(player, skill, true)) {
                unregister(); return;
            }
            var radius = MagneticFieldTuning.supportRadius(skill.getEffectiveProficiencyMilestone(player));
            if (unsupportedTicks > 0) { unsupportedTicks--; return; }
            if (!movement.supported(player, radius) || !EntityMotionGuard.canApplyMotionFrom(player, player)) {
                hovering = false;
                GravityControl.set(player, MagneticFieldEffects.SOURCE, false);
                state(player, true, false);
                unsupportedTicks = 4;
                return;
            }
            if (activeTicks % 20 == 0 && !AbilitySystemServer.getSystem(player).tryTimedOccupation(player.getUUID(),
                    skill.adjustProficiencyCost(player, SkillProficiencyProfile.CostKind.CONTINUOUS, 10), skill, 10)) {
                unregister(); return;
            }
            var input = player.getLastClientInput();
            if (input == null) input = Input.EMPTY;
            var velocity = movement.velocity(player, (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0),
                    (input.left() ? 1 : 0) - (input.right() ? 1 : 0),
                    (input.jump() ? 1 : 0) - (input.shift() ? 1 : 0), MagneticFieldTuning.FLIGHT_SPEED_PER_TICK, radius);
            GravityControl.set(player, MagneticFieldEffects.SOURCE, true);
            EntityMotionGuard.runWithMotionSource(player, () -> player.setDeltaMovement(velocity));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.resetFallDistance();
            hovering = true;
            state(player, true, true);
            if (activeTicks++ == 0) skill.reportTrigger(player);
            skill.reportActivity(player, velocity.lengthSqr() > 1.0e-8);
        }

        @Override protected void onUnregistered() {
            ended = true;
            FLIGHTS.remove(player, this);
            GravityControl.set(player, MagneticFieldEffects.SOURCE, false);
            state(player, false, false);
        }
    }
}
