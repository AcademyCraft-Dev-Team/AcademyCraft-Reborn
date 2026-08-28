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

/** Displays the locally predicted Aeromanipulation charge tier in the vanilla action-bar slot. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class AeromanipChargeHud {
    private static Skill activeSkill;
    private static int startTick;

    private AeromanipChargeHud() {
    }

    public static void begin(Skill skill) {
        var player = Minecraft.getInstance().player;
        if (player == null || skill == null) return;
        activeSkill = skill;
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
        var tier = AeromanipChargeTier.fromTicks(Math.max(0, currentTick - startTick));
        player.sendOverlayMessage(Component.translatable(
                "hud.academy.aeromanip_charge",
                activeSkill.getTranslatedName(),
                Component.translatable(tierTranslationKey(tier))
        ));
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
