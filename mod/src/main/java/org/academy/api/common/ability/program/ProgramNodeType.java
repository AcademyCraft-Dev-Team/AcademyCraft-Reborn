package org.academy.api.common.ability.program;

import com.mojang.serialization.Codec;

import java.util.List;

/**
 * Public extension point for a configured node in an ability program.
 *
 * @param <C> immutable configuration decoded from the graph
 */
public interface ProgramNodeType<C> {
    Codec<C> configurationCodec();

    int schemaVersion();

    ProgramNodeSchema schema(C configuration);

    /**
     * Configuration fields that may be supplied by optional data ports at runtime.
     *
     * <p>The default implementation discovers scalar record components whose encoded field has
     * the same name. Node implementations may override this method to expose a narrower surface
     * or to describe fields whose wire name cannot be inferred.</p>
     */
    default List<ProgramConfigurationPort> configurationPorts(C configuration) {
        return ProgramConfigurationPorts.infer(configurationCodec(), configuration, role());
    }

    /**
     * Returns the configured node schema including its optional configuration ports.
     */
    default ProgramNodeSchema resolvedSchema(C configuration) {
        return ProgramConfigurationPorts.attach(
                schema(configuration),
                configurationPorts(configuration)
        );
    }

    /**
     * Applies connected configuration-port values while retaining the stored configuration for
     * every unconnected port.
     */
    default C resolveConfiguration(C configuration, ProgramInputView inputs) {
        var resolved = ProgramConfigurationPorts.resolve(
                configurationCodec(),
                configuration,
                configurationPorts(configuration),
                inputs
        );
        schema(resolved);
        return resolved;
    }

    ProgramNodeRole role();

    ProgramNodePurity purity();

    ProgramNodeScope scope();
}
