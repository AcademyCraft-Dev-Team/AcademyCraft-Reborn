package org.academy.internal.common.ability.meltdowner.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
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
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.academy.internal.common.ability.meltdowner.skills.RadiationIntensify;
import org.academy.internal.common.ability.meltdowner.skills.lv2.ScatterBomb;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class AutoCruiseBeamCannon extends Skill {
    static final int DETECT_INTERVAL_TICKS = 10;
    static final int FIRE_INTERVAL_TICKS = 2;
    static final int DAMAGE_DELAY_TICKS = HighSpeedElectronBeam.MAX_CHARGE_TICKS;
    static final double SCAN_RADIUS = 16.0;
    static final float BASE_DAMAGE = 10.0f;
    static final float MAX_HEALTH_DAMAGE_RATIO = 0.01f;

    public AutoCruiseBeamCannon() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(10)
                .iterationTicks(FIRE_INTERVAL_TICKS)
                .maxStacks(Skill.NO_STACK_LIMIT)
                .initiallyDisabled()
                .maintenanceCost(50)
                .dependsOn(Skills.SCATTER_BOMB)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Scatter Bomb",
                        "academy:scatter_bomb"
                ))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y, InputConstants.RELEASE, 0)
        ), ctx -> Client.toggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MELTDOWNER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.AUTO_CRUISE_BEAM_CANNON.get(),
                        List.of(ScatterBomb.Client.SKILL_INFO),
                        R.textures.auto_cruise_beam_cannon_icon,
                        75,
                        45
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.AUTO_CRUISE_BEAM_CANNON + "_toggle";
        public static Config CONFIG = new Config();

        private static void toggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.AUTO_CRUISE_BEAM_CANNON.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
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
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.AUTO_CRUISE_BEAM_CANNON.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) Events.STATES.remove(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private static final Map<Player, State> STATES = new WeakHashMap<>();
        private static final Map<Player, List<PendingShot>> PENDING = new WeakHashMap<>();

        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!player.isAlive() || player.hasDisconnected()) {
                STATES.remove(player);
                PENDING.remove(player);
                return;
            }
            applyPending(player);

            var skill = Skills.AUTO_CRUISE_BEAM_CANNON.get();
            if (!skill.isEnabled(player)) {
                STATES.remove(player);
                return;
            }
            var system = AbilitySystemServer.getSystem(player);
            if (!system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(skill.getLevel(player)),
                    skill
            )) {
                if (skill.isEnabled(player)) skill.toggle(player);
                STATES.remove(player);
                return;
            }
            var state = STATES.computeIfAbsent(player, ignored -> new State());
            state.tick(player, skill);
        }

        private static void applyPending(ServerPlayer owner) {
            var pending = PENDING.get(owner);
            if (pending == null || pending.isEmpty()) return;
            var now = owner.level().getGameTime();
            pending.removeIf(shot -> {
                if (now < shot.applyAtTick) return false;
                var target = findTarget(owner, shot.targetId);
                if (target != null) applyShot(owner, target, shot.playerMultiplier);
                return true;
            });
            if (pending.isEmpty()) PENDING.remove(owner);
        }

        private static LivingEntity findTarget(ServerPlayer owner, UUID targetId) {
            for (var level : owner.level().getServer().getAllLevels()) {
                if (level.getEntity(targetId) instanceof LivingEntity living
                        && living.isAlive()
                        && !living.isRemoved()) {
                    return living;
                }
            }
            return null;
        }

        private static void applyShot(ServerPlayer owner, LivingEntity target, float playerMultiplier) {
            if (!(target.level() instanceof ServerLevel level)) return;
            var marked = Skills.RADIATION_INTENSIFY.get().isEnabled(owner)
                    && RadiationIntensify.isMarked(target, level.getGameTime());
            var damage = MeltdownerBeamDamage.calculate(
                    BASE_DAMAGE,
                    MAX_HEALTH_DAMAGE_RATIO,
                    target.getMaxHealth(),
                    playerMultiplier,
                    marked
            );
            var hurt = target.hurtServer(
                    level,
                    SkillDamageSource.of(owner, Skills.AUTO_CRUISE_BEAM_CANNON.get()),
                    damage
            );
            if (hurt && Skills.RADIATION_INTENSIFY.get().isEnabled(owner)) {
                RadiationIntensify.mark(target, level.getGameTime());
            }
        }

        private static void fire(ServerPlayer owner, LivingEntity target, float playerMultiplier) {
            if (!(owner.level() instanceof ServerLevel level)) return;
            spawnVisual(level, owner, target);
            level.playSound(null, owner.blockPosition(),
                    org.academy.internal.common.sounds.SoundEvents.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                    SoundSource.PLAYERS, 0.28f, 1.35f);
            PENDING.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new PendingShot(
                    target.getUUID(),
                    playerMultiplier,
                    level.getGameTime() + DAMAGE_DELAY_TICKS
            ));
        }

        private static void spawnVisual(ServerLevel level, ServerPlayer owner, LivingEntity target) {
            var eye = owner.getEyePosition().add(0, -0.3, 0);
            var center = target.getBoundingBox().getCenter();
            var toTarget = center.subtract(eye);
            var totalLength = toTarget.length();
            if (totalLength <= 1.0e-3) return;
            var direction = toTarget.scale(1.0 / totalLength);
            var spawn = eye.add(direction);
            var ownerBounds = owner.getBoundingBox().inflate(0.2);
            for (var guard = 0; ownerBounds.contains(spawn) && guard < 8; guard++) {
                spawn = spawn.add(direction.scale(0.2));
            }
            var fromSpawn = center.subtract(spawn);
            var distance = fromSpawn.length();
            if (distance <= 1.0e-3) return;
            var yaw = Math.toDegrees(Math.atan2(-fromSpawn.x, fromSpawn.z));
            var pitch = Math.toDegrees(-Math.asin(Math.clamp(fromSpawn.y / distance, -1.0, 1.0)));
            var beam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
            beam.configure(
                    owner,
                    Skills.AUTO_CRUISE_BEAM_CANNON.get(),
                    0.0f,
                    0.0f,
                    0.0f,
                    false,
                    false
            );
            beam.setBeamLength((float) distance);
            beam.setPos(spawn);
            beam.setYRot((float) yaw);
            beam.setXRot((float) pitch);
            level.addFreshEntity(beam);
        }

        private static final class State {
            private long lastDetect = Long.MIN_VALUE / 2;
            private long lastFire = Long.MIN_VALUE / 2;
            private final List<UUID> detected = new ArrayList<>();

            private void tick(ServerPlayer player, AutoCruiseBeamCannon skill) {
                var level = player.level();
                var now = level.getGameTime();
                if (now - lastDetect >= DETECT_INTERVAL_TICKS) {
                    detected.clear();
                    var targets = level.getEntitiesOfClass(
                            LivingEntity.class,
                            player.getBoundingBox().inflate(SCAN_RADIUS),
                            target -> isDetectable(player, target)
                                    && target.distanceToSqr(player) <= SCAN_RADIUS * SCAN_RADIUS
                    );
                    for (var target : targets) detected.add(target.getUUID());
                    lastDetect = now;
                }
                if (now - lastFire < FIRE_INTERVAL_TICKS) return;
                var target = pollRandomTarget(level, player, detected);
                if (target == null) return;
                var multiplier = AbilitySystemServer.getSystem(player)
                        .getPlayerDamageMultiplier(player.getUUID());
                if (skill.executeActive(player, (_, _) -> fire(player, target, multiplier))) {
                    lastFire = now;
                }
            }
        }

        static boolean isDetectable(ServerPlayer player, LivingEntity target) {
            if (target == player || !target.isAlive() || target.isRemoved() || target instanceof Player) {
                return false;
            }
            if (target instanceof TamableAnimal tameable && tameable.isOwnedBy(player)) {
                return false;
            }
            if (player.isAlliedTo(target)) return false;
            return target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == player;
        }

        private static LivingEntity pollRandomTarget(
                ServerLevel level,
                ServerPlayer player,
                List<UUID> detected
        ) {
            while (!detected.isEmpty()) {
                var index = level.getRandom().nextInt(detected.size());
                var entity = level.getEntity(detected.remove(index));
                if (entity instanceof LivingEntity living
                        && isDetectable(player, living)
                        && living.distanceToSqr(player) <= SCAN_RADIUS * SCAN_RADIUS) {
                    return living;
                }
            }
            return null;
        }

        private record PendingShot(UUID targetId, float playerMultiplier, long applyAtTick) {
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.AUTO_CRUISE_BEAM_CANNON_TOGGLE.get();
        }
    }
}
