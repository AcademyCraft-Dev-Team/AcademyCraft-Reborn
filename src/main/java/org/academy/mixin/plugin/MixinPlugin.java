package org.academy.mixin.plugin;

import org.academy.api.client.compatibility.IrisIntegration;
import org.academy.internal.common.compat.SableContainerMixinGate;
import org.academy.internal.common.compat.SableStaffMixinGate;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        IrisIntegration.init();
    }

    @Override
    public @Nullable String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("org.academy.mixin.common.compat.MixinSablePhysicsStaffHandler")
                || mixinClassName.equals("org.academy.mixin.common.compat.MixinSablePhysicsStaffDragSession")) {
            return SableStaffMixinGate.state() == SableStaffMixinGate.State.VERIFIED;
        }
        if (mixinClassName.equals("org.academy.mixin.common.compat.MixinSableSubLevelContainerTick")
                || mixinClassName.equals("org.academy.mixin.common.compat.MixinSableRestorationIsolation")
                || mixinClassName.equals("org.academy.mixin.common.compat.MixinSableRestorationPhysics")
                || mixinClassName.equals("org.academy.mixin.common.compat.MixinSableJointTracking")) {
            return SableContainerMixinGate.state() == SableContainerMixinGate.State.VERIFIED;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
