package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
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
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.world.damagesource.CTAEntityActuallyHurt;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.ReflectedSkillDamageSource;
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

import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public final class CrossingTheAbyss extends Skill {
    private static final float RESERVED_CP = 50.0f;

    public CrossingTheAbyss() {
        super(Builder.of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(RESERVED_CP)
                .iterationTicks(40)
                .maxStacks(1)
                .dependsOn(Skills.WHITE_WING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("White Wing", "academy:white_wing")));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, GLFW_KEY_L, GLFW_RELEASE, GLFW_MOD_ALT)
        ), _ -> Client.toggle());
        ToggleStatusHud.registerStateProvider(Skills.CROSSING_THE_ABYSS.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.CROSSING_THE_ABYSS_ACTIVE.get());
        });
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.CROSSING_THE_ABYSS.get(),
                        List.of(WhiteWing.Client.SKILL_INFO),
                        R.textures.crossing_the_abyss_icon,
                        180, 50
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.CROSSING_THE_ABYSS + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.CROSSING_THE_ABYSS.get())) return;
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
        private static final int REQUIRED_SURVIVED_LETHAL_RECORDS = 3;
        private static final Map<UUID, PendingHit> PENDING_HITS = new ConcurrentHashMap<>();
        private static final Map<UUID, HealLock> HEAL_LOCKS = new ConcurrentHashMap<>();
        private static final Map<UUID, Integer> SURVIVED_LETHAL = new ConcurrentHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.CROSSING_THE_ABYSS.get();
            skill.toggle(player);
            sync(player, skill.isEnabled(player));
            if (!skill.isEnabled(player)) clearForAttacker(player);
        }

        public static boolean isActive(ServerPlayer player) {
            return player != null
                    && Skills.CROSSING_THE_ABYSS.get().isEnabled(player)
                    && player.getData(AttachmentTypes.CROSSING_THE_ABYSS_ACTIVE.get());
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            var skill = Skills.CROSSING_THE_ABYSS.get();
            var data = skill.getRuntimeData(player).orElse(null);
            if (data != null && data.isEnabled()) {
                var system = AbilitySystemServer.getSystem(player);
                system.toggleSkill(player.getUUID(), skill.getKeyString());
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            }
            sync(player, false);
            clearForAttacker(player);
        }

        private static void tick(ServerPlayer player) {
            var skill = Skills.CROSSING_THE_ABYSS.get();
            var active = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (active) {
                var system = AbilitySystemServer.getSystem(player);
                active = system.ensurePermanentOccupation(
                        player.getUUID(), skill.getMaintenanceCost(skill.getLevel(player)), skill);
                if (!active) forceDeactivate(player);
            } else if (skill.getRuntimeData(player).map(data -> data.isEnabled()).orElse(false)) {
                forceDeactivate(player);
            }
            sync(player, active);
            if (player.tickCount % 20 == 0) cleanup(player);
        }

        private static void sync(ServerPlayer player, boolean active) {
            var attachment = AttachmentTypes.CROSSING_THE_ABYSS_ACTIVE.get();
            if (player.getData(attachment) != active) {
                player.setData(attachment, active);
                player.syncData(attachment);
            }
        }

        private static void recordIncoming(LivingIncomingDamageEvent event) {
            if (event.getEntity().level().isClientSide() || event.isCanceled() || event.getAmount() <= 0) return;
            if (ReflectedSkillDamageSource.isReflected(event.getSource())) return;
            var attacker = resolvePlayer(event.getSource());
            var target = event.getEntity();
            if (attacker == null || !isActive(attacker) || target == attacker
                    || CtaFriendlyFireWhitelist.shouldProtect(attacker, target)) return;
            var trueHealth = CTAEntityActuallyHurt.readTrueHealth(target);
            if (!Float.isFinite(trueHealth) || trueHealth <= 0.0f) return;
            PENDING_HITS.put(target.getUUID(), new PendingHit(
                    attacker.getUUID(), trueHealth, event.getAmount(), target.level().getGameTime()
            ));
            EntityControlApi.capTrueHealthTemporarily(
                    target,
                    Math.max(0.0f, trueHealth - event.getAmount()),
                    2L
            );
            EntityControlApi.banHeal(target, trueHealth);
        }

        private static void commitDamage(LivingDamageEvent.Post event) {
            if (event.getEntity().level().isClientSide()) return;
            if (ReflectedSkillDamageSource.isReflected(event.getSource())) return;
            var target = event.getEntity();
            var pending = PENDING_HITS.remove(target.getUUID());
            if (pending == null) return;
            if (target.level().getGameTime() - pending.gameTime > 2) {
                releasePendingTarget(target);
                return;
            }
            var attacker = resolvePlayer(event.getSource());
            if (attacker == null || !attacker.getUUID().equals(pending.attackerId) || !isActive(attacker)) {
                releasePendingTarget(target);
                return;
            }

            var appliedDamage = event.getHealthDamage();
            var expectedHealth = pending.healthBefore - appliedDamage;
            var observedHealth = CTAEntityActuallyHurt.readTrueHealth(target);
            if (expectedHealth > 0 && observedHealth > expectedHealth) {
                EntityControlApi.forceSetTrueHealth(target, expectedHealth);
            }
            EntityControlApi.clearTemporaryTrueHealthCap(target);
            if (!target.isAlive()) {
                EntityControlApi.allowHeal(target);
                HEAL_LOCKS.remove(target.getUUID());
                SURVIVED_LETHAL.remove(target.getUUID());
                return;
            }

            var ceiling = Math.max(0.0f, CTAEntityActuallyHurt.readTrueHealth(target));
            HEAL_LOCKS.compute(target.getUUID(), (_, existing) -> new HealLock(
                    attacker.getUUID(),
                    existing == null ? ceiling : Math.min(existing.ceiling, ceiling)
            ));
            EntityControlApi.banHeal(target, ceiling);

            if (expectedHealth > 0) return;
            var survived = SURVIVED_LETHAL.merge(target.getUUID(), 1, Integer::sum);
            if (survived >= REQUIRED_SURVIVED_LETHAL_RECORDS) {
                forceGuardedDeath(attacker, target);
                SURVIVED_LETHAL.remove(target.getUUID());
                HEAL_LOCKS.remove(target.getUUID());
                EntityControlApi.allowHeal(target);
            }
        }

        private static void forceGuardedDeath(ServerPlayer attacker, LivingEntity target) {
            var level = (ServerLevel) target.level();
            var source = SkillDamageSource.of(
                    attacker,
                    Skills.CROSSING_THE_ABYSS.get(),
                    net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL
            );
            var trueHealth = CTAEntityActuallyHurt.readTrueHealth(target);
            new CTAEntityActuallyHurt(target).actuallyHurt(
                    source,
                    Float.isFinite(trueHealth) ? Math.max(1.0f, trueHealth + 1.0f) : Float.MAX_VALUE,
                    true
            );
        }

        public static boolean handleHeal(LivingEntity target, float amount) {
            if (target == null || target.level().isClientSide() || amount <= 0) return false;
            var lock = HEAL_LOCKS.get(target.getUUID());
            if (lock == null) return false;
            var attacker = target.level().getServer() == null
                    ? null
                    : target.level().getServer().getPlayerList().getPlayer(lock.attackerId);
            if (!isActive(attacker)) {
                HEAL_LOCKS.remove(target.getUUID(), lock);
                return false;
            }
            var trueHealth = CTAEntityActuallyHurt.readTrueHealth(target);
            if (trueHealth + amount <= lock.ceiling) return false;
            if (trueHealth < lock.ceiling) EntityControlApi.forceSetTrueHealth(target, lock.ceiling);
            return true;
        }

        public static void onLivingTick(LivingEntity target) {
            if (target == null || target.level().isClientSide()) return;
            var pending = PENDING_HITS.get(target.getUUID());
            if (pending != null && target.level().getGameTime() - pending.gameTime > 2
                    && PENDING_HITS.remove(target.getUUID(), pending)) {
                releasePendingTarget(target);
            }
            var lock = HEAL_LOCKS.get(target.getUUID());
            if (lock == null) return;
            var server = target.level().getServer();
            var attacker = server == null ? null : server.getPlayerList().getPlayer(lock.attackerId);
            if (!isActive(attacker) || !target.isAlive()) {
                HEAL_LOCKS.remove(target.getUUID(), lock);
                SURVIVED_LETHAL.remove(target.getUUID());
                EntityControlApi.allowHeal(target);
                EntityControlApi.clearTemporaryTrueHealthCap(target);
                return;
            }
            if (CTAEntityActuallyHurt.readTrueHealth(target) > lock.ceiling) {
                EntityControlApi.forceSetTrueHealth(target, lock.ceiling);
            }
        }

        private static ServerPlayer resolvePlayer(net.minecraft.world.damagesource.DamageSource source) {
            if (source == null) return null;
            if (source.getEntity() instanceof ServerPlayer player) return player;
            if (source.getDirectEntity() instanceof ServerPlayer player) return player;
            if (source.getDirectEntity() instanceof Projectile projectile
                    && projectile.getOwner() instanceof ServerPlayer player) return player;
            return null;
        }

        private static void clearForAttacker(ServerPlayer attacker) {
            var attackerId = attacker.getUUID();
            var targetIds = PENDING_HITS.entrySet().stream()
                    .filter(entry -> entry.getValue().attackerId.equals(attackerId))
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            PENDING_HITS.entrySet().removeIf(entry -> entry.getValue().attackerId.equals(attackerId));
            targetIds.addAll(HEAL_LOCKS.entrySet().stream()
                    .filter(entry -> entry.getValue().attackerId.equals(attackerId))
                    .map(Map.Entry::getKey)
                    .toList());
            HEAL_LOCKS.entrySet().removeIf(entry -> entry.getValue().attackerId.equals(attackerId));
            var server = attacker.level().getServer();
            if (server == null) return;
            for (var level : server.getAllLevels()) {
                for (var targetId : targetIds) {
                    if (level.getEntity(targetId) instanceof LivingEntity living) {
                        EntityControlApi.allowHeal(living);
                        EntityControlApi.clearTemporaryTrueHealthCap(living);
                    }
                }
            }
        }

        private static void releasePendingTarget(LivingEntity target) {
            EntityControlApi.clearTemporaryTrueHealthCap(target);
            if (!HEAL_LOCKS.containsKey(target.getUUID())) EntityControlApi.allowHeal(target);
        }

        private static void cleanup(ServerPlayer player) {
            if (!isActive(player)) clearForAttacker(player);
        }

        private record PendingHit(UUID attackerId, float healthBefore, float damage, long gameTime) {
        }

        private record HealLock(UUID attackerId, float ceiling) {
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

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            Server.recordIncoming(event);
        }

        @SubscribeEvent
        public static void onDamageApplied(LivingDamageEvent.Post event) {
            Server.commitDamage(event);
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
            return PacketTypes.CROSSING_THE_ABYSS_TOGGLE.get();
        }
    }
}
