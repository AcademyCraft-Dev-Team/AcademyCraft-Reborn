package org.academy.internal.common.ability.electromaster.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.resources.R;
import org.academy.api.client.sound.LoopingPlayerSoundInstance;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.EMFieldEffectWrapper;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MagnetManipulation extends Skill {
    static final double MOVE_RANGE = 48.0;
    static final double MOVE_SPEED_PER_TICK = 18.0 / 20.0;
    static final double TARGET_PULL_SPEED_PER_TICK = 1.15;
    static final double PLAYER_STOP_DISTANCE = 1.35;
    static final double TARGET_STOP_DISTANCE = 0.65;
    static final double TARGET_FRONT_DISTANCE = 2.5;
    static final float MOVE_CP_COST = 10.0f;
    static final int MOVE_CP_INTERVAL_TICKS = 20;
    private static final TagKey<Block> MAGNETIC_BLOCKS = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID, "magnetic_blocks")
    );
    private static final TagKey<Item> MAGNETIC_ITEMS = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID, "magnetic_items")
    );
    private static final TagKey<EntityType<?>> MAGNETIC_ENTITY_TYPES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID, "magnetic_entities")
    );
    private static final EquipmentSlot[] CHECKED_EQUIPMENT_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public MagnetManipulation() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.ARC_GENERATE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
        );
    }

    static Vec3 calculateMoveVelocity(Vec3 origin, Vec3 target, Vec3 fallbackDirection) {
        var direction = target.subtract(origin);
        if (direction.lengthSqr() <= 1.0e-6) direction = fallbackDirection;
        if (direction.lengthSqr() <= 1.0e-6) return Vec3.ZERO;
        return direction.normalize().scale(MOVE_SPEED_PER_TICK);
    }

    static Vec3 calculatePullVelocity(Vec3 currentVelocity, Vec3 origin, Vec3 target,
                                      Vec3 fallbackDirection, double maxSpeed, double stopDistance) {
        if (!isFinite(currentVelocity) || !isFinite(origin) || !isFinite(target) || !isFinite(fallbackDirection)) {
            return Vec3.ZERO;
        }
        var direction = target.subtract(origin);
        var distance = direction.length();
        if (!Double.isFinite(distance)) return Vec3.ZERO;
        if (distance <= stopDistance) return currentVelocity.scale(0.25);
        if (distance <= 1.0e-6) direction = fallbackDirection;
        if (direction.lengthSqr() <= 1.0e-6) return Vec3.ZERO;
        var speed = Math.min(maxSpeed, Math.max(0.12, (distance - stopDistance) * 0.22));
        var desired = direction.normalize().scale(speed);
        var velocity = currentVelocity.scale(0.2).add(desired.scale(0.8));
        var length = velocity.length();
        return length > maxSpeed ? velocity.scale(maxSpeed / length) : velocity;
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static boolean isIronRelatedPath(String path) {
        var normalized = path.toLowerCase(Locale.ROOT);
        return normalized.equals("iron")
                || normalized.startsWith("iron_")
                || normalized.endsWith("_iron")
                || normalized.contains("_iron_");
    }

    static boolean isMagnetic(BlockState state) {
        var sound = state.getSoundType();
        if (state.is(MAGNETIC_BLOCKS)
                || sound == SoundType.IRON
                || sound == SoundType.ANVIL
                || sound == SoundType.CHAIN) {
            return true;
        }
        var block = state.getBlock();
        if (isIronRelatedPath(BuiltInRegistries.BLOCK.getKey(block).getPath())) return true;
        return block.builtInRegistryHolder().tags()
                .anyMatch(tag -> isIronRelatedPath(tag.location().getPath()));
    }

    static boolean isMagnetic(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(MAGNETIC_ITEMS)) return true;
        var item = stack.getItem();
        if (isIronRelatedPath(BuiltInRegistries.ITEM.getKey(item).getPath())) return true;
        return item.builtInRegistryHolder().tags()
                .anyMatch(tag -> isIronRelatedPath(tag.location().getPath()));
    }

    static boolean isMagnetic(Entity entity) {
        if (entity instanceof FallingBlockEntity fallingBlock && isMagnetic(fallingBlock.getBlockState())) return true;
        if (entity instanceof ItemEntity itemEntity && isMagnetic(itemEntity.getItem())) return true;

        var type = entity.getType();
        if (type.builtInRegistryHolder().is(MAGNETIC_ENTITY_TYPES)
                || isIronRelatedPath(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath())
                || type.builtInRegistryHolder().tags()
                .anyMatch(tag -> isIronRelatedPath(tag.location().getPath()))) {
            return true;
        }
        if (!(entity instanceof LivingEntity living)) return false;
        for (var slot : CHECKED_EQUIPMENT_SLOTS) {
            if (isMagnetic(living.getItemBySlot(slot))) return true;
        }
        return false;
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(EMFieldEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        if (!Client.CONFIG.containsKeyBinding(Client.KEY_NAME_START)
                && Client.CONFIG.containsKeyBinding(Client.OLD_KEY_NAME_MOVE_START)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_START,
                    Client.CONFIG.getKeyBinding(Client.OLD_KEY_NAME_MOVE_START));
        }
        if (!Client.CONFIG.containsKeyBinding(Client.KEY_NAME_STOP)
                && Client.CONFIG.containsKeyBinding(Client.OLD_KEY_NAME_MOVE_STOP)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_STOP,
                    Client.CONFIG.getKeyBinding(Client.OLD_KEY_NAME_MOVE_STOP));
        }
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(Client.KEY_NAME_START,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, 0))
                , _ -> Client.onMoveStart(PullMode.PLAYER_TO_TARGET));
        InputSystem.addKeyBinding(Client.KEY_NAME_STOP, Client.CONFIG.getKeyBinding(Client.KEY_NAME_STOP,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.RELEASE, 0))
                , _ -> Client.onMoveStop(PullMode.PLAYER_TO_TARGET));
        InputSystem.addKeyBinding(Client.KEY_NAME_TARGET_START, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TARGET_START,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X, InputConstants.PRESS, 0))
                , _ -> Client.onMoveStart(PullMode.TARGET_TO_PLAYER));
        InputSystem.addKeyBinding(Client.KEY_NAME_TARGET_STOP, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TARGET_STOP,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X, InputConstants.RELEASE, 0))
                , _ -> Client.onMoveStop(PullMode.TARGET_TO_PLAYER));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    enum PullMode {
        PLAYER_TO_TARGET,
        TARGET_TO_PLAYER
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.MAGNET_MANIPULATION.get(),
                        List.of(ArcGenerate.Client.SKILL_INFO),
                        R.textures.magnet_manipulation_icon,
                        64,
                        46
                )
        );
        public static final String KEY_NAME_START = SkillNames.MAGNET_MANIPULATION + "_start";
        public static final String KEY_NAME_STOP = SkillNames.MAGNET_MANIPULATION + "_stop";
        public static final String KEY_NAME_TARGET_START = SkillNames.MAGNET_MANIPULATION + "_target_start";
        public static final String KEY_NAME_TARGET_STOP = SkillNames.MAGNET_MANIPULATION + "_target_stop";
        private static final String OLD_KEY_NAME_MOVE_START = SkillNames.MAGNET_MANIPULATION + "_move_start";
        private static final String OLD_KEY_NAME_MOVE_STOP = SkillNames.MAGNET_MANIPULATION + "_move_stop";
        public static Config CONFIG = new Config();
        private static SoundInstance loopSound;
        private static @Nullable PullMode activeMode;

        public static void onMoveStart(PullMode mode) {
            if (!AbilitySystemClient.canUseSkill(Skills.MAGNET_MANIPULATION.get())) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (activeMode != null && activeMode != mode) {
                MisakaNetworkClient.send(MoveStopPacket.INSTANCE);
            }
            activeMode = mode;
            stopLoopSound();
            loopSound = new LoopingPlayerSoundInstance(
                    player, SoundEvents.MAGNET_MOVE_LOOP.get(), 1.0f, 1.0f,
                    () -> activeMode != null && Minecraft.getInstance().player == player);
            Minecraft.getInstance().getSoundManager().play(loopSound);
            MisakaNetworkClient.send(mode == PullMode.TARGET_TO_PLAYER
                    ? MoveStartPacket.TARGET_TO_PLAYER
                    : MoveStartPacket.PLAYER_TO_TARGET);
        }

        public static void onMoveStop(PullMode mode) {
            if (activeMode != mode) return;
            activeMode = null;
            stopLoopSound();
            MisakaNetworkClient.send(MoveStopPacket.INSTANCE);
        }

        private static void stopLoopSound() {
            if (loopSound == null) return;
            Minecraft.getInstance().getSoundManager().stop(loopSound);
            loopSound = null;
        }

        private static void tickVisual() {
            var player = Minecraft.getInstance().player;
            if (activeMode == null || player == null) return;
            if ((player.tickCount & 1) != 0) return;
            var center = player.position().add(0, player.getBbHeight() * 0.5, 0);
            var time = player.tickCount * 0.18;
            EMFieldEffectWrapper.INSTANCE.createField(3.0f);
            for (var i = 0; i < 6; i++) {
                var angle = time + i * Math.PI / 3.0;
                var start = center.add(Math.cos(angle) * 0.8, -0.55, Math.sin(angle) * 0.8);
                var end = center.add(Math.cos(angle + 1.1) * 0.65, 0.75, Math.sin(angle + 1.1) * 0.65);
                EMFieldEffectWrapper.INSTANCE.addFieldLine(
                        start, end, 0.25f, 0.65f, 1.0f, 0.035f, 0.8f, 0.18f, 8);
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public MagnetManipulation.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        private static final Map<Player, MoveContext> ACTIVE_MOVEMENT = createContextMap();

        @SubscribePacket
        public static void handleMoveStart(MoveStartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.MAGNET_MANIPULATION.get().isEnabled(player)) {
                return;
            }

            var previous = ACTIVE_MOVEMENT.get(player);
            if (previous != null) previous.end();

            var context = new MoveContext(player, packet.getMode());
            ACTIVE_MOVEMENT.put(player, context);
            setVisualState(player, true);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleMoveStop(MoveStopPacket packet) {
            var context = ACTIVE_MOVEMENT.get(packet.getPacketListener().getPlayer());
            if (context != null) context.end();
        }

        private static void setVisualState(ServerPlayer player, boolean active) {
            player.setData(AttachmentTypes.MAGNET_MANIPULATION_ACTIVE.get(), active);
            player.syncData(AttachmentTypes.MAGNET_MANIPULATION_ACTIVE.get());
        }
    }

    public static final class MoveContext extends ServerContext {
        private final ResourceKey<Level> dimension;
        private final PullMode mode;
        private @Nullable Entity controlledTarget;
        private boolean controlsFallingBlock;
        private int movingTicks;
        private boolean ended;

        private MoveContext(ServerPlayer player, PullMode mode) {
            super(player);
            dimension = player.level().dimension();
            this.mode = mode;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.MAGNET_MANIPULATION.get();
            if (player.hasDisconnected()
                    || !player.isAlive()
                    || !player.level().dimension().equals(dimension)
                    || !skill.isEnabled(player)) {
                end();
                return;
            }

            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            skill.reportActivity(player, false);
            var moved = mode == PullMode.TARGET_TO_PLAYER
                    ? pullTargetToPlayer()
                    : pullPlayerToTarget();
            if (!moved) return;
            skill.reportActivity(player, true);
            movingTicks++;
            if (movingTicks % MOVE_CP_INTERVAL_TICKS == 0
                    && !system.tryTimedOccupation(uuid, MOVE_CP_COST, skill, 10)) {
                end();
            }
        }

        private boolean pullPlayerToTarget() {
            releaseControlledTarget();
            var target = findCrosshairTarget();
            if (target == null) return false;
            var look = player.getLookAngle();
            player.setDeltaMovement(calculatePullVelocity(
                    player.getDeltaMovement(),
                    player.getBoundingBox().getCenter(),
                    target.location(),
                    look,
                    MOVE_SPEED_PER_TICK,
                    PLAYER_STOP_DISTANCE
            ));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.resetFallDistance();
            return true;
        }

        private boolean pullTargetToPlayer() {
            if (controlledTarget == null || !controlledTarget.isAlive()) {
                releaseControlledTarget();
                var target = findCrosshairTarget();
                if (target == null) return false;
                controlledTarget = target.entity() != null
                        ? target.entity()
                        : createFallingBlockTarget(target.blockPos());
                if (controlledTarget == null) return false;
            }

            var look = player.getLookAngle();
            var destination = player.getEyePosition().add(look.scale(TARGET_FRONT_DISTANCE));
            var targetOrigin = controlledTarget.getBoundingBox().getCenter();
            var velocity = calculatePullVelocity(
                    controlledTarget.getDeltaMovement(),
                    targetOrigin,
                    destination,
                    look,
                    TARGET_PULL_SPEED_PER_TICK,
                    TARGET_STOP_DISTANCE
            );
            controlledTarget.setDeltaMovement(velocity);
            controlledTarget.hurtMarked = true;
            controlledTarget.resetFallDistance();
            if (controlledTarget instanceof ServerPlayer targetPlayer) {
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }
            return true;
        }

        private @Nullable FallingBlockEntity createFallingBlockTarget(@Nullable BlockPos pos) {
            if (pos == null || !level().hasChunkAt(pos) || !level().mayInteract(player, pos)) return null;
            var state = level().getBlockState(pos);
            if (!isMagnetic(state)
                    || state.hasBlockEntity()
                    || state.getDestroySpeed(level(), pos) < 0.0f) {
                return null;
            }
            var fallingBlock = FallingBlockEntity.fall(level(), pos, state);
            fallingBlock.setNoGravity(true);
            controlsFallingBlock = true;
            return fallingBlock;
        }

        private @Nullable MagneticTarget findCrosshairTarget() {
            var eye = player.getEyePosition();
            var look = player.getLookAngle();
            if (look.lengthSqr() <= 1.0e-6) return null;
            look = look.normalize();
            var end = eye.add(look.scale(MOVE_RANGE));
            var blockHit = level().clip(new ClipContext(
                    eye,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            var blockDistance = blockHit.getType() == HitResult.Type.MISS
                    ? MOVE_RANGE * MOVE_RANGE
                    : eye.distanceToSqr(blockHit.getLocation());
            var entityHit = ProjectileUtil.getEntityHitResult(
                    level(),
                    player,
                    eye,
                    end,
                    player.getBoundingBox().expandTowards(look.scale(MOVE_RANGE)).inflate(1.0),
                    entity -> entity != player && entity.isAlive() && !entity.isSpectator(),
                    0.3f
            );
            if (entityHit != null && eye.distanceToSqr(entityHit.getLocation()) < blockDistance) {
                var entity = entityHit.getEntity();
                return isMagnetic(entity)
                        ? new MagneticTarget(entity, null, entity.getBoundingBox().getCenter())
                        : null;
            }
            if (blockHit.getType() == HitResult.Type.MISS) return null;
            var pos = blockHit.getBlockPos();
            return isMagnetic(level().getBlockState(pos))
                    ? new MagneticTarget(null, pos, blockHit.getLocation())
                    : null;
        }

        private void releaseControlledTarget() {
            if (controlledTarget != null && controlsFallingBlock && controlledTarget.isAlive()) {
                controlledTarget.setNoGravity(false);
            }
            controlledTarget = null;
            controlsFallingBlock = false;
        }

        private void end() {
            if (ended) return;
            ended = true;
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            releaseControlledTarget();
            Server.ACTIVE_MOVEMENT.remove(player, this);
            Server.setVisualState(player, false);
        }
    }

    private record MagneticTarget(@Nullable Entity entity, @Nullable BlockPos blockPos, Vec3 location) {
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Client.tickVisual();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MoveStartPacket extends Packet<ServerGamePacketListenerImpl, MoveStartPacket> {
        public static final MoveStartPacket PLAYER_TO_TARGET = new MoveStartPacket(PullMode.PLAYER_TO_TARGET);
        public static final MoveStartPacket TARGET_TO_PLAYER = new MoveStartPacket(PullMode.TARGET_TO_PLAYER);
        public static final StreamCodec<ByteBuf, MoveStartPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                packet -> packet.mode == PullMode.TARGET_TO_PLAYER,
                pullTarget -> pullTarget ? TARGET_TO_PLAYER : PLAYER_TO_TARGET
        );
        private final PullMode mode;

        private MoveStartPacket(PullMode mode) {
            this.mode = mode;
        }

        private PullMode getMode() {
            return mode;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MoveStartPacket> getPacketType() {
            return PacketTypes.MAGNET_MANIPULATION_MOVE_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MoveStopPacket extends Packet<ServerGamePacketListenerImpl, MoveStopPacket> {
        public static final MoveStopPacket INSTANCE = new MoveStopPacket();
        public static final StreamCodec<ByteBuf, MoveStopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private MoveStopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MoveStopPacket> getPacketType() {
            return PacketTypes.MAGNET_MANIPULATION_MOVE_STOP.get();
        }
    }
}
