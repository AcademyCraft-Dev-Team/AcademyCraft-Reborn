package org.academy.internal.common.ability.mentalout.control;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.entitycontrol.WorkSettings;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkOrderDataTest {
    private WorkOrderData.Entry entry(UUID owner, UUID worker) {
        return new WorkOrderData.Entry(owner.toString(), worker.toString(), Identifier.parse("academy:test"),
                Identifier.parse("minecraft:overworld"), BlockPos.ZERO, BlockPos.ZERO, WorkSettings.defaults(), 100, false, List.of());
    }
    @Test void legacyAssignmentsMigrateToTheSameStableOrderAcrossWorkers() {
        var owner = UUID.randomUUID();
        var first = entry(owner, UUID.randomUUID());
        var second = entry(owner, UUID.randomUUID());
        assertEquals(first.orderId(), second.orderId());
        var json = WorkOrderData.Entry.CODEC.encodeStart(JsonOps.INSTANCE, first).getOrThrow().getAsJsonObject();
        json.remove("order_id"); json.remove("revision"); json.remove("returning");
        assertEquals(first.orderId(), WorkOrderData.Entry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().orderId());
    }
    @Test void selectedLookupRespectsControllerAndSelectionOrder() {
        var owner = UUID.randomUUID();
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var data = new WorkOrderData();
        data.put(entry(owner, a)); data.put(entry(owner, b)); data.put(entry(UUID.randomUUID(), a));
        assertEquals(List.of(b.toString(), a.toString()), data.selected(owner, List.of(b, a)).stream().map(WorkOrderData.Entry::subject).toList());
        data.remove(entry(owner, b).key());
        assertEquals(1, data.selected(owner, List.of(b, a)).size());
    }
    @Test void pendingReturnCannotBeOverwrittenByReplacementAssignment() {
        var original = entry(UUID.randomUUID(), UUID.randomUUID());
        var data = new WorkOrderData();
        data.put(original.asReturning()); data.put(original);
        assertEquals(2, data.entries().size());
        data.remove(original.key());
        assertTrue(data.entries().getFirst().returning());
    }
    @Test void recoveryCursorDoesNotStarveOldEntriesWhenAssignmentsAreUpdated() {
        var data = new WorkOrderData(); var owner = UUID.randomUUID();
        var entries = new ArrayList<WorkOrderData.Entry>();
        for (int i = 0; i < 100; i++) { var e = entry(owner, UUID.randomUUID()); entries.add(e); data.put(e); }
        var seen = new HashSet<String>();
        for (int batch = 0; batch < 10; batch++) {
            data.recoveryBatch(10).forEach(e -> seen.add(e.key()));
            data.put(entries.getFirst().withPaused(batch % 2 == 0));
        }
        assertEquals(100, seen.size());
    }
}
