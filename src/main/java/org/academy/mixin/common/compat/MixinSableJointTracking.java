package org.academy.mixin.common.compat;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.academy.internal.common.structure.sable.SableJointIndex;
import org.academy.internal.common.structure.sable.SableStructureJoints;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe constraint handles from the pinned pipeline; do not change any constraint operation. */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class MixinSableJointTracking implements SableStructureJoints.TrackedPipeline {
    @Unique private final SableJointIndex<ServerSubLevel, PhysicsConstraintHandle> academy$joints =
            new SableJointIndex<>(PhysicsConstraintHandle::isValid);
    @Unique private boolean academy$jointSceneReady;

    @Inject(method = "init", at = @At("RETURN"))
    private void academy$beginJointObservation(Vector3dc gravity, double drag, CallbackInfo callback) {
        academy$joints.clear();
        academy$jointSceneReady = true;
    }

    @Inject(method = "addConstraint", at = @At("RETURN"))
    private void academy$recordJoint(PhysicsPipelineBody first, PhysicsPipelineBody second,
                                     PhysicsConstraintConfiguration<?> configuration,
                                     CallbackInfoReturnable<PhysicsConstraintHandle> callback) {
        var handle = callback.getReturnValue();
        if (first instanceof ServerSubLevel source) academy$joints.record(source, handle);
        if (second instanceof ServerSubLevel source) academy$joints.record(source, handle);
    }

    @Inject(method = "remove(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V", at = @At("RETURN"))
    private void academy$forgetRemovedBody(ServerSubLevel source, CallbackInfo callback) {
        academy$joints.remove(source);
    }

    @Inject(method = "dispose", at = @At("HEAD"))
    private void academy$endJointObservation(CallbackInfo callback) {
        academy$jointSceneReady = false;
        academy$joints.clear();
    }

    @Override
    public boolean academy$hasJoint(ServerSubLevel source) {
        return !academy$jointSceneReady || academy$joints.hasAny(source);
    }
}
