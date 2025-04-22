package org.academy.api.common.network;

import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;

public final class Packets {
    /**
     * Future
     */
    public static final ResourceLocation C2S_FUTURE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_future"));
    public static final ResourceLocation S2C_FUTURE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_future"));
    /**
     * Ability System
     */
    public static final ResourceLocation C2S_LEARN_SKILL = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_learn_skill"));
    public static final ResourceLocation S2C_COMPUTING_POWER_SYNC = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_computing_power_sync"));
    public static final ResourceLocation S2C_MAX_COMPUTING_POWER_SYNC = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_max_computing_power_sync"));
    public static final ResourceLocation S2C_SKILLS_SYC = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_skills_sync"));
    public static final ResourceLocation S2C_ABILITY_CATEGORY_SYNC = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_ability_category_sync"));
    public static final ResourceLocation C2S_ACQUIRE_CATEGORY = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_acquire_category"));
    /**
     * Wireless Network
     */
    public static final ResourceLocation C2S_CONNECT_NODE= NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_connect_node"));
    public static final ResourceLocation C2S_DISCONNECT_NODE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_disconnect_node"));
    /**
     * AbilityDeveloperScreen
     */
    public static final ResourceLocation S2C_OPEN_ABILITY_DEVELOPER_SCREEN = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_open_ability_developer_fragment"));
    public static final ResourceLocation S2C_ABILITY_DEVELOPER_SCREEN_RESPONSE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_ability_developer_screen_response"));
    public static final ResourceLocation C2S_GET_AVAILABLE_NODE_LIST = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_get_available_node_list"));
    public static final ResourceLocation C2S_GET_CURRENT_NODE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_get_current_node"));
    /**
     * Electromaster
     */
    public static final ResourceLocation C2S_RAILGUN_SHOOT = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_railgun_shoot"));
    public static final ResourceLocation C2S_ARC_GENERATE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_arc_generate"));
    /**
     * Teleport
     */
    public static final ResourceLocation C2S_SELF_TELEPORT = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_self_teleport"));
    /**
     * Meltdowner
     */
    public static final ResourceLocation C2S_SINGLE_HIGH_SPEED_ELECTRON_BEAM = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_single_high_speed_electron_beam"));
    /**
     * Accelerator
     */
    public static final ResourceLocation C2S_TOGGLE_REFLECTION = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_toggle_reflection"));
    public static final ResourceLocation C2S_REVERSE_BLOODFLOW = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_reverse_bloodflow"));
    public static final ResourceLocation C2S_STORM_WING_TOGGLE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_storming_toggle"));

    private Packets() {
    }
}