package org.academy.api.client.renderer.model;

import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.TextureSlot;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;

import java.util.Optional;

public class ModelTemplates {
    public static final ModelTemplate COIN = new ModelTemplate(
            Optional.of(new ResourceLocation(AcademyCraft.MOD_ID, "item/" + "coin")),
            Optional.empty(), TextureSlot.FRONT, TextureSlot.BACK);
}