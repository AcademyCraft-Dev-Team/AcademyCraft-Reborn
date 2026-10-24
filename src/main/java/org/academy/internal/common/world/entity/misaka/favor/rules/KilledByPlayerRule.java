package org.academy.internal.common.world.entity.misaka.favor.rules;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.favor.IFavorRule;
import org.jspecify.annotations.Nullable;

public final class KilledByPlayerRule implements IFavorRule {
    @Override
    public boolean matches(FavorContext context) {
        return context.kind() == FavorContext.Kind.KILLED_BY_PLAYER
                && !context.playerName().isEmpty();
    }

    @Override
    public void apply(FavorContext context, @Nullable MinecraftServer server) {
        FavorService.applyDelta(server, context.record(), context.playerName(), -5);
    }
}
