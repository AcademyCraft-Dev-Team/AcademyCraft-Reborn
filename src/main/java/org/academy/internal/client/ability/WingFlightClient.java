package org.academy.internal.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.WingControlIntent;
import org.academy.api.common.ability.WingFlightMotion;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.flight.WingFlightPose;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.wing.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.wing.WhiteWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.wing.PlatinumWing;
import org.academy.internal.common.attachment.AttachmentTypes;

import static org.lwjgl.glfw.GLFW.*;

/** Local movement uses vanilla position reporting and collisions; server ticks never drive the pilot. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class WingFlightClient {
    private static LocalPlayer previousPlayer;
    private static int previousButtons;

    private WingFlightClient() {
    }

    public static WingControlIntent readControl() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return new WingControlIntent(0, 0, 0);
        int buttons = 0;
        if (mc.gui.screen() == null) {
            if (down(GLFW_KEY_W)) buttons |= WingControlIntent.FRONT;
            if (down(GLFW_KEY_S)) buttons |= WingControlIntent.BACK;
            if (down(GLFW_KEY_A)) buttons |= WingControlIntent.LEFT;
            if (down(GLFW_KEY_D)) buttons |= WingControlIntent.RIGHT;
            if (down(GLFW_KEY_SPACE)) buttons |= WingControlIntent.BOOST;
        }
        return new WingControlIntent(buttons, player.getYRot(), player.getXRot());
    }

    private static boolean down(int key) {
        return InputSystem.isDown(InputSystem.InputType.KEYBOARD, key);
    }

    public static boolean travel(LocalPlayer player) {
        if (!WingFlightPose.hasActiveWing(player) || !player.isAlive() || player.isPassenger() || player.isSpectator()) {
            previousPlayer = null;
            previousButtons = 0;
            return false;
        }
        if (previousPlayer != player) previousButtons = 0;
        previousPlayer = player;
        var input = readControl();
        var config = player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get()) ? StormWing.Client.CONFIG
                : player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get()) ? BlackWing.Client.CONFIG
                : player.getData(AttachmentTypes.ACTIVATED_WHITE_WING.get()) ? WhiteWing.Client.CONFIG
                : PlatinumWing.Client.CONFIG;
        double scale = player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get())
                && AbilitySystemClient.getSkillProficiencyMilestone(Skills.STORM_WING.get()) >= 2 ? 1.15 : 1;
        player.setDeltaMovement(WingFlightMotion.step(player.getDeltaMovement(), input,
                previousButtons, config.getRemainingMomentum(), scale));
        previousButtons = input.buttons();
        player.resetFallDistance();
        player.move(MoverType.SELF, player.getDeltaMovement());
        // Retain the original air drag, with no gravity during hover. No server velocity echo is needed.
        player.setDeltaMovement(WingFlightMotion.afterMove(player.getDeltaMovement(), input.buttons()));
        player.setData(AttachmentTypes.WING_FLIGHT_POSE.get(), input.has(WingControlIntent.BOOST)
                ? WingFlightPose.Pose.FAST : input.buttons() != 0 ? WingFlightPose.Pose.SLOW
                : WingFlightPose.coastingPose(player));
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player != previousPlayer || player == null || !WingFlightPose.hasActiveWing(player)) {
            previousPlayer = null;
            previousButtons = 0;
        }
    }
}
