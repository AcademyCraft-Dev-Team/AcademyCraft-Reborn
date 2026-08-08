package org.academy.internal.client.renderer.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.api.client.resources.R;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
import org.academy.internal.common.ability.electromaster.SkyStrikeVisualPacket;
import org.academy.internal.common.ability.electromaster.skills.lv4.LightningStorm;
import org.academy.internal.common.ability.electromaster.skills.lv5.Thunderclap;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SkyStrikeVfxClient {
    private static boolean registered;

    private SkyStrikeVfxClient() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        VfxRegistry.register(
                SkyStrikeArcCoreData.class,
                VfxPhase.WORLD_TRANSLUCENT,
                new ArcRenderer(false, R.textures.ability.electromaster.skill.sky_strike.effect.lightning_ribbon)
        );
        VfxRegistry.register(
                SkyStrikeArcGlowData.class,
                VfxPhase.WORLD_GLOW,
                new ArcRenderer(true, R.textures.ability.electromaster.skill.sky_strike.effect.lightning_ribbon)
        );
        VfxRegistry.register(
                SkyStrikeWorldCoreData.class,
                VfxPhase.WORLD_TRANSLUCENT,
                new SkyStrikeWorldRenderer(false)
        );
        VfxRegistry.register(
                SkyStrikeWorldGlowData.class,
                VfxPhase.WORLD_GLOW,
                new SkyStrikeWorldRenderer(true)
        );
        VfxRegistry.register(
                SkyStrikeScreenFlashData.class,
                VfxPhase.SCREEN_SPACE_POST,
                new SkyStrikeScreenFlashRenderer()
        );
        SkyStrikeVisualPacket.initClient();
    }

    public static void spawn(Vec3 impact, long seed, SkyStrikeProfile profile) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var cameraPos = minecraft.gameRenderer.mainCamera().position();
        var distance = cameraPos.distanceTo(impact);
        if (!Double.isFinite(distance) || distance > SkyStrikeVisualPacket.BROADCAST_RANGE + 1.0) return;

        var detail = distance <= 48.0
                ? SkyStrikeGeometry.Detail.FULL
                : distance <= 96.0
                ? SkyStrikeGeometry.Detail.REDUCED
                : SkyStrikeGeometry.Detail.COLUMN_ONLY;
        var flashSetting = profile == SkyStrikeProfile.THUNDERCLAP
                ? Thunderclap.Client.CONFIG.getFlashIntensity()
                : LightningStorm.Client.CONFIG.getFlashIntensity();
        var shakeSetting = profile == SkyStrikeProfile.THUNDERCLAP
                ? Thunderclap.Client.CONFIG.getShakeIntensity()
                : LightningStorm.Client.CONFIG.getShakeIntensity();
        var feedbackAttenuation = distanceAttenuation(distance, profile.feedbackRange());
        var shakeAttenuation = distanceAttenuation(distance, profile.shakeRange());
        VfxManager.INSTANCE.spawn(new SkyStrikeVfx(
                impact,
                seed,
                profile,
                detail,
                flashSetting,
                feedbackAttenuation
        ));
        CameraShakeManager.add(profile, seed, shakeAttenuation, shakeSetting);
    }

    static float distanceAttenuation(double distance, double range) {
        if (!Double.isFinite(distance) || !Double.isFinite(range) || range <= 0.0) return 0.0f;
        return (float) Math.clamp(1.0 - Math.max(0.0, distance) / range, 0.0, 1.0);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        clear();
    }

    private static void clear() {
        CameraShakeManager.clear();
        SkyStrikeVfx.clearConcurrency();
        VfxManager.INSTANCE.clearEffects();
    }
}
