package org.academy.fabric.mixin.client;

import net.minecraft.client.renderer.EffectInstance;
import org.academy.AcademyCraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EffectInstance.class)
public abstract class MixinEffectInstance {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V"
            ),
            allow = 1
    )
    private static String init(String original) {
        var prefix = "shaders/program/";
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

    @ModifyArg(
            method = "getOrCreate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V"
            ),
            allow = 1
    )
    private static String getOrCreate(String original) {
        var prefix = "shaders/program/";
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