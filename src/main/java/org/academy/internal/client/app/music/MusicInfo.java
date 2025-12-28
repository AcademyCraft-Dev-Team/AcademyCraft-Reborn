package org.academy.internal.client.app.music;

import net.minecraft.resources.Identifier;

public record MusicInfo(
        Identifier icon, MusicSource source, String name, String subtitle
) {
}