package org.academy.internal.common.ability.meltdowner.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.LightShieldEffectRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.common.sounds.SoundEvents;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class LightShield extends Skill {
    static final int CP_INTERVAL_TICKS = 2;
    static final int ATTACK_INTERVAL_TICKS = 4;
    static final double ATTACK_RADIUS = 3.5;
    static final float BASE_DAMAGE = 3.0f;
    private static final String LEGACY_ELECTRON_BARRIER = "academy:electron_barrier";
    private static final List<String> REMOVED_SKILLS = List.of(
            "academy:trace_ring",
            "academy:homing_blast"
    );

    public LightShield() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .iterationTicks(0)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Single High-Speed Electron Beam",
                        "academy:single_high_speed_electron_beam"
                ))
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(LightShieldEffectRenderer.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        if (!Client.CONFIG.containsKeyBinding(Client.KEY_NAME_END)
                && Client.CONFIG.containsKeyBinding(Client.OLD_KEY_NAME_STOP)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_END,
                    Client.CONFIG.getKeyBinding(Client.OLD_KEY_NAME_STOP));
        }
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H, InputConstants.PRESS, 0)
        ), _ -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_END, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_END,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H, InputConstants.RELEASE, 0)
        ), _ -> Client.stop());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MELTDOWNER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.LIGHT_SHIELD.get(),
                        List.of(SingleHighSpeedElectronBeam.Client.SKILL_INFO),
                        R.textures.light_shield_icon,
                        55,
                        15
                )
        );
        public static final String KEY_NAME_START = SkillNames.LIGHT_SHIELD + "_start";
        public static final String KEY_NAME_END = SkillNames.LIGHT_SHIELD + "_end";
        private static final String OLD_KEY_NAME_STOP = SkillNames.LIGHT_SHIELD + "_stop";
        public static Config CONFIG = new Config();
        private static SoundInstance loopSound;
        private static boolean enabled;

        private static void start() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.LIGHT_SHIELD.get())) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            enabled = true;
            player.level().playLocalSound(
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHT_SHIELD_STARTUP.get(), SoundSource.PLAYERS,
                    1.0f, 1.0f, false);
            stopLoopSound();
            loopSound = new LoopingPlayerSoundInstance(
                    player, SoundEvents.LIGHT_SHIELD_LOOP.get(), 0.8f, 1.0f,
                    () -> enabled && Minecraft.getInstance().player == player);
            Minecraft.getInstance().getSoundManager().play(loopSound);
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void stop() {
            enabled = false;
            stopLoopSound();
            MisakaNetworkClient.send(StopPacket.INSTANCE);
        }

        private static void stopLoopSound() {
            if (loopSound == null) return;
            Minecraft.getInstance().getSoundManager().stop(loopSound);
            loopSound = null;
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
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Events.migrateLegacySkills(player);
            var skill = Skills.LIGHT_SHIELD.get();
            if (!skill.isEnabled(player) || CONTEXT_MAP.containsKey(player)) return;
            var context = new Context(player);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var context = CONTEXT_MAP.get(packet.getPacketListener().getPlayer());
            if (context != null) context.end();
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private static final Set<ServerPlayer> MIGRATED_PLAYERS =
                Collections.newSetFromMap(new WeakHashMap<>());

        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) migrateLegacySkills(player);
        }

        private static void migrateLegacySkills(ServerPlayer player) {
            if (MIGRATED_PLAYERS.contains(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            var playerData = system.getPlayerData(player.getUUID());
            if (playerData == null) return;
            var map = playerData.getSkillDataMap();
            var changed = false;

            var legacyBarrier = map.remove(LEGACY_ELECTRON_BARRIER);
            if (legacyBarrier != null) {
                var skill = Skills.LIGHT_SHIELD.get();
                var target = map.get(skill.getKeyString());
                if (target == null) {
                    target = skill.createData(player);
                    map.put(skill.getKeyString(), target);
                }
                mergeProgress(target, legacyBarrier);
                target.setEnabled(target.isEnabled() || legacyBarrier.isEnabled());
                system.releaseMaintenanceOccupation(player.getUUID(), LEGACY_ELECTRON_BARRIER);
                changed = true;
            }

            for (var removedKey : REMOVED_SKILLS) {
                if (map.remove(removedKey) != null) changed = true;
                system.releaseMaintenanceOccupation(player.getUUID(), removedKey);
            }
            if (changed) {
                playerData.markDirty();
                system.getSyncManager().schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
            }
            MIGRATED_PLAYERS.add(player);
        }

        private static void mergeProgress(SkillData target, SkillData legacy) {
            target.setLevel(Math.max(target.getLevel(), legacy.getLevel()));
            target.setMaxExp(Math.max(target.getMaxExp(), legacy.getMaxExp()));
            target.setExp(Math.max(target.getExp(), legacy.getExp()));
        }
    }

    public static final class Context extends ServerContext {
        private final ServerLevel initialLevel;
        private int ticks;
        private boolean ended;

        private Context(ServerPlayer player) {
            super(player);
            initialLevel = player.level();
            player.setData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get(), true);
            player.syncData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get());
            initialLevel.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.LIGHT_SHIELD_STARTUP.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.LIGHT_SHIELD.get();
            if (player.level() != initialLevel
                    || !skill.isEnabled(player)
                    || !player.isAlive()
                    || player.hasDisconnected()) {
                end();
                return;
            }

            ticks++;
            var system = AbilitySystemServer.getSystem(player);
            if (ticks % CP_INTERVAL_TICKS == 0
                    && !system.tryTimedOccupation(player.getUUID(), 5.0f, skill)) {
                end();
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10, 1, false, false, true));
            destroyIncomingProjectiles();
            var abilityPower = system.getPlayerAbilityPowerMultiplier(player.getUUID());
            var playerMultiplier = system.getPlayerDamageMultiplier(player.getUUID());
            if (ticks % ATTACK_INTERVAL_TICKS == 0) {
                applyRadialPulse(skill, abilityPower, playerMultiplier);
            }
        }

        private void destroyIncomingProjectiles() {
            var origin = player.getEyePosition();
            var forward = player.getLookAngle().normalize();
            for (var projectile : initialLevel.getEntitiesOfClass(
                    Projectile.class,
                    player.getBoundingBox().inflate(3.0),
                    projectile -> projectile.isAlive() && projectile.getOwner() != player
            )) {
                var delta = projectile.position().subtract(origin);
                if (delta.lengthSqr() <= 9.0 && delta.dot(forward) > 0.0) {
                    projectile.discard();
                }
            }
        }

        private void applyRadialPulse(LightShield skill, float abilityPower, float playerMultiplier) {
            var damage = calculateDamage(abilityPower, playerMultiplier);
            var source = SkillDamageSource.of(player, skill);
            var targets = initialLevel.getEntitiesOfClass(
                    Mob.class,
                    player.getBoundingBox().inflate(ATTACK_RADIUS),
                    mob -> mob.isAlive()
                            && mob.getType().getCategory() == MobCategory.MONSTER
                            && !player.isAlliedTo(mob)
            );
            for (var target : targets) {
                var delta = target.position().subtract(player.position());
                var direction = new Vec3(delta.x, 0.0, delta.z);
                if (direction.lengthSqr() <= 1.0e-8) {
                    var look = player.getLookAngle().scale(-1.0);
                    direction = new Vec3(look.x, 0.0, look.z);
                }
                if (direction.lengthSqr() > 1.0e-8) {
                    direction = direction.normalize().scale(0.65);
                    target.push(direction.x, 0.25, direction.z);
                }
                target.hurtServer(initialLevel, source, damage);
            }
        }

        private void end() {
            if (ended) return;
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            player.setData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get(), false);
            player.syncData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get());
            Server.CONTEXT_MAP.remove(player, this);
        }
    }

    static float calculateDamage(float abilityPower, float playerMultiplier) {
        return MeltdownerBeamDamage.calculate(
                BASE_DAMAGE * Math.max(0.0f, abilityPower),
                0.0f,
                0.0f,
                playerMultiplier,
                false
        );
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.LIGHT_SHIELD_START.get();
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
            return PacketTypes.LIGHT_SHIELD_STOP.get();
        }
    }
}
