package org.academy.internal.common.world.entity.misaka.favor.rules;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.favor.IFavorRule;
import org.jspecify.annotations.Nullable;

public final class AnniversaryCakeRule implements IFavorRule {
    @Override
    public boolean matches(FavorContext context) {
        if (context.kind() != FavorContext.Kind.ANNIVERSARY_CAKE || context.record().dailyCakeFavor) {
            return false;
        }
        int day = context.currentDay();
        int rescued = context.record().rescuedDayIndex;
        return day > rescued && (day - rescued) % 365 == 0;
    }

    @Override
    public void apply(FavorContext context, @Nullable MinecraftServer server) {
        context.record().dailyCakeFavor = true;
        if (server != null) {
            FavorService.modifyFavorLan(server, context.record(), context.playerName(), 10);
        } else {
            FavorService.modifyFavor(context.record(), context.playerName(), 10);
        }
    }
}
