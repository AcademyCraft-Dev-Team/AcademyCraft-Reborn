package org.academy.api.server.ability.aeromanip;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.server.ability.AbilityResourceLedger;
import org.academy.api.server.ability.AbilityResourceUsage;

public interface AeromanipResourceService extends AbilityResourceLedger {
    float getCapacity(ServerPlayer player);

    AbilityResourceUsage beginUse(ServerPlayer player);

    boolean tryConsume(ServerPlayer player, float amount);

    boolean isInUse(ServerPlayer player);

    boolean supportsCompressedAir(ServerPlayer player);

    void reconcile(ServerPlayer player);
}
