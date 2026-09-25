package org.academy.internal.common.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProgramConfigurationPortsTest {
    @Test
    void inferredPortsAreOptionalAndUnconnectedFieldsRetainConfiguration() {
        var configuration = new TestConfiguration("fallback", 2, 0.5);
        var ports = ProgramConfigurationPorts.infer(
                TestConfiguration.CODEC, configuration, ProgramNodeRole.VALUE);
        var schema = ProgramConfigurationPorts.attach(ProgramNodeSchema.EMPTY, ports);

        assertEquals(List.of("text", "count", "amount"),
                schema.inputs().stream().map(port -> port.name()).toList());
        assertTrue(schema.inputs().stream().noneMatch(port -> port.required()));
        assertSame(configuration, ProgramConfigurationPorts.resolve(
                TestConfiguration.CODEC, configuration, ports, new ProgramInputView(Map.of())));
    }

    @Test
    void connectedTextTagAndNumbersOverrideConfigurationThroughCodec() {
        var configuration = new TestConfiguration("fallback", 2, 0.5);
        var ports = ProgramConfigurationPorts.infer(
                TestConfiguration.CODEC, configuration, ProgramNodeRole.VALUE);
        var inputs = new ProgramInputView(Map.of(
                "text", List.of(new ProgramValue<>(
                        ProgramValueTypes.TAG,
                        new ProgramTag(Identifier.parse("minecraft:mineable/pickaxe"))
                )),
                "count", List.of(new ProgramValue<>(ProgramValueTypes.INTEGER, 7)),
                "amount", List.of(new ProgramValue<>(ProgramValueTypes.INTEGER, 3))
        ));

        var resolved = ProgramConfigurationPorts.resolve(
                TestConfiguration.CODEC, configuration, ports, inputs);

        assertEquals(new TestConfiguration("#minecraft:mineable/pickaxe", 7, 3.0), resolved);
    }

    @Test
    void entryConfigurationDoesNotExposeRuntimePorts() {
        assertTrue(ProgramConfigurationPorts.infer(
                TestConfiguration.CODEC,
                new TestConfiguration("entry", 1, 1.0),
                ProgramNodeRole.ENTRY
        ).isEmpty());
    }

    @Test
    void numericStringsFollowTheirConfiguredScalarKind() {
        var configuration = new CommonProgramNodeCatalog.ScalarConfiguration(
                CommonProgramNodeCatalog.ScalarKind.BIG_INTEGER,
                "42"
        );

        var ports = ProgramConfigurationPorts.infer(
                CommonProgramNodeCatalog.ScalarConfiguration.CODEC,
                configuration,
                ProgramNodeRole.VALUE
        );

        assertEquals(1, ports.size());
        assertEquals("value", ports.getFirst().field());
        assertEquals(ProgramValueTypes.BIG_INTEGER, ports.getFirst().type());
    }

    private record TestConfiguration(String text, int count, double amount) {
        private static final Codec<TestConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("text").forGetter(TestConfiguration::text),
                        Codec.INT.fieldOf("count").forGetter(TestConfiguration::count),
                        Codec.DOUBLE.fieldOf("amount").forGetter(TestConfiguration::amount)
                ).apply(instance, TestConfiguration::new));
    }
}
