package org.academy.mixin.common.compat;

import org.academy.internal.common.compat.PhysicsStaffDragAccess;
import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Enabled only for the exact Simulated artifact inspected by SableStaffMixinGate. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler$DragSession", remap = false)
public abstract class MixinSablePhysicsStaffDragSession implements PhysicsStaffDragAccess {

    @Override public Object academy$staffSubLevel() { return SableReflection.readField(this, "subLevel"); }
}
