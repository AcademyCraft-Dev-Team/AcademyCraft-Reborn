package org.academy.internal.coremod.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adds the per-player health guard to the merged parent implementation. */
public final class VectorHealthReadTransformer {
    private static final String LEDGER =
            "org/academy/internal/common/ability/accelerator/reflection/VectorHealthLedger";

    private VectorHealthReadTransformer() {
    }

    public static void apply(ClassNode owner) {
        var method = owner.methods.stream()
                .filter(candidate -> candidate.name.equals("getHealth") && candidate.desc.equals("()F"))
                .findFirst().orElseThrow(() -> new IllegalStateException("LivingEntity.getHealth is missing"));
        var replaced = 0;
        for (var instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.FRETURN) continue;
            var guard = new InsnList();
            var resultSlot = method.maxLocals++;
            guard.add(new VarInsnNode(Opcodes.FSTORE, resultSlot));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new VarInsnNode(Opcodes.FLOAD, resultSlot));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LEDGER, "guardParentRead",
                    "(Lnet/minecraft/world/entity/LivingEntity;F)F", false));
            method.instructions.insertBefore(instruction, guard);
            method.maxStack = Math.max(method.maxStack, 2);
            replaced++;
        }
        if (replaced == 0) throw new IllegalStateException("LivingEntity.getHealth has no float return");
    }
}
