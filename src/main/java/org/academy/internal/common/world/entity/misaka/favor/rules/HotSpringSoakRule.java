package org.academy.internal.common.world.entity.misaka.favor.rules;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.world.entity.misaka.MisakaHotSpring;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.favor.IFavorRule;
import org.jspecify.annotations.Nullable;

public final class HotSpringSoakRule implements IFavorRule {
    @Override
    public boolean matches(FavorContext context) {
        if (context.kind() != FavorContext.Kind.HOT_SPRING) {
            return false;
        }
        int last = context.record().lastHotSpringFavorDay;
        return last < 0 || context.currentDay() - last >= MisakaHotSpring.COOLDOWN_DAYS;
    }

    @Override
    public void apply(FavorContext context, @Nullable MinecraftServer server) {
        context.record().lastHotSpringFavorDay = context.currentDay();
        FavorService.applyDelta(server, context.record(), context.playerName(), 1);
    }
}
