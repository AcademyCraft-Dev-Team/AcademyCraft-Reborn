package org.academy.internal.common.world.entity.misaka;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Cosmetic orbiting / launching / crashing Misaka relay satellite.
 * Coverage authority is {@link MisakaRelayRegistry}.
 */
public final class RelaySatelliteEntity extends RenderOnlyEntity {
    public static final float ORBIT_RADIUS = 48.0f;
    /** Provisional: takeoff → scheduled orbit insertion lasts one game minute. */
    public static final int LAUNCH_DURATION_TICKS = 20 * 60;

    private static final EntityDataAccessor<Boolean> CRASHING =
            SynchedEntityData.defineId(RelaySatelliteEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LAUNCHING =
            SynchedEntityData.defineId(RelaySatelliteEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LAUNCH_AGE =
            SynchedEntityData.defineId(RelaySatelliteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HYPER =
            SynchedEntityData.defineId(RelaySatelliteEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3fc> ORBIT_ANCHOR =
            SynchedEntityData.defineId(RelaySatelliteEntity.class, EntityDataSerializers.VECTOR3);
    private static final double ORBIT_SPEED = 0.01;
    private static final double METEOR_MAX_SPEED = 3.5;
    private static final float DEFAULT_CRASH_EXPLOSION_POWER = 5.0f;

    private @Nullable UUID satelliteId;
    private double orbitAngle;
    private int launchAge;
    private double launchStartX;
    private double launchStartY;
    private double launchStartZ;

    public RelaySatelliteEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public RelaySatelliteEntity(Level level) {
        this(EntityTypes.RELAY_SATELLITE.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CRASHING, false);
        builder.define(LAUNCHING, false);
        builder.define(LAUNCH_AGE, 0);
        builder.define(HYPER, false);
        builder.define(ORBIT_ANCHOR, new Vector3f(0.0f, 0.0f, 0.0f));
    }

    public void setSatelliteId(UUID id) {
        this.satelliteId = id;
    }

    public @Nullable UUID getSatelliteId() {
        return satelliteId;
    }

    public void setOrbitAnchor(double x, double y, double z) {
        entityData.set(ORBIT_ANCHOR, new Vector3f((float) x, (float) y, (float) z));
    }

    public Vector3fc getOrbitAnchor() {
        return entityData.get(ORBIT_ANCHOR);
    }

    public void setHyper(boolean hyper) {
        entityData.set(HYPER, hyper);
    }

    public boolean isHyper() {
        return entityData.get(HYPER);
    }

    public boolean isCrashing() {
        return entityData.get(CRASHING);
    }

    public boolean isLaunching() {
        return entityData.get(LAUNCHING);
    }

    public float getLaunchProgress(float partialTick) {
        if (!isLaunching()) {
            return 1.0f;
        }
        return Mth.clamp((entityData.get(LAUNCH_AGE) + partialTick) / LAUNCH_DURATION_TICKS, 0.0f, 1.0f);
    }

    public void beginLaunch() {
        entityData.set(LAUNCHING, true);
        entityData.set(CRASHING, false);
        noPhysics = true;
        setNoGravity(true);
        launchAge = 0;
        entityData.set(LAUNCH_AGE, 0);
        launchStartX = getX();
        launchStartY = getY();
        launchStartZ = getZ();
        setDeltaMovement(new Vec3(0.0, 0.35, 0.0));
    }

    public void beginCrash() {
        entityData.set(CRASHING, true);
        entityData.set(LAUNCHING, false);
        noPhysics = false;
        setNoGravity(false);
        var yaw = random.nextFloat() * Mth.TWO_PI;
        var horizontal = 0.45 + random.nextDouble() * 0.55;
        setDeltaMovement(new Vec3(
                Mth.cos(yaw) * horizontal,
                -1.35 - random.nextDouble() * 0.65,
                Mth.sin(yaw) * horizontal
        ));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isCrashing()) {
                spawnMeteorTrailParticles();
            } else if (isLaunching()) {
                spawnLaunchTrailParticles();
            }
            return;
        }
        if (isCrashing()) {
            tickCrash();
            return;
        }
        if (isLaunching()) {
            tickLaunch();
            return;
        }
        var anchor = getOrbitAnchor();
        orbitAngle += ORBIT_SPEED;
        double x = anchor.x() + Mth.cos((float) orbitAngle) * ORBIT_RADIUS;
        double z = anchor.z() + Mth.sin((float) orbitAngle) * ORBIT_RADIUS;
        setPos(x, anchor.y(), z);
        setYRot((float) (Mth.wrapDegrees(orbitAngle * Mth.RAD_TO_DEG)));
    }

    private void tickLaunch() {
        launchAge++;
        if (launchAge % 5 == 0 || launchAge >= LAUNCH_DURATION_TICKS) {
            entityData.set(LAUNCH_AGE, launchAge);
        }
        var anchor = getOrbitAnchor();
        var end = new Vec3(anchor.x() + ORBIT_RADIUS, anchor.y(), anchor.z());
        if (launchAge >= LAUNCH_DURATION_TICKS) {
            setPos(end.x, end.y, end.z);
            setDeltaMovement(Vec3.ZERO);
            finishLaunch();
            return;
        }

        float t = launchAge / (float) LAUNCH_DURATION_TICKS;
        // Smoothstep: quicker climb early, gentle insertion into orbit.
        float eased = t * t * (3.0f - 2.0f * t);
        // Quadratic Bezier: rise above start first, then curve into the orbit slot.
        var control = new Vec3(launchStartX, end.y, launchStartZ);
        double u = 1.0 - eased;
        double x = u * u * launchStartX + 2.0 * u * eased * control.x + eased * eased * end.x;
        double y = u * u * launchStartY + 2.0 * u * eased * control.y + eased * eased * end.y;
        double z = u * u * launchStartZ + 2.0 * u * eased * control.z + eased * eased * end.z;

        var prevX = getX();
        var prevY = getY();
        var prevZ = getZ();
        setPos(x, y, z);
        setDeltaMovement(x - prevX, y - prevY, z - prevZ);
        setYRot((float) (Mth.atan2(x - prevX, z - prevZ) * Mth.RAD_TO_DEG));

        if (level() instanceof ServerLevel serverLevel && tickCount % 3 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    getX(), getY() - 0.4, getZ(),
                    2, 0.1, 0.06, 0.1, 0.008
            );
            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    getX(), getY() - 0.15, getZ(),
                    3, 0.08, 0.08, 0.08, 0.015
            );
        }
    }

