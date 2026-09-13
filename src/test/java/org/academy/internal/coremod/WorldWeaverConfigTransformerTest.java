package org.academy.internal.coremod;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldWeaverConfigTransformerTest {
    @Test
    void protectsOnlyBetterEndPillarNbtAndLeavesChunkAccessOutsideTheLock() {
        var owner = new ClassNode();
        owner.name = "net/minecraft/world/level/levelgen/feature/EndSpikeFeature";
        var handler = handler("org.betterx.betterend.mixin.common.SpikeFeatureMixin");
        owner.methods.add(handler);
        var chunkAccess = new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "net/minecraft/world/level/ServerLevelAccessor", "getChunk",
                "(II)Lnet/minecraft/world/level/chunk/ChunkAccess;", true);
        handler.instructions.add(chunkAccess);
        var foreign = handler("example.OtherMixin");
        owner.methods.add(foreign);

        assertEquals(3, WorldWeaverConfigTransformer.apply(owner));
        for (var instruction : handler.instructions) {
            if (instruction == chunkAccess) continue;
            var call = (MethodInsnNode) instruction;
            assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
            assertEquals("org/academy/internal/common/compat/WorldWeaverConfigAccess", call.owner);
            assertTrue(call.desc.startsWith("(Lnet/minecraft/nbt/CompoundTag;"));
        }
        assertEquals(Opcodes.INVOKEINTERFACE, chunkAccess.getOpcode());
        assertEquals("net/minecraft/nbt/CompoundTag", ((MethodInsnNode) foreign.instructions.getFirst()).owner);
        assertEquals(0, WorldWeaverConfigTransformer.apply(owner));
    }

    @Test
    void protectsPodiumCodecAccessAndSpikeHeightReads() {
        for (var target : List.of("EndPodiumFeature", "EndSpikeFeature$EndSpike")) {
            var owner = new ClassNode();
            owner.name = "net/minecraft/world/level/levelgen/feature/" + target;
            var method = handler("org.betterx.betterend.mixin.common."
                    + (target.equals("EndPodiumFeature") ? "EndPodiumFeatureMixin" : "EndSpikeMixin"));
            method.name = "be_updatePortalPos";
            method.instructions.clear();
            if (target.equals("EndPodiumFeature")) {
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag",
                        "read", "(Ljava/lang/String;Lcom/mojang/serialization/Codec;)Ljava/util/Optional;", false));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag",
                        "store", "(Ljava/lang/String;Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V", false));
            } else {
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag",
                        "contains", "(Ljava/lang/String;)Z", false));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag",
                        "getIntOr", "(Ljava/lang/String;I)I", false));
            }
            owner.methods.add(method);
            assertEquals(2, WorldWeaverConfigTransformer.apply(owner));
            for (var instruction : method.instructions) {
                var call = (MethodInsnNode) instruction;
                assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                assertEquals("org/academy/internal/common/compat/WorldWeaverConfigAccess", call.owner);
            }
        }
    }

    @Test
    void absentBetterEndIsANoOp() {
        var owner = new ClassNode();
        owner.name = "net/minecraft/world/level/levelgen/feature/EndSpikeFeature";
        assertEquals(0, WorldWeaverConfigTransformer.apply(owner));
    }

    private static MethodNode handler(String mixin) {
        var method = new MethodNode(Opcodes.ACC_PRIVATE, "handler$random$betterend$be_placeSpike", "()V", null, null);
        var annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = List.of("mixin", mixin);
        method.visibleAnnotations = List.of(annotation);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag",
                "contains", "(Ljava/lang/String;)Z", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag",
                "getIntOr", "(Ljava/lang/String;I)I", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag",
                "putInt", "(Ljava/lang/String;I)V", false));
        return method;
    }
}
