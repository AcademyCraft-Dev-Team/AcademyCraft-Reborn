package org.academy.internal.common.ability.electromaster;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.shape.SurfaceProjector;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityResourceSpec;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.damage.TrueHealthDamageSource;
import org.academy.api.server.ability.electromaster.IronSandResourceService;
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
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.ability.IronSandResourceManager;
import org.academy.internal.server.entity.SurvivalDefense;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;

public final class Electromaster extends AbilityCategory {
    private static @Nullable IronSandResourceService resourceService;
    public static final int PARALYSIS_TICKS = 10;
    public static final int INTERRUPTION_MIN_TICKS = 10;
    public static final int INTERRUPTION_MAX_TICKS = 20;
    public static final int CHARGE_TIMEOUT_TICKS = 100;

    private static final Identifier PARALYSIS_SPEED = Identifier.fromNamespaceAndPath("academy", "electrical_paralysis");
    private final Map<UUID, ChargeState> charges = new HashMap<>();

    public static long now(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return 0L;
        return level.getServer().getTickCount();
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
        var settings = level.getServer().getAcademyCraftServer()
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
                var group = cooldowns.getCooldownGroup(stack);
                var current = cooldowns.cooldowns.get(group);
                var end = current == null ? cooldowns.tickCount : current.endTime();
                var remaining = (int) Math.max(0L, state.interruptedUntil - now(target));
                if (end - cooldowns.tickCount >= remaining) continue;
                cooldowns.addCooldown(group, remaining);
                state.cooldownEnds.put(group, cooldowns.tickCount + remaining);
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
            var cooldowns = player.getCooldowns();
            state.cooldownEnds.forEach((group, ownEnd) -> {
                var current = cooldowns.cooldowns.get(group);
                if (current != null && current.endTime() == ownEnd) {
                    cooldowns.removeCooldown(group);
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
    public void initServer(MinecraftServerContext context) {
        var system = context.getAcademyCraftServer().getAbilitySystemServer();
        var ledger = new IronSandResourceManager(system.getPlayerDataManager(), system.getPlayerCPManager(),
                system.getSyncManager());
        AbilitySystemServer.SubsystemRegistry.registerSubsystem(ledger, SyncTypes.IRON_SAND_RESOURCE);
        system.registerResourceLedger(this, ledger);
        resourceService = ledger;
    }

    public static IronSandResourceService resourceService() {
        return Objects.requireNonNull(resourceService);
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
                        ? SkyStrikeDetail.FULL
                        : distance <= 96.0
                        ? SkyStrikeDetail.REDUCED
                        : SkyStrikeDetail.COLUMN_ONLY;
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
                                   SkyStrikeDetail requestedDetail, float flashIntensity, float feedbackAttenuation) {
                this.profile = profile;
                this.flashIntensity = clamp01(flashIntensity);
                this.feedbackAttenuation = clamp01(feedbackAttenuation);
                var detail = claimDetail(requestedDetail);
                detailedSlot = detail != SkyStrikeDetail.COLUMN_ONLY;
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
                var level = Minecraft.getInstance().level;
                if (level != null) graph.bindSurface("ground", new SkyStrikeTerrain(level, impact));
                graph.setAlwaysVisible(true);
                graph.setMinimumFarPlane(256);
                flashGraph.setAlwaysVisible(true);
                flashGraph.bind("flash_alpha", () -> Value.of(screenAlpha()));
                flashGraph.bind("flash_cap", () -> Value.of(profile.flashCap()));
            }

            private static float surfaceDetailScale() {
                return Math.min(1f, (float) FULL_SURFACE_BUDGET / Math.max(1, activeDetailedEffects));
            }

            private static SkyStrikeDetail claimDetail(SkyStrikeDetail requested) {
                if (requested == SkyStrikeDetail.COLUMN_ONLY) return requested;
                if (activeDetailedEffects >= MAX_DETAILED_EFFECTS) return SkyStrikeDetail.COLUMN_ONLY;
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

        public enum SkyStrikeDetail {
            FULL,
            REDUCED,
            COLUMN_ONLY
        }

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
