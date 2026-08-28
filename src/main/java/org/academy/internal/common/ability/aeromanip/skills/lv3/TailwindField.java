package org.academy.internal.common.ability.aeromanip.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeContext;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldSyncPacket;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.client.ability.aeromanip.AeromanipChargeHud;
import org.academy.internal.common.ability.aeromanip.AirflowField;
import org.academy.internal.common.ability.aeromanip.skills.lv2.PneumaticGrasp;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
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
import java.util.WeakHashMap;

/** Three-stage flow field: placed directional, following directional, or following radial. */
public final class TailwindField extends Skill {
    private static final double BASE_RADIUS = 8.0;
    private static final int PLACED_DURATION_TICKS = 120;
    private static final int FOLLOW_DURATION_TICKS = 160;

    public TailwindField() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .iterationTicks(10)
                .maxStacks(3)
                .dependsOn(Skills.PNEUMATIC_GRASP)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3)));
    }

    static Mode modeFor(AeromanipChargeTier tier) {
        return switch (tier) {
            case INSTANT -> Mode.PLACED_DIRECTIONAL;
            case HALF -> Mode.FOLLOW_DIRECTIONAL;
            case FULL -> Mode.FOLLOW_RADIAL;
        };
    }

    static Vec3 flowDirection(Mode mode, Vec3 center, Vec3 facing, Vec3 target) {
        if (mode == Mode.FOLLOW_RADIAL) {
            if (center == null || target == null) return Vec3.ZERO;
            var radial = target.subtract(center);
            return radial.lengthSqr() > 1.0e-8 ? radial.normalize() : Vec3.ZERO;
        }
        if (facing == null || facing.lengthSqr() <= 1.0e-8) return Vec3.ZERO;
        return facing.normalize();
    }

    @Override
    public void initClient() {
        AeromanipFieldSyncPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var binding = Client.CONFIG.getKeyBindingMigratingDefaults(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputSystem.ANY_ACTION, InputConstants.MOD_ALT),
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_T,
                        InputSystem.ANY_ACTION, InputConstants.MOD_ALT));
        if (binding.action() != InputSystem.ANY_ACTION) {
            binding = new InputSystem.KeyCombination(
                    binding.type(), binding.keys(), InputSystem.ANY_ACTION, binding.modifiers(),
                    binding.availableWhenScreen(), binding.unbound());
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, binding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addMaintainedKeyBinding(
                Client.KEY_NAME_CAST, binding, _ -> Client.start(), _ -> Client.stop());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.TAILWIND_FIELD.get(),
                        List.of(PneumaticGrasp.Client.SKILL_INFO),
                        R.textures.tailwind_field_icon,
                        20,
                        104));
        ToggleStatusHud.Companion.registerStateProvider(Skills.TAILWIND_FIELD.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && AeromanipFieldSyncPacket.Client.snapshot().values().stream()
                    .anyMatch(field -> field.ownerId().equals(player.getUUID())
                            && field.type() == AirflowField.Type.TAILWIND);
        });
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum Mode {
        PLACED_DIRECTIONAL,
        FOLLOW_DIRECTIONAL,
        FOLLOW_RADIAL
    }

    public static final class Client {
        /** Keep the legacy config key so existing Alt+T bindings survive the active-skill rewrite. */
        public static final String KEY_NAME_CAST = SkillNames.TAILWIND_FIELD + "_toggle";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void start() {
            if (AbilitySystemClient.canUseSkill(Skills.TAILWIND_FIELD.get())) {
                AeromanipChargeHud.begin(Skills.TAILWIND_FIELD.get());
                MisakaNetworkClient.send(StartPacket.INSTANCE);
            }
        }

        private static void stop() {
            AeromanipChargeHud.end(Skills.TAILWIND_FIELD.get());
            MisakaNetworkClient.send(StopPacket.INSTANCE);
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
        private static final Map<ServerPlayer, ChargeContext> CHARGES = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.TAILWIND_FIELD.get();
            if (CHARGES.containsKey(player) || !skill.isEnabled(player)) return;
            var context = new ChargeContext(player);
            CHARGES.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var context = CHARGES.get(packet.getPacketListener().getPlayer());
            if (context != null) context.release();
        }

        private static final class ChargeContext extends AeromanipChargeContext {
            private ChargeContext(ServerPlayer player) {
                super(player, Skills.TAILWIND_FIELD.get());
            }

            @Override
            protected void onReleased(AeromanipChargeTier tier, long chargeTicks) {
                var skill = Skills.TAILWIND_FIELD.get();
                var mode = modeFor(tier);
                var cp = switch (tier) {
                    case INSTANT -> 25.0f;
                    case HALF -> 35.0f;
                    case FULL -> 45.0f;
                };
                var air = switch (tier) {
                    case INSTANT -> 24.0f;
                    case HALF -> 36.0f;
                    case FULL -> 48.0f;
                };
                skill.executeActiveWithResource(
                        player,
                        _ -> cp * AeromanipConfig.cpMultiplier(player, SkillNames.TAILWIND_FIELD),
                        _ -> air,
                        (_, _) -> createField(player, skill, mode));
            }

            @Override
            protected void onTierReached(AeromanipChargeTier tier) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.AIRFLOW_FIELD.get(),
                        SoundSource.PLAYERS, 0.55f,
                        tier == AeromanipChargeTier.FULL ? 1.45f : 1.2f);
                AeromanipVfx.ring(player.level(),
                        player.position().add(0.0, 0.2, 0.0),
                        tier == AeromanipChargeTier.FULL ? 1.4 : 0.8);
            }

            @Override
            protected void onChargeEnded(boolean released) {
                CHARGES.remove(player, this);
            }
        }

        private static void createField(ServerPlayer player, TailwindField skill, Mode mode) {
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var radius = BASE_RADIUS * (milestone >= 1 ? 1.2 : 1.0)
                    * AeromanipConfig.rangeMultiplier(player, SkillNames.TAILWIND_FIELD);
            var durationBase = mode == Mode.PLACED_DIRECTIONAL
                    ? PLACED_DURATION_TICKS : FOLLOW_DURATION_TICKS;
            var duration = Math.max(1, Math.round(durationBase
                    * (milestone >= 2 ? 1.25f : 1.0f)
                    * AeromanipConfig.durationMultiplier(player, SkillNames.TAILWIND_FIELD)));
            var facing = player.getLookAngle().normalize();
            var field = new AirflowField(
                    UUID.randomUUID(), player.getUUID(), player.level().dimension(),
                    AirflowField.Type.TAILWIND, AirflowField.Shape.SPHERE,
                    player.getBoundingBox().getCenter(), facing,
                    radius, 0.0, milestone >= 3 ? 0.26f : 0.22f,
                    duration, milestone);
            if (mode == Mode.PLACED_DIRECTIONAL) {
                AeromanipFieldManager.activate(player, skill, field,
                        (owner, active, age) -> tickField(owner, active, age, mode));
            } else {
                AeromanipFieldManager.activatePersonal(player, skill, field,
                        (owner, active, age) -> tickField(owner, active, age, mode));
            }
        }

        private static void tickField(ServerPlayer owner, AirflowField field, int age, Mode mode) {
            var center = mode == Mode.PLACED_DIRECTIONAL
                    ? field.center() : owner.getBoundingBox().getCenter();
            var facing = mode == Mode.PLACED_DIRECTIONAL
                    ? field.direction() : owner.getLookAngle().normalize();
            if (mode != Mode.PLACED_DIRECTIONAL && age % 6 == 0) {
                var updated = new AirflowField(
                        field.id(), field.ownerId(), field.dimension(), field.type(), field.shape(),
                        center, facing, field.radius(), field.length(), field.strength(),
                        field.durationTicks(), field.proficiencyMilestone());
                AeromanipFieldSyncPacket.sendToTracking(owner, updated, true);
            }
            var box = new net.minecraft.world.phys.AABB(center, center).inflate(field.radius());
            var cap = ProficiencyPolicy.server(owner).maxBonusEntitiesPerTick();
            EntityMotionGuard.runWithMotionSource(owner, () -> {
                var localHandled = 0;
                for (var target : owner.level().getEntities(owner, box, Entity::isAlive)) {
                    if (localHandled++ >= cap) break;
                    if (target.getBoundingBox().getCenter().distanceToSqr(center)
                            > Math.pow(field.radius() + target.getBbWidth() * 0.5, 2.0)) continue;
                    applyFlow(owner, target, mode, center, facing, field.strength());
                }
            });
            if ((age & 3) == 0) spawnVisual(owner, mode, center, facing, field.radius());
        }

        private static void applyFlow(
                ServerPlayer owner, Entity target, Mode mode,
                Vec3 center, Vec3 facing, float strength
        ) {
            var direction = flowDirection(mode, center, facing,
                    target.getBoundingBox().getCenter());
            if (direction.lengthSqr() <= 1.0e-8) return;
            var force = AeromanipTargeting.forceMultiplier(owner, target);
            if (force <= 0.0) return;
            AeromanipTargeting.accelerateAlong(
                    target, direction, strength * force,
                    (0.9 + strength * 3.0) * force);
            target.resetFallDistance();
        }

        private static void spawnVisual(
                ServerPlayer owner, Mode mode, Vec3 center, Vec3 facing, double radius
        ) {
            if (mode == Mode.FOLLOW_RADIAL) {
                AeromanipVfx.ring(owner.level(), center.add(0.0, 0.25, 0.0), radius * 0.82);
            } else {
                var direction = facing.lengthSqr() > 1.0e-8 ? facing.normalize() : new Vec3(0, 0, 1);
                AeromanipVfx.stream(owner.level(),
                        center.subtract(direction.scale(radius * 0.72)).add(0.0, 0.25, 0.0),
                        direction, radius * 1.44);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.TAILWIND_FIELD_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> {
        public static final StopPacket INSTANCE = new StopPacket();
        public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() {
            return PacketTypes.TAILWIND_FIELD_STOP.get();
        }
    }
}
