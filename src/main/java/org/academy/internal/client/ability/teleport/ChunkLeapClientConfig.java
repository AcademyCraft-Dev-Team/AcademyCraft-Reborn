package org.academy.internal.client.ability.teleport;

import org.academy.AcademyCraftClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.config.SkillSettingsRegistry;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;

import java.util.List;

/**
 * Persisted client settings for 区块跃迁.
 *
 * <p>Unlike {@link TeleportDistanceConfig} these options are not a single value, so they live in
 * their own config class and are surfaced through the Skill Settings app. The server never trusts
 * them: every one of them limits what the client asks for, not what the server allows.
 */
public class ChunkLeapClientConfig extends KeyBindingConfig {
    /** Ceiling on chunks per swap requested by this client. */
    public static final int MIN_REGION_CHUNKS = 16;
    public static final int MAX_REGION_CHUNKS = 1024;
    public static final int MAX_VIEW_RADIUS = 20;

    private boolean movePlayersWithChunks = true;
    private int maxRegionChunks = 256;
    private boolean autoLoadMap = true;
    private int viewRadiusChunks = 12;
    private boolean showEntities = true;
    private int entityRefreshTicks = 10;
    private boolean confirmSwap = true;
    private boolean seamlessTransition = true;

    /** Static mirror so the map screen and view requester can read settings without a skill lookup. */
    private static volatile ChunkLeapClientConfig active;

    public ChunkLeapClientConfig() {
        active = this;
    }

    public static ChunkLeapClientConfig active() {
        var current = active;
        return current == null ? new ChunkLeapClientConfig() : current;
    }

    public static int maxRegionChunks() {
        return active().getMaxRegionChunks();
    }

    public static int viewRadiusChunks() {
        return active().getViewRadiusChunks();
    }

    public static boolean showEntities() {
        return active().isShowEntities();
    }

    public static boolean confirmSwap() {
        return active().isConfirmSwap();
    }

    public static boolean movePlayers() {
        return active().isMovePlayersWithChunks();
    }

    public static boolean seamlessTransition() {
        return active().isSeamlessTransition();
    }

    public boolean isSeamlessTransition() {
        return seamlessTransition;
    }

    public boolean isMovePlayersWithChunks() {
        return movePlayersWithChunks;
    }

    public int getMaxRegionChunks() {
        return Math.clamp(maxRegionChunks, MIN_REGION_CHUNKS, MAX_REGION_CHUNKS);
    }

    public boolean isAutoLoadMap() {
        return autoLoadMap;
    }

    public int getViewRadiusChunks() {
        return Math.clamp(viewRadiusChunks, 4, MAX_VIEW_RADIUS);
    }

    public boolean isShowEntities() {
        return showEntities;
    }

    public int getEntityRefreshTicks() {
        return Math.clamp(entityRefreshTicks, 5, 40);
    }

    public boolean isConfirmSwap() {
        return confirmSwap;
    }

    /** Registers the advanced settings block shown in the Skill Settings app. */
    public void registerSettings(Skill skill) {
        SkillSettingsRegistry.INSTANCE.register(skill, new SkillSettingsRegistry.Module(
                "chunk_leap",
                "app.academy.skill_settings.advanced.chunk_leap.title",
                List.of(
                        new SkillSettingsRegistry.Toggle(
                                "move_players_with_chunks",
                                "app.academy.skill_settings.advanced.chunk_leap.move_players",
                                this::isMovePlayersWithChunks,
                                value -> {
                                    movePlayersWithChunks = value;
                                    save();
                                }),
                        new SkillSettingsRegistry.IntegerRange(
                                "max_region_chunks",
                                "app.academy.skill_settings.advanced.chunk_leap.max_region",
                                MIN_REGION_CHUNKS, MAX_REGION_CHUNKS, 16,
                                this::getMaxRegionChunks,
                                value -> {
                                    maxRegionChunks = Math.clamp(value, MIN_REGION_CHUNKS, MAX_REGION_CHUNKS);
                                    save();
                                }),
                        new SkillSettingsRegistry.IntegerRange(
                                "view_radius_chunks",
                                "app.academy.skill_settings.advanced.chunk_leap.view_radius",
                                4, MAX_VIEW_RADIUS, 2,
                                this::getViewRadiusChunks,
                                value -> {
                                    viewRadiusChunks = Math.clamp(value, 4, MAX_VIEW_RADIUS);
                                    save();
                                }),
                        new SkillSettingsRegistry.Toggle(
                                "auto_load_map",
                                "app.academy.skill_settings.advanced.chunk_leap.auto_load",
                                this::isAutoLoadMap,
                                value -> {
                                    autoLoadMap = value;
                                    save();
                                }),
                        new SkillSettingsRegistry.Toggle(
                                "confirm_swap",
                                "app.academy.skill_settings.advanced.chunk_leap.confirm_swap",
                                this::isConfirmSwap,
                                value -> {
                                    confirmSwap = value;
                                    save();
                                }),
                        new SkillSettingsRegistry.Toggle(
                                "show_entities",
                                "app.academy.skill_settings.advanced.chunk_leap.show_entities",
                                this::isShowEntities,
                                value -> {
                                    showEntities = value;
                                    save();
                                }),
                        new SkillSettingsRegistry.Toggle(
                                "seamless_transition",
                                "app.academy.skill_settings.advanced.chunk_leap.seamless",
                                this::isSeamlessTransition,
                                value -> {
                                    seamlessTransition = value;
                                    save();
                                })
                )));
    }

    private void save() {
        AcademyCraftClient.Config.INSTANCE.save();
    }

    /** Gson type handler required so the config can be persisted and loaded per skill id. */
    public static final class Action implements TypeHandler<ChunkLeapClientConfig> {
        public static final TypeHandler<ChunkLeapClientConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public ChunkLeapClientConfig getDefault() {
            return new ChunkLeapClientConfig();
        }

        @Override
        public Class<ChunkLeapClientConfig> getTypeClass() {
            return ChunkLeapClientConfig.class;
        }
    }
}
