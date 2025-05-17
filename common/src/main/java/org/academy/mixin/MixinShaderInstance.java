package org.academy.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.academy.AcademyCraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@SuppressWarnings("InvalidInjectorMethodSignature")
@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance {
    @ModifyArg(
            method = {"<init>"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V"
            ),
            allow = 1
    )
    private static String resourceLocation(String original) {
        String prefix = "shaders/core/";
        String[] parts = original.split(":");

        if (parts.length == 2) {
            String potentialPath = parts[0];
            String filename = parts[1];

            if (potentialPath.startsWith(prefix)) {
                String namespace = potentialPath.substring(prefix.length());

                if (namespace.equals(AcademyCraft.MOD_ID)) {
                    String result = namespace + ":" + prefix + filename;
                    AcademyCraft.LOGGER.debug("Remapping shader location from '{}' to '{}'", original, result);
                    return result;
                }
            }
        }
        return original;
    }
}