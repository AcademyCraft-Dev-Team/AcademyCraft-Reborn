package org.academy.api.common.network;

import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;

public class Packets {
    public static final ResourceLocation C2S_RAILGUN_SHOOT = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_railgun_shoot"));
    public static final ResourceLocation C2S_SINGLE_HIGH_SPEED_ELECTRON_BEAM = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_single_high_speed_electron_beam"));
    public static final ResourceLocation C2S_SELF_TELEPORT = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_self_teleport"));
    public static final ResourceLocation C2S_DEBUG_CHANGE_CATEGORY = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_debug_change_category"));
    public static final ResourceLocation S2C_COMPUTING_POWER_SYNC = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_computing_power_sync"));
    public static final ResourceLocation S2C_MAX_COMPUTING_POWER_SYNC = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_max_computing_power_sync"));
    public static final ResourceLocation S2C_SKILLS_SYC = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_skills_sync"));
    public static final ResourceLocation S2C_ABILITY_CATEGORY_SYNC = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_ability_category_sync"));
    public static final ResourceLocation S2C_INFO = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_info"));
    public static final ResourceLocation C2S_ARC_GENERATE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_arc_generate"));
    public static final ResourceLocation C2S_LEARN_SKILL = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_learn_skill"));
    public static final ResourceLocation S2C_LEARN_SKILL = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_learn_skill"));
    public static final ResourceLocation C2S_FETCH_ALL_SKILL = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_fetch_all_skill"));
    public static final ResourceLocation S2C_FETCH_ALL_SKILL = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_fetch_all_skill"));
    public static final ResourceLocation S2C_OPEN_ABILITY_DEVELOPER_SCREEN = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_open_ability_developer_fragment"));
    public static final ResourceLocation C2S_TOGGLE_REFLECTION = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_toggle_reflection"));
    public static final ResourceLocation C2S_REVERSE_BLOODFLOW = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_reverse_bloodflow"));
    public static final ResourceLocation C2S_STORM_WING_TOGGLE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_storming_toggle"));
    public static final ResourceLocation C2S_ACQUIRE_CATEGORY = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "c2s_acquire_category"));
    public static final ResourceLocation S2C_ABILITY_DEVELOPER_SCREEN_RESPONSE = NetworkSystem.registerPacket(new ResourceLocation(AcademyCraft.MOD_ID, "s2c_ability_developer_screen_response"));

    private Packets() {
    }
}