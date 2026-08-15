package org.academy.internal.common.ability.meltdowner.skills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;

public final class ContinuousBeam {
    private static final float MAIN_HAND_VISUAL_OFFSET = 0.35f;

    private ContinuousBeam() {
    }

    public static HighSpeedElectronBeam spawn(ServerLevel level, ServerPlayer player,
                                              float scale, float initialLength) {
        return spawn(level, player, scale, initialLength, 0.0f);
    }

    public static HighSpeedElectronBeam spawnFromMainHand(ServerLevel level, ServerPlayer player,
                                                          float scale, float initialLength) {
        return spawn(level, player, scale, initialLength, mainHandVisualSideOffset(player));
    }

    private static HighSpeedElectronBeam spawn(ServerLevel level, ServerPlayer player,
                                               float scale, float initialLength, float visualSideOffset) {
        var beam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
        beam.shouldStopRay = false;
        beam.fired = true;
        beam.currentChargerTicks = HighSpeedElectronBeam.MAX_CHARGE_TICKS;
        beam.setContinuous(true);
        beam.setNoGravity(true);
        beam.setBeamScale(scale);
        beam.setVisualSideOffset(visualSideOffset);
        beam.setBeamLength(initialLength);
        follow(player, beam, initialLength);
        level.addFreshEntity(beam);
        return beam;
    }

    public static boolean follow(ServerPlayer player, HighSpeedElectronBeam beam, float length) {
        return follow(player, beam, length, 0.0f);
    }

    public static boolean followFromMainHand(ServerPlayer player, HighSpeedElectronBeam beam,
                                             float length, float forwardOffset) {
        return follow(player, beam, length, Math.max(0.0f, forwardOffset));
    }

    public static Vec3 mainHandOrigin(ServerPlayer player, float forwardOffset) {
        return player.getEyePosition().add(0.0, -0.3, 0.0)
                .add(player.getLookAngle().scale(Math.max(0.0f, forwardOffset)));
    }

    public static float mainHandVisualSideOffset(ServerPlayer player) {
        return player.getMainArm() == HumanoidArm.LEFT
                ? -MAIN_HAND_VISUAL_OFFSET
                : MAIN_HAND_VISUAL_OFFSET;
    }

    private static boolean follow(ServerPlayer player, HighSpeedElectronBeam beam,
                                  float length, float forwardOffset) {
        if (beam == null || beam.isRemoved() || beam.level() != player.level()) return false;
        beam.setPos(mainHandOrigin(player, forwardOffset));
        beam.setYRot(player.getYRot());
        beam.setXRot(player.getXRot());
        beam.setBeamLength(length);
        return true;
    }

    public static void kill(HighSpeedElectronBeam beam) {
        if (beam != null && !beam.isRemoved()) beam.discard();
    }
}
