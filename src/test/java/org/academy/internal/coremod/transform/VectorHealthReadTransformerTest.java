package org.academy.internal.coremod.transform;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VectorHealthReadTransformerTest {
    @Test
    void guardsEveryParentHealthReturn() {
        var owner = new ClassNode();
        owner.name = "net/minecraft/world/entity/LivingEntity";
        var getter = new MethodNode(Opcodes.ACC_PUBLIC, "getHealth", "()F", null, null);
        getter.instructions.add(new InsnNode(Opcodes.FCONST_0));
        getter.instructions.add(new InsnNode(Opcodes.FRETURN));
        getter.instructions.add(new InsnNode(Opcodes.FCONST_1));
        getter.instructions.add(new InsnNode(Opcodes.FRETURN));
        getter.maxLocals = 1;
        getter.maxStack = 1;
        owner.methods.add(getter);

        VectorHealthReadTransformer.apply(owner);

        var guards = 0;
        for (var instruction : getter.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            assertEquals("guardParentRead", call.name);
            assertEquals("(Lnet/minecraft/world/entity/LivingEntity;F)F", call.desc);
            assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
            guards++;
        }
        assertEquals(2, guards);
    }

    @Test
    void failsWhenMinecraftChangesTheParentGetter() {
        assertThrows(IllegalStateException.class, () -> VectorHealthReadTransformer.apply(new ClassNode()));
    }
}
