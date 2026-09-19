package org.academy.api.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.vfx.RepairVisualParts;
import org.academy.api.common.vfx.DirectionalArea;
import org.academy.internal.common.network.DarkmatterVisualPacket;
import org.academy.internal.common.network.DarkmatterVisualPacket.Kind;

import java.util.Map;
import java.util.WeakHashMap;

/** Visual-only events for players, programs and non-player actors. No combat/resource decisions. */
public final class DarkmatterGraphEffects {
    private record RepairPulse(long tick, int parts) { }
    private static final Map<LivingEntity, RepairPulse> REPAIR_PULSES = new WeakHashMap<>();
    private DarkmatterGraphEffects() { }

    public static void interference(LivingEntity actor, float range) {
        var cone = new DirectionalArea.Cone(range, Math.cos(Math.toRadians(40)));
        interference(actor, new DirectionalArea(actor.getEyePosition(), actor.getLookAngle(), cone, cone));
    }
    public static void interference(LivingEntity actor, DirectionalArea area) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        new DarkmatterVisualPacket(level.dimension().identifier(), Kind.INTERFERENCE, actor.getId(), true,
                area.origin(), Math.min(32, actor.getBbWidth()), Math.min(64, actor.getBbHeight()),
                (float) Math.min(512, area.radius()), 0, false,
                actor.getUUID().getLeastSignificantBits(), area).broadcast(level);
    }
    public static void stopInterference(LivingEntity actor) { attached(actor, Kind.INTERFERENCE, false, 0, 0); }
    public static void lightContact(LivingEntity target) { attached(target, Kind.CONTACT, true, 0, 0); }

    public static void repair(LivingEntity subject) { repair(subject, RepairVisualParts.BODY); }
    public static void repair(LivingEntity subject, int parts) {
        parts &= RepairVisualParts.ALL;
        if (parts == 0) return;
        long now = subject.level().getGameTime();
        var previous = REPAIR_PULSES.get(subject);
        if (previous != null && now >= previous.tick && now - previous.tick < 5) {
            if ((parts & ~previous.parts) == 0) return;
            parts |= previous.parts;
        }
        REPAIR_PULSES.put(subject, new RepairPulse(now, parts));
        attached(subject, Kind.REPAIR, true, 0, parts);
    }

    public static void disassemble(LivingEntity subject) { attached(subject, Kind.DISASSEMBLE, true, 0, 0); }

    /** Call only after successful removal; retain the removed block's exposed surface bounds. */
    public static void disassembleBlock(ServerLevel level, BlockPos position) {
        int faces = 0;
        for (var face : net.minecraft.core.Direction.values()) {
            if (!level.getBlockState(position.relative(face)).isSolidRender()) faces |= 1 << face.ordinal();
        }
        if (faces == 0) return;
        new DarkmatterVisualPacket(level.dimension().identifier(), Kind.DISASSEMBLE, -1, true,
                Vec3.atBottomCenterOf(position), 1, 1, 0, faces, true, position.asLong()).broadcast(level);
    }

    public static void disassemble(ServerLevel level, Vec3 position, float radius) {
        new DarkmatterVisualPacket(level.dimension().identifier(), Kind.DISASSEMBLE, -1, true,
                position, Math.clamp(radius * 2, 0.1f, 32), Math.clamp(radius * 2, 0.1f, 64),
                0, 63, false, BlockPos.containing(position).asLong()).broadcast(level);
    }

    private static void attached(LivingEntity actor, Kind kind, boolean active, float range, int parts) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        new DarkmatterVisualPacket(level.dimension().identifier(), kind, actor.getId(), active, actor.position(),
                Math.min(32, actor.getBbWidth()), Math.min(64, actor.getBbHeight()), Math.clamp(range, 0, 512), parts,
                !actor.isAlive(), actor.getUUID().getLeastSignificantBits()).broadcast(level);
    }
}
