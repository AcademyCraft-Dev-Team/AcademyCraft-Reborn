package org.academy.api.client.resources.sounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.function.BooleanSupplier;

public final class LoopingPlayerSoundInstance extends AbstractTickableSoundInstance {
    private final LocalPlayer player;
    private final BooleanSupplier shouldPlay;

    public LoopingPlayerSoundInstance(LocalPlayer player, SoundEvent sound, float volume, float pitch,
                                      BooleanSupplier shouldPlay) {
        super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.shouldPlay = shouldPlay;
        looping = true;
        delay = 0;
        this.volume = volume;
        this.pitch = pitch;
        tick();
    }

    @Override
    public void tick() {
        if (player.isRemoved() || Minecraft.getInstance().player != player || !shouldPlay.getAsBoolean()) {
            stop();
            return;
        }
        x = player.getX();
        y = player.getY();
        z = player.getZ();
    }
}
