package org.academy.internal.common.ability.electromaster;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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
        state(player, requested, hovering, false);
    }

    private static void state(ServerPlayer player, boolean requested, boolean hovering, boolean degraded) {
        if (player.getData(AttachmentTypes.MAGNETIC_LEVITATION_REQUESTED) != requested) {
            player.setData(AttachmentTypes.MAGNETIC_LEVITATION_REQUESTED, requested);
            player.syncData(AttachmentTypes.MAGNETIC_LEVITATION_REQUESTED);
        }
        if (player.getData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE) != hovering) {
            player.setData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE, hovering);
            player.syncData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE);
        }
        if (player.getData(AttachmentTypes.MAGNETIC_LEVITATION_DEGRADED) != degraded) {
            player.setData(AttachmentTypes.MAGNETIC_LEVITATION_DEGRADED, degraded);
            player.syncData(AttachmentTypes.MAGNETIC_LEVITATION_DEGRADED);
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
        private int graceTicks;
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
            var tuning = MagneticFieldEffects.levitationTuning(player);
            var radius = MagneticFieldTuning.supportRadius(skill.getEffectiveProficiencyMilestone(player));
            var canMove = EntityMotionGuard.canApplyMotionFrom(player, player);
            var input = player.getLastClientInput();
            if (input == null) input = Input.EMPTY;
            var forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
            var strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
            var vertical = (input.jump() ? 1 : 0) - (input.shift() ? 1 : 0);
            // One solved tick carries field presence and ground clearance together, so the support probe
            // runs once per tick rather than once for admission and again for motion.
            var step = canMove
                    ? movement.step(player, forward, strafe, vertical,
                            MagneticFieldTuning.FLIGHT_SPEED_PER_TICK, radius, tuning)
                    : null;

            if (step == null || !step.supported()) {
                // Losing the field degrades into a bounded, steerable sink instead of dropping the gravity
                // lease at once, so a mover that grazes the boundary can still climb back inside.
                if (graceTicks >= tuning.graceTicks()) { releaseField(); return; }
                graceTicks++;
                GravityControl.set(player, MagneticFieldEffects.SOURCE, true);
                applyMotion(movement.degradedVelocity(player, forward, strafe, vertical,
                        MagneticFieldTuning.FLIGHT_SPEED_PER_TICK, tuning));
                hovering = false;
                state(player, true, false, true);
                skill.reportActivity(player, true);
                return;
            }
            graceTicks = 0;

            if (activeTicks % 20 == 0 && !AbilitySystemServer.getSystem(player).tryTimedOccupation(player.getUUID(),
                    skill.adjustProficiencyCost(player, SkillProficiencyProfile.CostKind.CONTINUOUS, 10), skill, 10)) {
                unregister(); return;
            }
            GravityControl.set(player, MagneticFieldEffects.SOURCE, true);
            applyMotion(step.velocity());
            hovering = true;
            state(player, true, true);
            if (activeTicks++ == 0) skill.reportTrigger(player);
            skill.reportActivity(player, step.velocity().lengthSqr() > 1.0e-8);
        }

        private void applyMotion(Vec3 velocity) {
            EntityMotionGuard.runWithMotionSource(player, () -> player.setDeltaMovement(velocity));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.resetFallDistance();
        }

        private void releaseField() {
            graceTicks = 0;
            hovering = false;
            GravityControl.set(player, MagneticFieldEffects.SOURCE, false);
            state(player, true, false);
        }

        @Override protected void onUnregistered() {
            ended = true;
            graceTicks = 0;
            FLIGHTS.remove(player, this);
            GravityControl.set(player, MagneticFieldEffects.SOURCE, false);
            state(player, false, false);
        }
    }
}
