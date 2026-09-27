package org.academy.internal.server.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.gson.TypeHandler;

/**
 * 音乐系统服务端配置（academy-server.json 的 "music" 段），
 * 音乐室/全服点播/预设歌单的限额与开关均在此配置喵。
 */
public class MusicConfig {
    public static final String KEY = "music";

    @SerializedName("room")
    public final RoomSettings room = new RoomSettings();
    @SerializedName("jukebox")
    public final JukeboxSettings jukebox = new JukeboxSettings();
    @SerializedName("preset")
    public final PresetSettings preset = new PresetSettings();
    @SerializedName("sharedAccount")
    public final SharedAccountSettings sharedAccount = new SharedAccountSettings();

    public static class RoomSettings {
        @SerializedName("enabled")
        public boolean enabled = true;
        @SerializedName("maxRooms")
        public int maxRooms = 16;
        @SerializedName("maxMembers")
        public int maxMembers = 16;
    }

    public static class JukeboxSettings {
        @SerializedName("enabled")
        public boolean enabled = true;
        @SerializedName("maxQueue")
        public int maxQueue = 20;
        @SerializedName("perPlayerLimit")
        public int perPlayerLimit = 2;
        @SerializedName("requestCooldownSeconds")
        public int requestCooldownSeconds = 30;
        @SerializedName("maxTrackDurationSeconds")
        public int maxTrackDurationSeconds = 600;
        @SerializedName("voteMode")
        public String voteMode = "MAJORITY_ONLINE";
        @SerializedName("voteFixedCount")
        public int voteFixedCount = 3;
        @SerializedName("voteWindowSeconds")
        public int voteWindowSeconds = 45;
        @SerializedName("interruptPresetOnRequest")
        public boolean interruptPresetOnRequest = true;
    }

    public static class PresetSettings {
        @SerializedName("enabled")
        public boolean enabled = false;
        @SerializedName("mode")
        public String mode = "SEQUENTIAL";
    }

    public static class SharedAccountSettings {
        @SerializedName("enabled")
        public boolean enabled = false;
        @SerializedName("resolveRateLimitPerMinute")
        public int resolveRateLimitPerMinute = 30;
        @SerializedName("resolveCacheTtlSeconds")
        public int resolveCacheTtlSeconds = 600;
    }

    public static class Action implements TypeHandler<MusicConfig> {
        public static final TypeHandler<MusicConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public MusicConfig getDefault() {
            return new MusicConfig();
        }

        @Override
        public Class<MusicConfig> getTypeClass() {
            return MusicConfig.class;
        }
    }
}
