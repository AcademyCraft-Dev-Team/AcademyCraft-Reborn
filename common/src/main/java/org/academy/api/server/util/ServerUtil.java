package org.academy.api.server.util;

import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;

import java.util.UUID;

public class ServerUtil {
    public static boolean lacksSkill(UUID uuid, Skill skill) {
        return !AbilitySystemServer.getPlayerSkills(uuid).contains(skill.name);
    }
}