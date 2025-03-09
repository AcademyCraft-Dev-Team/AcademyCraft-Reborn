package org.academy.api.common.network;

import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;

/**
 * 模组所使用的所有网络包标识
 */
public class AcademyCraftNetworkResourceLocations {
    public static final ResourceLocation S2C_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_response");
    public static final ResourceLocation S2C_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_request");
    public static final ResourceLocation C2S_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_request");
    public static final ResourceLocation C2S_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_response");

    public static final ResourceLocation S2C_LEARN_ABILITY_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_learn_ability_response");
    public static final ResourceLocation C2S_LEARN_ABILITY_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_learn_ability_request");
    public static final ResourceLocation C2S_RAILGUN_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_railgun_request");
    public static final ResourceLocation S2C_CHANGE_ABILITY_CATEGORY_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_change_ability_category_request");
    public static final ResourceLocation C2S_CHANGE_ABILITY_CATEGORY_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_change_ability_category_response");
    public static final ResourceLocation C2S_GET_ALL_SKILL_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_get_all_skill_request");
    public static final ResourceLocation S2C_GET_ALL_SKILL_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_get_all_skill_response");
    public static final ResourceLocation C2S_LEARN_SKILL_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_learn_skill_request");
    public static final ResourceLocation S2C_LEARN_SKILL_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_learn_skill_response");
    public static final ResourceLocation C2S_GET_LEARNED_SKILL_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_get_learned_skill_request");
    public static final ResourceLocation S2C_GET_LEARNED_SKILL_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_get_learned_skill_response");
    public static final ResourceLocation C2S_SELF_TELEPORT_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_self_teleport_request");
    public static final ResourceLocation S2C_SELF_TELEPORT_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_self_teleport_response");
    public static final ResourceLocation C2S_ARC_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_arc_request");
    public static final ResourceLocation S2C_ARC_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_arc_response");
    public static final ResourceLocation C2S_LEARN_CURRICULUM_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_learn_curriculum_request");
    public static final ResourceLocation S2C_LEARN_CURRICULUM_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_learn_curriculum_response");
    public static final ResourceLocation C2S_SYNC_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_sync_request");
    public static final ResourceLocation S2C_SYNC_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_sync_response");
    public static final ResourceLocation C2S_DEBUG_FULL_ENERGY = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_debug_full_energy");

    private AcademyCraftNetworkResourceLocations() {
    }
}