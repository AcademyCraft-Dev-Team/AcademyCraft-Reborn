package org.academy.api.common.network;

import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;

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
    public static final ResourceLocation C2S_GET_SKILL_LIST_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_get_skill_list_request");
    public static final ResourceLocation S2C_GET_SKILL_LIST_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_get_skill_list_response");
    public static final ResourceLocation C2S_LEARN_SKILL_REQUEST = new ResourceLocation(AcademyCraft.MOD_ID, "c2s_learn_skill_request");
    public static final ResourceLocation S2C_LEARN_SKILL_RESPONSE = new ResourceLocation(AcademyCraft.MOD_ID, "s2c_learn_skill_response");

    private AcademyCraftNetworkResourceLocations() {
    }
}