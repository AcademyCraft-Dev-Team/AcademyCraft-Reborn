package org.academy.internal.common.world.entity.misaka.favor.rules;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.favor.IFavorRule;
import org.jspecify.annotations.Nullable;

public final class WitnessKillPlayerRule implements IFavorRule {
    @Override
    public boolean matches(FavorContext context) {
        return context.kind() == FavorContext.Kind.WITNESS_KILL_PLAYER
                && context.isWitnessedKill()
                && context.victimIsPlayer();
    }

    @Override
    public void apply(FavorContext context, @Nullable MinecraftServer server) {
        // Event path uses FavorRuleRegistry.propagateWitnessDeaths for multi-seed dedupe.
        if (server != null) {
            FavorService.modifyFavorLan(server, context.record(), context.playerName(), -2);
        } else {
            FavorService.modifyFavor(context.record(), context.playerName(), -2);
        }
    }
}
