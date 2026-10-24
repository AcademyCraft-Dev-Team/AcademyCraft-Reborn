package org.academy.internal.common.world.entity.misaka.favor;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

public interface IFavorRule {
    boolean matches(FavorContext context);

    void apply(FavorContext context, @Nullable MinecraftServer server);
}
