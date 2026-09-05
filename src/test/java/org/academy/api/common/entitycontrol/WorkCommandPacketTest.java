package org.academy.api.common.entitycontrol;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import org.academy.internal.common.ability.mentalout.skills.lv5.WideAreaInterference;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkCommandPacketTest {
    @Test void workPacketPreservesSettingsAndTargetOrder() {
        var settings = WorkSettings.defaults();
        var json = WorkSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow().toString();
        var packet = new WideAreaInterference.CommandPacket(42, WideAreaInterference.Action.WORK,
                List.of(UUID.randomUUID(), UUID.randomUUID()), new BlockPos(-3, 63, 8), new BlockPos(4, 65, 9), null, json);
        var first = Unpooled.buffer();
        var second = Unpooled.buffer();
        try {
            WideAreaInterference.CommandPacket.CODEC.encode(first, packet);
            var copy = first.copy();
            WideAreaInterference.CommandPacket decoded;
            try { decoded = WideAreaInterference.CommandPacket.CODEC.decode(copy); }
            finally { copy.release(); }
            assertEquals(settings, decoded.workSettings());
            WideAreaInterference.CommandPacket.CODEC.encode(second, decoded);
            assertEquals(first, second);
        } finally { first.release(); second.release(); }
    }
    @Test void refusesOversizedConfigurationBeforeDispatch() {
        assertThrows(IllegalArgumentException.class, () -> new WideAreaInterference.CommandPacket(1,
                WideAreaInterference.Action.WORK, List.of(UUID.randomUUID()), BlockPos.ZERO, BlockPos.ZERO,
                null, "x".repeat(8193)));
    }
}
