package org.academy.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.academy.AcademyCraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@SuppressWarnings("InvalidInjectorMethodSignature")
@Mixin(ShaderInstance.class)
public class MixinShaderInstance {
    @ModifyArg(
            method = {"<init>"},
            at = @At(
                    value = "INVOKE",
                    // Make sure this target signature is exactly correct for the specific
                    // ResourceLocation constructor being called within the ShaderInstance constructor.
                    // Use bytecode inspection (like javap or a decompiler) if unsure.
                    target = "Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V"
            ),
            // Consider specifying the 'index' if multiple String args exist or if multiple ResourceLocation.<init> calls exist.
            // index = 0, // Assuming it's the first String argument to the targeted INVOKE. Often needed for clarity/robustness.
            allow = 1 // Use with caution. Prefer more specific targeting if possible.
    )
    // Add the 'static' keyword here
    private static String resourceLocation(String original) {
        // It might be safer to check if the string *starts* with "shaders/core/"
        // rather than just containing it, depending on the possible inputs.
        String prefix = "shaders/core/";
        String[] parts = original.split(":");

        if (parts.length == 2) {
            String potentialPath = parts[0]; // e.g., "shaders/core/academy" or just "minecraft"
            String filename = parts[1];      // e.g., "my_shader.json" or "shaders/core/my_shader.json"

            // Check if the first part *looks like* the path structure you expect
            if (potentialPath.startsWith(prefix)) {
                // Extract the part *after* the prefix, assuming this is the intended namespace
                String namespace = potentialPath.substring(prefix.length());

                if (namespace.equals(AcademyCraft.MOD_ID)) {
                    // Your original logic: transforms "shaders/core/MODID:filename" -> "MODID:shaders/core/filename"
                    // Ensure this is the transformation you actually need for ResourceLocation loading.
                    String result = namespace + ":" + prefix + filename;
                    AcademyCraft.LOGGER.info("Remapping shader location from '{}' to '{}'", original, result);
                    return result;
                }
            }
        }
        // Consider logging 'original' here if you want to see *all* shader locations being created.
        // AcademyCraft.LOGGER.debug("Shader location unmodified: '{}'", original);
        return original;
    }
}