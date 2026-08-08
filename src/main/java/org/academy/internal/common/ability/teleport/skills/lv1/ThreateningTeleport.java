package org.academy.internal.common.ability.teleport.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
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

public final class ThreateningTeleport extends Skill {
    static final double MAX_RANGE = 32.0;
    static final float BASE_DAMAGE = 4.0f;

    public ThreateningTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(10)
                .iterationTicks(40)
                .maxStacks(1)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
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
            if (target == null) return;

            var camera = minecraft.gameRenderer.mainCamera().position();
            var bounds = target.getBoundingBox().inflate(0.05);
            var matrices = event.getMatrixStack();
            matrices.pushPose();
            matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
            event.submitCustomGeometry(Render.RenderTypes.MINE_DETECT_LINES, (snapshot, consumer) ->
                    LineBoxRenderer.renderWireframeBox(
                            snapshot, consumer, bounds,
                            0.75f, 0.35f, 1.0f, 1.0f
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
            if (target == null) return;
            MisakaNetworkClient.send(new CastPacket(target.getId()));
        }

        private static LivingEntity findTarget(LocalPlayer player) {
            var start = player.getEyePosition();
            var direction = player.getLookAngle().normalize();
            if (direction.lengthSqr() < 1.0e-8) return null;
            var fullEnd = start.add(direction.scale(MAX_RANGE));
            var blockHit = player.level().clip(new ClipContext(
                    start,
                    fullEnd,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            var end = blockHit.getType() == HitResult.Type.MISS
                    ? fullEnd
                    : blockHit.getLocation();
            var hit = ProjectileUtil.getEntityHitResult(
                    player,
                    start,
                    end,
                    new AABB(start, end).inflate(1.0),
                    entity -> entity instanceof LivingEntity
                            && entity != player
                            && entity.isAlive()
                            && entity.isPickable(),
                    MAX_RANGE * MAX_RANGE
            );
            return hit != null && hit.getEntity() instanceof LivingEntity living
                    ? living
                    : null;
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
        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level().getEntity(packet.getTargetEntityId()) instanceof LivingEntity target)
                    || target == player || !target.isAlive()
                    || player.distanceToSqr(target) > MAX_RANGE * MAX_RANGE
                    || player.getMainHandItem().isEmpty()) {
                return;
            }

            Skills.THREATENING_TELEPORT.get().executeActive(player, (ctx, actualCost) -> {
                if (!target.isAlive() || target.level() != player.level()
                        || player.distanceToSqr(target) > MAX_RANGE * MAX_RANGE) {
                    return;
                }
                var mainHand = player.getMainHandItem();
                if (mainHand.isEmpty()) {
                    return;
                }

                var teleported = mainHand.copyWithCount(1);
                if (!player.isCreative()) {
                    mainHand.shrink(1);
                    var item = new ItemEntity(player.level(), target.getX(),
                            target.getY() + target.getBbHeight() * 0.5, target.getZ(), teleported);
                    item.setDeltaMovement(0.0, 0.0, 0.0);
                    item.setDefaultPickUpDelay();
                    player.level().addFreshEntity(item);
                }

                var attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
                var weaponBonus = attackDamage == null
                        ? 0.0f
                        : (float) Math.max(0.0, attackDamage.getValue() - attackDamage.getBaseValue());
                var system = AbilitySystemServer.getSystem(player);
                var damage = TeleportDamage.threatening(
                        BASE_DAMAGE,
                        weaponBonus,
                        system.getPlayerDamageMultiplier(player.getUUID()),
                        Skills.SPACE_FOLDING_THEOREM.get().isEnabled(player)
                );

                player.level().playSound(null, target.blockPosition(), SoundEvents.THREATENING_TELEPORT.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                target.hurtServer(player.level(), SkillDamageSource.of(player, Skills.THREATENING_TELEPORT.get()), damage);
            });
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
