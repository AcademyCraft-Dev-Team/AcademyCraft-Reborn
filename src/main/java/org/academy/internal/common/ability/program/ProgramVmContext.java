package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramValue;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public final class ProgramVmContext {
    private final long gameTime;
    private final Map<String, ProgramValue<?>> variables;
    private final Map<String, Object> executorState;
    private final @Nullable Object attachment;
    private int nodeId = -1;
    private @Nullable Identifier nodeType;

    ProgramVmContext(
            long gameTime,
            Map<String, ProgramValue<?>> variables,
            Map<String, Object> executorState,
            @Nullable Object attachment
    ) {
        this.gameTime = gameTime;
        this.variables = variables;
        this.executorState = executorState;
        this.attachment = attachment;
    }

    public long gameTime() {
        return gameTime;
    }

    /**
     * Stable identity of the node currently invoking an executor.
     */
    public int nodeId() {
        if (nodeId < 0) throw new IllegalStateException("No program node is currently executing");
        return nodeId;
    }

    public Identifier nodeType() {
        if (nodeType == null) throw new IllegalStateException("No program node is currently executing");
        return nodeType;
    }

    public Optional<ProgramValue<?>> variable(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    public void setVariable(String name, ProgramValue<?> value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Program variable name cannot be blank");
        }
        variables.put(name, value);
    }

    public void removeVariable(String name) {
        variables.remove(name);
    }

    Optional<Object> executorState(String name) {
        return Optional.ofNullable(executorState.get(name));
    }

    void setExecutorState(String name, Object value) {
        executorState.put(name, value);
    }

    void removeExecutorState(String name) {
        executorState.remove(name);
    }

    public <T> Optional<T> attachment(Class<T> type) {
        return type.isInstance(attachment) ? Optional.of(type.cast(attachment)) : Optional.empty();
    }

    void enterNode(int nodeId, Identifier nodeType) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }
}
