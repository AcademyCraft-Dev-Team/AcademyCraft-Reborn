package org.academy.internal.coremod;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

/** Protects only BetterEnd's direct world-config NBT operations, never its chunk or block accesses. */
public final class WorldWeaverConfigTransformer {
    private static final String TAG = "net/minecraft/nbt/CompoundTag";
    private static final String GUARD = "org/academy/internal/common/compat/WorldWeaverConfigAccess";
    private static final Map<String, String> TARGET_MIXINS = Map.of(
            "net/minecraft/world/level/levelgen/feature/EndSpikeFeature", "org.betterx.betterend.mixin.common.SpikeFeatureMixin",
            "net/minecraft/world/level/levelgen/feature/EndSpikeFeature$EndSpike", "org.betterx.betterend.mixin.common.EndSpikeMixin",
            "net/minecraft/world/level/levelgen/feature/EndPodiumFeature", "org.betterx.betterend.mixin.common.EndPodiumFeatureMixin"
    );
    private static final Map<String, String> CALLS = Map.of(
            "contains", "(Ljava/lang/String;)Z",
            "getIntOr", "(Ljava/lang/String;I)I",
            "putInt", "(Ljava/lang/String;I)V",
            "read", "(Ljava/lang/String;Lcom/mojang/serialization/Codec;)Ljava/util/Optional;",
            "store", "(Ljava/lang/String;Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"
    );

    private WorldWeaverConfigTransformer() {}

    public static int apply(ClassNode owner) {
        var targetMixin = TARGET_MIXINS.get(owner.name);
        if (targetMixin == null) return 0;
        var changed = 0;
        for (var method : owner.methods) {
            if (!isBetterEndHandler(method, targetMixin)) continue;
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(TAG)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !call.desc.equals(CALLS.get(call.name))) continue;
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = GUARD;
                call.desc = "(L" + TAG + ";" + call.desc.substring(1);
                call.itf = false;
                changed++;
            }
        }
        return changed;
    }

    private static boolean isBetterEndHandler(MethodNode method, String targetMixin) {
        if (method.visibleAnnotations == null) return false;
        for (var annotation : method.visibleAnnotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                    || annotation.values == null) continue;
            for (var i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1)
                        .equals(targetMixin)) return true;
            }
        }
        return false;
    }
}
