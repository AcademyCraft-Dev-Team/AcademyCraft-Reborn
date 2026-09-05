package org.academy.internal.common.world.entity.misaka.favor.rules;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.favor.IFavorRule;
import org.jspecify.annotations.Nullable;

public final class AttackedByPlayerRule implements IFavorRule {
    @Override
    public boolean matches(FavorContext context) {
        return context.kind() == FavorContext.Kind.ATTACKED_BY_PLAYER
                && context.actor() != null;
    }

    @Override
    public void apply(FavorContext context, @Nullable MinecraftServer server) {
        if (server != null) {
            FavorService.modifyFavorLan(server, context.record(), context.playerName(), -1);
        } else {
            FavorService.modifyFavor(context.record(), context.playerName(), -1);
        }
    }
}
