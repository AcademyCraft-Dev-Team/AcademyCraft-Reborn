package org.academy.mixin.common;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.academy.internal.common.advancement.AbilityAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ServerAdvancementManager.class)
public abstract class MixinServerAdvancementManager {
    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;"
                    + "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL")
    )
    private void alignAbilityAdvancementBranches(
            Map<Identifier, Advancement> advancements,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci
    ) {
        var manager = (ServerAdvancementManager) (Object) this;
        var root = manager.get(AbilityAdvancements.ROOT);
        if (root != null) {
            root.value().display().ifPresent(display -> display.setLocation(0.0f, 0.0f));
        }
        for (var id : AbilityAdvancements.BRANCHES) {
            var branch = manager.get(id);
            if (branch != null) {
                branch.value().display().ifPresent(display -> display.setLocation(1.0f, 0.0f));
            }
        }
    }
}