    private void finishLaunch() {
        entityData.set(LAUNCHING, false);
        entityData.set(LAUNCH_AGE, LAUNCH_DURATION_TICKS);
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel serverLevel && satelliteId != null) {
            MisakaRelayRegistry.get(serverLevel.getServer())
                    .completeLaunch(serverLevel.getServer(), satelliteId);
        }
    }

    private void tickCrash() {
        var motion = getDeltaMovement().add(0.0, -0.12, 0.0);
        var speed = motion.length();
        if (speed > 1.0e-4 && speed < METEOR_MAX_SPEED) {
            motion = motion.scale(Math.min(1.06, (speed + 0.1) / speed));
        }
        if (motion.y < -METEOR_MAX_SPEED) {
            motion = new Vec3(motion.x, -METEOR_MAX_SPEED, motion.z);
        }
        setDeltaMovement(motion);

        var from = position();
        var to = from.add(motion);
        var hit = level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this
        ));
        if (hit.getType() == HitResult.Type.BLOCK) {
            var impact = hit.getLocation();
            setPos(impact.x, impact.y, impact.z);
            finishCrash();
            return;
        }

        move(MoverType.SELF, motion);
        if (level() instanceof ServerLevel serverLevel && tickCount % 2 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    getX(), getY(), getZ(),
                    4, 0.18, 0.18, 0.18, 0.03
            );
            serverLevel.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    getX(), getY(), getZ(),
                    2, 0.12, 0.12, 0.12, 0.01
            );
        }
        if (onGround() || verticalCollision || getY() < level().dimensionType().minY() + 1) {
            finishCrash();
        }
    }

    private void spawnLaunchTrailParticles() {
        var motion = getDeltaMovement();
        var back = motion.lengthSqr() > 1.0e-8
                ? motion.normalize().scale(-0.35)
                : new Vec3(0.0, -0.35, 0.0);
        for (var i = 0; i < 4; i++) {
            var ox = (random.nextDouble() - 0.5) * 0.3;
            var oy = (random.nextDouble() - 0.5) * 0.2;
            var oz = (random.nextDouble() - 0.5) * 0.3;
            level().addParticle(
                    ParticleTypes.FLAME,
                    getX() + ox, getY() + oy - 0.2, getZ() + oz,
                    back.x + ox * 0.02, back.y, back.z + oz * 0.02
            );
            level().addParticle(
                    ParticleTypes.CLOUD,
                    getX() + ox, getY() + oy - 0.3, getZ() + oz,
                    back.x * 0.4, back.y * 0.4, back.z * 0.4
            );
        }
        if ((tickCount & 1) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0, 0.04, 0.0);
        }
    }

    private void spawnMeteorTrailParticles() {
        var motion = getDeltaMovement();
        var back = motion.lengthSqr() > 1.0e-6
                ? motion.normalize().scale(-0.35)
                : new Vec3(0.0, 0.35, 0.0);
        for (var i = 0; i < 5; i++) {
            var ox = (random.nextDouble() - 0.5) * 0.45;
            var oy = (random.nextDouble() - 0.5) * 0.45;
            var oz = (random.nextDouble() - 0.5) * 0.45;
            level().addParticle(
                    ParticleTypes.FLAME,
                    getX() + ox, getY() + oy, getZ() + oz,
                    back.x + ox * 0.04, back.y, back.z + oz * 0.04
            );
            level().addParticle(
                    ParticleTypes.SMOKE,
                    getX() + ox, getY() + oy, getZ() + oz,
                    back.x * 0.6, back.y * 0.6, back.z * 0.6
            );
        }
        if ((tickCount & 1) == 0) {
            level().addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    private void finishCrash() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            discard();
            return;
        }
        explodeOnImpact(serverLevel);
        if (satelliteId != null) {
            MisakaRelayRegistry.get(serverLevel.getServer()).completeCrash(serverLevel.getServer(), satelliteId);
        }
        if (!isRemoved()) {
            discard();
        }
    }

    private void explodeOnImpact(ServerLevel serverLevel) {
        var power = DEFAULT_CRASH_EXPLOSION_POWER;
        var destroyBlocks = true;
        var academy = serverLevel.getServer().getAcademyCraftServer();
        if (academy != null) {
            var config = academy.getGenericConfig();
            power = Math.max(0.0f, config.misakaRelayCrashExplosionPower);
            destroyBlocks = config.booleanMap.getOrDefault("destroyBlocks", true);
        }
        if (power <= 0.0f) {
            return;
        }
        serverLevel.explode(
                this,
                getX(),
                getY(),
                getZ(),
                power,
                false,
                destroyBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE
        );
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.getString("satellite_id").ifPresent(id -> {
            try {
                satelliteId = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                satelliteId = null;
            }
        });
        setOrbitAnchor(
                input.getDoubleOr("anchor_x", getX()),
                input.getDoubleOr("anchor_y", getY()),
                input.getDoubleOr("anchor_z", getZ())
        );
        orbitAngle = input.getDoubleOr("orbit_angle", 0.0);
        entityData.set(CRASHING, input.getBooleanOr("crashing", false));
        entityData.set(LAUNCHING, input.getBooleanOr("launching", false));
        entityData.set(HYPER, input.getBooleanOr("hyper", false));
        launchAge = input.getIntOr("launch_age", 0);
        entityData.set(LAUNCH_AGE, launchAge);
        launchStartX = input.getDoubleOr("launch_start_x", getX());
        launchStartY = input.getDoubleOr("launch_start_y", getY());
        launchStartZ = input.getDoubleOr("launch_start_z", getZ());
        if (isCrashing()) {
            noPhysics = false;
            setNoGravity(false);
        } else {
            noPhysics = true;
            setNoGravity(true);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (satelliteId != null) {
            output.putString("satellite_id", satelliteId.toString());
        }
        var anchor = getOrbitAnchor();
        output.putDouble("anchor_x", anchor.x());
        output.putDouble("anchor_y", anchor.y());
        output.putDouble("anchor_z", anchor.z());
        output.putDouble("orbit_angle", orbitAngle);
        output.putBoolean("crashing", isCrashing());
        output.putBoolean("launching", isLaunching());
        output.putBoolean("hyper", isHyper());
        output.putInt("launch_age", launchAge);
        output.putDouble("launch_start_x", launchStartX);
        output.putDouble("launch_start_y", launchStartY);
        output.putDouble("launch_start_z", launchStartZ);
    }
}
