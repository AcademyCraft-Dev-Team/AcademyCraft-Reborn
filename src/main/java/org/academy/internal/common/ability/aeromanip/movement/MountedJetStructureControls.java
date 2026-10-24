package org.academy.internal.common.ability.aeromanip.movement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.aeromanip.JetStructureControl;
import org.academy.api.common.structure.*;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;

import java.util.*;

/** Repeated nozzle activation drives each retained structure once, sharing a whole-body force budget. */
final class MountedJetStructureControls {
    private static final Map<UUID, Map<StructureRef, Control>> CONTROLS = new HashMap<>();

    private MountedJetStructureControls() {
    }

    static void tick(ServerPlayer player) {
        var groups = new HashMap<StructureRef, List<HighSpeedJetNozzle.StructureMount>>();
        var host = (ServerLevel) player.level();
        if (player.isAlive() && !player.hasDisconnected() && Skills.HIGH_SPEED_JET.get().isEnabled(player)) {
            for (var nozzle : HighSpeedJet.Server.ownedNozzles(host, player)) {
                if (nozzle.isRemoved() || nozzle.activeTicks() <= 0) continue;
                nozzle.structureMount().ifPresent(mount -> {
                    if (!HighSpeedJetStructureService.manages(player, mount.reference())
                            && HighSpeedJetStructureService.resolvesSupportCell(host, mount.reference(), mount.offset())) {
                        groups.computeIfAbsent(mount.reference(), ignored -> new ArrayList<>()).add(mount);
                    }
                });
            }
        }
        var slots = CONTROLS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        for (var entry : List.copyOf(slots.entrySet())) {
            if (!groups.containsKey(entry.getKey())) {
                entry.getValue().close();
                slots.remove(entry.getKey());
            }
        }
        for (var entry : groups.entrySet()) {
            var reference = entry.getKey();
            var handle = StructureSpaces.resolve(host.getServer(), reference).orElse(null);
            var motion = StructureDynamics.motion(host, reference).orElse(null);
            if (handle == null || motion == null) continue;
            var control = slots.get(reference);
            if (control != null && (control.host != host || !control.runtimeId.equals(handle.runtimeId()))) {
                control.close();
                slots.remove(reference);
                control = null;
            }
            var directions = entry.getValue().stream().map(mount -> motion.pose().localToWorldDirection(
                    HighSpeedJetStructureService.localThrustDirection(mount.face()))).toList();
            var force = JetStructureControl.limitedCombinedThrustForce(directions, motion.linearVelocity(),
                    motion.mass(), 1, Skills.HIGH_SPEED_JET.get().getEffectiveProficiencyMilestone(player));
            var command = new StructurePhysicsControl.Force(motion.localCenterOfMass(), force);
            if (control == null) {
                control = acquire(player, host, handle, command);
                if (control == null) continue;
                slots.put(reference, control);
            }
            if (!control.update(player, command)) {
                control.close();
                slots.remove(reference);
            }
        }
        if (slots.isEmpty()) CONTROLS.remove(player.getUUID());
    }

    private static Control acquire(ServerPlayer player, ServerLevel host, StructureHandle handle,
                                   StructurePhysicsControl command) {
        var control = new Control(host, handle.reference(), handle.runtimeId());
        if (handle.reference().backend().equals(StructureBackendRouter.SABLE_ID)
                || SubLevelStructureRefs.isSubLevel(handle.reference())) {
            if (StructureDirectControl.occupied(handle.reference(), handle.runtimeId())
                    || StructureDirectControl.acquire(host, handle.reference(), player, control.id)
                    != StructureDirectControl.Status.ACTIVE) return null;
            control.direct = true;
        } else {
            var region = StructurePhysics.activeRegion(host, handle.reference()).orElse(null);
            if (region == null) {
                try {
                    region = StructurePhysics.adoptExistingRegion(host, handle.reference());
                } catch (IllegalStateException | IllegalArgumentException rejection) {
                    return null;
                }
            }
            if (region.status() != StructurePhysicsRegion.Status.READY
                    || region.motion(handle.reference()).isEmpty()) return null;
            control.lease = region.acquireEntityControl(handle.reference(), player, control.id, command, 3).orElse(null);
            if (control.lease == null) return null;
            control.region = region;
        }
        return control;
    }

    static void clear(UUID actor) {
        var controls = CONTROLS.remove(actor);
        if (controls != null) controls.values().forEach(Control::close);
    }

    static void clearAll() {
        for (var actor : List.copyOf(CONTROLS.keySet())) clear(actor);
    }

    private static final class Control {
        final UUID id = UUID.randomUUID();
        final ServerLevel host;
        final StructureRef reference;
        final UUID runtimeId;
        StructurePhysicsLease lease;
        StructurePhysicsRegion region;
        boolean direct;

        Control(ServerLevel host, StructureRef reference, UUID runtimeId) {
            this.host = host;
            this.reference = reference;
            this.runtimeId = runtimeId;
        }

        boolean update(ServerPlayer player, StructurePhysicsControl command) {
            if (direct) return StructureDirectControl.submit(host, reference, player, id, command);
            if (lease == null || lease.state() != StructurePhysicsLease.State.ACTIVE) return false;
            try {
                lease.setControl(command);
                lease.renew(3);
                var motion = region.motion(reference).orElse(null);
                return motion != null && HighSpeedJetStructureService.followRegion(host, reference, region, motion);
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                return false;
            }
        }

        void close() {
            if (direct) StructureDirectControl.release(reference, id);
            if (lease != null) lease.close();
        }
    }
}
