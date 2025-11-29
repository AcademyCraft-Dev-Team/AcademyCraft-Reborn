package org.academy.internal.client.app.mediaplayer;

import net.minecraft.resources.Identifier;

public record MediaInfo(Identifier icon, MediaSource source, String name, String subtitle) {
}
