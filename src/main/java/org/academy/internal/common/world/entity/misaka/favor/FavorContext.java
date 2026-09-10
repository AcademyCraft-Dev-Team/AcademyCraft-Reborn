package org.academy.internal.common.world.entity.misaka.favor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.entity.misaka.MisakaDayTime;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record FavorContext(
        Kind kind,
        MisakaSisterRecord record,
        UUID misakaUuid,
        String playerName,
        @Nullable LivingEntity actor,
        @Nullable LivingEntity victim,
        @Nullable DamageSource damageSource,
        int currentDay,
        @Nullable MisakaSisterEntity witness
) {
    public enum Kind {
        ATTACKED_BY_PLAYER,
        KILLED_BY_PLAYER,
        FEED_FAVORITE_FOOD,
        ANNIVERSARY_CAKE,
        HOT_SPRING
    }

    public static FavorContext attacked(MisakaSisterEntity sister, MisakaSisterRecord record, Player attacker, DamageSource source) {
        return new FavorContext(
                Kind.ATTACKED_BY_PLAYER,
                record,
                sister.getMisakaUuid(),
                attacker.getGameProfile().name(),
                attacker,
                sister,
                source,
                currentDay(sister),
                sister
        );
    }

    public static FavorContext killedSister(MisakaSisterEntity sister, MisakaSisterRecord record, @Nullable LivingEntity killer) {
        var name = killer instanceof Player player ? player.getGameProfile().name() : "";
        return new FavorContext(
                Kind.KILLED_BY_PLAYER,
                record,
                sister.getMisakaUuid(),
                name,
                killer,
                sister,
                null,
                currentDay(sister),
                sister
        );
    }

    public static FavorContext feedFavorite(MisakaSisterRecord record, UUID misakaUuid, ServerPlayer feeder) {
        return new FavorContext(
                Kind.FEED_FAVORITE_FOOD,
                record,
                misakaUuid,
                feeder.getGameProfile().name(),
                feeder,
                null,
                null,
                currentDay(feeder),
                null
        );
    }

    public static FavorContext anniversaryCake(MisakaSisterRecord record, UUID misakaUuid, ServerPlayer feeder) {
        return new FavorContext(
                Kind.ANNIVERSARY_CAKE,
                record,
                misakaUuid,
                feeder.getGameProfile().name(),
                feeder,
                null,
                null,
                currentDay(feeder),
                null
        );
    }

    public static FavorContext hotSpring(MisakaSisterRecord record, UUID misakaUuid, ServerPlayer player) {
        return new FavorContext(
                Kind.HOT_SPRING,
                record,
                misakaUuid,
                player.getGameProfile().name(),
                player,
                null,
                null,
                currentDay(player),
                null
        );
    }

    private static int currentDay(LivingEntity entity) {
        return MisakaDayTime.dayIndex(entity.level());
    }
}
