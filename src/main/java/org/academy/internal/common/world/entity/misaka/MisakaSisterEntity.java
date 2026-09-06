package org.academy.internal.common.world.entity.misaka;

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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.internal.common.world.entity.misaka.ai.MisakaArcZapGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaLivelyGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaCropGrazeGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaFollowGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaForageFoodGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaNetworkWanderGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaPersonalityCombatGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaSleepInBedGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaSocialGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaStarveGuardGoal;
import org.academy.internal.common.world.entity.misaka.ai.MisakaUnawakenedStrollGoal;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorRuleRegistry;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.misaka.MisakaComputeIndex;
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

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation JUMP = RawAnimation.begin().thenLoop("jump");
    private static final EntityDataAccessor<Integer> SERIAL = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AWAKENED = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PERCEPTION = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PERSONALITY = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STARVING = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> WANDER_STYLE = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FOOD_LEVEL = SynchedEntityData.defineId(
            MisakaSisterEntity.class, EntityDataSerializers.INT);

    private final SisterFoodData foodData = new SisterFoodData();
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private @Nullable UUID misakaUuid;
    private @Nullable MisakaFollowGoal followGoal;
    private @Nullable MisakaNetworkWanderGoal networkWanderGoal;

    public MisakaSisterEntity(EntityType<? extends MisakaSisterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("Movement", test -> {
            // Carried by player: freeze movement clips; renderer applies hug silhouette.
            if (isPassenger() && getVehicle() instanceof Player) {
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
        builder.define(WANDER_STYLE, WanderStyle.FREE_MOVE.ordinal());
        builder.define(FOOD_LEVEL, 20);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MisakaUnawakenedStrollGoal(this));
        goalSelector.addGoal(2, new MisakaStarveGuardGoal(this));
        goalSelector.addGoal(3, new MisakaForageFoodGoal(this));
        goalSelector.addGoal(4, new MisakaCropGrazeGoal(this));
        goalSelector.addGoal(5, new MisakaSleepInBedGoal(this));
        goalSelector.addGoal(6, new MisakaPersonalityCombatGoal(this));
        goalSelector.addGoal(7, followGoal = new MisakaFollowGoal(this));
        goalSelector.addGoal(8, networkWanderGoal = new MisakaNetworkWanderGoal(this));
        goalSelector.addGoal(9, new MisakaSocialGoal(this));
        goalSelector.addGoal(10, new MisakaArcZapGoal(this));
        goalSelector.addGoal(11, new MisakaLivelyGoal(this));
        goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        ensureRegistered();
        syncFromRoster();
        updateLastKnownChunk();
        if (tickCount % 20 == 0) {
            recheckNetworkCoverage();
        }
        tickSleepWake();
        if (isAwakened()) {
            if (!isPassenger()) {
                foodData.tick(this);
            }
            tickFoodAndStarvation();
            syncStarvingToRoster();
        }
        tickAwakeWindowSpotting();
        syncFoodLevel();
    }

    private void tickSleepWake() {
        if (!isSleeping()) {
            return;
        }
        if (!MisakaDayTime.isNight(level())
                || getWanderStyle() == WanderStyle.WAITING
                || isStarving()
                || isPassenger()) {
            stopSleeping();
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide()) {
            ensureRegistered();
        }
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
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isStarving() && super.canBeSeenAsEnemy();
    }

    @Override
    public Component getDisplayName() {
        int serial = entityData.get(SERIAL);
        if (serial <= 0) {
            return super.getDisplayName();
        }
        return Component.literal("御坂" + serial + "号");
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        var record = rosterRecord().orElse(null);
        if (record == null) {
            return InteractionResult.PASS;
        }
        String name = serverPlayer.getGameProfile().name();
        ItemStack stack = player.getItemInHand(hand);

        if (MisakaFoodTraits.isPromax(stack)) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_PROMAX)) {
                if (record.promaxUsed) {
                    MisakaInteractionFeedback.promaxUsed(this, serverPlayer);
                } else {
                    MisakaInteractionFeedback.refuse(this, serverPlayer);
                }
                return InteractionResult.FAIL;
            }
            if (PerceptionService.tryBreakLimit(level().getServer(), record)) {
                stack.shrink(1);
                InteractionGate.touchBenevolent(record, name, level().getServer());
                syncFromRecord(record);
                MisakaSisterRoster.get(level().getServer()).setDirty();
                MisakaComputeContribution.refreshCpForRecord(
                        level().getServer(), record);
                MisakaInteractionFeedback.promaxOk(this, serverPlayer);
                return InteractionResult.CONSUME;
            }
            MisakaInteractionFeedback.reconstructionBlocked(this, serverPlayer);
            return InteractionResult.FAIL;
        }

        if (MisakaFoodTraits.isTower(stack) && !record.awakened) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_TOWER_AWAKEN)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return InteractionResult.FAIL;
            }
            PerceptionService.awakenWithTower(record, name, level().getGameTime(), level().getServer());
            InteractionGate.touchBenevolent(record, name, level().getServer());
            syncFromRecord(record);
            stack.shrink(1);
            MisakaSisterRoster.get(level().getServer()).setDirty();
            MisakaComputeContribution.refreshCpForRecord(
                    level().getServer(), record);
            MisakaInteractionFeedback.awaken(this, serverPlayer);
            return InteractionResult.CONSUME;
        }

        if (MisakaFoodTraits.isTower(stack) && record.awakened) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_FOOD)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return InteractionResult.FAIL;
            }
            if (!foodData.needsFood()) {
                MisakaInteractionFeedback.full(this, serverPlayer);
                return InteractionResult.FAIL;
            }
            ItemStack fed = stack.copyWithCount(1);
            MisakaFoodTraits.applyTo(this, stack);
            onHandFed(serverPlayer, stack);
            stack.shrink(1);
            InteractionGate.touchBenevolent(record, name, level().getServer());
            MisakaSisterRoster.get(level().getServer()).setDirty();
            MisakaComputeContribution.refreshCpForRecord(
                    level().getServer(), record);
            MisakaInteractionFeedback.fed(this, serverPlayer, fed, MisakaFoodTraits.isFavorite(fed));
            return InteractionResult.CONSUME;
        }

        boolean isCake = stack.is(Items.CAKE);
        boolean isFood = stack.get(DataComponents.FOOD) != null;
        if (isCake || isFood) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.FEED_FOOD)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return InteractionResult.FAIL;
            }
            if (!foodData.needsFood()) {
                MisakaInteractionFeedback.full(this, serverPlayer);
                return InteractionResult.FAIL;
            }
            ItemStack fed = stack.copyWithCount(1);
            MisakaFoodTraits.applyTo(this, stack);
            onHandFed(serverPlayer, stack);
            stack.shrink(1);
            InteractionGate.touchBenevolent(record, name, level().getServer());
            MisakaSisterRoster.get(level().getServer()).setDirty();
            MisakaComputeContribution.refreshCpForRecord(
                    level().getServer(), record);
            MisakaInteractionFeedback.fed(this, serverPlayer, fed, MisakaFoodTraits.isFavorite(fed));
            return InteractionResult.CONSUME;
        }

        if (stack.isEmpty()) {
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.PET)) {
                MisakaInteractionFeedback.refuse(this, serverPlayer);
                return InteractionResult.FAIL;
            }
            if (!record.dailyPet) {
                record.dailyPet = true;
                PerceptionService.gain(level().getServer(), record, 1);
                MisakaSisterRoster.get(level().getServer()).setDirty();
            }
            InteractionGate.touchBenevolent(record, name, level().getServer());
            syncFromRecord(record);
            MisakaComputeContribution.refreshCpForRecord(
                    level().getServer(), record);
            MisakaInteractionFeedback.pet(this, serverPlayer);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
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
        foodData.read(input);
        syncFoodLevel();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (misakaUuid != null) {
            output.putString("academy_misaka_uuid", misakaUuid.toString());
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

    public MisakaPersonality getPersonality() {
        return MisakaPersonality.fromOrdinal(entityData.get(PERSONALITY));
    }

    public WanderStyle getWanderStyle() {
        return WanderStyle.fromOrdinal(entityData.get(WANDER_STYLE));
    }

    public @Nullable UUID getMisakaUuid() {
        return misakaUuid;
    }

    public void bindToRecord(MisakaSisterRecord record) {
        misakaUuid = record.misakaUuid;
        syncFromRecord(record);
    }

    public void setWanderStyle(WanderStyle style) {
        rosterRecord().ifPresent(record -> {
            record.wanderStyle = style;
            entityData.set(WANDER_STYLE, style.ordinal());
            style.apply(this);
            MisakaSisterRoster.get(level().getServer()).setDirty();
        });
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
        rosterRecord().ifPresent(record -> {
            if (MisakaFoodTraits.isFavorite(stack)) {
                PerceptionService.gain(level().getServer(), record, 2);
            }
            MisakaSisterRoster.get(level().getServer()).setDirty();
            syncFromRecord(record);
        });
    }

    public void tickFoodAndStarvation() {
        if (foodData.getFoodLevel() == 0 && tickCount % 80 == 0) {
            if (getHealth() > 1.0f) {
                hurt(damageSources().starve(), 1.0f);
            }
            if (getHealth() < 1.0f) {
                setHealth(1.0f);
            }
        }
    }

    private void onHandFed(ServerPlayer player, ItemStack stack) {
        rosterRecord().ifPresent(record -> {
            int sourceGain = MisakaFoodTraits.isTower(stack) ? 5 : 2;
            PerceptionService.gain(level().getServer(), record, sourceGain);
            if (MisakaFoodTraits.isFavorite(stack)) {
                PerceptionService.gain(level().getServer(), record, 2);
                FavorRuleRegistry.trigger(FavorContext.feedFavorite(record, misakaUuid, player));
            }
            if (stack.is(net.minecraft.world.item.Items.CAKE)) {
                FavorRuleRegistry.trigger(FavorContext.anniversaryCake(record, misakaUuid, player));
            }
            MisakaSisterRoster.get(level().getServer()).setDirty();
            syncFromRecord(record);
            syncFoodLevel();
        });
    }

    private void ensureRegistered() {
        if (misakaUuid != null || level().getServer() == null) {
            return;
        }
        int day = MisakaDayTime.dayIndex(level());
        var registered = MisakaSisterRoster.get(level().getServer()).tryRegisterRescued(getRandom(), day);
        if (registered.isEmpty()) {
            // Serial pool exhausted — forbid creating more sisters.
            discard();
            return;
        }
        var record = registered.get();
        misakaUuid = record.misakaUuid;
        syncFromRecord(record);
    }

    private void syncFromRoster() {
        rosterRecord().ifPresent(this::syncFromRecord);
    }

    private void syncFromRecord(MisakaSisterRecord record) {
        entityData.set(SERIAL, record.serial);
        entityData.set(AWAKENED, record.awakened);
        entityData.set(PERCEPTION, record.perception);
        entityData.set(PERSONALITY, record.personality.ordinal());
        entityData.set(STARVING, record.starving);
        entityData.set(WANDER_STYLE, record.wanderStyle.ordinal());
        if (!record.awakened) {
            foodData.setFoodLevel(20);
            foodData.setSaturation(5.0f);
        }
        syncFoodLevel();
    }

    private void syncStarvingToRoster() {
        if (misakaUuid == null || level().getServer() == null) {
            return;
        }
        boolean starvingNow = foodData.getFoodLevel() == 0;
        entityData.set(STARVING, starvingNow);
        var server = level().getServer();
        var roster = MisakaSisterRoster.get(server);
        var existing = roster.get(misakaUuid);
        if (existing.isEmpty() || existing.get().starving == starvingNow) {
            return;
        }
        roster.modify(misakaUuid, record -> record.starving = starvingNow);
        existing = roster.get(misakaUuid);
        existing.ifPresent(record -> MisakaComputeContribution.refreshCpForRecord(server, record));
    }

    private void updateLastKnownChunk() {
        if (misakaUuid == null || level().getServer() == null) {
            return;
        }
        var chunk = chunkPosition();
        rosterRecord().ifPresent(record -> {
            if (chunk.equals(record.lastKnownChunk)) {
                return;
            }
            record.lastKnownChunk = chunk;
            record.lastKnownDimension = level().dimension();
            MisakaSisterRoster.get(level().getServer()).setDirty();
            recheckNetworkCoverage(record);
        });
    }

    private void recheckNetworkCoverage() {
        rosterRecord().ifPresent(this::recheckNetworkCoverage);
    }

    private void recheckNetworkCoverage(MisakaSisterRecord record) {
        var server = level().getServer();
        if (server == null) {
            return;
        }
        if (!record.awakened || record.networkNodePos == null) {
            return;
        }
        // Topology resolve uses overworld wireless data; coverage sample uses this entity's level.
        var overworld = server.overworld();
        var networkId = MisakaNAT.get().resolveNetworkId(overworld, record.networkNodePos);
        boolean nowIn = MisakaNAT.get().canUseMisakaService(
                (ServerLevel) level(),
                networkId,
                blockPosition()
        );
        Boolean was = MisakaComputeIndex.get().lastCoverageContributing(record.misakaUuid);
        if (was == null) {
            MisakaComputeIndex.get().seedCoverageContributing(record.misakaUuid, nowIn);
            return;
        }
        if (was == nowIn) {
            return;
        }
        MisakaComputeIndex.get().adjustCoverageContribution(server, record, was, nowIn);
    }

    private void tickAwakeWindowSpotting() {
        rosterRecord().ifPresent(record -> {
            if (!record.awakened || level().getGameTime() > record.awakeWindowEndGameTime) {
                return;
            }
            var server = level().getServer();
            for (ServerPlayer player : ((ServerLevel) level()).getPlayers(
                    player -> player.hasLineOfSight(this) && distanceToSqr(player) <= 16.0 * 16.0)) {
                String name = player.getGameProfile().name();
                if (!record.awakeSpottedNames.add(name)) {
                    continue;
                }
                var component = org.academy.internal.common.world.entity.misaka.favor.FavorService
                        .resolveLanComponent((ServerLevel) level(), record, MisakaSisterRoster.get(server).all());
                for (var member : component) {
                    member.awakeSpottedNames.add(name);
                }
                org.academy.internal.common.world.entity.misaka.favor.FavorService
                        .modifyFavorLan(server, record, name, 1);
                MisakaSisterRoster.get(server).setDirty();
            }
        });
    }

    public static final class SisterFoodData {
        private int foodLevel = 20;
        private float saturationLevel = 5.0f;
        private int foodTickTimer;
        private int starveTickTimer;

        public int getFoodLevel() {
            return foodLevel;
        }

        public void setFoodLevel(int foodLevel) {
            this.foodLevel = Mth.clamp(foodLevel, 0, 20);
        }

        public void setSaturation(float saturationLevel) {
            this.saturationLevel = Mth.clamp(saturationLevel, 0.0f, foodLevel);
        }

        public boolean needsFood() {
            return foodLevel < 20;
        }

        /** Cake / crop-style: saturation from nutrition × modifier × 2 (vanilla FoodData.eat(int, float)). */
        public void eat(int nutrition, float saturationModifier) {
            eatFood(nutrition, nutrition * saturationModifier * 2.0f);
        }

        /** Registry food: saturation value already absolute (vanilla FoodData.eat(FoodProperties)). */
        public void eatFood(int nutrition, float saturation) {
            foodLevel = Math.min(20, foodLevel + nutrition);
            saturationLevel = Math.min(foodLevel, saturationLevel + saturation);
        }

        public void tick(MisakaSisterEntity entity) {
            foodTickTimer++;
            if (foodTickTimer >= 80) {
                foodTickTimer = 0;
                if (foodLevel >= 18 && saturationLevel > 0.0f) {
                    if (entity.getHealth() < entity.getMaxHealth()) {
                        entity.heal(1.0f);
                    }
                    saturationLevel = Math.max(0.0f, saturationLevel - 1.0f);
                } else if (foodLevel > 0) {
                    if (saturationLevel > 0.0f) {
                        saturationLevel = Math.max(0.0f, saturationLevel - 1.0f);
                    } else if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
                        foodLevel--;
                    }
                }
            }
        }

        public void read(ValueInput input) {
            foodLevel = input.getIntOr("academy_food_level", 20);
            saturationLevel = input.getFloatOr("academy_food_saturation", 5.0f);
        }

        public void write(ValueOutput output) {
            output.putInt("academy_food_level", foodLevel);
            output.putFloat("academy_food_saturation", saturationLevel);
        }
    }
}
