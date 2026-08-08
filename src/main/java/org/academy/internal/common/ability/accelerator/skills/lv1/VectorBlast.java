package org.academy.internal.common.ability.accelerator.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
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
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.CTADamageUtil;
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

public final class VectorBlast extends Skill {
    static final double RANGE = 64.0;
    static final double BEAM_RADIUS = 1.0;
    static final double ABYSS_TARGET_RANGE = 32.0;
    static final double ABYSS_RADIUS = 8.0;
    static final float BASE_DAMAGE = 10.0f;

    public VectorBlast() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(10)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VECTOR_ACCEL)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        var blastBinding = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE, InputConstants.MOD_SHIFT);
        var pullStartBinding = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.PRESS, InputConstants.MOD_ALT);
        var pullStopBinding = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE, InputConstants.MOD_ALT);
        var pushStartBinding = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.PRESS, InputConstants.MOD_CONTROL);
        var pushStopBinding = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE, InputConstants.MOD_CONTROL);
        var legacyAltBlast = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE, InputConstants.MOD_ALT);
        var reversedPullStart = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.PRESS, InputConstants.MOD_SHIFT);
        var reversedPullStop = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE, InputConstants.MOD_SHIFT);

        var migrated = false;
        if (legacyAltBlast.equals(Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE))) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_USE, blastBinding);
            migrated = true;
        }
        if (reversedPullStart.equals(Client.CONFIG.getKeyBinding(Client.KEY_NAME_PULL_START))) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_PULL_START, pullStartBinding);
            migrated = true;
        }
        if (reversedPullStop.equals(Client.CONFIG.getKeyBinding(Client.KEY_NAME_PULL_STOP))) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_PULL_STOP, pullStopBinding);
            migrated = true;
        }
        if (migrated) {
            AcademyCraftClient.Config.INSTANCE.setConfig(key, Client.CONFIG);
            AcademyCraftClient.Config.INSTANCE.save();
        }

        InputSystem.addKeyBinding(
                Client.KEY_NAME_USE,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE, blastBinding),
                Client::use
        );
        InputSystem.addKeyBinding(Client.KEY_NAME_PULL_START,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_PULL_START, pullStartBinding),
                ignored -> Client.sendControl(UsePacket.Action.PULL_START));
        InputSystem.addKeyBinding(Client.KEY_NAME_PULL_STOP,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_PULL_STOP, pullStopBinding),
                ignored -> Client.sendControl(UsePacket.Action.PULL_STOP));
        InputSystem.addKeyBinding(Client.KEY_NAME_PUSH_START,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_PUSH_START, pushStartBinding),
                ignored -> Client.sendControl(UsePacket.Action.PUSH_START));
        InputSystem.addKeyBinding(Client.KEY_NAME_PUSH_STOP,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_PUSH_STOP, pushStopBinding),
                ignored -> Client.sendControl(UsePacket.Action.PUSH_STOP));

        // InputSystem also persists a global copy used by the terminal settings app. Migrate that
        // copy after registration so an old Alt-attack binding cannot override the skill config.
        if (legacyAltBlast.equals(InputSystem.getKeyBinding(Client.KEY_NAME_USE))) {
            InputSystem.setKeyBinding(Client.KEY_NAME_USE, blastBinding);
        }
        if (reversedPullStart.equals(InputSystem.getKeyBinding(Client.KEY_NAME_PULL_START))) {
            InputSystem.setKeyBinding(Client.KEY_NAME_PULL_START, pullStartBinding);
        }
        if (reversedPullStop.equals(InputSystem.getKeyBinding(Client.KEY_NAME_PULL_STOP))) {
            InputSystem.setKeyBinding(Client.KEY_NAME_PULL_STOP, pullStopBinding);
        }
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    static boolean isInsideBeam(Vec3 origin, Vec3 direction, Vec3 target, double length) {
        if (direction.lengthSqr() <= 1.0e-6 || length <= 0.0) return false;
        var normalized = direction.normalize();
        var relative = target.subtract(origin);
        var forward = relative.dot(normalized);
        if (forward <= 0.0 || forward >= length) return false;
        return relative.subtract(normalized.scale(forward)).lengthSqr() <= BEAM_RADIUS * BEAM_RADIUS;
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.VECTOR_BLAST.get(), List.of(), R.textures.vector_blast_icon, 30, 60)
        );
        public static final String KEY_NAME_USE = SkillNames.VECTOR_BLAST + "_use";
        public static final String KEY_NAME_PULL_START = SkillNames.VECTOR_BLAST + "_pull_start";
        public static final String KEY_NAME_PULL_STOP = SkillNames.VECTOR_BLAST + "_pull_stop";
        public static final String KEY_NAME_PUSH_START = SkillNames.VECTOR_BLAST + "_push_start";
        public static final String KEY_NAME_PUSH_STOP = SkillNames.VECTOR_BLAST + "_push_stop";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use(InputSystem.BindingContext binding) {
            if ((binding.modifiers() & (InputConstants.MOD_ALT | InputConstants.MOD_CONTROL)) != 0) return;
            sendControl(UsePacket.Action.BLAST);
        }

        private static void sendControl(UsePacket.Action action) {
            var player = Minecraft.getInstance().player;
            if (player == null || ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.VECTOR_BLAST.get())) return;
            MisakaNetworkClient.send(new UsePacket(action));
        }

        public static final class Config extends KeyBindingConfig {
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
        private static final double CONTROL_RANGE = 32.0;
        private static final int CONTROL_COST_INTERVAL = 5;
        private static final Map<UUID, ControlState> ACTIVE_CONTROLS = new ConcurrentHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level() instanceof ServerLevel level)) return;
            switch (packet.action) {
                case BLAST -> Skills.VECTOR_BLAST.get().executeActive(
                        player, (context, actualCost) -> fire(player, level));
                case PULL_START -> startControl(player, level, ControlMode.PULL);
                case PUSH_START -> startControl(player, level, ControlMode.PUSH);
                case PULL_STOP -> stop(player, ControlMode.PULL);
                case PUSH_STOP -> stop(player, ControlMode.PUSH);
            }
        }

        private static void startControl(ServerPlayer player, ServerLevel level, ControlMode mode) {
            var skill = Skills.VECTOR_BLAST.get();
            if (!skill.isEnabled(player)) return;
            var previous = ACTIVE_CONTROLS.put(
                    player.getUUID(), new ControlState(mode, level.getGameTime()));
            if (previous == null || previous.mode != mode) skill.reportTrigger(player);
        }

        private static void stop(ServerPlayer player, ControlMode mode) {
            ACTIVE_CONTROLS.computeIfPresent(player.getUUID(), (ignored, state) ->
                    state.mode == mode ? null : state);
        }

        private static void tick(ServerPlayer player) {
            var state = ACTIVE_CONTROLS.get(player.getUUID());
            if (state == null) return;
            var skill = Skills.VECTOR_BLAST.get();
            if (!player.isAlive() || player.hasDisconnected() || !skill.isEnabled(player)
                    || !(player.level() instanceof ServerLevel level)) {
                ACTIVE_CONTROLS.remove(player.getUUID());
                return;
            }

            var now = level.getGameTime();
            skill.reportActivity(player, false);
            if (now >= state.nextCostTick) {
                if (!skill.executeContinuous(player, (context, actualCost) -> {
                }, false)) {
                    ACTIVE_CONTROLS.remove(player.getUUID());
                    return;
                }
                state.nextCostTick = now + CONTROL_COST_INTERVAL;
            }

            var target = findControlTarget(player, level);
            if (target == null) return;
            var targetCenter = target.getBoundingBox().getCenter();
            var delta = state.mode == ControlMode.PULL
                    ? player.getEyePosition().subtract(targetCenter)
                    : targetCenter.subtract(player.getEyePosition());
            if (delta.lengthSqr() <= 1.0e-6) return;
            var distance = Math.sqrt(delta.lengthSqr());
            var strength = state.mode == ControlMode.PULL
                    ? Math.min(0.48, 0.18 + distance * 0.018)
                    : Math.min(0.62, 0.26 + distance * 0.022);
            var velocity = target.getDeltaMovement().scale(0.35).add(delta.normalize().scale(strength));
            target.setDeltaMovement(velocity);
            skill.reportActivity(player, true);
            target.hurtMarked = true;
            target.resetFallDistance();
            if (target instanceof ServerPlayer targetPlayer) {
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }
        }

        private static LivingEntity findControlTarget(ServerPlayer player, ServerLevel level) {
            var origin = player.getEyePosition();
            var direction = player.getLookAngle().normalize();
            var end = origin.add(direction.scale(CONTROL_RANGE));
            var hit = ProjectileUtil.getEntityHitResult(
                    level,
                    player,
                    origin,
                    end,
                    player.getBoundingBox().expandTowards(direction.scale(CONTROL_RANGE)).inflate(1.25),
                    entity -> entity instanceof LivingEntity living
                            && living != player
                            && living.isAlive()
                            && !living.isSpectator(),
                    0.3f
            );
            return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
        }

        private static void fire(ServerPlayer player, ServerLevel level) {
            var origin = player.getEyePosition();
            var direction = player.getLookAngle();
            if (direction.lengthSqr() <= 1.0e-6) return;
            direction = direction.normalize();
            var end = origin.add(direction.scale(RANGE));

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.0f);
            for (var index = 1; index <= (int) RANGE; index++) {
                var point = origin.add(direction.scale(index));
                level.sendParticles(ParticleTypes.VECTOR_BLAST.get(),
                        point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }

            var system = AbilitySystemServer.getSystem(player);
            var damage = BASE_DAMAGE
                    * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                    * system.getPlayerDamageMultiplier(player.getUUID());
            var source = SkillDamageSource.of(
                    player,
                    Skills.VECTOR_BLAST.get(),
                    org.academy.internal.common.world.damagesource.DamageTypes.VEC
            );
            var search = new AABB(origin, end).inflate(BEAM_RADIUS);
            for (var target : level.getEntitiesOfClass(
                    LivingEntity.class, search, entity -> entity != player && entity.isAlive())) {
                if (isInsideBeam(origin, direction, target.getBoundingBox().getCenter(), RANGE)) {
                    target.hurtServer(level, source, damage);
                }
            }

            if (player.getData(AttachmentTypes.CROSSING_THE_ABYSS_ACTIVE.get())) {
                fireAbyssBlast(player, level, origin, direction, damage, source);
            }
        }

        private static void fireAbyssBlast(ServerPlayer player, ServerLevel level, Vec3 origin,
                                           Vec3 direction, float damage, SkillDamageSource source) {
            var end = origin.add(direction.scale(ABYSS_TARGET_RANGE));
            var entityHit = ProjectileUtil.getEntityHitResult(
                    level, player, origin, end,
                    player.getBoundingBox().expandTowards(direction.scale(RANGE)).inflate(1.0),
                    entity -> entity instanceof LivingEntity living
                            && living != player
                            && living.isAlive(),
                    0.3f
            );

            Vec3 center;
            if (entityHit != null && entityHit.getType() != HitResult.Type.MISS) {
                center = entityHit.getLocation();
            } else {
                var blockHit = level.clip(new ClipContext(
                        origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                center = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
            }

            var area = new AABB(center, center).inflate(ABYSS_RADIUS);
            for (var target : level.getEntitiesOfClass(
                    LivingEntity.class, area, entity -> entity != player && entity.isAlive())) {
                CTADamageUtil.applyCompositeDamage(
                        target,
                        player,
                        SkillDamageSource.of(
                                player,
                                Skills.VECTOR_BLAST.get(),
                                org.academy.internal.common.world.damagesource.DamageTypes.CTA
                        ),
                        damage
                );
            }
        }

        private enum ControlMode {
            PULL,
            PUSH
        }

        private static final class ControlState {
            private final ControlMode mode;
            private long nextCostTick;

            private ControlState(ControlMode mode, long nextCostTick) {
                this.mode = mode;
                this.nextCostTick = nextCostTick;
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = ByteBufCodecs.VAR_INT.map(
                ordinal -> new UsePacket(Action.values()[Math.clamp(ordinal, 0, Action.values().length - 1)]),
                packet -> packet.action.ordinal()
        );
        private final Action action;

        public UsePacket(Action action) {
            this.action = action;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.VECTOR_BLAST_USE.get();
        }

        public enum Action {
            BLAST,
            PULL_START,
            PULL_STOP,
            PUSH_START,
            PUSH_STOP
        }
    }
}
