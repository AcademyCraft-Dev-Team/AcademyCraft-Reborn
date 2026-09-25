package org.academy.internal.coremod;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

/**
 * Narrow inliner for the merged, private (float)->float health hook.
 */
public final class HealthReadInliner {
    private HealthReadInliner() {
    }

    public static void apply(ClassNode owner) {
        var body = owner.methods.stream().filter(method -> method.name.contains("academy$inlineOffsetRead")
                && method.desc.equals("(F)F")).findFirst().orElseThrow();
        if (!body.tryCatchBlocks.isEmpty()) throw new IllegalStateException("Unexpected health hook handlers");
        int replaced = 0;
        for (var caller : owner.methods) {
            if (caller == body) continue;
            for (var instruction : caller.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(owner.name)
                        || !call.name.equals(body.name) || !call.desc.equals(body.desc)) continue;
                int base = caller.maxLocals;
                caller.maxLocals += body.maxLocals;
                var copy = new InsnList();
                copy.add(new VarInsnNode(Opcodes.FSTORE, base + 1));
                copy.add(new VarInsnNode(Opcodes.ASTORE, base));
                var labels = new HashMap<LabelNode, LabelNode>();
                for (var node : body.instructions)
                    if (node instanceof LabelNode label) labels.put(label, new LabelNode());
                var end = new LabelNode();
                for (var node : body.instructions) {
                    if (node instanceof FrameNode || node instanceof LineNumberNode) continue;
                    if (node.getOpcode() == Opcodes.FRETURN) {
                        copy.add(new JumpInsnNode(Opcodes.GOTO, end));
                    } else {
                        var cloned = node.clone(labels);
                        if (cloned instanceof VarInsnNode variable) variable.var += base;
                        if (cloned instanceof IincInsnNode increment) increment.var += base;
                        copy.add(cloned);
                    }
                }
                copy.add(end);
                caller.instructions.insertBefore(call, copy);
                caller.instructions.remove(call);
                caller.maxStack += body.maxStack;
                for (var node : caller.instructions.toArray())
                    if (node instanceof FrameNode) caller.instructions.remove(node);
                replaced++;
            }
        }
        if (replaced == 0) throw new IllegalStateException("Health offset hook was not inlined");
        owner.methods.remove(body);
    }
}
