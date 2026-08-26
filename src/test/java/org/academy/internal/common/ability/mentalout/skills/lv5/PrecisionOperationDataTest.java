package org.academy.internal.common.ability.mentalout.skills.lv5;

import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PrecisionOperationDataTest {
    private static PrecisionGraph validGraph() {
        return new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(1, PrecisionGraph.NodeKind.ROSTER, 0.0, 8.0, 8.0),
                        new PrecisionGraph.Node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR, 0.0, 24.0, 8.0)
                ),
                List.of(new PrecisionGraph.Edge(1, 0, 2, 0))
        );
    }

    private static PrecisionGraph invalidGraph() {
        return new PrecisionGraph(
                List.of(new PrecisionGraph.Node(
                        1,
                        PrecisionGraph.NodeKind.END_INTRUSION,
                        0.0,
                        Double.NaN,
                        0.0
                )),
                List.of()
        );
    }

    private static PrecisionGraph legacyMultiActionGraph() {
        return new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(1, PrecisionGraph.NodeKind.ROSTER, 0.0, 8.0, 8.0),
                        new PrecisionGraph.Node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR, 0.0, 24.0, 8.0),
                        new PrecisionGraph.Node(3, PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION, 0.0, 40.0, 8.0)
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 2, 0),
                        new PrecisionGraph.Edge(1, 0, 3, 0)
                )
        );
    }

    private static PrecisionGraph flowGraph() {
        return new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(1, PrecisionGraph.NodeKind.ROSTER, 0.0, 8.0, 8.0),
                        new PrecisionGraph.Node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR, 0.0, 24.0, 8.0),
                        new PrecisionGraph.Node(3, PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION, 20.0, 40.0, 8.0)
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 2, 0),
                        new PrecisionGraph.Edge(1, 0, 3, 0),
                        new PrecisionGraph.Edge(2, 0, 3, 1)
                )
        );
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void dataAlwaysExposesFourNormalizedSlots() throws ReflectiveOperationException {
        var data = new PrecisionOperation.Data();
        setField(data, "revision", -5L);
        setField(data, "slots", new ArrayList<>(List.of(validGraph(), invalidGraph())));

        PrecisionOperation.normalizeData(data);

        assertEquals(0L, data.revision());
        assertEquals(validGraph().validate().normalized(), data.slot(0));
        assertEquals(PrecisionGraph.EMPTY, data.slot(1));
        assertEquals(PrecisionGraph.EMPTY, data.slot(2));
        assertEquals(PrecisionGraph.EMPTY, data.slot(3));
    }

    @Test
    void replacingSlotAdvancesRevisionAndCopyIsIndependent() {
        var data = new PrecisionOperation.Data();
        data.replaceSlot(2, validGraph());
        var copy = data.copy();

        data.replaceSlot(2, PrecisionGraph.EMPTY);

        assertEquals(2L, data.revision());
        assertEquals(1L, copy.revision());
        assertNotEquals(data.slot(2), copy.slot(2));
        assertEquals(validGraph().validate().normalized(), copy.slot(2));
    }

    @Test
    void replacementIndexIsBoundedToFixedSlotRange() {
        var data = new PrecisionOperation.Data();

        data.replaceSlot(-20, validGraph());
        data.replaceSlot(20, validGraph());

        assertEquals(validGraph().validate().normalized(), data.slot(0));
        assertEquals(validGraph().validate().normalized(), data.slot(3));
        assertEquals(2L, data.revision());
    }

    @Test
    void versionOneDataMigratesFlowOnlyOnce() throws ReflectiveOperationException {
        var data = new PrecisionOperation.Data();
        setField(data, "schemaVersion", 1);
        setField(data, "revision", 4L);
        setField(data, "slots", new ArrayList<>(List.of(legacyMultiActionGraph())));

        PrecisionOperation.normalizeData(data);
        PrecisionOperation.normalizeData(data);

        assertEquals(PrecisionOperation.Data.SCHEMA_VERSION, data.schemaVersion());
        assertEquals(5L, data.revision());
        assertEquals(List.of(2, 3), data.slot(0).validate().actionOrder());
    }

    @Test
    void versionTwoDataKeepsExistingFlowAndMigratesPermanentDurations() throws ReflectiveOperationException {
        var data = new PrecisionOperation.Data();
        setField(data, "schemaVersion", 2);
        setField(data, "revision", 7L);
        setField(data, "slots", new ArrayList<>(List.of(flowGraph())));

        PrecisionOperation.normalizeData(data);
        PrecisionOperation.normalizeData(data);

        assertEquals(PrecisionOperation.Data.SCHEMA_VERSION, data.schemaVersion());
        assertEquals(8L, data.revision());
        assertEquals(flowGraph().validate().normalized(), data.slot(0));
        assertEquals(0.0, data.slot(0).nodes().stream()
                .filter(node -> node.kind() == PrecisionGraph.NodeKind.MENTAL_STUPOR)
                .findFirst().orElseThrow().parameter());
    }
}
