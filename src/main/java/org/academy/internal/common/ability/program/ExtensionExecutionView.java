package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import java.util.Optional;

/** Independent view prevents exposing ProgramVmContext or accepting submissions after the executor returns. */
final class ExtensionExecutionView implements ProgramExecutionContext {
    private final ProgramVmContext vm;
    private final ProgramNodeExtension<?> extension;
    private final int nodeId;
    private final Identifier nodeType;
    private boolean active = true;

    ExtensionExecutionView(ProgramVmContext vm, ProgramNodeExtension<?> extension) {
        this.vm = vm;
        this.extension = extension;
        nodeId = vm.nodeId();
        nodeType = vm.nodeType();
    }

    @Override public long gameTime() { return vm.gameTime(); }
    @Override public int nodeId() { return nodeId; }
    @Override public Identifier nodeType() { return nodeType; }
    @Override public Optional<ProgramTargetResolver> targetResolver() { return vm.targetResolver(); }

    @Override
    public void submit(ProgramAction action) {
        if (!active || extension.role() != ProgramNodeRole.ACTION) {
            throw new IllegalStateException("Only the currently executing ACTION node may submit actions");
        }
        var frame = vm.attachment(ProgramExecutionFrame.class)
                .orElseThrow(() -> new IllegalStateException("No server action frame"));
        frame.extensionActions().stage(nodeId, extension.scope(), action);
    }

    void close() { active = false; }
}
