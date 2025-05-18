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
    public static final String C2S_ACQUIRE_CATEGORY = "c2s_acquire_category";
    public static final String S2C_EXP_SYNC = "s2c_exp_sync";
    /**
     * Wireless Network
     */
    public static final String C2S_CONNECT_NODE = "c2s_connect_node";
    public static final String C2S_DISCONNECT_NODE = "c2s_disconnect_node";
    public static final String C2S_GET_AVAILABLE_NODES = "c2s_get_available_nodes";
    public static final String C2S_GET_CURRENT_NODE = "c2s_get_current_node";
    public static final String C2S_SET_NODE_NAME = "c2s_set_node_name";
    public static final String C2S_SET_NODE_PASS = "c2s_set_node_pass";
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
    public static final String C2S_REVERSE_BLOODFLOW = "c2s_reverse_bloodflow";
    public static final String C2S_KINETIC_ENERGY_APPLIED_TOGGLE = "c2s_kinetic_energy_applied_toggle";
    public static final String C2S_DIR_STRIKE_START = "c2s_dir_strike_start";
    public static final String C2S_DIR_STRIKE_END = "c2s_dir_strike_end";

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
                    NetworkSystem.registerPacketName(fieldValue);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    private Packets() {
    }
}