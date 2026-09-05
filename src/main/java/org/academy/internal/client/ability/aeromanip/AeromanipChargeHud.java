package org.academy.internal.client.ability.aeromanip;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeSync;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeProgress;
import org.academy.api.common.ability.AirMobility;
import org.misaka.MisakaNetworkClient;

/** Local progress with server-confirmed release tiers, correlated to the current gesture. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class AeromanipChargeHud {
    private static Skill activeSkill;
    private static int startTick;
    private static long gesture;
    private static final AeromanipChargeProgress PROGRESS = new AeromanipChargeProgress();
    private static Skill lastSkill;

    private AeromanipChargeHud() {
    }

    public static void begin(Skill skill) {
        var player = Minecraft.getInstance().player;
        if (player == null || skill == null) return;
        activeSkill = skill;
        lastSkill = skill;
        PROGRESS.begin(++gesture);
        MisakaNetworkClient.send(new AeromanipChargeSync.Request(skill.getKeyString(), gesture));
        startTick = player.tickCount;
        show(player.tickCount);
    }

    public static void end(Skill skill) {
        if (activeSkill != skill) return;
        clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player != null) AirMobility.applySupport(player);
        if (activeSkill == null) return;
        if (player == null || !player.isAlive() || player.isRemoved()) {
            clear();
            return;
        }
        show(player.tickCount);
    }

    private static void show(int currentTick) {
        var player = Minecraft.getInstance().player;
        if (player == null || activeSkill == null) return;
        var tier = PROGRESS.tier();
        var label = Component.translatable(tierTranslationKey(tier));
        player.sendOverlayMessage(Component.translatable(
                "hud.academy.aeromanip_charge",
                activeSkill.getTranslatedName(),
                PROGRESS.awaitingConfirmation(Math.max(0, currentTick - startTick))
                        ? Component.translatable("hud.academy.aeromanip_charge.confirming", label) : label
        ));
    }

    public static void confirm(long serverGesture, int tier, boolean released) {
        if (!PROGRESS.matches(serverGesture)) return;
        if (tier == -1) {
            clear();
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(Component.translatable("message.academy.aeromanip.charge_cancelled"));
            return;
        }
        if (!PROGRESS.accept(serverGesture, tier)) return;
        var player = Minecraft.getInstance().player;
        if (released && player != null && lastSkill != null) {
            player.sendOverlayMessage(Component.translatable("message.academy.aeromanip.released",
                    lastSkill.getTranslatedName(), Component.translatable(tierTranslationKey(AeromanipChargeTier.values()[tier]))));
        }
    }

    private static String tierTranslationKey(AeromanipChargeTier tier) {
        return switch (tier) {
            case INSTANT -> "hud.academy.aeromanip_charge.instant";
            case HALF -> "hud.academy.aeromanip_charge.half";
            case FULL -> "hud.academy.aeromanip_charge.full";
        };
    }

    private static void clear() {
        var player = Minecraft.getInstance().player;
        activeSkill = null;
        if (player != null) player.sendOverlayMessage(Component.empty());
    }
}
