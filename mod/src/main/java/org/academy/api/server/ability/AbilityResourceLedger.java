package org.academy.api.server.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.Skill;

public interface AbilityResourceLedger {
    float getCurrent(ServerPlayer player);

    boolean adjustsCost();

    boolean tryCast(ServerPlayer player, Skill skill, float cpCost, float resourceCost,
                    int iterationPoints, String stackGroup, int stackLimit);

    Component insufficientResource(ServerPlayer player, float required);
}
