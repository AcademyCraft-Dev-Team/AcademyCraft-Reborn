package org.academy.internal.client.ability.program;

import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgramConnectionDefaultsTest {
    @Test
    void scalarConstantsMatchDraggedInputsWithoutChangingPaletteDefaults() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        var entry = catalog.entry(CommonProgramNodeIds.SCALAR_CONSTANT);
        for (var type : new org.academy.api.common.ability.program.ProgramValueType[]{
                ProgramValueTypes.FLOAT, ProgramValueTypes.INTEGER,
                ProgramValueTypes.BIG_INTEGER, ProgramValueTypes.BOOLEAN}) {
            var configuration = ProgramConfigurationOptions.defaultsForConnection(catalog, entry, type, true);
            assertNotNull(configuration, type.toString());
            assertEquals(type, catalog.schema(entry.id(), configuration).outputs().getFirst().type());
        }
        assertEquals("integer", entry.defaultConfiguration().getAsJsonObject().get("type").getAsString());
        assertNull(ProgramConfigurationOptions.defaultsForConnection(
                catalog, entry, ProgramValueTypes.ENTITY_REFERENCE, true));
    }

    @Test
    void downstreamComparisonMatchesFloatingPointOutput() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        var entry = catalog.entry(CommonProgramNodeIds.NUMERIC_COMPARE);
        var configuration = ProgramConfigurationOptions.defaultsForConnection(
                catalog, entry, ProgramValueTypes.FLOAT, false);
        assertNotNull(configuration);
        var schema = catalog.schema(entry.id(), configuration);
        assertEquals(ProgramValueTypes.FLOAT, schema.input("left").orElseThrow().type());
        assertEquals(ProgramValueTypes.FLOAT, schema.input("right").orElseThrow().type());
    }
}
