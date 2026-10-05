package org.academy.internal.common.ability.electromaster;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.shape.SurfaceProjector;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityResourceSpec;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.ColorModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.ColorKnot;
import org.academy.api.common.arc.property.Gradient;
import org.academy.api.common.arc.property.Knot;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.damage.TrueHealthDamageSource;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfxgraph.CameraShakeManager;
import org.academy.internal.client.render.vfxgraph.FrameContext;
import org.academy.internal.client.render.vfxgraph.TickingVfx;
import org.academy.internal.client.render.vfxgraph.VfxContexts;
import org.academy.internal.common.ability.development.AbilityDevelopmentProfiles;
import org.academy.internal.common.ability.electromaster.skills.lv5.LightningStorm;
import org.academy.internal.common.ability.electromaster.skills.lv5.Thunderclap;
import org.academy.internal.common.ability.electromaster.skystrike.SkyStrikeProfile;
import org.academy.internal.common.ability.electromaster.skystrike.SkyStrikeVisualPacket;
import org.academy.internal.common.ability.mentalout.control.MentalControlMobAccess;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.world.damagesource.CTAEntityActuallyHurt;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBlade;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.entity.SurvivalDefense;
import org.academy.mixin.common.CooldownInstanceAccess;
import org.academy.mixin.common.ItemCooldownsAccess;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;



public final class Electromaster extends AbilityCategory {
    public static final int PARALYSIS_TICKS = 10;
    public static final int INTERRUPTION_MIN_TICKS = 10;
    public static final int INTERRUPTION_MAX_TICKS = 20;
    public static final int CHARGE_TIMEOUT_TICKS = 100;

    private static final Identifier PARALYSIS_SPEED = Identifier.fromNamespaceAndPath("academy", "electrical_paralysis");
    private final Map<UUID, ChargeState> charges = new HashMap<>();

    public static long now(LivingEntity target) {
        return target.level().getServer().getTickCount();
    }

    public static int chargePoints(String skill) {
        return switch (skill) {
            case "lightning_nova", "thunder_lance" -> 2;
            case "railgun", "thunderclap", "ball_lightning" -> 3;
            default -> 1;
        };
    }

    public int electricalCharge(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return 0;
        var state = charges.get(target.getUUID());
        return state != null && state.target.get() == target && now(target) < state.chargeExpires ? state.points : 0;
    }

    public boolean isParalyzed(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return false;
        var state = charges.get(target.getUUID());
        return state != null && state.target.get() == target && now(target) < state.paralyzedUntil;
    }

    public int electricalInterruptionTicks(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return 0;
        var state = charges.get(target.getUUID());
        return state != null && state.target.get() == target
                ? (int) Math.max(0L, state.interruptedUntil - now(target)) : 0;
    }

    public boolean blocksMobAttack(LivingEntity attacker) {
        return attacker instanceof Mob && electricalInterruptionTicks(attacker) > 0;
    }

    public boolean blocksOutgoingDamage(DamageSource source) {
        var owner = source.getEntity();
        if (owner == null && source.getDirectEntity() instanceof Projectile projectile) {
            owner = projectile.getOwner();
        }
        if (owner == null) owner = source.getDirectEntity();
        return owner instanceof LivingEntity attacker && blocksMobAttack(attacker);
    }

    public float outgoingDamage(DamageSource source, float damage) {
        if (blocksOutgoingDamage(source)) return 0.0f;
        return source.getEntity() instanceof LivingEntity attacker && isParalyzed(attacker)
                ? damage * 0.8f : damage;
    }

    public void onDamageCompleted(LivingEntity target, DamageContainer hit, float healthDamage) {
        if (!(hit.getSource() instanceof SkillDamageSource source) || !(healthDamage > 0.0f)) return;
        addCharge(target, source.electricalChargePoints() >= 0
                ? source.electricalChargePoints()
                : chargePoints(source.getSkill().getKey().getPath()), source);
    }

    public int addCharge(LivingEntity target, int points) {
        return addCharge(target, points, null);
    }

