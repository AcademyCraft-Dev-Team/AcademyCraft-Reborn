package org.academy.api.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.client.input.InputSystem;

import java.util.List;

@Environment(EnvType.CLIENT)
public class KeyBindingUtil {
    public static void registerSkillKeyBinding(Skill skill, Runnable runnable, List<Integer> defaultValue) {
        List<Integer> key = AcademyCraft.clientConfig.getKey(skill.name, defaultValue);
        InputSystem.addKeyRelease(key, runnable);
    }
}