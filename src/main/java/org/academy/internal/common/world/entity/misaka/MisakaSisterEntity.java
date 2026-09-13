package org.academy.internal.common.world.entity.misaka;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.internal.common.world.entity.misaka.ai.MisakaArcZapGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaCropGrazeGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaEscapeHazardGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaFollowGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaForageFoodGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaLivelyGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaNetworkWanderGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaPersonalityCombatGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaSleepInBedGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaSocialGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaStarveGuardGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaSwimAshoreGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaUnawakenedStrollGoal;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.misaka.MisakaPanelSupport;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class MisakaSisterEntity extends PathfinderMob implements GeoEntity {
    /** Hitbox/render height relative to Steve (0.6 × 1.8). */
    public static final float STEVE_SCALE = 0.94f;
    /** Unscaled geo foot→head height in blocks (16 px = 1 block). */
    public static final float GEO_HEIGHT_BLOCKS = 2.4f;
    /** GeckoLib {@code withScale} so rendered height matches {@code 1.8 * STEVE_SCALE}. */
    public static final float MODEL_RENDER_SCALE = (1.8f * STEVE_SCALE) / GEO_HEIGHT_BLOCKS;
    /** Forward offset from player while carried (blocks, local space). */
    public static final float CARRY_FORWARD = 0.28f;
    /**
     * Strafe offset while carried (blocks, local space). Positive = player's right.
     * Shifts her across the chest so FP sees a facial profile instead of the occiput.
     */
    public static final float CARRY_SIDEWAYS = 0.22f;
    /** Max distance (blocks) for player pickup / drop interactions. */
    public static final double PICKUP_RANGE = 4.0;
    public static final double PICKUP_RANGE_SQR = PICKUP_RANGE * PICKUP_RANGE;
    /** Max distance (blocks) for panel / bind / wander packets. */
    public static final double PANEL_RANGE = 64.0;
    public static final double PANEL_RANGE_SQR = PANEL_RANGE * PANEL_RANGE;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation JUMP = RawAnimation.begin().thenLoop("jump");
    static final EntityDataAccessor<Integer> SERIAL = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> AWAKENED = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> PERCEPTION = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> PERSONALITY = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> STARVING = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> INCAPACITATED = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> WANDER_STYLE = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> FOOD_LEVEL = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);

    private final SisterFoodData foodData = new SisterFoodData();
    private final MisakaHotSpringSoak hotSpringSoak = new MisakaHotSpringSoak();
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable UUID misakaUuid;
    /** Survives uuid-loss on reload so {@link MisakaSisterRosterSync#ensureRegistered} can rebind. */
    int pendingSerial;
    private long lastHandInteractGameTime = Long.MIN_VALUE;
    private @Nullable MisakaFollowGoal followGoal;
    private @Nullable MisakaNetworkWanderGoal networkWanderGoal;
    /** First tick after spawn/load must mirror roster once even if not dirty. */
    private boolean needsInitialRosterSync = true;
    /** True after incap hold was entered this downed stretch (skip repeat sanitize). */
    private boolean incapHoldLatched;
    private @Nullable BlockPos lastCoverageSamplePos;

    public MisakaSisterEntity(EntityType<? extends MisakaSisterEntity> type, Level level) {
        super(type, level);
        // PathfinderMob (not Animal) uses Mob despawn by default. Roster identity must outlive
        // player distance — otherwise discard leaves orphan roster rows (e.g. incap ghosts).
        setPersistenceRequired();
    }

    /**
     * Sisters are persistent NPCs tied to {@link MisakaSisterRoster}; never cull when far away.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("Movement", test -> {
            // Carried / downed: freeze movement clips; renderer applies static silhouette.
            if (isPassenger() && getVehicle() instanceof Player) {
                return PlayState.STOP;
            }
            if (isIncapacitated()) {
                return PlayState.STOP;
            }
            if (!onGround()) {
                return test.setAndContinue(JUMP);
            }
            if (test.isMoving()) {
                return test.setAndContinue(WALK);
            }
            return test.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SERIAL, 0);
        builder.define(AWAKENED, false);
        builder.define(PERCEPTION, 1);
        builder.define(PERSONALITY, MisakaPersonality.TIMID.ordinal());
        builder.define(STARVING, false);
        builder.define(INCAPACITATED, false);
        builder.define(WANDER_STYLE, WanderStyle.FREE_MOVE.ordinal());
        builder.define(FOOD_LEVEL, 20);
    }

    @Override
    protected void registerGoals() {
        // Vanilla hazard stack: float in fluids, surface for air, panic after env damage.
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new BreathAirGoal(this));
        // Powder-snow preempt + vanilla PanicGoal (lava/fire/cactus/freeze after hit).
        goalSelector.addGoal(1, new MisakaEscapeHazardGoal(this));
        // Vanilla has no land-exit after Float/BreathAir — swim to nearest dry stand.
        goalSelector.addGoal(1, new MisakaSwimAshoreGoal(this));
        goalSelector.addGoal(2, new MisakaUnawakenedStrollGoal(this));
        goalSelector.addGoal(3, new MisakaStarveGuardGoal(this));
        goalSelector.addGoal(4, new MisakaForageFoodGoal(this));
        goalSelector.addGoal(5, new MisakaCropGrazeGoal(this));
        goalSelector.addGoal(6, new MisakaSleepInBedGoal(this));
        goalSelector.addGoal(7, new MisakaPersonalityCombatGoal(this));
        goalSelector.addGoal(8, followGoal = new MisakaFollowGoal(this));
        goalSelector.addGoal(9, networkWanderGoal = new MisakaNetworkWanderGoal(this));
        goalSelector.addGoal(10, new MisakaSocialGoal(this));
        goalSelector.addGoal(11, new MisakaArcZapGoal(this));
        goalSelector.addGoal(12, new MisakaLivelyGoal(this));
        goalSelector.addGoal(13, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public boolean isNoAi() {
        return isIncapacitated() || (getVehicle() instanceof Player) || super.isNoAi();
    }

    /** Hard gate for Mob.serverAiStep in MC 26.2 (goals + navigation). */
    @Override
    public boolean isEffectiveAi() {
        if (isIncapacitated() || getVehicle() instanceof Player) {
            return false;
        }
        return super.isEffectiveAi();
    }

    /** Immobile also zeroes walk/jump input in LivingEntity.aiStep (MC 26.2). */
    @Override
    protected boolean isImmobile() {
        return isIncapacitated() || super.isImmobile();
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isIncapacitated() && !(getVehicle() instanceof Player)) {
            if (onGround()) {
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            // Allow falling only — no path / knockback glide while downed.
            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(travelVector);
    }

    @Override
    public void tick() {
        // Roster → entityData → NoAI must be applied BEFORE super.tick(), otherwise
        // Mob.serverAiStep runs goals/navigation for a full tick while still "able".
        if (!level().isClientSide()) {
            MisakaSisterRosterSync.ensureRegistered(this);
            MisakaSisterRosterSync.syncFromRosterIfDirty(this);
            if (rosterOrDataIncapacitated()) {
                applyIncapacitatedHold();
            } else {
                incapHoldLatched = false;
            }
        }

        super.tick();

        if (level().isClientSide()) {
            return;
        }

        // Re-assert motion hold after AI without re-sanitizing pose every tick.
        if (rosterOrDataIncapacitated()) {
            applyIncapacitatedHoldMotionOnly();
        }

        MisakaSisterRosterSync.updateLastKnownChunk(this);
        BlockPos here = blockPosition();
        if (!here.equals(lastCoverageSamplePos) || tickCount % 100 == 0) {
            lastCoverageSamplePos = here.immutable();
            MisakaSisterRosterSync.recheckNetworkCoverage(this);
        }
        tickSleepWake();
        if (isAwakened() && !isIncapacitated()) {
            int foodBefore = foodData.getFoodLevel();
            if (!isPassenger()) {
                foodData.tick(this);
            }
            foodData.tickFoodAndStarvation(this);
            if (foodBefore != foodData.getFoodLevel()
                    || (foodData.getFoodLevel() == 0) != isStarving()) {
                MisakaSisterRosterSync.syncStarvingToRoster(this);
            }
            hotSpringSoak.tick(this);
        }
        MisakaSisterRosterSync.tickAwakeWindowSpotting(this);
        syncFoodLevel();
    }

    boolean needsInitialRosterSync() {
        return needsInitialRosterSync;
    }

    void clearInitialRosterSync() {
        needsInitialRosterSync = false;
    }

    /**
     * Keep a downed sister planted: real NoAI flag + clear nav / motion.
     * Pose sanitize runs once when entering the hold.
     */
    public void applyIncapacitatedHold() {
        boolean entering = !incapHoldLatched;
        if (!entityData.get(INCAPACITATED)) {
            entityData.set(INCAPACITATED, true);
            entering = true;
        }
        applyIncapacitatedHoldMotionOnly();
        if (entering) {
            sanitizePoseAfterLoad();
            clearNearbyAttackers();
            refreshNametag();
            incapHoldLatched = true;
        }
    }

    private void applyIncapacitatedHoldMotionOnly() {
        setNoAi(true);
        setTarget(null);
        getNavigation().stop();
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
        jumping = false;
        clearFire();
        setTicksFrozen(0);
        if (!(getVehicle() instanceof Player)) {
            if (onGround()) {
                setDeltaMovement(Vec3.ZERO);
            } else {
                setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
            }
        }
    }

    /** Drop mob targets that already locked her before she went down. */
    private void clearNearbyAttackers() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var box = getBoundingBox().inflate(48.0);
        for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, box)) {
            if (mob.getTarget() == this) {
                mob.setTarget(null);
            }
        }
    }

    /**
     * Roster is authoritative on the server when linked; entityData alone can lag a tick
     * behind {@code incap=true} in SavedData (which is what {@code /misaka info} prints).
     */
    private boolean rosterOrDataIncapacitated() {
        if (entityData.get(INCAPACITATED)) {
            return true;
        }
        if (level().isClientSide() || misakaUuid == null || level().getServer() == null) {
            return false;
        }
        return MisakaSisterRoster.get(level().getServer())
                .get(misakaUuid)
                .map(record -> record.incapacitated)
                .orElse(false);
    }

    /**
     * Clear death flip / orphaned SLEEPING pose leftovers.
     * Call on load, incap transitions, and wake — not every tick.
     */
    public void sanitizePoseAfterLoad() {
        dead = false;
        deathTime = 0;
        if (getPose() == Pose.DYING) {
            setPose(Pose.STANDING);
        }
        if (getPose() == Pose.SLEEPING && !isSleeping()) {
            setPose(Pose.STANDING);
        }
        if (!(getVehicle() instanceof Player) && onGround() && Math.abs(getXRot()) > 60.0f) {
            setXRot(0.0f);
        }
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        // One-shot upright restore at the wake transition (server syncs pose to clients).
        setPose(Pose.STANDING);
        setXRot(0.0f);
        deathTime = 0;
        dead = false;
    }

    /** Called when roster incap flips false (tower recover). */
    public void clearIncapacitatedHold() {
        incapHoldLatched = false;
        sanitizePoseAfterLoad();
        // Only clear the synched NoAI bit if we are not still carried.
        if (!(getVehicle() instanceof Player)) {
            setNoAi(false);
        }
        refreshNametag();
    }

    private void tickSleepWake() {
        if (!isSleeping()) {
            return;
        }
        if (!MisakaDayTime.isNight(level())
                || getWanderStyle() == WanderStyle.WAITING
                || isStarving()
                || isIncapacitated()
                || isPassenger()) {
            stopSleeping();
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide()) {
            setPersistenceRequired();
            MisakaSisterRosterSync.ensureRegistered(this);
            org.academy.internal.server.misaka.MisakaLoadedSisterIndex.put(this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            org.academy.internal.server.misaka.MisakaLoadedSisterIndex.remove(this);
        }
        super.remove(reason);
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        if (!(vehicle instanceof Player player)) {
            return super.getVehicleAttachmentPoint(vehicle);
        }
        // positionRider: passengerPos = ridePos - offset.
        // Align Misaka's mid-torso to the player's arm/chest hold point so changing STEVE_SCALE
        // (and thus getBbHeight) keeps the hug height looking right.
        float holdY = player.getBbHeight() * 0.55f;
        float misakaTorso = getBbHeight() * 0.45f;
        float feetY = Mth.clamp(holdY - misakaTorso, player.getBbHeight() * 0.12f, player.getBbHeight() * 0.42f);
        // Local X = strafe (right+), local Z = forward — same basis as Entity#getInputVector.
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yawRad);
        float cos = Mth.cos(yawRad);
        double desiredX = player.getX() + CARRY_SIDEWAYS * cos - CARRY_FORWARD * sin;
        double desiredY = player.getY() + feetY;
        double desiredZ = player.getZ() + CARRY_FORWARD * cos + CARRY_SIDEWAYS * sin;
        Vec3 ride = player.getPassengerRidingPosition(this);
        return new Vec3(ride.x - desiredX, ride.y - desiredY, ride.z - desiredZ);
    }

    @Override
    public boolean canRide(Entity vehicle) {
        return vehicle instanceof Player;
    }

    /** Carried against the chest often clips into walls; do not treat that as in-wall. */
    @Override
    public boolean isInWall() {
        return !(getVehicle() instanceof Player) && super.isInWall();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (getVehicle() instanceof Player && source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        // Downed sisters cannot flee hazards; block non-player damage so rescue stays viable.
        if (isIncapacitated() && !(source.getEntity() instanceof Player)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean canFreeze() {
        return !isIncapacitated() && super.canFreeze();
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isStarving() && !isIncapacitated() && super.canBeSeenAsEnemy();
    }

    @Override
    public Component getName() {
        int serial = entityData.get(SERIAL);
        if (serial <= 0) {
            return super.getName();
        }
        Component base = Component.literal("御坂" + serial + "号");
        if (isIncapacitated()) {
            return base.copy()
                    .append(Component.translatable("entity.academy.misaka_sister.incap_suffix")
                            .withStyle(ChatFormatting.RED));
        }
        return base;
    }

    @Override
    public Component getDisplayName() {
        return getName();
    }

    /** Keep nametag in sync with roster serial (visible above head). */
    public void refreshNametag() {
        int serial = entityData.get(SERIAL);
        if (serial > 0) {
            setCustomName(getName());
            setCustomNameVisible(true);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SERIAL.equals(key) || INCAPACITATED.equals(key)) {
            refreshNametag();
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            // Relay tower/food/tablet/pet to server — client withoutItem alone never recovers.
            ItemStack held = player.getItemInHand(hand);
            if (shouldClientRelayHandInteract(held)) {
                org.misaka.MisakaNetworkClient.send(
                        new org.academy.internal.common.network.misaka.MisakaSisterHandInteractPacket(
                                getUUID(), hand));
            }
            return handledWithoutItemClient();
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // Dedupe EntityInteract + HandInteractPacket / Item#use in the same tick.
        long gameTime = level().getGameTime();
        if (gameTime == lastHandInteractGameTime) {
            return handledWithoutItem();
        }
        lastHandInteractGameTime = gameTime;
        MisakaSisterRosterSync.ensureRegistered(this);
        var record = rosterRecord().orElse(null);
        if (record == null) {
            return InteractionResult.PASS;
        }
        String name = serverPlayer.getGameProfile().name();
        ItemStack stack = player.getItemInHand(hand);

        boolean downed = record.incapacitated || entityData.get(INCAPACITATED);
        if (MisakaFoodTraits.isTower(stack) && downed) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_RECOVER)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return handledWithoutItem();
            }
            var server = level().getServer();
            MisakaSisterRoster.get(server).modify(record.misakaUuid, r -> r.incapacitated = false);
            record.incapacitated = false;
            setHealth(Math.min(getMaxHealth(), Math.max(getHealth(), getMaxHealth() * 0.5f)));
            entityData.set(INCAPACITATED, false);
            clearIncapacitatedHold();
            stack.shrink(1);
            MisakaSisterRosterSync.syncFromRecord(this, record);
            MisakaInteractionFeedback.recovered(this, serverPlayer);
            InteractionGate.scheduleAfterInteract(server, record, name);
            return InteractionResult.CONSUME;
        }

        if (downed) {
            // Panel stays available so players can see why she is unresponsive.
            if (stack.is(org.academy.internal.common.world.item.Items.ABILITY_CONTROL_TABLET.get())) {
                if (!InteractionGate.allow(record, name, InteractionGate.Intent.PANEL)) {
                    MisakaInteractionFeedback.refuse(this, serverPlayer);
                    return handledWithoutItem();
                }
                MisakaPanelSupport.sendPanel(serverPlayer, this);
                return handledWithoutItem();
            }
            MisakaInteractionFeedback.incapacitated(this, serverPlayer);
            return handledWithoutItem();
        }

        if (MisakaFoodTraits.isPromax(stack)) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_PROMAX)) {
                if (record.promaxUsed) {
                    MisakaInteractionFeedback.promaxUsed(this, serverPlayer);
                } else {
                    MisakaInteractionFeedback.refuse(this, serverPlayer);
                }
                return handledWithoutItem();
            }
            if (PerceptionService.tryBreakLimit(level().getServer(), record)) {
                stack.shrink(1);
                MisakaSisterRosterSync.syncFromRecord(this, record);
                MisakaSisterRoster.get(level().getServer()).setDirty();
                MisakaInteractionFeedback.promaxOk(this, serverPlayer);
                InteractionGate.scheduleAfterInteract(level().getServer(), record, name);
                return InteractionResult.CONSUME;
            }
            MisakaInteractionFeedback.reconstructionBlocked(this, serverPlayer);
            return handledWithoutItem();
        }

        if (MisakaFoodTraits.isTower(stack) && !record.awakened) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_TOWER_AWAKEN)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return handledWithoutItem();
            }
            PerceptionService.awakenWithTower(record, name, level().getGameTime(), level().getServer());
            stack.shrink(1);
            MisakaSisterRosterSync.syncFromRecord(this, record);
            MisakaSisterRoster.get(level().getServer()).setDirty();
            MisakaInteractionFeedback.awaken(this, serverPlayer);
            InteractionGate.scheduleAfterInteract(level().getServer(), record, name);
            return InteractionResult.CONSUME;
        }

        if (MisakaFoodTraits.isTower(stack) && record.awakened) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_FOOD)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return handledWithoutItem();
            }
            if (!foodData.needsFood()) {
                MisakaInteractionFeedback.full(this, serverPlayer);
                return handledWithoutItem();
            }
            ItemStack fed = stack.copyWithCount(1);
            MisakaFoodTraits.applyTo(this, stack);
            foodData.onHandFed(this, serverPlayer, stack);
            stack.shrink(1);
            MisakaSisterRoster.get(level().getServer()).setDirty();
            MisakaInteractionFeedback.fed(this, serverPlayer, fed, MisakaFoodTraits.isFavorite(fed));
            InteractionGate.scheduleAfterInteract(level().getServer(), record, name);
            return InteractionResult.CONSUME;
        }

        boolean isCake = stack.is(Items.CAKE);
        boolean isFood = stack.get(DataComponents.FOOD) != null;
        if (isCake || isFood) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_FOOD)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return handledWithoutItem();
            }
            if (!foodData.needsFood()) {
                MisakaInteractionFeedback.full(this, serverPlayer);
                return handledWithoutItem();
            }
            ItemStack fed = stack.copyWithCount(1);
            MisakaFoodTraits.applyTo(this, stack);
            foodData.onHandFed(this, serverPlayer, stack);
            stack.shrink(1);
            MisakaSisterRoster.get(level().getServer()).setDirty();
            MisakaInteractionFeedback.fed(this, serverPlayer, fed, MisakaFoodTraits.isFavorite(fed));
            InteractionGate.scheduleAfterInteract(level().getServer(), record, name);
            return InteractionResult.CONSUME;
        }

        // Tablet: open panel only; privilege/CP deferred so max-favor rebuild cannot block UI.
        if (stack.is(org.academy.internal.common.world.item.Items.ABILITY_CONTROL_TABLET.get())) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.PANEL)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return handledWithoutItem();
            }
            MisakaPanelSupport.sendPanel(serverPlayer, this);
            InteractionGate.scheduleAfterInteract(level().getServer(), record, name);
            return handledWithoutItem();
        }

        if (stack.isEmpty()) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.PET)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return handledWithoutItem();
            }
            boolean firstToday = !record.dailyPet;
            if (firstToday) {
                record.dailyPet = true;
                PerceptionService.gain(level().getServer(), record, 1);
                MisakaSisterRoster.get(level().getServer()).setDirty();
            }
            MisakaSisterRosterSync.syncFromRecord(this, record);
            if (firstToday) {
                MisakaInteractionFeedback.pet(this, serverPlayer);
            } else {
                MisakaInteractionFeedback.petAlready(this, serverPlayer);
            }
            InteractionGate.scheduleAfterInteract(level().getServer(), record, name);
            return handledWithoutItem();
        }

        return InteractionResult.PASS;
    }

    /**
     * Re-entry from Item#use when vanilla EntityInteract missed the sister but our aim ray
     * still hits her (common when privilege follow / carry puts the AABB on the crosshair).
     */
    public InteractionResult forceMobInteract(Player player, InteractionHand hand) {
        return mobInteract(player, hand);
    }

    /** Ends the interact pipeline without falling through to Item#use (food / tablet). */
    private static InteractionResult handledWithoutItem() {
        return InteractionResult.SUCCESS_SERVER.withoutItem();
    }

    /**
     * Client-side: same consumable result without claiming an item transform, so the client
     * does not continue into {@code Item#use} after a predicted entity hit.
     */
    private static InteractionResult handledWithoutItemClient() {
        return InteractionResult.SUCCESS.withoutItem();
    }

    private static boolean shouldClientRelayHandInteract(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (MisakaFoodTraits.isTower(stack) || MisakaFoodTraits.isPromax(stack)) {
            return true;
        }
        if (stack.is(org.academy.internal.common.world.item.Items.ABILITY_CONTROL_TABLET.get())) {
            return true;
        }
        return stack.is(Items.CAKE) || stack.get(DataComponents.FOOD) != null;
    }

    /** Aimed living Misaka sister within entity interaction range, or empty. */
    public static java.util.Optional<MisakaSisterEntity> findAimedSister(Player player) {
        double range = player.entityInteractionRange();
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 to = from.add(look.scale(range));
        var box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player,
                from,
                to,
                box,
                entity -> entity instanceof MisakaSisterEntity && entity.isAlive(),
                range * range
        );
        if (hit != null && hit.getEntity() instanceof MisakaSisterEntity sister) {
            return java.util.Optional.of(sister);
        }
        return java.util.Optional.empty();
    }

    public static boolean isPlayerAimingAtSister(Player player) {
        return findAimedSister(player).isPresent();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("academy_misaka_uuid").ifPresent(value -> {
            try {
                misakaUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
            }
        });
        pendingSerial = input.getIntOr("academy_misaka_serial", 0);
        foodData.read(input);
        syncFoodLevel();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (misakaUuid != null) {
            output.putString("academy_misaka_uuid", misakaUuid.toString());
        }
        int serial = entityData.get(SERIAL);
        if (serial <= 0) {
            serial = pendingSerial;
        }
        if (serial > 0) {
            output.putInt("academy_misaka_serial", serial);
        }
        foodData.write(output);
    }

    public SisterFoodData getFoodData() {
        return foodData;
    }

    public int getFoodLevel() {
        return foodData.getFoodLevel();
    }

    public void syncFoodLevel() {
        entityData.set(FOOD_LEVEL, foodData.getFoodLevel());
    }

    public boolean isAwakened() {
        return entityData.get(AWAKENED);
    }

    public boolean isStarving() {
        return entityData.get(STARVING);
    }

    public boolean isIncapacitated() {
        if (entityData.get(INCAPACITATED)) {
            return true;
        }
        // Server: trust roster so AI gates match /misaka info before entityData catches up.
        if (!level().isClientSide() && misakaUuid != null && level().getServer() != null) {
            return MisakaSisterRoster.get(level().getServer())
                    .get(misakaUuid)
                    .map(record -> record.incapacitated)
                    .orElse(false);
        }
        return false;
    }

    public MisakaPersonality getPersonality() {
        return MisakaPersonality.fromOrdinal(entityData.get(PERSONALITY));
    }

    public WanderStyle getWanderStyle() {
        return WanderStyle.fromOrdinal(entityData.get(WANDER_STYLE));
    }

    public @Nullable UUID getMisakaUuid() {
        return misakaUuid;
    }

    /** Synched roster serial (0 if not yet bound). */
    public int getSerial() {
        int serial = entityData.get(SERIAL);
        return serial > 0 ? serial : pendingSerial;
    }

    public void bindToRecord(MisakaSisterRecord record) {
        MisakaSisterRosterSync.bindToRecord(this, record);
    }

    public void setWanderStyle(WanderStyle style) {
        MisakaSisterRosterSync.setWanderStyle(this, style);
    }

    public Optional<MisakaSisterRecord> rosterRecord() {
        if (misakaUuid == null || level().getServer() == null) {
            return Optional.empty();
        }
        return MisakaSisterRoster.get(level().getServer()).get(misakaUuid);
    }

    public @Nullable MisakaFollowGoal followGoal() {
        return followGoal;
    }

    public @Nullable MisakaNetworkWanderGoal networkWanderGoal() {
        return networkWanderGoal;
    }

    public void onSelfFed(ItemStack stack) {
        foodData.onSelfFed(this, stack);
    }

    public void tickFoodAndStarvation() {
        foodData.tickFoodAndStarvation(this);
    }
}
