package org.academy.internal.client.render.vfx;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PlasmaVfxClient {
    private static final Identifier CHARGE_ASSET = Identifier.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "vfxgraph/plasma_cannon_charge"
    );
    private static final Identifier FOCUS_ASSET = Identifier.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "vfxgraph/plasma_cannon_focus"
    );
    private static final Identifier PROJECTILE_ASSET = Identifier.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "vfxgraph/plasma_cannon_projectile"
    );
    private static final Identifier IMPACT_ASSET = Identifier.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "vfxgraph/plasma_cannon_impact"
    );
    private static final int FOCUS_START_STAGE = 2;
    private static final float FOCUS_START = (float) FOCUS_START_STAGE / PlasmaGeneration.MAX_STAGE;
    private static final float FOCUS_CONVERGENCE_END = 0.78f;
    private static final float FORMATION_AT_CONVERGENCE = 0.62f;
    private static final float TORNADO_EXPAND_MIN = 0.5f;
    private static final float TORNADO_EXPAND_MAX = 3.0f;
    private static final float PLASMA_MINIMUM_FAR_PLANE = 512.0f;
    private static final int IMPACT_LIFETIME_TICKS = 60;
    private static final Map<Plasma, PlasmaEffects> EFFECTS = new IdentityHashMap<>();
    private static final ArrayList<TimedEffect> IMPACTS = new ArrayList<>();

    private PlasmaVfxClient() {
    }

    public static void register() {
        // All plasma cannon visuals are graph-authored. The entity renderer remains empty.
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Plasma plasma) {
            spawnCharge(plasma);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickImpacts();
        var manager = VfxGraphManager.INSTANCE;
        var iterator = EFFECTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var plasma = entry.getKey();
            var effects = entry.getValue();
            effects.capturePosition(plasma);

            if (plasma.isRemoved()) {
                stop(manager, effects.gather);
                stop(manager, effects.focus);
                stop(manager, effects.projectile);
                if (effects.launched) {
                    spawnImpact(effects.lastPosition);
                }
                iterator.remove();
                continue;
            }

            if (!plasma.isLaunched()) {
                if (plasma.getGatherProgress() >= FOCUS_START && effects.focus == null) {
                    effects.focus = spawnFocus(plasma, effects);
                }
                continue;
            }

            if (!effects.launched) {
                effects.launched = true;
                stop(manager, effects.gather);
                effects.gather = null;
                stop(manager, effects.focus);
                effects.focus = null;
                effects.projectile = spawnProjectile(plasma, effects);
            }
            effects.updateProjectilePosition(plasma);
        }
    }

    private static void spawnCharge(Plasma plasma) {
        var manager = VfxGraphManager.INSTANCE;
        // 实体重新加入客户端世界时可能再次触发 join；先停止同一实体的旧图实例，
        // 避免两套 focus 电弧同时存活。
        var previous = EFFECTS.remove(plasma);
        if (previous != null) {
            stop(manager, previous.gather);
            stop(manager, previous.focus);
            stop(manager, previous.projectile);
        }
        ActiveEffect gather = null;
        try {
            var fixedOrigin = chargeOrigin(plasma);
            var fixedFocus = worldPosition(plasma);
            var fixedFocusOffset = new Vector3f(fixedFocus).sub(fixedOrigin);
            gather = manager.spawn(CHARGE_ASSET, new Vector3f(fixedOrigin));
            keepVisibleAtLowViewDistance(gather);
            gather.bind("emission", () -> Value.of(gatherEmission(plasma)));
            gather.bind("charge_progress", () -> Value.of(plasma.getGatherProgress()));
            gather.bind("expand_rate", () -> Value.of(tornadoExpandRate(plasma)));
            gather.bind("focus_offset", () -> Value.of(new Vector3f(fixedFocusOffset)));
            EFFECTS.put(plasma, new PlasmaEffects(gather, fixedFocus, fixedOrigin));
        } catch (RuntimeException exception) {
            stop(manager, gather);
            AcademyCraft.getLogger().error("Unable to spawn graph-authored plasma charge VFX", exception);
        }
    }

    private static @Nullable ActiveEffect spawnFocus(Plasma plasma, PlasmaEffects effects) {
        try {
            var effect = VfxGraphManager.INSTANCE.spawn(FOCUS_ASSET, new Vector3f(effects.fixedFocus));
            keepVisibleAtLowViewDistance(effect);
            effect.bind("convergence_progress", () -> Value.of(convergenceProgress(plasma)));
            effect.bind("formation_progress", () -> Value.of(formationProgress(plasma)));
            return effect;
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().error("Unable to spawn graph-authored plasma focus VFX", exception);
            return null;
        }
    }

    private static @Nullable ActiveEffect spawnProjectile(Plasma plasma, PlasmaEffects effects) {
        try {
            effects.projectileOrigin.set(worldPosition(plasma));
            effects.projectilePosition.zero();
            var direction = initialDirection(plasma);
            effects.projectileDirection.set((float) direction.x, (float) direction.y, (float) direction.z);
            var effect = VfxGraphManager.INSTANCE.spawn(PROJECTILE_ASSET, new Vector3f(effects.projectileOrigin));
            keepVisibleAtLowViewDistance(effect);
            effect.bind("projectile_position", () -> Value.of(new Vector3f(effects.projectilePosition)));
            effect.bind("projectile_direction", () -> Value.of(new Vector3f(effects.projectileDirection)));
            return effect;
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().error("Unable to spawn graph-authored plasma projectile VFX", exception);
            return null;
        }
    }

    private static void spawnImpact(Vector3f position) {
        try {
            var effect = VfxGraphManager.INSTANCE.spawn(IMPACT_ASSET, new Vector3f(position));
            keepVisibleAtLowViewDistance(effect);
            IMPACTS.add(new TimedEffect(effect));
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().error("Unable to spawn graph-authored plasma impact VFX", exception);
        }
    }

    private static void tickImpacts() {
        var iterator = IMPACTS.iterator();
        while (iterator.hasNext()) {
            var timed = iterator.next();
            timed.age++;
            if (timed.age > IMPACT_LIFETIME_TICKS) {
                VfxGraphManager.INSTANCE.stop(timed.effect);
                iterator.remove();
            }
        }
    }

    private static Vector3f chargeOrigin(Plasma plasma) {
        var owner = owner(plasma);
        if (owner != null) {
            return worldPosition(owner);
        }
        return new Vector3f((float) plasma.getX(), (float) plasma.getY() - 31f, (float) plasma.getZ());
    }

    private static @Nullable Entity owner(Plasma plasma) {
        var ownerId = plasma.getOwnerEntityId();
        return ownerId == 0 ? null : plasma.level().getEntity(ownerId);
    }

    private static Vec3 initialDirection(Plasma plasma) {
        var owner = owner(plasma);
        var look = owner == null ? Vec3.ZERO : owner.getLookAngle();
        return look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
    }

    private static Vector3f worldPosition(Entity entity) {
        return new Vector3f((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
    }

    private static float gatherEmission(Plasma plasma) {
        if (plasma.isRemoved() || plasma.isLaunched()) {
            return 0.0f;
        }
        var eased = (float) Mth.smoothstep(Mth.clamp(plasma.getGatherProgress(), 0.0f, 1.0f));
        return Mth.clamp(0.32f + eased * 0.68f, 0.0f, 1.0f);
    }

    /** 第一蓄力层正好持续 40 tick（2 秒），风暴缩放在这一层内线性走完 0.5 → 3.0。 */
    private static float tornadoExpandRate(Plasma plasma) {
        float firstStage = Mth.clamp(
                plasma.getGatherProgress() * PlasmaGeneration.MAX_STAGE,
                0.0f,
                1.0f
        );
        return Mth.lerp(firstStage, TORNADO_EXPAND_MIN, TORNADO_EXPAND_MAX);
    }

    private static float stagedFocusProgress(float gatherProgress) {
        float chargeStages = Mth.clamp(gatherProgress, 0.0f, 1.0f)
                * PlasmaGeneration.MAX_STAGE;
        float activeStageProgress = chargeStages - FOCUS_START_STAGE;
        if (activeStageProgress <= 0.0f) return 0.0f;

        int completedStages = Mth.floor(activeStageProgress);
        float withinStage = activeStageProgress - completedStages;
        float stagedEase = (float) Mth.smoothstep(Mth.clamp(
                (withinStage - 0.08f) / 0.82f,
                0.0f,
                1.0f
        ));
        float focusStages = PlasmaGeneration.MAX_STAGE - FOCUS_START_STAGE;
        return Mth.clamp((completedStages + stagedEase) / focusStages, 0.0f, 1.0f);
    }

    /** 螺旋汇聚保留按蓄力层级推进，但在 78% 总蓄力时先于龙卷风收缩完成。 */
    private static float convergenceProgress(Plasma plasma) {
        float endProgress = stagedFocusProgress(FOCUS_CONVERGENCE_END);
        return Mth.clamp(stagedFocusProgress(plasma.getGatherProgress()) / endProgress, 0.0f, 1.0f);
    }

    /** 汇聚完成时先形成紧凑球体，剩余蓄力阶段再平滑膨胀到完整 projectile 尺寸。 */
    private static float formationProgress(Plasma plasma) {
        float gatherProgress = Mth.clamp(plasma.getGatherProgress(), 0.0f, 1.0f);
        if (gatherProgress <= FOCUS_CONVERGENCE_END) {
            return convergenceProgress(plasma) * FORMATION_AT_CONVERGENCE;
        }
        float swell = (float) Mth.smoothstep(Mth.clamp(
                (gatherProgress - FOCUS_CONVERGENCE_END) / (1.0f - FOCUS_CONVERGENCE_END),
                0.0f,
                1.0f
        ));
        return Mth.lerp(swell, FORMATION_AT_CONVERGENCE, 1.0f);
    }

    private static void keepVisibleAtLowViewDistance(ActiveEffect effect) {
        effect.setAlwaysVisible(true);
        effect.setMinimumFarPlane(PLASMA_MINIMUM_FAR_PLANE);
    }

    private static void stop(VfxGraphManager manager, @Nullable ActiveEffect effect) {
        if (effect != null) {
            manager.stop(effect);
        }
    }

    private static final class PlasmaEffects {
        private @Nullable ActiveEffect gather;
        private @Nullable ActiveEffect focus;
        private @Nullable ActiveEffect projectile;
        private final Vector3f projectileOrigin = new Vector3f();
        private final Vector3f projectilePosition = new Vector3f();
        private final Vector3f projectileDirection = new Vector3f(0f, 0f, 1f);
        private final Vector3f lastPosition;
        private final Vector3f fixedOrigin;
        private final Vector3f fixedFocus;
        private boolean launched;

        private PlasmaEffects(ActiveEffect gather, Vector3f initialPosition, Vector3f fixedOrigin) {
            this.gather = gather;
            this.lastPosition = new Vector3f(initialPosition);
            this.fixedOrigin = new Vector3f(fixedOrigin);
            this.fixedFocus = new Vector3f(initialPosition);
        }

        private void capturePosition(Plasma plasma) {
            var current = worldPosition(plasma);
            float dx = current.x - lastPosition.x;
            float dy = current.y - lastPosition.y;
            float dz = current.z - lastPosition.z;
            float lengthSquared = dx * dx + dy * dy + dz * dz;
            if (launched && lengthSquared > 1.0E-5f) {
                float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
                projectileDirection.set(dx * inverseLength, dy * inverseLength, dz * inverseLength);
            }
            lastPosition.set(current);
        }

        private void updateProjectilePosition(Plasma plasma) {
            projectilePosition.set(worldPosition(plasma)).sub(projectileOrigin);
        }
    }

    private static final class TimedEffect {
        private final ActiveEffect effect;
        private int age;

        private TimedEffect(ActiveEffect effect) {
            this.effect = effect;
        }
    }
}
