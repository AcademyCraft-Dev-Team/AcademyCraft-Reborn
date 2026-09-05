package org.academy.internal.common.world.entity.misaka;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Villager/animal-style interaction feedback for Misaka sisters.
 * Server-side particles + vanilla sounds + action-bar hints; no gameplay changes.
 */
public final class MisakaInteractionFeedback {
    private MisakaInteractionFeedback() {
    }

    public static void lookAt(MisakaSisterEntity sister, Player player) {
        sister.getLookControl().setLookAt(player, 30.0f, 30.0f);
    }

    public static void pet(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        hearts(sister, 5);
        play(sister, SoundEvents.VILLAGER_YES, 0.8f, 1.15f);
    }

    public static void fed(MisakaSisterEntity sister, ServerPlayer player, ItemStack food, boolean favorite) {
        lookAt(sister, player);
        itemCrumbs(sister, food, 8);
        if (favorite) {
            hearts(sister, 3);
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
        angry(sister, 5);
        play(sister, SoundEvents.VILLAGER_NO, 0.8f, 0.95f);
        actionBar(player, "message.academy.misaka_refuse");
    }

    public static void awaken(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        hearts(sister, 12);
        play(sister, SoundEvents.PLAYER_LEVELUP, 0.55f, 1.2f);
        actionBar(player, "message.academy.misaka_awakened");
    }

    public static void promaxOk(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        if (sister.level() instanceof ServerLevel serverLevel) {
            double x = sister.getX();
            double y = sister.getY() + sister.getBbHeight() * 0.6;
            double z = sister.getZ();
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 12, 0.35, 0.35, 0.35, 0.02);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y, z, 18, 0.4, 0.5, 0.4, 0.5);
        }
        play(sister, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.0f);
        actionBar(player, "message.academy.misaka_promax_ok");
    }

    public static void promaxUsed(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        smoke(sister, 6);
        play(sister, SoundEvents.VILLAGER_NO, 0.8f, 0.9f);
        actionBar(player, "message.academy.misaka_promax_used");
    }

    public static void reconstructionBlocked(MisakaSisterEntity sister, ServerPlayer player) {
        lookAt(sister, player);
        smoke(sister, 6);
        play(sister, SoundEvents.VILLAGER_NO, 0.8f, 0.85f);
        actionBar(player, "screen.academy.misaka_reconstruction_blocked");
    }

    public static void gift(MisakaSisterEntity sister) {
        hearts(sister, 4);
        play(sister, SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
    }

    public static void pickupDenied(ServerPlayer player) {
        actionBar(player, "message.academy.misaka_pickup_denied");
    }

    private static void hearts(MisakaSisterEntity sister, int count) {
        if (!(sister.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.HEART,
                sister.getX(),
                sister.getY() + sister.getBbHeight() * 0.85,
                sister.getZ(),
                count,
                0.25,
                0.2,
                0.25,
                0.02
        );
    }

    private static void angry(MisakaSisterEntity sister, int count) {
        if (!(sister.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                sister.getX(),
                sister.getY() + sister.getBbHeight() * 0.9,
                sister.getZ(),
                count,
                0.25,
                0.15,
                0.25,
                0.0
        );
    }

    private static void smoke(MisakaSisterEntity sister, int count) {
        if (!(sister.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                sister.getX(),
                sister.getY() + sister.getBbHeight() * 0.6,
                sister.getZ(),
                count,
                0.2,
                0.2,
                0.2,
                0.01
        );
    }

    private static void itemCrumbs(MisakaSisterEntity sister, ItemStack food, int count) {
        if (!(sister.level() instanceof ServerLevel serverLevel) || food.isEmpty()) {
            return;
        }
        serverLevel.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(food)),
                sister.getX(),
                sister.getY() + sister.getEyeHeight() * 0.6,
                sister.getZ(),
                count,
                0.15,
                0.15,
                0.15,
                0.05
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
