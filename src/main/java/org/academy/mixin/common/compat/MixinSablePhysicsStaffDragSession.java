package org.academy.mixin.common.compat;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.academy.internal.common.compat.PhysicsStaffDragAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Enabled only for the exact Simulated artifact inspected by SableStaffMixinGate. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler$DragSession", remap = false)
public abstract class MixinSablePhysicsStaffDragSession implements PhysicsStaffDragAccess {
    @Shadow @Final private ServerSubLevel subLevel;

    @Override public Object academy$staffSubLevel() { return subLevel; }
}
