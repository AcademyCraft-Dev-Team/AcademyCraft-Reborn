package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.ability.electromaster.skills.lv4.Railgun;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;

/** Charge and coin-return hand attachments. All geometry and materials are authored in VFXGraph. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class RailgunChargeVfxClient {
    private static final Map<Player, Hands> ACTIVE = new IdentityHashMap<>();
    private static boolean registered;

    private static final class Hands {
        final ActiveEffect charge;
        ActiveEffect hint;
        Hands(Player player) { charge = create(player, false); }
        void stop() {
            charge.stop();
            if (hint != null) hint.stop();
        }
    }

    private RailgunChargeVfxClient() { }
    public static void register() { registered = true; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!registered) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        ACTIVE.entrySet().removeIf(entry -> {
            if (charging(entry.getKey())) return false;
            entry.getValue().stop();
            return true;
        });
        for (var player : mc.level.players()) {
            if (!charging(player)) continue;
            var hands = ACTIVE.computeIfAbsent(player, Hands::new);
            var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
            if (data != null && data.coinReturnHint()) {
                if (hands.hint == null || hands.hint.isStopped()) hands.hint = create(player, true);
            } else if (hands.hint != null) {
                hands.hint.stop();
                hands.hint = null;
            }
        }
    }

    private static boolean charging(Player player) {
        if (player.isRemoved() || player.level() != Minecraft.getInstance().level) return false;
        var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
        return data != null && !data.released();
    }

    private static ActiveEffect create(Player player, boolean hint) {
        var effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/railgun_charge"), player.position().toVector3f());
        effect.bind("seed", () -> Value.of((float) (player.getUUID().getLeastSignificantBits() & 0xFFFFFFL)));
        effect.bind("hint", () -> Value.of(hint ? 1f : 0f));
        effect.bindFrame((active, camera, partialTick) -> attach(active, player, hint, camera, partialTick));
        effect.setRenderDistance(96);
        return effect;
    }

    private static boolean attach(ActiveEffect effect, Player player, boolean hint, GraphCamera camera, float partialTick) {
        if (!charging(player)) return false;
        var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
        if (data == null || hint && !data.coinReturnHint()) return false;
        var mc = Minecraft.getInstance();
        boolean firstPerson = player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        boolean right = hint ? data.mainHandRight() : data.rightHand();
        Matrix4f transform;
        if (firstPerson) {
            transform = new Matrix4f(camera.viewRotation()).invert();
            transform.setTranslation(camera.position());
            transform.translate(right ? 0.30f : -0.30f, -0.22f, -0.46f).scale(0.90f);
        } else {
            var root = WingAvatarRegistry.entries().get(player.getId());
            if (root == null) { effect.setFrameVisible(false); return true; }
            transform = new Matrix4f(root);
            transform.m30(transform.m30() + camera.position().x);
            transform.m31(transform.m31() + camera.position().y);
            transform.m32(transform.m32() + camera.position().z);
            transform.translate(right ? -0.38f : 0.38f, 0.55f, -0.24f);
        }
        effect.setFrameVisible(true);
        effect.setTransform(transform);
        effect.setCullingSphere(effect.position(), 1);
        effect.effect().setLiveParam("time", Value.of((player.tickCount + partialTick) / 20f));
        effect.effect().setLiveParam("strength", Value.of(hint ? 0.55f
                : Math.clamp((data.ticks() + partialTick) / Math.max(1, Railgun.CHARGE_TIME), 0.15f, 1)));
        return true;
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    private static void clear() {
        ACTIVE.values().forEach(Hands::stop);
        ACTIVE.clear();
    }
}
