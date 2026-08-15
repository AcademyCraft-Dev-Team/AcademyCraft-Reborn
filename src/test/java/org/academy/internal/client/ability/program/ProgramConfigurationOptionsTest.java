package org.academy.internal.client.ability.program;

import com.google.gson.JsonPrimitive;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeCatalog;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeIds;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeCatalog;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeIds;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramNodeCatalog;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramNodeIds;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.PrecisionProgramNodeIds;
import org.academy.internal.common.ability.teleport.program.TeleportProgramNodeCatalog;
import org.academy.internal.common.ability.teleport.program.TeleportProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramConfigurationOptionsTest {
    @Test
    void numericKindsAndOperatorsUseFiniteStepOptions() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        var arithmetic = catalog.entry(CommonProgramNodeIds.NUMERIC_ARITHMETIC);
        var comparison = catalog.entry(CommonProgramNodeIds.NUMERIC_COMPARE);

        assertEquals(
                List.of("integer", "big_integer", "float"),
                values(ProgramConfigurationOptions.options(
                        arithmetic, "type", new JsonPrimitive("integer")))
        );
        assertEquals(
                List.of("add", "subtract", "multiply", "divide", "modulo"),
                values(ProgramConfigurationOptions.options(
                        arithmetic, "operator", new JsonPrimitive("add")))
        );
        assertEquals(
                List.of("equal", "less", "less_equal", "greater", "greater_equal"),
                values(ProgramConfigurationOptions.options(
                        comparison, "operator", new JsonPrimitive("equal")))
        );
    }

    @Test
    void everyKnownFiniteConfigurationUsesOptionsButFreeNumbersRemainText() {
        var mentalout = AbilityProgramDefinitions.mentalout().editorCatalog();
        var scalar = mentalout.entry(CommonProgramNodeIds.SCALAR_CONSTANT);
        assertEquals(4, ProgramConfigurationOptions.options(
                scalar, "type", new JsonPrimitive("integer")).size());
        assertTrue(ProgramConfigurationOptions.options(
                scalar, "value", new JsonPrimitive("12.5")).isEmpty());

        var movement = mentalout.entry(CommonProgramNodeIds.TRIGGER_MOVEMENT);
        assertEquals(
                List.of("jump", "sneak", "sprint", "elytra", "swim"),
                values(ProgramConfigurationOptions.options(
                        movement, "condition", new JsonPrimitive("jump")))
        );
        var loop = mentalout.entry(CommonProgramNodeIds.TRIGGER_LOOP);
        assertTrue(ProgramConfigurationOptions.options(
                loop, "interval", new JsonPrimitive(20)).isEmpty());
        assertEquals(List.of("below", "above"), values(
                ProgramConfigurationOptions.options(
                        mentalout.entry(CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD),
                        "mode", new JsonPrimitive("below"))));

        assertEquals(
                List.of("entity", "block"),
                values(ProgramConfigurationOptions.options(
                        mentalout.entry(CommonProgramNodeIds.LOOK_TARGET),
                        "target_type",
                        new JsonPrimitive("entity")
                ))
        );

        assertEquals(16, ProgramConfigurationOptions.options(
                mentalout.entry(CommonProgramNodeIds.VARIABLE_GET),
                "type",
                new JsonPrimitive("academy:program_type/boolean")
        ).size());
        assertEquals(9, ProgramConfigurationOptions.options(
                mentalout.entry(CommonProgramNodeIds.FILTER_ENTITY_TYPE),
                "type",
                new JsonPrimitive("living")
        ).size());
        assertEquals(2, ProgramConfigurationOptions.options(
                mentalout.entry(CommonProgramNodeIds.BOOLEAN_CONSTANT),
                "value",
                new JsonPrimitive(false)
        ).size());

        assertEquals(7, precisionOptions(mentalout, PrecisionGraph.NodeKind.ABILITY_SUPPORTED));
        assertEquals(2, precisionOptions(mentalout, PrecisionGraph.NodeKind.SORT_BY_DISTANCE));
        assertEquals(8, precisionOptions(mentalout, PrecisionGraph.NodeKind.TYPE_FILTER));

        var accelerator = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR).editorCatalog();
        var action = accelerator.entry(AcceleratorProgramNodeIds.APPLY_VECTOR);
        assertEquals(List.of("0", "1", "2"), values(ProgramConfigurationOptions.options(
                action, "strength", new JsonPrimitive(1))));
        var shockwave = accelerator.entry(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE);
        assertEquals(2, ProgramConfigurationOptions.options(
                shockwave, "destroy_blocks", new JsonPrimitive(false)).size());
        assertTrue(ProgramConfigurationOptions.options(
                shockwave, "power", new JsonPrimitive(1.0f)).isEmpty());
        assertTrue(ProgramConfigurationOptions.isPowerSlider(
                "power", new JsonPrimitive(1.0f)));
        assertTrue(ProgramConfigurationOptions.options(
                shockwave, "radius", new JsonPrimitive(11)).isEmpty());

        var meltdowner = AbilityProgramDefinitions.require(
                MeltdownerProgramNodeCatalog.MELTDOWNER).editorCatalog();
        assertEquals(List.of("direction", "target"), values(
                ProgramConfigurationOptions.options(
                        meltdowner.entry(MeltdownerProgramNodeIds.ELECTRON_BEAM),
                        "aim_mode", new JsonPrimitive("direction"))));

        var electromaster = AbilityProgramDefinitions.require(
                ElectromasterProgramNodeCatalog.ELECTROMASTER).editorCatalog();
        assertEquals(List.of("entity", "block"), values(
                ProgramConfigurationOptions.options(
                        electromaster.entry(ElectromasterProgramNodeIds.MAGNETIC_MOVE),
                        "target_type", new JsonPrimitive("entity"))));
        assertEquals(List.of("pull", "launch"), values(
                ProgramConfigurationOptions.options(
                        electromaster.entry(ElectromasterProgramNodeIds.MAGNETIC_MOVE),
                        "mode", new JsonPrimitive("pull"))));
        assertEquals(List.of("below", "above"), values(
                ProgramConfigurationOptions.options(
                        electromaster.entry(ElectromasterProgramNodeIds.ENERGY_DETECTION),
                        "mode", new JsonPrimitive("below"))));

        var teleport = AbilityProgramDefinitions.require(
                TeleportProgramNodeCatalog.TELEPORT).editorCatalog();
        assertEquals(List.of("entity", "block"), values(
                ProgramConfigurationOptions.options(
                        teleport.entry(TeleportProgramNodeIds.ENTITY_TELEPORT),
                        "target_type", new JsonPrimitive("entity"))));
    }

    @Test
    void stepButtonsWrapInBothDirections() {
        var options = ProgramConfigurationOptions.options(
                AbilityProgramDefinitions.mentalout().editorCatalog()
                        .entry(CommonProgramNodeIds.NUMERIC_COMPARE),
                "operator",
                new JsonPrimitive("equal")
        );

        assertEquals("less", ProgramConfigurationOptions.step(
                options, new JsonPrimitive("equal"), 1).value().getAsString());
        assertEquals("greater_equal", ProgramConfigurationOptions.step(
                options, new JsonPrimitive("equal"), -1).value().getAsString());
    }

    private static List<String> values(List<ProgramConfigurationOptions.Option> options) {
        return options.stream().map(option -> option.value().getAsString()).toList();
    }

    private static int precisionOptions(
            org.academy.internal.common.ability.program.ProgramEditorNodeCatalog catalog,
            PrecisionGraph.NodeKind kind
    ) {
        return ProgramConfigurationOptions.options(
                catalog.entry(PrecisionProgramNodeIds.id(kind)),
                "parameter",
                new JsonPrimitive(kind.defaultParameter())
        ).size();
    }
}
