package org.academy.internal.common.ability.teleport.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportDamage;
import org.academy.internal.common.ability.teleport.TeleportTargeting;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ThreateningTeleport extends Skill {
    static final double MAX_RANGE = 64.0;
    static final double UNTARGETED_RANGE = 16.0;
    static final float BASE_DAMAGE = 4.0f;

    public ThreateningTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(10)
                .iterationTicks(5)
                .maxStacks(10)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    static Vec3 untargetedDestination(Vec3 eyePosition, Vec3 viewDirection) {
        var direction = viewDirection.normalize();
        if (direction.lengthSqr() < 1.0e-6) return eyePosition;
        return eyePosition.add(direction.scale(UNTARGETED_RANGE));
    }

    static boolean shouldDropTeleportedItem(boolean hasTarget, boolean targetKilled) {
        return !hasTarget || targetKilled;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_LEFT,
                        InputConstants.PRESS, InputConstants.MOD_ALT)
        ), ctx -> Client.cast());
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.THREATENING_TELEPORT.get(),
                        List.of(),
                        R.textures.threatening_teleport_icon,
                        30,
                        50
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.THREATENING_TELEPORT + "_cast";
        public static Config CONFIG = new Config();

        @SubscribeEvent
        public static void onLevelRender(LevelRenderEvent event) {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null
                    || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.THREATENING_TELEPORT.get())
                    || player.getMainHandItem().isEmpty()
                    || !isPreviewing()) {
                return;
            }
            var target = findTarget(player);
            AABB preview;
            if (target != null) {
                preview = target.getBoundingBox().inflate(0.2);
            } else {
                var point = ThreateningTeleport.untargetedDestination(
                        player.getEyePosition(event.getPartialTick()),
                        player.getViewVector(event.getPartialTick())
                );
                preview = new AABB(
                        point.x - 0.5, point.y - 0.5, point.z - 0.5,
                        point.x + 0.5, point.y + 0.5, point.z + 0.5
                );
            }

            var camera = minecraft.gameRenderer.mainCamera().position();
            var matrices = event.getMatrixStack();
            matrices.pushPose();
            matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
            event.submitCustomGeometry(Render.RenderTypes.MINE_DETECT_LINES, (snapshot, consumer) ->
                    LineBoxRenderer.renderWireframeBox(
                            snapshot, consumer, preview,
                            target != null ? 0.6f : 1.0f,
                            target != null ? 0.2f : 0.1f,
                            target != null ? 1.0f : 0.1f,
                            1.0f
                    ));
            matrices.popPose();
        }

        private static boolean isPreviewing() {
            return InputSystem.isDown(InputSystem.InputType.KEYBOARD, InputConstants.KEY_LALT)
                    || InputSystem.isDown(InputSystem.InputType.KEYBOARD, InputConstants.KEY_RALT);
        }

        private static void cast() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.THREATENING_TELEPORT.get())) {
                return;
            }
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            var target = findTarget(minecraft.player);
            MisakaNetworkClient.send(new CastPacket(target == null ? -1 : target.getId()));
        }

        private static LivingEntity findTarget(LocalPlayer player) {
            return TeleportTargeting.findFirstLivingEntity(player, MAX_RANGE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
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
        private static final Map<UUID, PendingKillDrop> PENDING_KILL_DROPS = new ConcurrentHashMap<>();

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.THREATENING_TELEPORT.get();
            LivingEntity target = null;
            if (packet.getTargetEntityId() >= 0) {
                if (!(player.level().getEntity(packet.getTargetEntityId()) instanceof LivingEntity living)
                        || living == player || !living.isAlive()
                        || player.distanceToSqr(living) > MAX_RANGE * MAX_RANGE) {
                    return;
                }
                target = living;
            }
            if (player.getMainHandItem().isEmpty()) return;
            var lockedTarget = target;

            skill.executeActive(player, (ctx, actualCost) -> {
                if (lockedTarget != null && (!lockedTarget.isAlive()
                        || lockedTarget.level() != player.level()
                        || player.distanceToSqr(lockedTarget) > MAX_RANGE * MAX_RANGE)) {
                    return;
                }
                var mainHand = player.getMainHandItem();
                if (mainHand.isEmpty()) {
                    return;
                }

                var teleported = mainHand.copyWithCount(1);
                var destination = lockedTarget == null
                        ? ThreateningTeleport.untargetedDestination(
                        player.getEyePosition(), player.getLookAngle())
                        : new Vec3(lockedTarget.getX(),
                        lockedTarget.getY() + lockedTarget.getBbHeight() * 0.5, lockedTarget.getZ());
                if (!player.isCreative()) mainHand.shrink(1);

                if (lockedTarget == null) {
                    if (shouldDropTeleportedItem(false, false)) {
                        dropTeleportedItem(
                                player, destination, teleported,
                                !player.isCreative() && ctx.milestone() >= 3);
                    }
                    player.level().playSound(
                            null,
                            BlockPos.containing(destination),
                            SoundEvents.THREATENING_TELEPORT.get(),
                            SoundSource.PLAYERS,
                            1.0f,
                            1.0f
                    );
                    return;
                }

                var attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
                var weaponBonus = attackDamage == null
                        ? 0.0f
                        : (float) Math.max(0.0, attackDamage.getValue() - attackDamage.getBaseValue());
                var system = AbilitySystemServer.getSystem(player);
                var damage = TeleportDamage.threatening(
                        BASE_DAMAGE,
                        weaponBonus,
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID()),
                        SpaceFoldingTheorem.damageMultiplier(player)
                );

                player.level().playSound(null, lockedTarget.blockPosition(), SoundEvents.THREATENING_TELEPORT.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                var wasAlive = lockedTarget.isAlive();
                PendingKillDrop pendingDrop = null;
                if (!player.isCreative()) {
                    pendingDrop = new PendingKillDrop(
                            player, teleported.copy(), ctx.milestone() >= 3);
                    PENDING_KILL_DROPS.put(lockedTarget.getUUID(), pendingDrop);
                }
                try {
                    lockedTarget.hurtServer(player.level(), SkillDamageSource.of(player, skill), damage);
                } finally {
                    if (pendingDrop != null
                            && PENDING_KILL_DROPS.remove(lockedTarget.getUUID(), pendingDrop)
                            && !lockedTarget.isAlive()) {
                        dropTeleportedItem(
                                player, lockedTarget.position(), pendingDrop.stack(),
                                pendingDrop.returnToCaster());
                    }
                }
                var targetKilled = wasAlive && !lockedTarget.isAlive();
                if (targetKilled) {
                    SpaceFoldingTheorem.refundKillCost(player, actualCost);
                }
            });
        }

        private static void dropTeleportedItem(
                ServerPlayer player,
                Vec3 destination,
                ItemStack teleported,
                boolean returnToCaster
        ) {
            var dropped = spawnDroppedItem(player, destination, teleported);
            if (dropped == null) {
                returnToCaster(player, teleported.copy());
                return;
            }
            if (!returnToCaster || dropped == null) return;

            var returningItem = dropped;
            TimedSkillEffectRuntime.schedule(player, 60, () -> {
                if (!returningItem.isAlive() || returningItem.isRemoved()
                        || returningItem.getItem().isEmpty()) {
                    return;
                }
                var returning = returningItem.getItem().copy();
                returningItem.discard();
                returnToCaster(player, returning);
            });
        }

        private static ItemEntity spawnDroppedItem(
                ServerPlayer player,
                Vec3 destination,
                ItemStack stack
        ) {
            if (stack.isEmpty()) return null;
            var level = player.level();
            var spawnPosition = validDropPosition(level, destination)
                    ? destination
                    : player.position().add(0.0, 0.5, 0.0);
            var block = BlockPos.containing(spawnPosition);
            level.getChunk(block.getX() >> 4, block.getZ() >> 4);

            var item = new ItemEntity(
                    level, spawnPosition.x, spawnPosition.y, spawnPosition.z, stack.copy());
            item.setDeltaMovement(0.0, 0.0, 0.0);
            item.setDefaultPickUpDelay();
            item.setThrower(player);
            return level.addFreshEntity(item) ? item : null;
        }

        private static boolean validDropPosition(ServerLevel level, Vec3 position) {
            if (position == null
                    || !Double.isFinite(position.x)
                    || !Double.isFinite(position.y)
                    || !Double.isFinite(position.z)) {
                return false;
            }
            var block = BlockPos.containing(position);
            return block.getY() >= level.getMinY()
                    && block.getY() < level.getMaxY()
                    && level.getWorldBorder().isWithinBounds(block);
        }

        private static void returnToCaster(ServerPlayer player, ItemStack stack) {
            if (stack.isEmpty()) return;
            if (player.isAlive() && !player.hasDisconnected()
                    && player.getInventory().add(stack)) {
                return;
            }
            var fallback = player.drop(stack, false);
            if (fallback == null && !stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }

        private record PendingKillDrop(
                ServerPlayer player,
                ItemStack stack,
                boolean returnToCaster
        ) {
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void onLivingDrops(LivingDropsEvent event) {
            var pending = Server.PENDING_KILL_DROPS.remove(event.getEntity().getUUID());
            if (pending == null) return;
            Server.dropTeleportedItem(
                    pending.player(), event.getEntity().position(), pending.stack(),
                    pending.returnToCaster());
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = ByteBufCodecs.VAR_INT
                .map(CastPacket::new, CastPacket::getTargetEntityId);
        private final int targetEntityId;

        public CastPacket(int targetEntityId) {
            this.targetEntityId = targetEntityId;
        }

        public int getTargetEntityId() {
            return targetEntityId;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.THREATENING_TELEPORT_CAST.get();
        }
    }
}
