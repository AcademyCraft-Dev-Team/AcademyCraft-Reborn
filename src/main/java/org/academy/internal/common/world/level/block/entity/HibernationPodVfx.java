package org.academy.internal.common.world.level.block.entity;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.network.SpawnVfxGraphPacket;

/**
 * 休眠舱开舱冷气：前半段只铺舱内软雾；开盖放出御坂时才从舱心向前喷，像把人推出来。
 * 使用项目 mist 图，不回退原版粒子。
 */
public final class HibernationPodVfx {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final Identifier MIST_BURST = graph("aeromanip_mist_burst");
    private static final Identifier MIST_FIELD = graph("aeromanip_mist_field");
    private static final Identifier MIST_STREAM = graph("aeromanip_mist_stream");

    /** 妹妹躯干高度（舱内站立）。 */
    private static final double TORSO_Y = 1.05;
    private static final double DOOR_MOUTH = 0.48;

    private static final float FIELD_SCALE = 0.70f;
    private static final float BURST_SCALE = 0.55f;
    /** 推出拍略强于日常补雾，但仍克制。 */
    private static final float PUSH_BURST_SCALE = 0.72f;
    private static final float PUSH_STREAM_SCALE = 0.36f;
    private static final float STREAM_SCALE = 0.28f;

    private static final float LIFE_PUFF = 0.55f;
    private static final float LIFE_HOLD = 0.75f;
    private static final float LIFE_PUSH = 0.70f;

    private HibernationPodVfx() {
    }

    /** 开舱瞬间：只起舱内冷气，不往外喷。 */
    public static void opening(ServerLevel level, BlockPos mainPos, Direction doorFacing) {
        softCabin(level, mainPos, LIFE_HOLD);
    }

    /**
     * 开舱过程：维持舱内软雾。舱心外喷留给 {@link #opened}，与放人同步。
     */
    public static void openingTick(ServerLevel level, BlockPos mainPos, Direction doorFacing, int progress) {
        int openTicks = HibernationPodBlockEntity.OPENING_TICKS;
        // 盖将尽时停软雾，把视觉让给推出拍。
        if (progress >= openTicks - 1) {
            return;
        }
        if (progress % 2 == 0) {
            softCabin(level, mainPos, LIFE_PUFF);
        }
    }

    /**
     * 开盖完成、御坂踏出：舱心 mist 向前推，门口接一缕，读成把人送出舱。
     */
    public static void opened(ServerLevel level, BlockPos mainPos, Direction doorFacing) {
        pushSisterOut(level, mainPos, doorFacing);
    }

    /** 舱内体积软雾（无舱心爆发、无门口射流）。 */
    private static void softCabin(ServerLevel level, BlockPos mainPos, float life) {
        RandomSource random = level.getRandom();
        Vec3 lower = cabinPos(mainPos, 0.75 + random.nextDouble() * 0.2);
        Vec3 upper = cabinPos(mainPos, 1.55 + random.nextDouble() * 0.25);
        spawn(level, MIST_FIELD, lower, UP, FIELD_SCALE, life);
        spawn(level, MIST_FIELD, upper, UP, FIELD_SCALE * 0.9f, life);
    }

    /**
     * 推出拍：从舱心（妹妹位置）沿出门方向爆发 + 流束，门口再接一小股。
     */
    private static void pushSisterOut(ServerLevel level, BlockPos mainPos, Direction doorFacing) {
        Vec3 outward = outward(doorFacing);
        // 舱心 / 躯干：主推力。
        Vec3 torso = cabinPos(mainPos, TORSO_Y);
        spawn(level, MIST_BURST, torso, outward, PUSH_BURST_SCALE, LIFE_PUSH);
        spawn(level, MIST_STREAM, torso, outward, PUSH_STREAM_SCALE, LIFE_PUSH);
        // 门口接住外溢，避免推力停在舱内。
        Vec3 door = doorPos(mainPos, doorFacing, TORSO_Y);
        spawn(level, MIST_STREAM, door, outward, STREAM_SCALE, LIFE_PUFF);
        spawn(level, MIST_BURST, door, outward, BURST_SCALE, LIFE_PUFF);
    }

    private static Vec3 cabinPos(BlockPos mainPos, double y) {
        return new Vec3(mainPos.getX() + 0.5, mainPos.getY() + y, mainPos.getZ() + 0.5);
    }

    private static Vec3 doorPos(BlockPos mainPos, Direction door, double y) {
        return new Vec3(
                mainPos.getX() + 0.5 + door.getStepX() * DOOR_MOUTH,
                mainPos.getY() + y,
                mainPos.getZ() + 0.5 + door.getStepZ() * DOOR_MOUTH
        );
    }

    private static Vec3 outward(Direction door) {
        return new Vec3(door.getStepX(), 0.06, door.getStepZ()).normalize();
    }

    private static void spawn(ServerLevel level, Identifier graph, Vec3 position,
            Vec3 direction, float scale, float lifetimeSeconds) {
        var safe = direction.lengthSqr() > 1.0e-8 ? direction.normalize() : UP;
        SpawnVfxGraphPacket.broadcast(level, graph, position, safe, -1, scale, lifetimeSeconds, Map.of());
    }

    private static Identifier graph(String name) {
        return AcademyCraft.academy("vfxgraph/" + name);
    }
}
