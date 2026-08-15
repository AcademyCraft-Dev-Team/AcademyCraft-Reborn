package org.academy.api.common.ability.program;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * The typed port surface exposed by one configured node instance.
 */
public record ProgramNodeSchema(
        List<ProgramPortDefinition> inputs,
        List<ProgramPortDefinition> outputs
) {
    public static final ProgramNodeSchema EMPTY = new ProgramNodeSchema(List.of(), List.of());

    public ProgramNodeSchema {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        requireUniqueNames(inputs, "input");
        requireUniqueNames(outputs, "output");
    }

    public Optional<ProgramPortDefinition> input(String name) {
        return inputs.stream().filter(port -> port.name().equals(name)).findFirst();
    }

    public Optional<ProgramPortDefinition> output(String name) {
        return outputs.stream().filter(port -> port.name().equals(name)).findFirst();
    }

    private static void requireUniqueNames(List<ProgramPortDefinition> ports, String direction) {
        var names = new HashSet<String>();
        for (var port : ports) {
            if (!names.add(port.name())) {
                throw new IllegalArgumentException("Duplicate program " + direction + " port " + port.name());
            }
        }
    }
}
