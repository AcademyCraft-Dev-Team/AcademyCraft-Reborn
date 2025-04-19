package org.academy.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.academy.AcademyCraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ShaderInstance.class)
public class ShaderInstanceMixin {
    @ModifyArg(
            method = {"<init>"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V"
            ),
            allow = 1
    )
    private String resourceLocation(String original) {
        String[] parts = original.split(":");
        if (parts.length == 2) {
            String center = parts[0];
            String namespace = center.replace("shaders/core/", "");
            if (namespace.equals(AcademyCraft.MOD_ID)) {
                String filename = parts[1];
                String result = namespace + ":" + "shaders/core/" + filename;
                AcademyCraft.LOGGER.info(result);
                return result;
            }
        }
        return original;
    }
}