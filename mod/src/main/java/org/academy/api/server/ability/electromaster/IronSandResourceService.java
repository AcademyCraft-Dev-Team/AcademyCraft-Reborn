package org.academy.api.server.ability.electromaster;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.server.ability.AbilityResourceAccount;
import org.academy.api.server.ability.AbilityResourceLedger;

/**
 * Server-owned MP ledger. Non-player actors supply this same account contract to their context.
 */
public interface IronSandResourceService extends AbilityResourceLedger {
    AbilityResourceAccount account(ServerPlayer subject);

    void reconcileCapacity(ServerPlayer subject);

    boolean isDefenseActive(ServerPlayer subject);

    boolean setDefense(ServerPlayer subject, boolean enabled);
}
