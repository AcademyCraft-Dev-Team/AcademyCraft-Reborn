package org.academy.internal.client.app.mediaplayer;

import net.minecraft.resources.ResourceLocation;

public record MediaInfo(ResourceLocation icon, MediaSource source, String name, String subtitle) {
}
