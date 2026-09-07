package org.academy.internal.common.world.level.block.entity;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.MisakaDayTime;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.level.block.HibernationPodBlock;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * One-time openable hibernation pod. Right-click spawns a Misaka Sister inside (hidden),
 * plays {@code openning} with cold air (she becomes visible), then she walks out when {@code opened}.
 */
public final class HibernationPodBlockEntity extends MultiBlockEntity implements GeoBlockEntity {
    /** Matches {@code openning} length 0.5417s at 20 tps. */
    public static final int OPENING_TICKS = 11;
    /** Lids / fog have started; sister becomes visible through the opening. */
    public static final int REVEAL_TICKS = 3;
    private static final float INTERIOR_Y_OFFSET = 0.2F;
    private static final double EXIT_WALK_SPEED = 0.35;
    private static final int EXIT_DISTANCE = 2;

    private static final RawAnimation OPENING =
            RawAnimation.begin().thenPlay("openning").thenLoop("opened");
    private static final RawAnimation OPENED = RawAnimation.begin().thenLoop("opened");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private OpenPhase openPhase = OpenPhase.CLOSED;
    private int openingProgress;
    private @Nullable UUID containedSisterId;
    private boolean sisterReleased;

    public HibernationPodBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.HIBERNATION_POD.get(), pos, state);
    }

    public OpenPhase openPhase() {
        return openPhase;
    }

    public boolean isOpenedOrOpening() {
        return openPhase != OpenPhase.CLOSED;
    }

    public boolean containsSister(MisakaSisterEntity sister) {
        return containedSisterId != null
                && !sisterReleased
                && containedSisterId.equals(sister.getUUID());
    }

    /**
     * Starts the one-time opening sequence on the main pod (server). Spawns a sister inside first.
     *
     * @return true if opening started
     */
    public boolean tryOpen(@Nullable Player player) {
        HibernationPodBlockEntity main = mainEntity();
        if (main == null || main.openPhase != OpenPhase.CLOSED || level == null || level.isClientSide()) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!main.spawnContainedSister(serverLevel, player)) {
            return false;
        }
        main.openPhase = OpenPhase.OPENING;
        main.openingProgress = 0;
        main.setChanged();
        level.sendBlockUpdated(main.getBlockPos(), main.getBlockState(), main.getBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private boolean spawnContainedSister(ServerLevel level, @Nullable Player player) {
        var roster = MisakaSisterRoster.get(level.getServer());
        var registered = roster.tryRegisterRescued(level.getRandom(), MisakaDayTime.dayIndex(level));
        if (registered.isEmpty()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("message.academy.misaka_serial_pool_exhausted")
                );
            }
            return false;
        }
        var record = registered.get();
        MisakaSisterEntity sister = EntityTypes.MISAKA_SISTER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (sister == null) {
            roster.release(record.misakaUuid);
            return false;
        }
        Direction door = doorFacing();
        BlockPos pos = getBlockPos();
        placeSisterInCabin(sister, pos, door);
        sister.bindToRecord(record);
        sister.setNoAi(true);
        sister.setInvisible(true);
        sister.setNoGravity(true);
        sister.setInvulnerable(true);
        sister.noPhysics = true;
        sister.setDeltaMovement(Vec3.ZERO);
        if (!level.addFreshEntity(sister)) {
            roster.release(record.misakaUuid);
            return false;
        }
        containedSisterId = sister.getUUID();
        sisterReleased = false;
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HibernationPodBlockEntity be) {
        if (!be.isMain()) {
            return;
        }
        if (be.openPhase == OpenPhase.OPENING) {
            be.openingProgress++;
            if (level.isClientSide()) {
                be.spawnColdAirParticles(level);
            } else if (level instanceof ServerLevel serverLevel) {
                be.tickContainedSister(serverLevel);
            }
            if (be.openingProgress >= OPENING_TICKS) {
                be.openPhase = OpenPhase.OPENED;
                be.openingProgress = OPENING_TICKS;
                if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                    be.releaseSister(serverLevel);
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                }
            }
        } else if (be.openPhase == OpenPhase.OPENED
                && !be.sisterReleased
                && !level.isClientSide()
                && level instanceof ServerLevel serverLevel) {
            // Recover release if mid-open save missed the transition edge.
            be.releaseSister(serverLevel);
        }
    }

    private void tickContainedSister(ServerLevel level) {
        MisakaSisterEntity sister = findContainedSister(level).orElse(null);
        if (sister == null) {
            return;
        }
        Direction door = doorFacing();
        BlockPos pos = getBlockPos();
        placeSisterInCabin(sister, pos, door);
        sister.setDeltaMovement(Vec3.ZERO);
        sister.setNoAi(true);
        sister.setNoGravity(true);
        sister.setInvulnerable(true);
        sister.noPhysics = true;
        sister.clearFire();
        if (openingProgress >= REVEAL_TICKS) {
            sister.setInvisible(false);
        }
    }

    private void releaseSister(ServerLevel level) {
        if (sisterReleased) {
            return;
        }
        MisakaSisterEntity sister = findContainedSister(level).orElse(null);
        sisterReleased = true;
        containedSisterId = null;
        if (sister == null) {
            setChanged();
            return;
        }
        Direction door = doorFacing();
        BlockPos pos = getBlockPos();
        // Step just outside the door before enabling physics so she is not crushed by the pod.
        double exitX = pos.getX() + 0.5 + door.getStepX() * 0.9;
        double exitZ = pos.getZ() + 0.5 + door.getStepZ() * 0.9;
        faceDoor(sister, door);
        sister.snapTo(exitX, pos.getY() + INTERIOR_Y_OFFSET, exitZ, sister.getYRot(), 0.0F);
        faceDoor(sister, door);
        sister.setInvisible(false);
        sister.setNoAi(false);
        sister.setNoGravity(false);
        sister.setInvulnerable(false);
        sister.noPhysics = false;
        BlockPos walkTarget = pos.relative(door, EXIT_DISTANCE);
        sister.getNavigation().moveTo(
                walkTarget.getX() + 0.5,
                sister.getY(),
                walkTarget.getZ() + 0.5,
                EXIT_WALK_SPEED
        );
        setChanged();
    }

    private static void placeSisterInCabin(MisakaSisterEntity sister, BlockPos pos, Direction door) {
        faceDoor(sister, door);
        sister.snapTo(
                pos.getX() + 0.5,
                pos.getY() + INTERIOR_Y_OFFSET,
                pos.getZ() + 0.5,
                sister.getYRot(),
                0.0F
        );
        faceDoor(sister, door);
    }

    /**
     * {@code snapTo}/{@code setYRot} alone leave {@code yBodyRot} stale while {@code noAi} is set,
     * so GeckoLib keeps rendering her facing the previous body yaw (looks like moonwalking out).
     */
    private static void faceDoor(MisakaSisterEntity sister, Direction door) {
        float yaw = Mth.wrapDegrees(door.toYRot());
        sister.setYRot(yaw);
        sister.setYBodyRot(yaw);
        sister.setYHeadRot(yaw);
        sister.yRotO = yaw;
        sister.yBodyRotO = yaw;
        sister.yHeadRotO = yaw;
    }

    /**
     * Cabin door follows {@link BlockStateProperties#HORIZONTAL_FACING}
     * (same as GeoBlockRenderer lid on model -Z after facing rotation).
     */
    private Direction doorFacing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    private Optional<MisakaSisterEntity> findContainedSister(ServerLevel level) {
        if (containedSisterId == null) {
            return Optional.empty();
        }
        if (level.getEntity(containedSisterId) instanceof MisakaSisterEntity sister) {
            return Optional.of(sister);
        }
        return Optional.empty();
    }

    private void spawnColdAirParticles(Level level) {
        RandomSource random = level.getRandom();
        BlockPos pos = getBlockPos();
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        for (int i = 0; i < 10; i++) {
            double ox = (random.nextDouble() - 0.5) * 1.8;
            double oy = 0.2 + random.nextDouble() * 3.2;
            double oz = (random.nextDouble() - 0.5) * 1.8;
            double vx = (random.nextDouble() - 0.5) * 0.08;
            double vy = 0.03 + random.nextDouble() * 0.1;
            double vz = (random.nextDouble() - 0.5) * 0.08;
            level.addParticle(ParticleTypes.CLOUD, x + ox, pos.getY() + oy, z + oz, vx, vy, vz);
            level.addParticle(
                    ParticleTypes.WHITE_SMOKE,
                    x + ox * 0.85,
                    pos.getY() + oy,
                    z + oz * 0.85,
                    vx * 0.7,
                    vy * 0.9,
                    vz * 0.7
            );
        }
    }

    public @Nullable HibernationPodBlockEntity mainEntity() {
        MultiBlockEntity main = getMain();
        return main instanceof HibernationPodBlockEntity pod ? pod : null;
    }

    public AABB getRenderBoundingBox() {
        BlockPos base = isMain() ? worldPosition : (mainPos != null ? mainPos : worldPosition);
        return new AABB(
                base.getX() - 0.5,
                base.getY() - 0.5,
                base.getZ() - 0.5,
                base.getX() + 1.5,
                base.getY() + HibernationPodBlock.HEIGHT + 0.5,
                base.getZ() + 1.5
        );
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("lid", state -> {
            HibernationPodBlockEntity main = mainEntity();
            OpenPhase phase = main != null ? main.openPhase : openPhase;
            return switch (phase) {
                case OPENING -> state.setAndContinue(OPENING);
                case OPENED -> state.setAndContinue(OPENED);
                case CLOSED -> PlayState.STOP;
            };
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("open_phase", openPhase.name());
        output.putInt("opening_progress", openingProgress);
        output.putBoolean("sister_released", sisterReleased);
        if (containedSisterId != null) {
            output.putString("contained_sister", containedSisterId.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        openPhase = OpenPhase.fromName(input.getString("open_phase").orElse(OpenPhase.CLOSED.name()));
        openingProgress = input.getInt("opening_progress").orElse(0);
        sisterReleased = input.getBooleanOr("sister_released", false);
        containedSisterId = input.getString("contained_sister")
                .map(id -> {
                    try {
                        return UUID.fromString(id);
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                })
                .orElse(null);
    }

    public enum OpenPhase {
        CLOSED,
        OPENING,
        OPENED;

        static OpenPhase fromName(String name) {
            try {
                return OpenPhase.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return CLOSED;
            }
        }
    }
}
