package org.academy.mixin.common.compat;

import org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint.PhysicsConstraintHandle;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import org.academy.internal.common.structure.sable.SableJointIndex;
import org.academy.internal.common.structure.sable.SableStructureJoints;
import org.joml.Vector3dc;
import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe constraint handles from the pinned pipeline; do not change any constraint operation. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class MixinSableJointTracking implements SableStructureJoints.TrackedPipeline {
    @Unique private final SableJointIndex<Object, PhysicsConstraintHandle> academy$joints =
            new SableJointIndex<>(PhysicsConstraintHandle::isValid);
    @Unique private boolean academy$jointSceneReady;

    @Inject(method = "init", at = @At("RETURN"))
    private void academy$beginJointObservation(Vector3dc gravity, double drag, CallbackInfo callback) {
        academy$joints.clear();
        academy$jointSceneReady = true;
    }

    @Inject(method = "addConstraint", at = @At("RETURN"))
    private void academy$recordJoint(@Coerce Object first, @Coerce Object second,
                                     @Coerce Object configuration,
                                     CallbackInfoReturnable<Object> callback) {
        var handle = SableReflection.wrap(PhysicsConstraintHandle.class, callback.getReturnValue());
        if (SableReflection.optional(ServerSubLevel.class, first) != null) academy$joints.record(first, handle);
        if (SableReflection.optional(ServerSubLevel.class, second) != null) academy$joints.record(second, handle);
    }

    @Inject(method = "remove(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V", at = @At("RETURN"))
    private void academy$forgetRemovedBody(@Coerce Object source, CallbackInfo callback) {
        academy$joints.remove(source);
    }

    @Inject(method = "dispose", at = @At("HEAD"))
    private void academy$endJointObservation(CallbackInfo callback) {
        academy$jointSceneReady = false;
        academy$joints.clear();
    }

    @Override
    public boolean academy$hasJoint(Object source) {
        return !academy$jointSceneReady || academy$joints.hasAny(source);
    }
}
