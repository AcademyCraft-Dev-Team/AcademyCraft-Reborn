package org.academy.api.common.ability.program;

/**
 * A registry-backed ability-program node supplied by another mod.
 *
 * <p>Register implementations in {@code Registries.Keys.PROGRAM_NODE_TYPES}. AcademyCraft freezes
 * the registered extension set after normal mod registration, then uses the same node instance for
 * compilation, execution, editor presentation, and client/server compatibility fingerprints.</p>
 *
 * @param <C> immutable node configuration decoded by the node type
 */
public interface ProgramNodeExtension<C> extends ProgramNodeType<C> {
    /**
     * Returns the executor invoked through the read-only extension boundary.
     */
    ProgramNodeExecution<C> execution();

    /**
     * Returns immutable palette, inspector, and default-configuration metadata.
     */
    ProgramNodeEditorMetadata editorMetadata();

    /**
     * Compatibility revision included in the deterministic extension fingerprint.
     *
     * <p>Increment this value when execution semantics change without changing the graph schema
     * version.</p>
     */
    default int compatibilityVersion() {
        return schemaVersion();
    }
}
