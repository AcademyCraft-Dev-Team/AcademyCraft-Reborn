package org.academy.internal.common.world.entity.misaka.favor.rules;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.favor.IFavorRule;
import org.jspecify.annotations.Nullable;

public final class FeedFavoriteFoodRule implements IFavorRule {
    @Override
    public boolean matches(FavorContext context) {
        return context.kind() == FavorContext.Kind.FEED_FAVORITE_FOOD
                && !context.record().dailyFavoriteFavor;
    }

    @Override
    public void apply(FavorContext context, @Nullable MinecraftServer server) {
        context.record().dailyFavoriteFavor = true;
        FavorService.applyDelta(server, context.record(), context.playerName(), 1);
    }
}
