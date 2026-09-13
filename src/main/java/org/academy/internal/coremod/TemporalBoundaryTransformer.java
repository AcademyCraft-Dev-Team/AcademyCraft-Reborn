package org.academy.internal.coremod;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Applies immunity at known vanilla/Academy boundaries without naming addons or their handlers. */
public final class TemporalBoundaryTransformer {
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String ENTITY = "Lnet/minecraft/world/entity/Entity;";
    private static final String SERVER = "org/academy/internal/server/time/TemporalBoundaryProtection";
    private static final String CLIENT = "org/academy/internal/client/time/TemporalClientRuntime";
    private static final Map<String, Set<String>> INPUT_BOUNDARIES = Map.of(
            "net/minecraft/client/KeyboardHandler", Set.of("keyPress"),
            "net/minecraft/client/MouseHandler", Set.of("onButton", "onScroll", "turnPlayer"),
            "org/academy/api/client/input/InputSystem", Set.of("handleKey", "handleMouseButton", "handleMouseScroll")
    );

    private TemporalBoundaryTransformer() {}

    public static void apply(ClassNode owner) {
        if (owner.name.equals("net/minecraft/server/level/ServerLevel")) {
            rewrite(owner, method -> method.name.equals("tickNonPassenger"), SERVER, "protectTickCallback", 1);
            rewrite(owner, method -> method.name.equals("tickPassenger"), SERVER, "protectTickCallback", 2);
        } else if (owner.name.equals("net/minecraft/client/multiplayer/ClientLevel")) {
            rewrite(owner, method -> method.name.equals("tickNonPassenger"), CLIENT, "protectTickCallback", 1);
        } else {
            var boundaries = INPUT_BOUNDARIES.get(owner.name);
            if (boundaries != null) {
                rewrite(owner, method -> boundaries.contains(method.name), CLIENT, "protectInputCallback", -1);
            }
        }
    }

    static int rewrite(ClassNode owner, Predicate<MethodNode> boundary, String guardOwner,
                       String guardMethod, int entityLocal) {
        var foreignHandlers = new java.util.HashSet<String>();
        for (var method : owner.methods) {
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")) continue;
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (annotation.values.get(i).equals("mixin")
                            && !annotation.values.get(i + 1).toString().startsWith("org.academy.")) {
                        foreignHandlers.add(method.name + method.desc);
                    }
                }
            }
        }
        int changed = 0;
        for (var method : owner.methods) {
            if (!boundary.test(method)) continue;
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(owner.name)
                        || !call.desc.endsWith(CALLBACK + ")V")
                        || !foreignHandlers.contains(call.name + call.desc)) continue;
                if (call.getPrevious() instanceof MethodInsnNode previous
                        && previous.owner.equals(guardOwner) && previous.name.equals(guardMethod)) continue;
                var protection = new InsnList();
                if (entityLocal >= 0) protection.add(new VarInsnNode(Opcodes.ALOAD, entityLocal));
                protection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, guardOwner, guardMethod,
                        "(" + CALLBACK + (entityLocal >= 0 ? ENTITY : "") + ")" + CALLBACK, false));
                method.instructions.insertBefore(call, protection);
                method.maxStack += entityLocal >= 0 ? 1 : 0;
                changed++;
            }
        }
        return changed;
    }
}
