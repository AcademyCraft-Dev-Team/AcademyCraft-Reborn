package org.academy.internal.common.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.electromaster.MagneticallyManipulable;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A reusable magnetic grappling hook that remains attached to blocks or entities. */
public final class MagneticHook extends AbstractArrow implements ItemSupplier, MagneticallyManipulable {
    private static final byte FLYING = 0;
    private static final byte ATTACHED_TO_BLOCK = 1;
    private static final byte ATTACHED_TO_ENTITY = 2;
    private static final EntityDataAccessor<Byte> ATTACHMENT_TYPE =
            SynchedEntityData.defineId(MagneticHook.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<BlockPos> ATTACHED_BLOCK =
            SynchedEntityData.defineId(MagneticHook.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Byte> ATTACHED_FACE =
            SynchedEntityData.defineId(MagneticHook.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> ATTACHED_ENTITY_ID =
            SynchedEntityData.defineId(MagneticHook.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ATTACHMENT_X =
            SynchedEntityData.defineId(MagneticHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ATTACHMENT_Y =
            SynchedEntityData.defineId(MagneticHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ATTACHMENT_Z =
            SynchedEntityData.defineId(MagneticHook.class, EntityDataSerializers.FLOAT);

    private @Nullable UUID hookOwnerUuid;
    private @Nullable UUID attachedEntityUuid;
    private long launchOrder;

    public MagneticHook(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
        setBaseDamage(0.0);
    }

    public MagneticHook(ServerLevel level, LivingEntity owner, long launchOrder) {
        super(EntityTypes.MAGNETIC_HOOK.get(), owner, level,
                new ItemStack(Items.MAGNETIC_HOOK.get()), null);
        hookOwnerUuid = owner.getUUID();
        this.launchOrder = launchOrder;
        pickup = Pickup.DISALLOWED;
        setBaseDamage(0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACHMENT_TYPE, FLYING);
        builder.define(ATTACHED_BLOCK, BlockPos.ZERO);
        builder.define(ATTACHED_FACE, (byte) Direction.DOWN.get3DDataValue());
        builder.define(ATTACHED_ENTITY_ID, -1);
        builder.define(ATTACHMENT_X, 0.0f);
        builder.define(ATTACHMENT_Y, 0.0f);
        builder.define(ATTACHMENT_Z, 0.0f);
    }

    @Override
    public void tick() {
        if (isAttachedToBlock()) {
            tickBlockAttachment();
            return;
        }
        if (isAttachedToEntity()) {
            tickEntityAttachment();
            return;
        }
        super.tick();
    }

    private void tickBlockAttachment() {
        var blockPos = attachedBlockPos();
        if (!level().isClientSide() && level().getBlockState(blockPos).isAir()) {
            discard();
            return;
        }
        holdStill();
        super.tick();
        var face = attachedFace();
        setPos(Vec3.atCenterOf(blockPos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.52)));
    }

    private void tickEntityAttachment() {
        var attached = resolveAttachedEntity();
        if (attached == null) {
            if (!level().isClientSide()) discard();
            return;
        }
        holdStill();
        super.tick();
        setPos(attached.position().add(attachmentOffset()));
    }

    private void holdStill() {
        setNoPhysics(true);
        setNoGravity(true);
        setInGround(false);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner()
                && !(entity instanceof MagneticHook)
                && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        var target = result.getEntity();
        attachedEntityUuid = target.getUUID();
        entityData.set(ATTACHMENT_TYPE, ATTACHED_TO_ENTITY);
        entityData.set(ATTACHED_ENTITY_ID, target.getId());
        var offset = result.getLocation().subtract(target.position());
        entityData.set(ATTACHMENT_X, (float) offset.x);
        entityData.set(ATTACHMENT_Y, (float) offset.y);
        entityData.set(ATTACHMENT_Z, (float) offset.z);
        setPos(result.getLocation());
        holdStill();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        entityData.set(ATTACHMENT_TYPE, ATTACHED_TO_BLOCK);
        entityData.set(ATTACHED_BLOCK, result.getBlockPos().immutable());
        entityData.set(ATTACHED_FACE, (byte) result.getDirection().get3DDataValue());
        setPos(result.getLocation().add(Vec3.atLowerCornerOf(result.getDirection().getUnitVec3i()).scale(0.03)));
        holdStill();
    }

    private @Nullable Entity resolveAttachedEntity() {
        var entityId = entityData.get(ATTACHED_ENTITY_ID);
        var attached = entityId < 0 ? null : level().getEntity(entityId);
        if (attached != null && !attached.isRemoved()) return attached;
        if (!(level() instanceof ServerLevel serverLevel) || attachedEntityUuid == null) return null;
        attached = serverLevel.getEntityInAnyDimension(attachedEntityUuid);
        if (attached != null && !attached.isRemoved() && attached.level() == level()) {
            entityData.set(ATTACHED_ENTITY_ID, attached.getId());
            return attached;
        }
        return null;
    }

    public boolean isAttached() {
        return entityData.get(ATTACHMENT_TYPE) != FLYING;
    }

    public boolean isAttachedToBlock() {
        return entityData.get(ATTACHMENT_TYPE) == ATTACHED_TO_BLOCK;
    }

    public boolean isAttachedToEntity() {
        return entityData.get(ATTACHMENT_TYPE) == ATTACHED_TO_ENTITY;
    }

    public boolean isAttachedTo(BlockPos blockPos) {
        return isAttachedToBlock() && attachedBlockPos().equals(blockPos);
    }

    public boolean isAttachedTo(Entity entity) {
        return isAttachedToEntity() && attachedEntityUuid != null
                && attachedEntityUuid.equals(entity.getUUID());
    }

    public boolean isOwnedBy(Player player) {
        return hookOwnerUuid != null && hookOwnerUuid.equals(player.getUUID());
    }

    public long launchOrder() {
        return launchOrder;
    }

    private BlockPos attachedBlockPos() {
        return entityData.get(ATTACHED_BLOCK);
    }

    private Direction attachedFace() {
        return Direction.from3DDataValue(Byte.toUnsignedInt(entityData.get(ATTACHED_FACE)));
    }

    private Vec3 attachmentOffset() {
        return new Vec3(entityData.get(ATTACHMENT_X),
                entityData.get(ATTACHMENT_Y), entityData.get(ATTACHMENT_Z));
    }

    @Override
    public boolean isPickable() {
        return isAttached();
    }

    @Override
    public float getPickRadius() {
        return isAttached() ? 0.35f : super.getPickRadius();
    }

    @Override
    public ItemStack getItem() {
        var stack = new ItemStack(Items.MAGNETIC_HOOK.get());
        if (isAttached()) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(), List.of(true), List.of(), List.of()));
        }
        return stack;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("academy_hook_owner").ifPresent(value -> hookOwnerUuid = parseUuid(value));
        input.getString("academy_attached_entity").ifPresent(value -> attachedEntityUuid = parseUuid(value));
        launchOrder = input.getLongOr("academy_launch_order", 0L);
        entityData.set(ATTACHMENT_TYPE, input.getByteOr("academy_attachment_type", FLYING));
        entityData.set(ATTACHED_BLOCK, new BlockPos(
                input.getIntOr("academy_block_x", 0),
                input.getIntOr("academy_block_y", 0),
                input.getIntOr("academy_block_z", 0)));
        entityData.set(ATTACHED_FACE, input.getByteOr("academy_attached_face", (byte) 0));
        entityData.set(ATTACHMENT_X, input.getIntOr("academy_attachment_x_milli", 0) / 1_000.0f);
        entityData.set(ATTACHMENT_Y, input.getIntOr("academy_attachment_y_milli", 0) / 1_000.0f);
        entityData.set(ATTACHMENT_Z, input.getIntOr("academy_attachment_z_milli", 0) / 1_000.0f);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (hookOwnerUuid != null) output.putString("academy_hook_owner", hookOwnerUuid.toString());
        if (attachedEntityUuid != null) output.putString("academy_attached_entity", attachedEntityUuid.toString());
        output.putLong("academy_launch_order", launchOrder);
        output.putByte("academy_attachment_type", entityData.get(ATTACHMENT_TYPE));
        var blockPos = attachedBlockPos();
        output.putInt("academy_block_x", blockPos.getX());
        output.putInt("academy_block_y", blockPos.getY());
        output.putInt("academy_block_z", blockPos.getZ());
        output.putByte("academy_attached_face", entityData.get(ATTACHED_FACE));
        var offset = attachmentOffset();
        output.putInt("academy_attachment_x_milli", (int) Math.round(offset.x * 1_000.0));
        output.putInt("academy_attachment_y_milli", (int) Math.round(offset.y * 1_000.0));
        output.putInt("academy_attachment_z_milli", (int) Math.round(offset.z * 1_000.0));
    }

    private static @Nullable UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
