package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
import org.academy.internal.common.ability.teleport.skills.lv4.QuickLocationTeleport;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;

public final class DefensiveTeleport extends Skill {
    static final double SELECTION_SIZE = 5.0;
    static final double MAX_SELECTION_DISTANCE = 20.0;

    public DefensiveTeleport() {
        super(Builder.of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(20)
                .iterationTicks(10)
                .maxStacks(20)
                .dependsOn(Skills.QUICK_LOCATION_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Quick Location Teleport", "academy:quick_location_teleport")));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                        InputConstants.PRESS, 0)), _ -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_END, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_END,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                        InputConstants.RELEASE, 0)), _ -> Client.end());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DEFENSIVE_TELEPORT.get(),
                        List.of(QuickLocationTeleport.Client.SKILL_INFO),
                        R.textures.defensive_teleport_icon,
                        30,
                        30
                )
        );
        public static final String KEY_NAME_START = SkillNames.DEFENSIVE_TELEPORT + "_start";
        public static final String KEY_NAME_END = SkillNames.DEFENSIVE_TELEPORT + "_end";
        public static Config CONFIG = new Config();
        private static SelectionContext current;

        private Client() {
        }

        private static void start() {
            var player = Minecraft.getInstance().player;
            if (ClientUtil.hasScreen() || player == null || current != null
                    || !AbilitySystemClient.canUseSkill(Skills.DEFENSIVE_TELEPORT.get())) return;
            current = new SelectionContext();
            AbilitySystemClient.registerContext(current);
        }

        private static void end() {
            if (current == null) return;
            var center = current.center;
            current.cleanup();
            if (!ClientUtil.hasScreen()) MisakaNetworkClient.send(new TogglePacket(center));
        }

        private static final class SelectionContext extends ClientContext {
            private double distance = 6.0;
            private Vec3 center = Vec3.ZERO;

            @SubscribeEvent
            public void onScroll(MouseScrollEvent event) {
                distance = Mth.clamp(distance + event.yOffset, 2.5, MAX_SELECTION_DISTANCE);
                event.setCanceled(true);
            }

            @SubscribeEvent
            public void onRender(LevelRenderEvent event) {
                var minecraft = Minecraft.getInstance();
                var player = minecraft.player;
                if (player == null || player.isRemoved()) {
                    cleanup();
                    return;
                }
                var partialTick = event.getPartialTick();
                center = player.getEyePosition(partialTick)
                        .add(player.getViewVector(partialTick).scale(distance));
                var size = AbilitySystemClient.getSkillProficiencyMilestone(Skills.DEFENSIVE_TELEPORT.get()) >= 2
                        ? 7.0 : SELECTION_SIZE;
                var half = size / 2.0;
                var box = new AABB(center.x - half, center.y - half, center.z - half,
                        center.x + half, center.y + half, center.z + half);
                var selected = minecraft.level == null ? List.<Entity>of()
                        : minecraft.level.getEntities(player, box,
                        entity -> isPreviewThreat(player, entity));
                var camera = minecraft.gameRenderer.mainCamera().position();
                var matrices = event.getMatrixStack();
                matrices.pushPose();
                matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
                event.submitCustomGeometry(Render.RenderTypes.MINE_DETECT_LINES,
                        (snapshot, consumer) -> {
                            LineBoxRenderer.renderWireframeBox(
                                    snapshot, consumer, box, 1.0f, 0.25f, 0.75f, 1.0f);
                            for (var entity : selected) {
                                LineBoxRenderer.renderWireframeBox(
                                        snapshot, consumer, entity.getBoundingBox().inflate(0.04),
                                        1.0f, 1.0f, 1.0f, 1.0f);
                            }
                        });
                matrices.popPose();
            }

            private static boolean isPreviewThreat(Player player, Entity entity) {
                if (entity == player || !entity.isAlive() || entity.isRemoved()) return false;
                if (entity instanceof Projectile projectile) {
                    var owner = projectile.getOwner();
                    return owner != player && (owner == null || !player.isAlliedTo(owner));
                }
                if (!(entity instanceof LivingEntity living) || player.isAlliedTo(living)) return false;
                if (living instanceof Player target) {
                    return !target.isCreative() && !target.isSpectator();
                }
                return living instanceof Enemy || living instanceof Mob mob && mob.getTarget() == player;
            }

            private void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (current == this) current = null;
            }
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
        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var center = packet.center;
            if (!finite(center) || center.distanceToSqr(player.getEyePosition())
                    > MAX_SELECTION_DISTANCE * MAX_SELECTION_DISTANCE) return;
            var mark = LocationTeleport.Server.getDefensiveMark(player);
            if (mark == null) return;
            var destinationLevel = LocationTeleport.Server.resolveLevel(player, mark);
            if (destinationLevel == null || !player.level().hasChunkAt(BlockPos.containing(center))) {
                return;
            }
            LocationTeleport.Server.forceDestinationChunk(destinationLevel, mark.x(), mark.z(),
                    "defensive_teleport_" + player.getStringUUID());
            destinationLevel.getChunk(mark.x() >> 4, mark.z() >> 4);
            var destination = new Vec3(mark.x() + 0.5, mark.y() + 0.5, mark.z() + 0.5);
            var skill = Skills.DEFENSIVE_TELEPORT.get();
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var size = milestone >= 2 ? 7.0 : SELECTION_SIZE;
            var half = size / 2.0;
            var selection = new AABB(center.x - half, center.y - half, center.z - half,
                    center.x + half, center.y + half, center.z + half);
            var selected = player.level().getEntities(player, selection,
                    entity -> isSelectedThreat(player, entity));
            if (selected.isEmpty()) return;

            skill.executeActive(player, (ctx, _) -> {
                var index = 0;
                for (var entity : selected) {
                    if (!EntityMotionGuard.canApplyMotionFrom(player, entity)) continue;
                    var offsetX = index % 3 - 1;
                    var offsetZ = index / 3 % 3 - 1;
                    var desired = destination.add(offsetX * 0.55, 0, offsetZ * 0.55);
                    var safe = TeleportSafety.findSafe(entity, destinationLevel, desired);
                    if (safe == null) continue;
                    if (entity.level() == destinationLevel) {
                        TeleportSync.teleportInstantly(entity, safe);
                    } else {
                        entity.teleportTo(destinationLevel, safe.x, safe.y, safe.z,
                                Set.of(), entity.getYRot(), entity.getXRot(), false);
                    }
                    entity.resetFallDistance();
                    if (ctx.milestone() >= 3) {
                        if (entity instanceof Projectile projectile) {
                            projectile.setOwner(null);
                            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.5));
                        } else if (entity instanceof LivingEntity living) {
                            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
                        }
                    }
                    index++;
                }
            });
        }

        static boolean isSelectedThreat(ServerPlayer player, Entity entity) {
            if (entity == player || !entity.isAlive() || entity.isRemoved()) return false;
            if (!EntityMotionGuard.canApplyMotionFrom(player, entity)) return false;
            if (entity instanceof Projectile projectile) {
                var owner = projectile.getOwner();
                return owner != player && (!player.isAlliedTo(owner));
            }
            if (!(entity instanceof LivingEntity living) || player.isAlliedTo(living)) return false;
            if (living instanceof ServerPlayer target) {
                return !target.isCreative() && !target.isSpectator();
            }
            return living instanceof Enemy || living instanceof Mob mob && mob.getTarget() == player;
        }

        private static boolean finite(Vec3 value) {
            return value != null && Double.isFinite(value.x)
                    && Double.isFinite(value.y) && Double.isFinite(value.z);
        }
    }

    /**
     * Compatibility helper retained for existing behavioral tests.
     */
    public static final class Events {
        private Events() {
        }

        static boolean isHeadingToward(Vec3 velocity, Vec3 toPlayer) {
            return velocity.lengthSqr() > 1.0e-8 && velocity.dot(toPlayer) > 0;
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = Vec3.STREAM_CODEC
                .map(TogglePacket::new, packet -> packet.center);
        private final Vec3 center;

        public TogglePacket(Vec3 center) {
            this.center = center;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.DEFENSIVE_TELEPORT_TOGGLE.get();
        }
    }
}
