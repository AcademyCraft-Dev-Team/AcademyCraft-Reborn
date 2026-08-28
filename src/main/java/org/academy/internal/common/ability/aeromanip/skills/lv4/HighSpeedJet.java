package org.academy.internal.common.ability.aeromanip.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.ability.aeromanip.HighSpeedJetHighlightClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

/** Places persistent block-face or entity-mounted jet nozzles and remotely fires them. */
public final class HighSpeedJet extends Skill {
    private static final double PLACEMENT_RANGE = 8.0;
    private static final double CONTROL_RANGE = 64.0;

    public HighSpeedJet() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .iterationTicks(15)
                .maxStacks(5)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4)));
    }

    static int maximumNozzles(int milestone) {
        return milestone >= 1 ? 12 : 8;
    }

    static int activationDuration(int milestone) {
        return milestone >= 2 ? 60 : 40;
    }

    static float activationCpCost(int nozzleCount) {
        return 8.0f + Math.max(0, nozzleCount) * 2.0f;
    }

    static float activationAirCost(int nozzleCount) {
        return Math.max(0, nozzleCount) * 8.0f;
    }

    static Vec3 towardPlayerDirection(Vec3 playerEye, AABB targetBounds, Vec3 fallback) {
        var direction = playerEye == null || targetBounds == null
                ? Vec3.ZERO
                : playerEye.subtract(targetBounds.getCenter());
        if (direction.lengthSqr() <= 1.0e-8 && fallback != null) direction = fallback;
        return direction.lengthSqr() <= 1.0e-8
                ? new Vec3(0.0, 1.0, 0.0)
                : direction.normalize();
    }

    public static Vec3 entityThrustDirection(Vec3 nozzleDirection) {
        return nozzleDirection == null || nozzleDirection.lengthSqr() <= 1.0e-8
                ? Vec3.ZERO
                : nozzleDirection.normalize().scale(-1.0);
    }

    @Override
    public void initClient() {
        HighSpeedJetHighlightClient.init();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultPlacementBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_G,
                InputSystem.ANY_ACTION,
                InputConstants.MOD_ALT);
        var placementBinding = Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_PLACE, defaultPlacementBinding);
        if (placementBinding.action() != InputSystem.ANY_ACTION) {
            placementBinding = new InputSystem.KeyCombination(
                    placementBinding.type(), placementBinding.keys(), InputSystem.ANY_ACTION,
                    placementBinding.modifiers(), placementBinding.availableWhenScreen(),
                    placementBinding.unbound());
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_PLACE, placementBinding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addMaintainedKeyBinding(
                Client.KEY_NAME_PLACE,
                placementBinding,
                _ -> Client.startPlacement(),
                _ -> Client.finishPlacement());
        InputSystem.addKeyBinding(
                Client.KEY_NAME_ACTIVATE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_ACTIVATE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H, InputConstants.RELEASE,
                                InputConstants.MOD_ALT)),
                _ -> Client.activate());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilitySystemClient.addSkillInfo(
                        AbilityCategories.AEROMANIP.get(),
                        new AbilitySystemClient.SkillInfo(
                                Skills.HIGH_SPEED_JET.get(),
                                List.of(),
                                R.textures.high_speed_jet_icon,
                                40,
                                136));
        public static final String KEY_NAME_PLACE = SkillNames.HIGH_SPEED_JET + "_place";
        public static final String KEY_NAME_ACTIVATE = SkillNames.HIGH_SPEED_JET + "_activate";
        public static Config CONFIG = new Config();
        private static PlacementContext placementContext;

        private Client() {
        }

        private static void startPlacement() {
            if (placementContext != null || ClientUtil.hasScreen()
                    || !AbilitySystemClient.canToggleSkill(Skills.HIGH_SPEED_JET.get())
                    || AbilitySystemClient.getSkillData(Skills.HIGH_SPEED_JET.get())
                    .map(data -> !data.isEnabled()).orElse(true)) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            placementContext = new PlacementContext(player);
            AbilitySystemClient.registerContext(placementContext);
        }

        private static void finishPlacement() {
            var context = placementContext;
            if (context == null) return;
            var hasTarget = context.hasTarget();
            context.cleanup();
            if (hasTarget && !ClientUtil.hasScreen()) MisakaNetworkClient.send(PlacePacket.INSTANCE);
        }

        private static void activate() {
            if (AbilitySystemClient.canUseSkill(Skills.HIGH_SPEED_JET.get())) {
                MisakaNetworkClient.send(ActivatePacket.INSTANCE);
            }
        }

        private static final class PlacementContext extends ClientContext {
            private final LocalPlayer player;
            private PlacementTarget target;

            private PlacementContext(LocalPlayer player) {
                this.player = player;
                updateTarget(0.0f);
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (placementContext != this || player.isRemoved() || ClientUtil.hasScreen()
                        || !AbilitySystemClient.canToggleSkill(Skills.HIGH_SPEED_JET.get())) {
                    cleanup();
                    return;
                }
                var target = updateTarget(event.getPartialTick());
                if (!(target instanceof BlockPlacement block)) return;
                var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
                var matrices = event.getMatrixStack();
                matrices.pushPose();
                matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
                event.submitCustomGeometry(Render.RenderTypes.MINE_DETECT_LINES, (snapshot, consumer) -> {
                    var state = player.level().getBlockState(block.pos());
                    var shape = state.getShape(player.level(), block.pos());
                    var box = shape.isEmpty()
                            ? new AABB(block.pos())
                            : shape.bounds().move(block.pos());
                    LineBoxRenderer.renderFace(
                            snapshot, consumer, box, block.face(), 1.0f, 1.0f, 1.0f, 1.0f);
                });
                matrices.popPose();
            }

            private PlacementTarget updateTarget(float partialTick) {
                target = resolvePlacementTarget(player, partialTick);
                HighSpeedJetHighlightClient.setPreviewEntity(
                        target instanceof EntityPlacement entity ? entity.entity() : null);
                return target;
            }

            private boolean hasTarget() {
                var partialTick = Minecraft.getInstance().getDeltaTracker()
                        .getGameTimeDeltaPartialTick(false);
                return updateTarget(partialTick) != null;
            }

            private void cleanup() {
                HighSpeedJetHighlightClient.clearPreview();
                AbilitySystemClient.unregisterContext(this);
                if (placementContext == this) placementContext = null;
            }
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

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
        public static void handlePlace(PlacePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.HIGH_SPEED_JET.get();
            if (!(player.level() instanceof ServerLevel level) || !skill.isEnabled(player)) return;
            var target = resolvePlacementTarget(player, 0.0f);
            if (target == null) return;
            var loaded = ownedNozzles(level, player);
            var existing = loaded.stream()
                    .filter(nozzle -> matches(nozzle, target))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                existing.discard();
                return;
            }
            var configuredMaximum = resolvedMaximumNozzles(player, skill);
            if (loaded.size() >= configuredMaximum) return;
            if (target instanceof BlockPlacement block
                    && !level.getBlockState(block.pos())
                    .isFaceSturdy(level, block.pos(), block.face())) return;
            skill.executeActiveWithResource(
                    player,
                    _ -> 18.0f * AeromanipConfig.cpMultiplier(player, SkillNames.HIGH_SPEED_JET),
                    _ -> 12.0f,
                    (_, _) -> {
                        var nozzle = new HighSpeedJetNozzle(
                                EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), level);
                        if (target instanceof BlockPlacement block) {
                            nozzle.attach(player.getUUID(), block.pos(), block.face());
                        } else if (target instanceof EntityPlacement entity) {
                            nozzle.attach(
                                    player.getUUID(),
                                    entity.entity(),
                                    towardPlayerDirection(
                                            player.getEyePosition(),
                                            entity.entity().getBoundingBox(),
                                            player.getLookAngle()));
                        }
                        level.addFreshEntity(nozzle);
                    });
        }

        @SubscribePacket
        public static void handleActivate(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.HIGH_SPEED_JET.get();
            if (!(player.level() instanceof ServerLevel level) || !skill.isEnabled(player)) return;
            var nozzles = ownedNozzles(level, player);
            if (nozzles.isEmpty()) return;
            var maximum = resolvedMaximumNozzles(player, skill);
            if (nozzles.size() > maximum) nozzles = nozzles.subList(0, maximum);
            var resolvedNozzles = List.copyOf(nozzles);
            var count = resolvedNozzles.size();
            var duration = Math.max(1, Math.round(
                    activationDuration(skill.getEffectiveProficiencyMilestone(player))
                            * AeromanipConfig.durationMultiplier(
                            player, SkillNames.HIGH_SPEED_JET)));
            skill.executeActiveWithResource(
                    player,
                    _ -> activationCpCost(count)
                            * AeromanipConfig.cpMultiplier(player, SkillNames.HIGH_SPEED_JET),
                    _ -> activationAirCost(count),
                    (_, _) -> resolvedNozzles.forEach(nozzle -> nozzle.activate(duration)));
        }

        private static List<HighSpeedJetNozzle> ownedNozzles(
                ServerLevel level,
                ServerPlayer player
        ) {
            var range = CONTROL_RANGE
                    * AeromanipConfig.rangeMultiplier(player, SkillNames.HIGH_SPEED_JET);
            return level.getEntitiesOfClass(
                    HighSpeedJetNozzle.class,
                    player.getBoundingBox().inflate(range),
                    nozzle -> nozzle.isOwnedBy(player));
        }

        private static int resolvedMaximumNozzles(ServerPlayer player, HighSpeedJet skill) {
            var configuredBaseMaximum = Math.round(AeromanipConfig.skillFloat(
                    player, SkillNames.HIGH_SPEED_JET, "maximumNozzles", 8.0f));
            return Math.max(1, Math.min(32,
                    configuredBaseMaximum
                            + (skill.getEffectiveProficiencyMilestone(player) >= 1 ? 4 : 0)));
        }

        private static boolean matches(HighSpeedJetNozzle nozzle, PlacementTarget target) {
            if (target instanceof BlockPlacement block) {
                return nozzle.isAttachedTo(block.pos(), block.face());
            }
            return target instanceof EntityPlacement entity
                    && nozzle.isAttachedTo(entity.entity());
        }
    }

    private static PlacementTarget resolvePlacementTarget(Player player, float partialTick) {
        if (player == null) return null;
        var eye = player.getEyePosition(partialTick);
        var look = player.getViewVector(partialTick);
        if (look.lengthSqr() <= 1.0e-8) return null;
        var end = eye.add(look.normalize().scale(PLACEMENT_RANGE));
        var blockHit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        var rayEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        var entityHit = ProjectileUtil.getEntityHitResult(
                player.level(), player, eye, rayEnd,
                new AABB(eye, rayEnd).inflate(0.6),
                entity -> entity != player && entity.isAlive() && entity.isPickable()
                        && !(entity instanceof HighSpeedJetNozzle),
                0.2f);
        if (entityHit != null) return new EntityPlacement(entityHit.getEntity());
        if (blockHit instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return new BlockPlacement(hit.getBlockPos().immutable(), hit.getDirection());
        }
        return null;
    }

    private sealed interface PlacementTarget permits BlockPlacement, EntityPlacement {
    }

    private record BlockPlacement(BlockPos pos, Direction face) implements PlacementTarget {
    }

    private record EntityPlacement(Entity entity) implements PlacementTarget {
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class PlacePacket
            extends Packet<ServerGamePacketListenerImpl, PlacePacket> {
        public static final PlacePacket INSTANCE = new PlacePacket();
        public static final StreamCodec<ByteBuf, PlacePacket> CODEC = StreamCodec.unit(INSTANCE);

        private PlacePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, PlacePacket> getPacketType() {
            return PacketTypes.HIGH_SPEED_JET_PLACE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket
            extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.HIGH_SPEED_JET_ACTIVATE.get();
        }
    }
}
