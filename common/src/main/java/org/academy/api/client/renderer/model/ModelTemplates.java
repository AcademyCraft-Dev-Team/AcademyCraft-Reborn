package org.academy.api.client.renderer.model;

import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.TextureSlot;

import java.util.Optional;

import static org.academy.AcademyCraft.getResourceLocation;

public class ModelTemplates {
    public static final ModelTemplate COIN = new ModelTemplate(
            Optional.of(getResourceLocation("item/" + "coin")),
            Optional.empty(), TextureSlot.FRONT, TextureSlot.BACK);
}