    public int addCharge(LivingEntity target, int points, @Nullable DamageSource cause) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive() || points <= 0
                || target instanceof Player player && DamageTypes.isImmunePlayer(player)) return 0;
        var state = charges.computeIfAbsent(target.getUUID(), _ -> new ChargeState(target));
        var now = now(target);
        if (now >= state.chargeExpires) state.points = 0;
        var sum = (long) state.points + points;
        var discharges = (int) Math.min(Integer.MAX_VALUE, sum / 5L);
        state.points = (int) (sum % 5L);
        state.chargeExpires = now + CHARGE_TIMEOUT_TICKS;
        if (discharges > 0) {
            var duration = INTERRUPTION_MIN_TICKS + target.getRandom().nextInt(
                    INTERRUPTION_MAX_TICKS - INTERRUPTION_MIN_TICKS + 1);
            if (!discharge(target, cause, discharges)) return 0;
            if (!target.isAlive()) return discharges;
            state.paralyzedUntil = now + PARALYSIS_TICKS;
            state.interruptedUntil = Math.max(state.interruptedUntil, now + duration);
            interrupt(target);
            syncParalysis(target);
            syncItemCooldowns(target, state);
        }
        return discharges;
    }

    private static boolean discharge(LivingEntity target, @Nullable DamageSource cause, int count) {
        var level = (ServerLevel) target.level();
        DamageSource source;
        if (cause instanceof SkillDamageSource skillSource && cause.getEntity() instanceof ServerPlayer owner) {
            source = SkillDamageSource.of(owner, skillSource.getSkill(), DamageTypes.ELECTRO_DAMAGE)
                    .withElectricalChargePoints(0).withoutHostilityMark();
        } else {
            source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.ELECTRO_DAMAGE),
                    cause == null ? null : cause.getDirectEntity(), cause == null ? null : cause.getEntity());
        }
        var before = CTAEntityActuallyHurt.readTrueHealth(target);
        if (target.isInvulnerableTo(level, source)
                || SurvivalDefense.applyHealthReadGuard(target, 0.0f) >= before
                || EntityControlApi.clampHealthWrite(target, 0.0f) >= before) return false;
        var settings = ((MinecraftServerContext) level.getServer()).getAcademyCraftServer()
                .getAbilityConfig().electromaster;
        var damage = settings.paralysisDamage(target.getMaxHealth());
        if (damage <= 0.0f) return true;
        var total = (float) Math.min(Float.MAX_VALUE, (double) damage * count);
        var dischargeSource = TrueHealthDamageSource.of(source);
        var maximumHealthPart = damage > settings.paralysisDamage(0.0f) ? total : 0.0f;
        var accepted = DamageComposition.withMaximumHealthPart(target, dischargeSource, maximumHealthPart,
                () -> SkillDamageUtil.applyVerifiedTrueHealth(target, dischargeSource, total));
        return accepted && CTAEntityActuallyHurt.readTrueHealth(target) < before;
    }

    @SuppressWarnings("unchecked")
    private static void interrupt(LivingEntity target) {
        target.stopUsingItem();
        if (target instanceof ServerPlayer player) AbilitySystemServer.getSystem(player).interruptActiveSkills(player);
        if (target instanceof Mob mob) {
            if (mob instanceof MentalControlMobAccess access) access.academy$stopAutonomousGoals();
            ((Brain<LivingEntity>) mob.getBrain()).stopAll((ServerLevel) mob.level(), mob);
            mob.getNavigation().stop();
        }
    }

    private static void syncParalysis(LivingEntity target) {
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(PARALYSIS_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(PARALYSIS_SPEED, -0.8,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void syncItemCooldowns(LivingEntity target, ChargeState state) {
        if (target instanceof ServerPlayer player) {
            for (var index = 0; index < player.getInventory().getContainerSize(); index++) {
                var stack = player.getInventory().getItem(index);
                if (stack.isEmpty()) continue;
                var cooldowns = player.getCooldowns();
                var access = (ItemCooldownsAccess) cooldowns;
                var group = cooldowns.getCooldownGroup(stack);
                var current = access.academy$cooldowns().get(group);
                var end = current == null ? access.academy$tickCount()
                        : ((CooldownInstanceAccess) current).academy$endTime();
                var remaining = (int) Math.max(0L, state.interruptedUntil - now(target));
                if (end - access.academy$tickCount() >= remaining) continue;
                cooldowns.addCooldown(group, remaining);
                state.cooldownEnds.put(group, access.academy$tickCount() + remaining);
            }
        }
    }

    private static void clearParalysis(LivingEntity target, ChargeState state) {
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(PARALYSIS_SPEED);
        state.paralyzedUntil = 0;
    }

    private static void clearInterruption(LivingEntity target, ChargeState state) {
        if (target instanceof ServerPlayer player) {
            var access = (ItemCooldownsAccess) player.getCooldowns();
            state.cooldownEnds.forEach((group, ownEnd) -> {
                var current = access.academy$cooldowns().get(group);
                if (current != null && ((CooldownInstanceAccess) current).academy$endTime() == ownEnd) {
                    player.getCooldowns().removeCooldown(group);
                }
            });
        }
        state.cooldownEnds.clear();
        state.interruptedUntil = 0;
    }

    public void tick(MinecraftServer server) {
        for (var entry : List.copyOf(charges.entrySet())) {
            var state = entry.getValue();
            var target = state.target.get();
            if (target == null) {
                charges.remove(entry.getKey());
                continue;
            }
            if (target.level().getServer() != server) continue;
            if (!target.isAlive() || target.isRemoved()) {
                clearParalysis(target, state);
                clearInterruption(target, state);
                charges.remove(entry.getKey());
                continue;
            }
            if (state.paralyzedUntil > 0 && now(target) >= state.paralyzedUntil) clearParalysis(target, state);
            else if (state.paralyzedUntil > 0) syncParalysis(target);
            if (state.interruptedUntil > 0 && now(target) >= state.interruptedUntil) clearInterruption(target, state);
            else if (state.interruptedUntil > 0) {
                interrupt(target);
                target.setJumping(false);
                target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
                syncItemCooldowns(target, state);
            }
            if (now(target) >= state.chargeExpires && state.paralyzedUntil == 0 && state.interruptedUntil == 0)
                charges.remove(entry.getKey());
        }
    }

    public void leave(LivingEntity target) {
        var state = charges.remove(target.getUUID());
        if (state != null) {
            clearParalysis(target, state);
            clearInterruption(target, state);
        }
    }

    public void stop() {
        charges.values().forEach(state -> {
            var target = state.target.get();
            if (target != null) {
                clearParalysis(target, state);
                clearInterruption(target, state);
            }
        });
        charges.clear();
    }

    private static final class ChargeState {
        final WeakReference<LivingEntity> target;
        final Map<Identifier, Integer> cooldownEnds = new HashMap<>();
        int points;
        long chargeExpires;
        long paralyzedUntil;
        long interruptedUntil;

        ChargeState(LivingEntity target) {
            this.target = new WeakReference<>(target);
        }
    }

    public Electromaster() {
        super(0.1F, AbilityDevelopmentProfiles.ELECTROMASTER);
    }

    @Override
    public Optional<AbilityResourceSpec> getResourceSpec() {
        return Optional.of(AbilityResourceSpec.fixed(100));
    }

    @Override
    public Optional<ResourceKey<DamageType>> getDefaultDamageType() {
        return Optional.of(DamageTypes.ELECTRO_DAMAGE);
    }

    @Override
    public Identifier getIcon() {
        return R.textures.ability.electromaster.icon;
    }

    @Override
    public String getDisplayName() {
        return "Electromaster";
    }

    public static final class Client {
        private static boolean skyStrikeRegistered;

        private Client() {
        }
        public static final class ArcEffectGraphVfx {
            private static final float CULLING_RADIUS = 10.0f;
            private static final float RENDER_DISTANCE = 64.0f;

            private final ArcEffect arc;
            private final List<ArcPath> localPaths = new ArrayList<>();
            private final Matrix4f translation = new Matrix4f();

            private ArcEffectGraphVfx(ArcEffect arc) {
                this.arc = arc;
            }

            public static void spawn(ArcEffect arc) {
                var adapter = new ArcEffectGraphVfx(arc);
                var effect = VfxGraphManager.INSTANCE.spawn(
                        AcademyCraft.academy("vfxgraph/arc_effect"),
                        arc.position().toVector3f());
                effect.bindArcs("paths", _ -> adapter.localPaths);
                effect.bindGameTime("time");
                effect.bindFrame(adapter::sample);
                effect.setRenderDistance(RENDER_DISTANCE);
            }

            private boolean sample(ActiveEffect effect, GraphCamera camera, float partialTick) {
                if (arc.isRemoved() || !arc.isAlive() || arc.tickCount > arc.getLifeTime()) {
                    return false;
                }
                var position = arc.position();
                effect.setPosition(position.toVector3f());
                effect.setCullingSphere(position.toVector3f(), CULLING_RADIUS);
                var offset = translation.translation(
                        -(float) position.x, -(float) position.y, -(float) position.z);
                localPaths.clear();
                for (var path : arc.getArcPaths()) {
                    localPaths.add(new ArcPath(
                            path.path().transform(offset),
                            path.modifiers(),
                            path.resolution(),
                            path.branches()));
                }
                return true;
            }
        }

        public static void registerSkyStrike() {
            if (skyStrikeRegistered) return;
            skyStrikeRegistered = true;
            SkyStrikeVisualPacket.initClient();
        }

        public static final class SkyStrike {
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
                VfxContexts.register(new SkyStrikeEffect(
                        impact,
                        seed,
                        profile,
                        detail,
                        flashSetting,
                        feedbackAttenuation
                ));
                CameraShakeManager.add(new CameraShakeManager.Shake(
                        seed,
                        profile.shakeDegrees() * CameraShakeManager.clampUnit(shakeAttenuation)
                                * CameraShakeManager.clampUnit(shakeSetting),
                        profile.shakeDurationTicks(),
                        profile.shakeCapDegrees()));
            }

            static float distanceAttenuation(double distance, double range) {
                if (!Double.isFinite(distance) || !Double.isFinite(range) || range <= 0.0) return 0.0f;
                return (float) Mth.clamp(1.0 - Math.max(0.0, distance) / range, 0.0, 1.0);
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
                SkyStrikeEffect.clearConcurrency();
            }
        }

        /**
         * Owns the graph-authored world effect and bounded screen feedback on the same paused game clock.
         */
        public static final class SkyStrikeEffect implements TickingVfx {
            private static final int MAX_DETAILED_EFFECTS = 12;
            private static final int FULL_SURFACE_BUDGET = 4;
            private static final Identifier FLASH_ASSET = Identifier.fromNamespaceAndPath(
                    AcademyCraft.MOD_ID, "vfxgraph/sky_strike_flash");
            private static int activeDetailedEffects;
            private static final Set<SkyStrikeEffect> ACTIVE = new HashSet<>();

            private final SkyStrikeProfile profile;
            private final float flashIntensity;
            private final float feedbackAttenuation;
            private final boolean detailedSlot;
            private final ActiveEffect graph;
            private final ActiveEffect flashGraph;
            private float ageTicks;
            private boolean alive = true;
            private boolean released;

            public SkyStrikeEffect(Vec3 impact, long seed, SkyStrikeProfile profile,
                                SkyStrikeGeometry.Detail requestedDetail, float flashIntensity, float feedbackAttenuation) {
                this.profile = profile;
                this.flashIntensity = clamp01(flashIntensity);
                this.feedbackAttenuation = clamp01(feedbackAttenuation);
                var detail = claimDetail(requestedDetail);
                detailedSlot = detail != SkyStrikeGeometry.Detail.COLUMN_ONLY;
                var asset = Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                        profile == SkyStrikeProfile.THUNDERCLAP
                                ? "vfxgraph/sky_strike_thunderclap" : "vfxgraph/sky_strike_storm");
                var position = new Vector3f((float) impact.x, (float) impact.y, (float) impact.z);
                try {
                    graph = VfxGraphManager.INSTANCE.spawn(asset, position);
                } catch (RuntimeException failure) {
                    releaseSlot();
                    throw failure;
                }
                try {
                    flashGraph = VfxGraphManager.INSTANCE.spawn(FLASH_ASSET, position);
                } catch (RuntimeException failure) {
                    graph.stop();
                    releaseSlot();
                    throw failure;
                }
                ACTIVE.add(this);
                // Explicit time also keeps both render passes and the feedback pulse on one clock.
                graph.bind("time", () -> Value.of(ageTicks / 20f));
                graph.bind("ground_radius", () -> Value.of(profile.ringEndRadius()));
                graph.bind("seed", () -> Value.of((float) ((seed ^ (seed >>> 32)) & 0xFFFFFFL)));
                graph.bind("detail", () -> Value.of(switch (detail) {
                    case FULL -> 1f;
                    case REDUCED -> 0.45f;
                    case COLUMN_ONLY -> 0f;
                }));
                graph.bind("surface_detail", () -> Value.of(surfaceDetailScale()));
                graph.bind("cloud_opacity", () -> Value.of(profile == SkyStrikeProfile.THUNDERCLAP ? 1f : 0.32f));
                // The cloud extends far above the impact point used by the normal small-effect culler.
                var level = Minecraft.getInstance().level;
                if (level != null) graph.bindSurface("ground", new SkyStrikeTerrain(level, impact));
                graph.setAlwaysVisible(true);
                graph.setMinimumFarPlane(256);
                // Screen feedback lives on the graph too (E1): alpha is computed on the same
                // paused game clock and injected as a live param; the output block aggregates,
                // caps and draws the fullscreen quad.
                flashGraph.setAlwaysVisible(true);
                flashGraph.bind("flash_alpha", () -> Value.of(screenAlpha()));
                flashGraph.bind("flash_cap", () -> Value.of(profile.flashCap()));
            }

            private static float surfaceDetailScale() {
                return Math.min(1f, (float) FULL_SURFACE_BUDGET / Math.max(1, activeDetailedEffects));
            }

            private static SkyStrikeGeometry.Detail claimDetail(SkyStrikeGeometry.Detail requested) {
                if (requested == SkyStrikeGeometry.Detail.COLUMN_ONLY) return requested;
                if (activeDetailedEffects >= MAX_DETAILED_EFFECTS) return SkyStrikeGeometry.Detail.COLUMN_ONLY;
                activeDetailedEffects++;
                return requested;
            }

            static void clearConcurrency() {
                for (var effect : List.copyOf(ACTIVE)) effect.finish();
                activeDetailedEffects = 0;
            }

            private void finish() {
                alive = false;
                graph.stop();
                flashGraph.stop();
                ACTIVE.remove(this);
                releaseSlot();
            }

            private static float clamp01(float value) {
                return Float.isFinite(value) ? Mth.clamp(value, 0, 1) : 0;
            }

            @Override
            public void tick(FrameContext ctx) {
                if (!alive) return;
                if (Float.isFinite(ctx.dt()) && ctx.dt() > 0) ageTicks += Math.min(ctx.dt(), 4);
                if (ageTicks >= profile.lifetimeTicks()) {
                    finish();
                }
            }

            @Override
            public boolean isAlive() {
                return alive;
            }

            @Override
            public void onRemoved() {
                if (alive) finish();
            }

            private float screenAlpha() {
                return profile.flashAlpha() * flashCurve(ageTicks)
                        * flashIntensity * feedbackAttenuation;
            }

            private float flashCurve(float age) {
                float initial = age < profile.flashDurationTicks()
                        ? Mth.square(1f - age / profile.flashDurationTicks()) : 0;
                if (!profile.restrike()) return initial;
                float restrike = Math.max(0, 1f - Math.abs(age - 3f) / 0.65f) * 0.45f;
                return Math.min(1, initial + restrike);
            }

            private void releaseSlot() {
                if (!detailedSlot || released) return;
                released = true;
                activeDetailedEffects = Math.max(0, activeDetailedEffects - 1);
            }
        }

        public static record SkyStrikeGeometry(
                List<ArcPath> paths,
                int aerialArcCount,
                int inwardArcCount,
                int groundArcCount
        ) {
            private static final AttributeCurve TAPER = new AttributeCurve(List.of(
                    new Knot(0.0f, 1.0f),
                    new Knot(0.72f, 0.9f),
                    new Knot(1.0f, 0.04f)
            ));
            private static final Gradient BLUE_WHITE = new Gradient(List.of(
                    new ColorKnot(0.0f, new Vector3f(0.96f, 0.99f, 1.0f)),
                    new ColorKnot(0.55f, new Vector3f(0.49f, 0.80f, 1.0f)),
                    new ColorKnot(1.0f, new Vector3f(0.20f, 0.55f, 1.0f))
            ));

            public SkyStrikeGeometry {
                paths = List.copyOf(paths);
            }

            public static SkyStrikeGeometry build(
                    SkyStrikeProfile profile,
                    Vec3 impact,
                    long seed,
                    Detail detail
            ) {
                var random = new Random(seed);
                if (detail == Detail.COLUMN_ONLY) {
                    return new SkyStrikeGeometry(List.of(), 0, 0, 0);
                }

                var aerialCount = profile.aerialArcCount();
                var inwardCount = profile.inwardArcCount();
                var groundCount = profile.groundArcCount();
                if (detail == Detail.REDUCED) {
                    aerialCount = (aerialCount + 1) / 2;
                    inwardCount = (inwardCount + 1) / 2;
                    groundCount = 0;
                }

                var paths = new ArrayList<ArcPath>(aerialCount + inwardCount + groundCount);
                var thunderclap = profile == SkyStrikeProfile.THUNDERCLAP;
                var aerialMinLength = thunderclap ? 8.0 : 5.0;
                var aerialLengthRange = thunderclap ? 10.0 : 5.0;
                for (var i = 0; i < aerialCount; i++) {
                    var height = profile.columnHeight() * (0.28 + random.nextDouble() * 0.62);
                    var start = impact.add(
                            gaussian(random) * profile.columnWidth() * 0.08,
                            height,
                            gaussian(random) * profile.columnWidth() * 0.08
                    );
                    var angle = random.nextDouble() * Mth.TWO_PI;
                    var length = aerialMinLength + random.nextDouble() * aerialLengthRange;
                    var vertical = (random.nextDouble() - 0.35) * length * 0.5;
                    var end = start.add(Mth.cos(angle) * length, vertical, Mth.sin(angle) * length);
                    paths.add(arc(start, end, thunderclap ? 0.38f : 0.28f,
                            thunderclap ? 2.8f : 1.8f, random.nextLong()));
                }

                for (var i = 0; i < inwardCount; i++) {
                    var angle = random.nextDouble() * Mth.TWO_PI;
                    var radius = 0.8 + random.nextDouble() * 2.7;
                    var start = impact.add(
                            Mth.cos(angle) * radius,
                            (random.nextDouble() - 0.35) * 2.0,
                            Mth.sin(angle) * radius
                    );
                    var end = impact.add(gaussian(random) * 0.18, 0.28 + random.nextDouble() * 0.4,
                            gaussian(random) * 0.18);
                    paths.add(arc(start, end, 0.52f, 2.6f, random.nextLong()));
                }

                for (var i = 0; i < groundCount; i++) {
                    var angle = Mth.TWO_PI * i / Math.max(1, groundCount) + random.nextDouble() * 0.18;
                    var minRadius = thunderclap ? 3.0 : 1.5;
                    var maxRadius = thunderclap ? 9.0 : 4.5;
                    var radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
                    var start = impact.add(0, 0.16, 0);
                    var end = impact.add(Mth.cos(angle) * radius, 0.10, Mth.sin(angle) * radius);
                    paths.add(arc(start, end, thunderclap ? 0.32f : 0.24f,
                            thunderclap ? 1.8f : 1.1f, random.nextLong()));
                }
                return new SkyStrikeGeometry(paths, aerialCount, inwardCount, groundCount);
            }

            private static ArcPath arc(Vec3 start, Vec3 end, float jaggedness, float thickness, long seed) {
                return new ArcPath(
                        new LinePath(start.toVector3f(), end.toVector3f()),
                        List.of(
                                new JaggedModifier(jaggedness, 4, seed),
                                new TaperModifier(TAPER, thickness),
                                new ColorModifier(BLUE_WHITE)
                        ),
                        1.6f,
                        List.of()
                );
            }

            private static double gaussian(Random random) {
                return random.nextGaussian();
            }

            public enum Detail {
                FULL,
                REDUCED,
                COLUMN_ONLY
            }
        }

        /**
         * Per-effect terrain snapshot. Cached block-column probes never load chunks or alter terrain.
         */
        public static final class SkyStrikeTerrain implements SurfaceProjector {
            private final ClientLevel level;
            private final Vec3 origin;
            private final Map<Long, Float> heights = new HashMap<>();

            SkyStrikeTerrain(ClientLevel level, Vec3 origin) {
                this.level = level;
                this.origin = origin;
            }

            @Override
            public Vector3f project(float x, float y, float z, Vector3f destination) {
                int blockX = (int) Math.floor(origin.x + x);
                int blockZ = (int) Math.floor(origin.z + z);
                long key = ((long) blockX << 32) ^ (blockZ & 0xFFFFFFFFL);
                float ground = heights.computeIfAbsent(key, ignored -> sample(blockX, blockZ));
                return destination.set(x, ground + 0.08f, z);
            }

            private float sample(int x, int z) {
                if (!level.isLoaded(new BlockPos(x, (int) origin.y, z))) return Float.NaN;
                var start = new Vec3(x + 0.5, origin.y + 16, z + 0.5);
                var end = new Vec3(x + 0.5, origin.y - 32, z + 0.5);
                var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.ANY, CollisionContext.empty()));
                return hit.getType() == HitResult.Type.MISS ? Float.NaN : (float) (hit.getLocation().y - origin.y);
            }
        }
    }
}
