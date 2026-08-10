package org.academy.internal.common.ability.accelerator.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.config.SkillSettingsRegistry;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseButtonEvent;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.accelerator.skills.lv2.VectorAccel;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.CTADamageUtil;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.ReflectedSkillDamageSource;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.academy.internal.common.world.entity.skill.KineticShockwave;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class KineticEnergyApplied extends Skill {
    public static final int MIN_IMPACT_LEVEL = 1;
    public static final int MAX_IMPACT_LEVEL = 6;
    public static final int DEFAULT_IMPACT_LEVEL = 1;
    private static final float DAMAGE_PER_IMPACT_LEVEL_SQUARED = 4.0f;
    private static final double SERVER_AIR_VERIFY_REACH = 6.0;
    private static final float MAX_VISUAL_RADIUS = 24.0f;
    private static final int MAX_BLOCKS_PER_TASK_TICK = 1024;
    private static final int MAX_SCANS_PER_TASK_TICK = 32768;
    private static final int MAX_TASKS_PER_PLAYER = 2;
    private static final int MIN_IMPACT_TRIGGER_INTERVAL_TICKS = 2;
    private static final String LEGACY_KINETIC_SUPERPOSITION = "academy:kinetic_superposition";
    private static final String LEGACY_DIRECTED_SHOCK = "academy:directed_shock";
    private static final Map<UUID, ArrayDeque<BreakTask>> BREAK_TASKS = new HashMap<>();
    private static final Map<UUID, Long> LAST_IMPACT_TRIGGER_TICKS = new HashMap<>();
    private static final Map<Integer, List<BlockOffset>> SPHERE_OFFSET_CACHE = new HashMap<>();

    public KineticEnergyApplied() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VECTOR_ACCEL)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition("Vector Acceleration", "academy:vector_accel"))
        );
    }

    public static int clampImpactLevel(int level) {
        return Mth.clamp(level, MIN_IMPACT_LEVEL, MAX_IMPACT_LEVEL);
    }

    public static int nextImpactLevel(int level) {
        var current = clampImpactLevel(level);
        return current >= MAX_IMPACT_LEVEL ? MIN_IMPACT_LEVEL : current + 1;
    }

    public static float getImpactRadius(int impactLevel) {
        var level = clampImpactLevel(impactLevel);
        return level * level + 2.0f;
    }

    public static float getImpactDamage(int impactLevel, float abilityPower, float damageMultiplier) {
        var level = clampImpactLevel(impactLevel);
        return level * level * DAMAGE_PER_IMPACT_LEVEL_SQUARED
                * Math.max(0.0f, abilityPower)
                * Math.max(0.0f, damageMultiplier);
    }

    static boolean isDistinctImpactTrigger(long previousTick, long currentTick) {
        return currentTick < previousTick
                || currentTick - previousTick >= MIN_IMPACT_TRIGGER_INTERVAL_TICKS;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        Client.registerSettings();
        if (!Client.CONFIG.containsKeyBinding(Client.KEY_NAME_BLOCK_BREAK)
                && Client.CONFIG.containsKeyBinding(Client.OLD_KEY_NAME_SHOCKWAVE_TOGGLE)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_BLOCK_BREAK,
                    Client.CONFIG.getKeyBinding(Client.OLD_KEY_NAME_SHOCKWAVE_TOGGLE));
        }

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_K,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), ctx -> Client.toggle());
        InputSystem.addKeyBinding(Client.KEY_NAME_BLOCK_BREAK,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_BLOCK_BREAK,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_K,
                                InputConstants.PRESS, 0)),
                ctx -> Client.toggleBlockBreak());
        InputSystem.addKeyBinding(Client.KEY_NAME_IMPACT_LEVEL,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_IMPACT_LEVEL,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_5,
                                InputConstants.PRESS, 0)),
                ctx -> Client.cycleImpactLevel());

        ToggleStatusHud.registerDetailProvider(Skills.KINETIC_ENERGY_APPLIED.get(), Client::statusText);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.KINETIC_ENERGY_APPLIED.get(),
                        List.of(VectorAccel.Client.SKILL_INFO),
                        R.textures.kinetic_energy_applied_icon,
                        118,
                        74
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.KINETIC_ENERGY_APPLIED + "_toggle";
        public static final String KEY_NAME_BLOCK_BREAK = SkillNames.KINETIC_ENERGY_APPLIED + "_block_break_toggle";
        public static final String KEY_NAME_IMPACT_LEVEL = SkillNames.KINETIC_ENERGY_APPLIED + "_impact_level";
        private static final String OLD_KEY_NAME_SHOCKWAVE_TOGGLE =
                SkillNames.KINETIC_ENERGY_APPLIED + "_shockwave_toggle";
        public static Config CONFIG = new Config();
        private static boolean settingsRegistered;

        private Client() {
        }

        private static void registerSettings() {
            if (settingsRegistered) return;
            settingsRegistered = true;
            SkillSettingsRegistry.register(
                    Skills.KINETIC_ENERGY_APPLIED.get(),
                    new SkillSettingsRegistry.Module(
                            "shockwave",
                            "",
                            List.of(new SkillSettingsRegistry.Toggle(
                                    "block_drops",
                                    "app.academy.skill_settings.advanced.kinetic_block_drops",
                                    Client::blockDropsEnabled,
                                    Client::setBlockDropsEnabled
                            ))
                    )
            );
        }

        public static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.KINETIC_ENERGY_APPLIED.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static void toggleBlockBreak() {
            if (!AbilitySystemClient.canToggleSkill(Skills.KINETIC_ENERGY_APPLIED.get())) return;
            MisakaNetworkClient.send(ToggleShockwavePacket.INSTANCE);
        }

        public static void cycleImpactLevel() {
            MisakaNetworkClient.send(CycleImpactLevelPacket.INSTANCE);
        }

        private static boolean blockDropsEnabled() {
            var player = Minecraft.getInstance().player;
            return player == null || player.getData(AttachmentTypes.KINETIC_BLOCK_DROPS_ENABLED.get());
        }

        private static void setBlockDropsEnabled(boolean enabled) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            player.setData(AttachmentTypes.KINETIC_BLOCK_DROPS_ENABLED.get(), enabled);
            MisakaNetworkClient.send(new SetBlockDropsPacket(enabled));
        }

        public static String statusText() {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return Component.translatable("hud.academy.toggle_status.on").getString();
            }
            var blockBreak = player.getData(AttachmentTypes.KINETIC_BLOCK_BREAK_ENABLED.get());
            var impactLevel = clampImpactLevel(player.getData(AttachmentTypes.KINETIC_IMPACT_LEVEL.get()));
            return Component.translatable(
                    "hud.academy.kinetic_energy_applied.detail",
                    Component.translatable("hud.academy.toggle_status.on"),
                    Component.translatable(blockBreak
                            ? "hud.academy.toggle_status.on"
                            : "hud.academy.toggle_status.off"),
                    impactLevel
            ).getString();
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

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onMouseButton(MouseButtonEvent event) {
            if (event.button != GLFW.GLFW_MOUSE_BUTTON_1
                    || event.action != InputConstants.PRESS
                    || event.modifiers != 0) return;

            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (ClientUtil.hasScreen() || player == null) return;
            if (!AbilitySystemClient.canUseSkill(Skills.KINETIC_ENERGY_APPLIED.get())) return;
            if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) return;
            MisakaNetworkClient.send(AttackWavePacket.INSTANCE);
        }
    }

    public static final class Server {
        private static final Set<ServerPlayer> MIGRATED_PLAYERS = Collections.newSetFromMap(new WeakHashMap<>());
        private static final Identifier MOD_MOVEMENT_SPEED = AcademyCraft.academy("kea_movement_speed");
        private static final Identifier MOD_KNOCKBACK_RESISTANCE = AcademyCraft.academy("kea_knockback_resistance");
        private static final Identifier MOD_STEP_HEIGHT = AcademyCraft.academy("kea_step_height");
        private static final Identifier MOD_MOVEMENT_EFFICIENCY = AcademyCraft.academy("kea_movement_efficiency");
        private static final Identifier MOD_EXPLOSION_KNOCKBACK_RESISTANCE = AcademyCraft.academy("kea_explosion_knockback_resistance");
        private static final Identifier MOD_JUMP_STRENGTH = AcademyCraft.academy("kea_jump_strength");

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            Skills.KINETIC_ENERGY_APPLIED.get().toggle(packet.getPacketListener().getPlayer());
        }

        @SubscribePacket
        public static void handleToggleShockwave(ToggleShockwavePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.KINETIC_ENERGY_APPLIED.get().isEnabled(player)) return;
            player.setData(AttachmentTypes.KINETIC_BLOCK_BREAK_ENABLED.get(),
                    !player.getData(AttachmentTypes.KINETIC_BLOCK_BREAK_ENABLED.get()));
        }

        @SubscribePacket
        public static void handleCycleImpactLevel(CycleImpactLevelPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.KINETIC_ENERGY_APPLIED.get().isEnabled(player)) return;
            player.setData(AttachmentTypes.KINETIC_IMPACT_LEVEL.get(),
                    nextImpactLevel(player.getData(AttachmentTypes.KINETIC_IMPACT_LEVEL.get())));
        }

        @SubscribePacket
        public static void handleSetBlockDrops(SetBlockDropsPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            player.setData(AttachmentTypes.KINETIC_BLOCK_DROPS_ENABLED.get(), packet.enabled());
            player.syncData(AttachmentTypes.KINETIC_BLOCK_DROPS_ENABLED.get());
        }

        @SubscribePacket
        public static void handleAttackWave(AttackWavePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!canCreateShockwave(player)) return;
            attackAir(player);
        }

        public static float onProjectileShoot(Projectile projectile, Entity shooter, float velocity) {
            if (!(shooter instanceof ServerPlayer player)) return velocity;
            var skill = Skills.KINETIC_ENERGY_APPLIED.get();
            if (!skill.isEnabled(player) || !player.isAlive() || player.hasDisconnected()) return velocity;
            spawnProjectileEffect(projectile, player);
            return velocity * 2.0f;
        }

        private static boolean canCreateShockwave(ServerPlayer player) {
            return player != null
                    && player.isAlive()
                    && !player.hasDisconnected()
                    && Skills.KINETIC_ENERGY_APPLIED.get().isEnabled(player);
        }

        private static void attackAir(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) return;
            if (!serverSeesAir(player, level)) return;
            if (!claimImpactTrigger(player, level.getGameTime())) return;

            var impactLevel = clampImpactLevel(player.getData(AttachmentTypes.KINETIC_IMPACT_LEVEL.get()));
            var look = normalizeOrDefault(player.getViewVector(1.0f));
            var center = player.getEyePosition().add(look.scale(impactLevel * impactLevel));
            executeImpact(level, player, center, look, impactLevel, null);
        }

        private static boolean claimImpactTrigger(ServerPlayer player, long currentTick) {
            var playerId = player.getUUID();
            var previousTick = LAST_IMPACT_TRIGGER_TICKS.get(playerId);
            if (previousTick != null && !isDistinctImpactTrigger(previousTick, currentTick)) return false;
            LAST_IMPACT_TRIGGER_TICKS.put(playerId, currentTick);
            return true;
        }

        private static void executeImpact(ServerLevel level, ServerPlayer player, Vec3 center,
                                          Vec3 direction, int impactLevel, BlockPos priorityBlock) {
            var system = AbilitySystemServer.getSystem(player);
            if (!system.tryTimedOccupation(
                    player.getUUID(),
                    impactLevel * 10.0f,
                    Skills.KINETIC_ENERGY_APPLIED.get(),
                    20
            )) return;
            var blockRadius = getImpactRadius(impactLevel);
            var radius = Skills.KINETIC_ENERGY_APPLIED.get().hasProficiencyMilestone(player, 2)
                    ? blockRadius * 1.15f
                    : blockRadius;
            var damage = getImpactDamage(
                    impactLevel,
                    system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                    system.getPlayerDamageMultiplier(player.getUUID())
            );
            applyAreaDamage(level, player, center, direction, radius, damage, impactLevel);
            spawnShockwave(level, player, center, direction, radius, impactLevel);
            if (canDestroyBlocks(player)) {
                enqueueBreakTask(level, player, center, blockRadius, direction, impactLevel, priorityBlock);
            }
        }

        private static void applyAreaDamage(ServerLevel level, ServerPlayer player, Vec3 center,
                                            Vec3 direction, float radius, float damage, int impactLevel) {
            var radiusSquared = radius * radius;
            var source = SkillDamageSource.of(
                    player,
                    Skills.KINETIC_ENERGY_APPLIED.get(),
                    org.academy.internal.common.world.damagesource.DamageTypes.CTA
            );
            var targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                    target -> target != player
                            && target.isAlive()
                            && !target.isSpectator()
                            && !CtaFriendlyFireWhitelist.shouldProtect(player, target)
                            && target.getBoundingBox().getCenter().distanceToSqr(center) <= radiusSquared);

            for (var target : targets) {
                CTADamageUtil.applyCompositeDamage(target, player, source, damage);
                target.hurtMarked = true;
                var skill = Skills.KINETIC_ENERGY_APPLIED.get();
                var now = level.getGameTime();
                if (skill.hasProficiencyMilestone(player, 3)
                        && TimedSkillEffectRuntime.get(
                        player.getUUID(), target.getUUID(), skill, "reverberation_cd", now).isEmpty()
                        && TimedSkillEffectRuntime.put(
                        player, target.getUUID(), skill, "reverberation_cd", 20, 0.0f)) {
                    TimedSkillEffectRuntime.schedule(player, 6, () -> {
                        if (!target.isAlive() || target.level() != level) return;
                        target.hurtServer(level, source, damage * 0.3f);
                    });
                }
            }
        }

        private static void spawnShockwave(ServerLevel level, ServerPlayer player, Vec3 center,
                                           Vec3 direction, float radius, int impactLevel) {
            var normalized = normalizeOrDefault(direction);
            var shockwave = new KineticShockwave(EntityTypes.KINETIC_SHOCKWAVE.get(), level);
            shockwave.configure(normalized, Math.min(radius, MAX_VISUAL_RADIUS), impactLevel);
            shockwave.setPos(center.x, center.y, center.z);
            shockwave.setYRot((float) (Mth.atan2(normalized.z, normalized.x) * Mth.RAD_TO_DEG) - 90.0f);
            shockwave.setXRot((float) (-(Mth.atan2(normalized.y,
                    Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * Mth.RAD_TO_DEG)));
            level.addFreshEntity(shockwave);

            var soundPosition = audiblePosition(player, center);
            level.playSound(null, soundPosition.x, soundPosition.y, soundPosition.z,
                    SoundEvents.KINETIC_SHOCKWAVE.get(), SoundSource.PLAYERS,
                    Mth.clamp(0.75f + impactLevel * 0.08f, 0.75f, 1.25f),
                    Mth.clamp(1.08f - impactLevel * 0.025f, 0.9f, 1.08f));
        }

        private static void spawnProjectileEffect(Projectile projectile, Entity shooter) {
            var glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE.get(), shooter.level());
            var look = shooter.getLookAngle();
            glowCircle.setPos(projectile.getX() + look.x, projectile.getY() + look.y, projectile.getZ() + look.z);
            glowCircle.setYRot(shooter.getYRot());
            glowCircle.setXRot(shooter.getXRot());
            shooter.level().addFreshEntity(glowCircle);
        }

        private static boolean serverSeesAir(ServerPlayer player, ServerLevel level) {
            var reach = Math.max(SERVER_AIR_VERIFY_REACH,
                    Math.max(reach(player, Attributes.BLOCK_INTERACTION_RANGE),
                            reach(player, Attributes.ENTITY_INTERACTION_RANGE)));
            var start = player.getEyePosition();
            var look = normalizeOrDefault(player.getViewVector(1.0f));
            var end = start.add(look.scale(reach));

            var blockHit = level.clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() != HitResult.Type.MISS) return false;

            var searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
            var entityHit = ProjectileUtil.getEntityHitResult(
                    level, player, start, end, searchBox,
                    entity -> entity instanceof LivingEntity living
                            && living != player
                            && living.isAlive()
                            && !living.isSpectator()
                            && living.isPickable()
                            && !CtaFriendlyFireWhitelist.shouldProtect(player, living),
                    0.3f
            );
            return entityHit == null;
        }

        private static boolean canDestroyBlocks(ServerPlayer player) {
            return player.getData(AttachmentTypes.KINETIC_BLOCK_BREAK_ENABLED.get())
                    && DestroyBlocksSetting.canDestroyBlocks(player, Skills.KINETIC_ENERGY_APPLIED.get());
        }

        private static void enqueueBreakTask(ServerLevel level, ServerPlayer player, Vec3 center,
                                             float radius, Vec3 direction, int impactLevel,
                                             BlockPos priorityBlock) {
            var tasks = BREAK_TASKS.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
            while (tasks.size() >= MAX_TASKS_PER_PLAYER) tasks.pollFirst();
            tasks.addLast(new BreakTask(
                    level.dimension(), center, radius, direction, impactLevel, priorityBlock,
                    player.getData(AttachmentTypes.KINETIC_BLOCK_DROPS_ENABLED.get())
            ));
        }

        private static void tickBreakTasks(ServerPlayer player) {
            var tasks = BREAK_TASKS.get(player.getUUID());
            if (tasks == null || tasks.isEmpty()) return;
            if (!(player.level() instanceof ServerLevel level)) {
                tasks.clear();
                return;
            }

            var task = tasks.peekFirst();
            if (task == null) return;
            if (!task.dimension.equals(level.dimension()) || player.isDeadOrDying() || player.isRemoved()) {
                tasks.pollFirst();
            } else if (task.tick(level, player)) {
                tasks.pollFirst();
            }
            if (tasks.isEmpty()) BREAK_TASKS.remove(player.getUUID());
        }

        private static double reach(ServerPlayer player, Holder<Attribute> attribute) {
            var instance = player.getAttribute(attribute);
            return instance == null ? 0.0 : instance.getValue();
        }

        private static Vec3 normalizeOrDefault(Vec3 direction) {
            if (direction == null || !Double.isFinite(direction.x) || !Double.isFinite(direction.y)
                    || !Double.isFinite(direction.z) || direction.lengthSqr() < 1.0E-6) {
                return new Vec3(0.0, 1.0, 0.0);
            }
            return direction.normalize();
        }

        private static Vec3 audiblePosition(ServerPlayer player, Vec3 center) {
            var origin = player.getEyePosition();
            var offset = center.subtract(origin);
            if (offset.lengthSqr() <= 144.0 || offset.lengthSqr() < 1.0E-6) return center;
            return origin.add(offset.normalize().scale(12.0));
        }

        private static void migrateLegacySkills(ServerPlayer player) {
            if (MIGRATED_PLAYERS.contains(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            var playerData = system.getPlayerData(player.getUUID());
            if (playerData == null) return;

            var map = playerData.getSkillDataMap();
            var legacySuperposition = map.remove(LEGACY_KINETIC_SUPERPOSITION);
            var legacyDirectedShock = map.remove(LEGACY_DIRECTED_SHOCK);
            var changed = legacySuperposition != null || legacyDirectedShock != null;
            if (changed) {
                var skill = Skills.KINETIC_ENERGY_APPLIED.get();
                var target = map.get(skill.getKeyString());
                if (target == null) {
                    target = skill.createData(player);
                    map.put(skill.getKeyString(), target);
                }
                mergeProgress(target, legacySuperposition);
                mergeProgress(target, legacyDirectedShock);
                if (legacySuperposition != null && legacySuperposition.isEnabled()) target.setEnabled(true);
                playerData.markDirty();
                system.releaseMaintenanceOccupation(player.getUUID(), LEGACY_KINETIC_SUPERPOSITION);
                system.releaseMaintenanceOccupation(player.getUUID(), LEGACY_DIRECTED_SHOCK);
                system.getSyncManager().schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
            }
            MIGRATED_PLAYERS.add(player);
        }

        private static void mergeProgress(SkillData target, SkillData legacy) {
            if (legacy == null) return;
            target.setProficiency(Math.max(target.getProficiency(), legacy.getProficiency()));
        }

        private static void syncAttributes(ServerPlayer player, boolean enabled) {
            syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOD_MOVEMENT_SPEED, 0.1, enabled);
            syncModifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), MOD_KNOCKBACK_RESISTANCE, 1.0, enabled);
            syncModifier(player.getAttribute(Attributes.STEP_HEIGHT), MOD_STEP_HEIGHT, 0.4, enabled);
            syncModifier(player.getAttribute(Attributes.MOVEMENT_EFFICIENCY), MOD_MOVEMENT_EFFICIENCY, 1.0, enabled);
            syncModifier(player.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE),
                    MOD_EXPLOSION_KNOCKBACK_RESISTANCE, 1.0, enabled);
            syncModifier(player.getAttribute(Attributes.JUMP_STRENGTH), MOD_JUMP_STRENGTH, 0.58, enabled);
        }

        private static void syncModifier(AttributeInstance attribute, Identifier id, double amount, boolean enabled) {
            if (attribute == null) return;
            var current = attribute.getModifier(id);
            if (!enabled) {
                if (current != null) attribute.removeModifier(id);
                return;
            }
            if (current != null && Double.compare(current.amount(), amount) == 0) return;
            if (current != null) attribute.removeModifier(id);
            attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
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
            return PacketTypes.KINETIC_ENERGY_APPLIED_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ToggleShockwavePacket extends Packet<ServerGamePacketListenerImpl, ToggleShockwavePacket> {
        public static final ToggleShockwavePacket INSTANCE = new ToggleShockwavePacket();
        public static final StreamCodec<ByteBuf, ToggleShockwavePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ToggleShockwavePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ToggleShockwavePacket> getPacketType() {
            return PacketTypes.KINETIC_ENERGY_APPLIED_SHOCKWAVE_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CycleImpactLevelPacket extends Packet<ServerGamePacketListenerImpl, CycleImpactLevelPacket> {
        public static final CycleImpactLevelPacket INSTANCE = new CycleImpactLevelPacket();
        public static final StreamCodec<ByteBuf, CycleImpactLevelPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CycleImpactLevelPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CycleImpactLevelPacket> getPacketType() {
            return PacketTypes.KINETIC_ENERGY_APPLIED_IMPACT_LEVEL.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SetBlockDropsPacket extends Packet<ServerGamePacketListenerImpl, SetBlockDropsPacket> {
        public static final StreamCodec<ByteBuf, SetBlockDropsPacket> CODEC =
                ByteBufCodecs.BOOL.map(SetBlockDropsPacket::new, SetBlockDropsPacket::enabled);
        private final boolean enabled;

        public SetBlockDropsPacket(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean enabled() {
            return enabled;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SetBlockDropsPacket> getPacketType() {
            return PacketTypes.KINETIC_ENERGY_APPLIED_BLOCK_DROPS_SET.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class AttackWavePacket extends Packet<ServerGamePacketListenerImpl, AttackWavePacket> {
        public static final AttackWavePacket INSTANCE = new AttackWavePacket();
        public static final StreamCodec<ByteBuf, AttackWavePacket> CODEC = StreamCodec.unit(INSTANCE);

        private AttackWavePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, AttackWavePacket> getPacketType() {
            return PacketTypes.KINETIC_ENERGY_APPLIED_ATTACK_WAVE.get();
        }
    }

    private static List<BlockOffset> sphereOffsetsFor(int blockRadius) {
        return SPHERE_OFFSET_CACHE.computeIfAbsent(Math.max(0, blockRadius), KineticEnergyApplied::createSphereOffsets);
    }

    private static List<BlockOffset> createSphereOffsets(int blockRadius) {
        var offsets = new ArrayList<BlockOffset>();
        for (var dx = -blockRadius; dx <= blockRadius; dx++) {
            for (var dy = -blockRadius; dy <= blockRadius; dy++) {
                for (var dz = -blockRadius; dz <= blockRadius; dz++) {
                    offsets.add(new BlockOffset(dx, dy, dz, dx * dx + dy * dy + dz * dz));
                }
            }
        }
        offsets.sort(Comparator.comparingInt(BlockOffset::distanceSquared));
        return List.copyOf(offsets);
    }

    private static final class BreakTask {
        private final ResourceKey<Level> dimension;
        private final Vec3 center;
        private final BlockPos origin;
        private final double radiusSquared;
        private final int impactLevel;
        private final List<BlockOffset> offsets;
        private final BlockPos priorityBlock;
        private final boolean dropBlocks;
        private int offsetIndex;
        private boolean priorityProcessed;

        private BreakTask(ResourceKey<Level> dimension, Vec3 center, float radius, Vec3 direction,
                          int impactLevel, BlockPos priorityBlock, boolean dropBlocks) {
            this.dimension = dimension;
            this.center = center;
            origin = BlockPos.containing(center);
            radiusSquared = radius * radius;
            this.impactLevel = impactLevel;
            offsets = sphereOffsetsFor(Mth.ceil(radius));
            this.priorityBlock = priorityBlock == null ? null : priorityBlock.immutable();
            this.dropBlocks = dropBlocks;
        }

        private boolean tick(ServerLevel level, ServerPlayer player) {
            var changed = 0;
            var scanned = 0;
            if (!priorityProcessed) {
                priorityProcessed = true;
                scanned++;
                if (priorityBlock != null && tryMutate(level, player, priorityBlock)) changed++;
            }

            while (offsetIndex < offsets.size()
                    && changed < MAX_BLOCKS_PER_TASK_TICK
                    && scanned < MAX_SCANS_PER_TASK_TICK) {
                var offset = offsets.get(offsetIndex++);
                scanned++;
                if (tryMutate(level, player, origin.offset(offset.dx(), offset.dy(), offset.dz()))) changed++;
            }
            return offsetIndex >= offsets.size();
        }

        private boolean tryMutate(ServerLevel level, ServerPlayer player, BlockPos pos) {
            var x = pos.getX() + 0.5 - center.x;
            var y = pos.getY() + 0.5 - center.y;
            var z = pos.getZ() + 0.5 - center.z;
            if (x * x + y * y + z * z > radiusSquared) return false;
            if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)) return false;

            var state = level.getBlockState(pos);
            if (state.isAir()) return clearFluid(level, player, pos, state);

            var bedrock = state.is(Blocks.BEDROCK);
            if (state.getDestroySpeed(level, pos) < 0.0f
                    && !(impactLevel >= MAX_IMPACT_LEVEL && bedrock)) {
                return clearFluid(level, player, pos, state);
            }
            if (bedrock) {
                return level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
            return level.destroyBlock(pos, dropBlocks, player);
        }

        private boolean clearFluid(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
            var fluidState = state.getFluidState();
            if (fluidState.isEmpty()) return false;

            if (state.getBlock() instanceof BucketPickup bucketPickup
                    && !bucketPickup.pickupBlock(player, level, pos, state).isEmpty()) {
                return true;
            }
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                var dried = state.setValue(BlockStateProperties.WATERLOGGED, false);
                level.setBlock(pos, dried, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                level.updateNeighborsAt(pos, dried.getBlock());
                return true;
            }
            if (state.getBlock() instanceof LiquidBlock) {
                return level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
            return false;
        }
    }

    private record BlockOffset(int dx, int dy, int dz, int distanceSquared) {
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            Server.migrateLegacySkills(player);
            Server.tickBreakTasks(player);

            var skill = Skills.KINETIC_ENERGY_APPLIED.get();
            var enabled = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            var system = AbilitySystemServer.getSystem(player);
            if (enabled) {
                enabled = system.ensurePermanentOccupation(
                        player.getUUID(), skill.getMaintenanceCost(player), skill);
                if (!enabled) system.toggleSkill(player.getUUID(), skill.getKeyString());
            }
            Server.syncAttributes(player, enabled);
        }

        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!(event.getTarget() instanceof LivingEntity target) || !target.isAlive()) return;
            if (!Server.canCreateShockwave(player)) return;
            if (!Server.claimImpactTrigger(player, player.level().getGameTime())) return;

            var center = target.getBoundingBox().getCenter();
            var impactLevel = clampImpactLevel(player.getData(AttachmentTypes.KINETIC_IMPACT_LEVEL.get()));
            Server.executeImpact((ServerLevel) player.level(), player, center,
                    center.subtract(player.getEyePosition()), impactLevel, null);
        }

        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
            if (!Server.canCreateShockwave(player)) return;
            if (!Server.claimImpactTrigger(player, player.level().getGameTime())) return;

            var center = Vec3.atCenterOf(event.getPos());
            var impactLevel = clampImpactLevel(player.getData(AttachmentTypes.KINETIC_IMPACT_LEVEL.get()));
            Server.executeImpact((ServerLevel) player.level(), player, center,
                    center.subtract(player.getEyePosition()), impactLevel, event.getPos());
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) clearTransientState(player);
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) clearTransientState(player);
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) clearTransientState(player);
        }

        private static void clearTransientState(ServerPlayer player) {
            BREAK_TASKS.remove(player.getUUID());
            LAST_IMPACT_TRIGGER_TICKS.remove(player.getUUID());
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            var source = event.getSource();
            if (ReflectedSkillDamageSource.isReflected(source)) return;
            if (source instanceof SkillDamageSource skillSource
                    && skillSource.getSkill() == Skills.KINETIC_ENERGY_APPLIED.get()) return;

            ServerPlayer player = null;
            if (source.getEntity() instanceof ServerPlayer owner) {
                player = owner;
            } else if (source.getDirectEntity() instanceof ServerPlayer owner) {
                player = owner;
            } else if (source.getDirectEntity() instanceof Projectile projectile
                    && projectile.getOwner() instanceof ServerPlayer owner) {
                player = owner;
            }
            if (player == null || event.getEntity() == player) return;

            var skill = Skills.KINETIC_ENERGY_APPLIED.get();
            if (!skill.isEnabled(player)) return;
            if (CtaFriendlyFireWhitelist.shouldProtect(player, event.getEntity())) {
                event.setAmount(0.0f);
                event.setCanceled(true);
                return;
            }

            event.setAmount(event.getAmount() * 2.0f);
            var system = AbilitySystemServer.getSystem(player);
            var bonusDamage = 4.0f
                    * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                    * system.getPlayerDamageMultiplier(player.getUUID());
            CTADamageUtil.applyCompositeDamage(
                    event.getEntity(),
                    player,
                    SkillDamageSource.of(
                            player,
                            skill,
                            org.academy.internal.common.world.damagesource.DamageTypes.CTA
                    ),
                    bonusDamage
            );
        }
    }
}
