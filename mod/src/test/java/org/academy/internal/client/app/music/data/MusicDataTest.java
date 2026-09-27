package org.academy.internal.client.app.music.data;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicDataTest {
    private static final Gson GSON = new Gson();

    @Test
    void readsResourcePackSourceTypeField() {
        var data = GSON.fromJson("""
                {
                  "icon": "academy:musics/example/icon.png",
                  "source_type": "RESOURCE_LOCATION",
                  "source": "academy:musics/example/source.ogg",
                  "subtitle": "Example Artist"
                }
                """, MusicData.class);

        assertEquals("RESOURCE_LOCATION", data.getSourceType());
    }

    @Test
    void keepsCamelCaseSourceTypeCompatibility() {
        var data = GSON.fromJson("""
                {
                  "icon": "academy:musics/example/icon.png",
                  "sourceType": "PATH",
                  "source": "C:/music/example.ogg",
                  "subtitle": "Example Artist"
                }
                """, MusicData.class);

        assertEquals("PATH", data.getSourceType());
    }
}
