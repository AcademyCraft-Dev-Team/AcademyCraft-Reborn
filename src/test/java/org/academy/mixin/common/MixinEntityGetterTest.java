package org.academy.mixin.common;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.internal.common.structure.BlockStructureCollisionRuntime.CollisionQuery;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MixinEntityGetterTest {
    @Test
    void ordinaryEntityKeepsTheWrappedOperationAndItsExtraShape() throws Exception {
        var originalShape = Shapes.block();
        var foreignShape = Shapes.box(2, 0, 0, 3, 1, 1);
        var builder = ImmutableList.<VoxelShape>builder();
        var calls = new AtomicInteger();
        Operation<ImmutableList.Builder<VoxelShape>> original = arguments -> {
            calls.incrementAndGet();
            assertSame(builder, arguments[0]);
            assertSame(originalShape, arguments[1]);
            return builder.add((VoxelShape) arguments[1]).add(foreignShape);
        };
        var handler = MixinEntityGetter.class.getDeclaredMethod(
                "academy$replaceStructureBounds", ImmutableList.Builder.class, Object.class,
                Operation.class, Entity.class, AABB.class, Entity.class, LocalRef.class);
        handler.setAccessible(true);
        var query = new LocalRef<CollisionQuery>() {
            private CollisionQuery value;

            @Override
            public CollisionQuery get() {
                return value;
            }

            @Override
            public void set(CollisionQuery value) {
                this.value = value;
            }
        };

        var result = handler.invoke(new MixinEntityGetter() {}, builder, originalShape, original,
                null, new AABB(0, 0, 0, 4, 4, 4), null, query);

        assertSame(builder, result);
        assertEquals(1, calls.get());
        assertEquals(ImmutableList.of(originalShape, foreignShape), builder.build());
    }

    @Test
    void nativeCollisionLoopHasOneTargetAndTheExpectedEntityLocal() throws Exception {
        var type = new ClassNode();
        try (var input = EntityGetter.class.getResourceAsStream("EntityGetter.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(type, 0);
        }
        var method = type.methods.stream()
                .filter(candidate -> candidate.name.equals("getEntityCollisions"))
                .findFirst().orElseThrow();
        var additions = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("com/google/common/collect/ImmutableList$Builder")
                    && call.name.equals("add")
                    && call.desc.equals("(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;")) {
                additions.add(call);
            }
        }
        assertEquals(1, additions.size(), "The narrow collision replacement must target exactly one operation");
        int target = method.instructions.indexOf(additions.getFirst());
        var entityLocals = method.localVariables.stream()
                .filter(local -> local.desc.equals("Lnet/minecraft/world/entity/Entity;"))
                .filter(local -> method.instructions.indexOf(local.start) <= target
                        && method.instructions.indexOf(local.end) > target)
                .sorted(Comparator.comparingInt(local -> local.index))
                .toList();
        assertEquals(2, entityLocals.size(), "@Local ordinal 1 must remain the loop entity, after source");
        assertEquals(1, entityLocals.getFirst().index);
        assertEquals("entity", entityLocals.getLast().name);
    }
}
