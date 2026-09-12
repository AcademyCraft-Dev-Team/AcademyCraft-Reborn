package org.academy.internal.common.world.entity.misaka;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Villager/animal-style interaction feedback for Misaka sisters.
 * Server-side VFX Graph + vanilla sounds + action-bar hints; no gameplay changes.
 */
public final class MisakaInteractionFeedback {
    private MisakaInteractionFeedback() {
    }

    public static void lookAt(MisakaSisterEntity sister, Player player) {
        sister.getLookControl().setLookAt(player, 30.0f, 30.0f);
    }

    public static void pet(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        affection(sister, 5);
        play(sister, SoundEvents.VILLAGER_YES, 0.8f, 1.15f);
    }

    public static void fed(MisakaSisterEntity sister, ServerPlayer player, ItemStack food, boolean favorite) {
        lookAt(sister, player);
        feedCrumb(sister);
        if (favorite) {
            affection(sister, 3);
        }
        play(sister, SoundEvents.GENERIC_EAT, 0.9f, 1.0f);
    }

    public static void full(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        play(sister, SoundEvents.VILLAGER_NO, 0.7f, 1.0f);
        actionBar(player, "message.academy.misaka_full");
    }

    public static void refuse(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        reject(sister);
        play(sister, SoundEvents.VILLAGER_NO, 0.8f, 0.95f);
        actionBar(player, "message.academy.misaka_refuse");
    }

    public static void awaken(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        affection(sister, 12);
        play(sister, SoundEvents.PLAYER_LEVELUP, 0.55f, 1.2f);
        actionBar(player, "message.academy.misaka_awakened");
    }

    public static void recovered(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        affection(sister, 8);
        play(sister, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.35f);
        actionBar(player, "message.academy.misaka_recovered");
    }

    public static void promaxOk(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        if (sister.level() instanceof ServerLevel serverLevel) {
            MisakaVfx.upgradeOk(serverLevel, torso(sister));
        }
        play(sister, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.0f);
        actionBar(player, "message.academy.misaka_promax_ok");
    }

    public static void promaxUsed(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        reject(sister);
        play(sister, SoundEvents.VILLAGER_NO, 0.8f, 0.9f);
        actionBar(player, "message.academy.misaka_promax_used");
    }

    public static void reconstructionBlocked(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        reject(sister);
        play(sister, SoundEvents.VILLAGER_NO, 0.8f, 0.85f);
        actionBar(player, "screen.academy.misaka_reconstruction_blocked");
    }

    public static void gift(MisakaSisterEntity sister) {
        affection(sister, 4);
        play(sister, SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
    }

    public static void hotSpringSteam(MisakaSisterEntity sister, ServerPlayer player) {
        if (!(sister.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MisakaVfx.hotSpringSteam(serverLevel, MisakaHotSpring.steamPoint(sister));
        MisakaVfx.hotSpringSteam(serverLevel, MisakaHotSpring.steamPoint(player));
    }

    public static void hotSpringComplete(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        affection(sister, 6);
        hotSpringSteam(sister, player);
        play(sister, SoundEvents.VILLAGER_YES, 0.85f, 1.1f);
        actionBar(player, "message.academy.misaka_hot_spring");
    }

    public static void pickupDenied(ServerPlayer player) {
        actionBar(player, "message.academy.misaka_pickup_denied");
    }

    private static void affection(MisakaSisterEntity sister, int intensity) {
        if (!(sister.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MisakaVfx.affection(serverLevel, head(sister), intensity);
    }

    private static void reject(MisakaSisterEntity sister) {
        if (!(sister.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MisakaVfx.reject(serverLevel, torso(sister));
    }

    private static void feedCrumb(MisakaSisterEntity sister) {
        if (!(sister.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MisakaVfx.feedCrumb(serverLevel, new Vec3(
                sister.getX(),
                sister.getY() + sister.getEyeHeight() * 0.6,
                sister.getZ()
        ));
    }

    private static Vec3 head(MisakaSisterEntity sister) {
        return new Vec3(
                sister.getX(),
                sister.getY() + sister.getBbHeight() * 0.85,
                sister.getZ()
        );
    }

    private static Vec3 torso(MisakaSisterEntity sister) {
        return new Vec3(
                sister.getX(),
                sister.getY() + sister.getBbHeight() * 0.6,
                sister.getZ()
        );
    }

    private static void play(MisakaSisterEntity sister, SoundEvent sound, float volume, float pitch) {
        sister.level().playSound(
                null,
                sister.getX(),
                sister.getY(),
                sister.getZ(),
                sound,
                SoundSource.NEUTRAL,
                volume,
                pitch
        );
    }

    private static void play(MisakaSisterEntity sister, Holder<SoundEvent> sound, float volume, float pitch) {
        sister.level().playSound(
                null,
                sister.getX(),
                sister.getY(),
                sister.getZ(),
                sound,
                SoundSource.NEUTRAL,
                volume,
                pitch
        );
    }

    private static void actionBar(ServerPlayer player, String key) {
        player.sendOverlayMessage(Component.translatable(key));
    }
}
