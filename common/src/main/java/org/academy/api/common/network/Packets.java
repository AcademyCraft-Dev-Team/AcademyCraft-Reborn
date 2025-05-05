package org.academy.api.common.network;

import java.lang.reflect.Field;

public final class Packets {
    /**
     * Future
     */
    public static final String C2S_FUTURE = "c2s_future";
    public static final String S2C_FUTURE = "s2c_future";
    /**
     * Ability System
     */
    public static final String C2S_LEARN_SKILL = "c2s_learn_skill";
    public static final String S2C_COMPUTING_POWER_SYNC = "s2c_computing_power_sync";
    public static final String S2C_MAX_COMPUTING_POWER_SYNC = "s2c_max_computing_power_sync";
    public static final String S2C_SKILLS_SYC = "s2c_skills_sync";
    public static final String S2C_ABILITY_CATEGORY_SYNC = "s2c_ability_category_sync";
    public static final String C2S_ACQUIRE_CATEGORY = "c2s_acquire_category";
    /**
     * Wireless Network
     */
    public static final String C2S_CONNECT_NODE = "c2s_connect_node";
    public static final String C2S_DISCONNECT_NODE = "c2s_disconnect_node";
    public static final String S2C_ABILITY_DEVELOPER_SCREEN_RESPONSE = "s2c_ability_developer_screen_response";
    public static final String C2S_GET_AVAILABLE_NODES = "c2s_get_available_nodes";
    public static final String C2S_GET_CURRENT_NODE = "c2s_get_current_node";
    public static final String C2S_SET_NODE_NAME = "c2s_set_node_name";
    public static final String C2S_SET_NODE_PASS = "c2s_set_node_pass";
    /**
     * Ability Developer
     */
    public static final String C2S_LEARN = "c2s_learn";
    /**
     * Electromaster
     */
    public static final String C2S_RAILGUN_SHOOT = "c2s_railgun_shoot";
    public static final String C2S_ARC_GENERATE = "c2s_arc_generate";
    /**
     * Teleport
     */
    public static final String C2S_SELF_TELEPORT = "c2s_self_teleport";
    /**
     * Meltdowner
     */
    public static final String C2S_SINGLE_HIGH_SPEED_ELECTRON_BEAM = "c2s_single_high_speed_electron_beam";
    /**
     * Accelerator
     */
    public static final String C2S_TOGGLE_REFLECTION = "c2s_toggle_reflection";
    public static final String C2S_REVERSE_BLOODFLOW = "c2s_reverse_bloodflow";
    public static final String C2S_STORM_WING_TOGGLE = "c2s_storming_toggle";
    public static final String C2S_STORM_WING_FRONT = "c2s_storming_front";
    public static final String C2S_STORM_WING_BACK = "c2s_storming_back";
    public static final String C2S_STORM_WING_LEFT = "c2s_storming_left";
    public static final String C2S_STORM_WING_RIGHT = "c2s_storming_right";
    public static final String C2S_STORM_WING_KEEP = "c2s_storming_keep";
    public static final String C2S_KINETIC_ENERGY_APPLIED_TOGGLE = "c2s_kinetic_energy_applied_toggle";
    public static final String C2S_DIR_STRIKE_START = "c2s_dir_strike_start";
    public static final String C2S_DIR_STRIKE_END = "c2s_dir_strike_end";
    /**
     * Other
     */
    public static final String S2C_OPEN_SCREEN = "s2c_open_screen";

    public static void init() {
        Field[] fields = Packets.class.getDeclaredFields();

        for (Field field : fields) {
            if (java.lang.reflect.Modifier.isPublic(field.getModifiers()) &&
                    java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                    java.lang.reflect.Modifier.isFinal(field.getModifiers()) &&
                    field.getType() == String.class) {

                String fieldValue;
                try {
                    fieldValue = (String) field.get(null);
                    NetworkSystem.registerPacket(fieldValue);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    private Packets() {
    }
}