package org.academy.internal.common.world.entity.misaka.favor.rules;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.entity.misaka.MobRelation;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.favor.IFavorRule;
import org.jspecify.annotations.Nullable;

public final class KilledBenevolentPlayerRule implements IFavorRule {
    @Override
    public boolean matches(FavorContext context) {
        return context.kind() == FavorContext.Kind.KILLED_BENEVOLENT_PLAYER
                && context.victim() instanceof Player player
                && FavorService.relation(context.record(), player.getGameProfile().name()) == MobRelation.BENEVOLENT;
    }

    @Override
    public void apply(FavorContext context, @Nullable MinecraftServer server) {
        // Event path uses FavorRuleRegistry.applyKilledBenevolentLan for multi-seed dedupe.
        if (server != null) {
            FavorService.modifyFavorLan(server, context.record(), context.playerName(), -8);
        } else {
            FavorService.modifyFavor(context.record(), context.playerName(), -8);
        }
    }
}
