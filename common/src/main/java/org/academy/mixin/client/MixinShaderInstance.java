package org.academy.mixin.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.academy.AcademyCraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance {
    /**
     *  这里使用 static 的原因是, forge 的 mixin 要求这个方法为 static
     *  Mixin apply failed academy.mixins.json:MixinShaderInstance -> net.minecraft.client.renderer.ShaderInstance: org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException @ModifyArg handler before this() invocation must be static in injector net/minecraft/client/renderer/ShaderInstance::resourceLocation [INJECT Applicator Phase -> academy.mixins.json:MixinShaderInstance -> Apply Injections ->  -> Inject -> academy.mixins.json:MixinShaderInstance->@ModifyArg::resourceLocation(Ljava/lang/String;)Ljava/lang/String;]
     */
    @ModifyArg(
            method = {"<init>"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V"
            ),
            allow = 1
    )
    private static String resourceLocation(String original) {
        var prefix = "shaders/core/";
        var parts = original.split(":");

        if (parts.length == 2) {
            var potentialPath = parts[0];
            var filename = parts[1];

            if (potentialPath.startsWith(prefix)) {
                var namespace = potentialPath.substring(prefix.length());

                if (namespace.equals(AcademyCraft.MOD_ID)) {
                    var result = namespace + ":" + prefix + filename;
                    AcademyCraft.LOGGER.debug("Remapping shader location from '{}' to '{}'", original, result);
                    return result;
                }
            }
        }
        return original;
    }
}