package org.academy.internal.common.ability.aeromanip.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.common.util.ViewTargetScanner;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeContext;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.client.ability.aeromanip.AeromanipChargeHud;
import org.academy.internal.common.ability.aeromanip.skills.lv2.PneumaticGrasp;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class LaminarCutter extends Skill {
    private static final double BLADE_HALF_WIDTH = 2.5;
    private static final double BLADE_HALF_THICKNESS = 0.55;
    private static final Identifier LAMINAR_FRACTURE_ID = AcademyCraft.academy("laminar_fracture");

    public LaminarCutter() {
        super(Builder.of(AbilityCategories.AEROMANIP.get()).damage().level(AbilityLevel.LEVEL3).energyCost(30_000)
                .cpCost(20).iterationTicks(10).maxStacks(20)
                .dependsOn(Skills.PNEUMATIC_GRASP)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3)));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_RIGHT,
                InputSystem.ANY_ACTION,
                InputConstants.MOD_ALT);
        var binding = Client.CONFIG.getKeyBindingMigratingDefaults(
                Client.KEY_NAME_CAST,
                defaultBinding,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_V,
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
                        Skills.LAMINAR_CUTTER.get(),
                        List.of(PneumaticGrasp.Client.SKILL_INFO),
                        R.textures.laminar_cutter_icon, 75, 104));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_CAST = SkillNames.LAMINAR_CUTTER + "_cast";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();

        private static void start() {
            if (AbilitySystemClient.canUseSkill(Skills.LAMINAR_CUTTER.get())) {
                AeromanipChargeHud.begin(Skills.LAMINAR_CUTTER.get());
                MisakaNetworkClient.send(StartPacket.INSTANCE);
            }
        }

        private static void stop() {
            AeromanipChargeHud.end(Skills.LAMINAR_CUTTER.get());
            MisakaNetworkClient.send(StopPacket.INSTANCE);
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
        private static final Map<ServerPlayer, ChargeContext> CHARGES = new WeakHashMap<>();

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.LAMINAR_CUTTER.get();
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

        public static boolean tryProgramCast(
                ServerPlayer player,
                Vec3 direction,
                float maximumRange,
                float damageScale,
                float baseCost
        ) {
            return tryCast(player, direction, maximumRange, damageScale, baseCost,
                    AeromanipChargeTier.INSTANT);
        }

        public static boolean tryProgramCast(
                ServerPlayer player,
                Vec3 direction,
                float maximumRange,
                float damageScale,
                float baseCost,
                AeromanipChargeTier tier
        ) {
            return tryCast(player, direction, maximumRange, damageScale, baseCost, tier);
        }

        static double bladeHalfWidth(AeromanipChargeTier tier) {
            return tier == AeromanipChargeTier.INSTANT ? BLADE_HALF_WIDTH : 4.0;
        }

        static float baseDamage(AeromanipChargeTier tier) {
            return switch (tier) {
                case INSTANT -> 4.0f;
                case HALF -> 6.0f;
                case FULL -> 8.0f;
            };
        }

        static double knockback(AeromanipChargeTier tier) {
            return tier == AeromanipChargeTier.INSTANT ? 0.55 : 1.25;
        }

        static Vec3 bladeRight(Vec3 direction) {
            if (direction == null || !Double.isFinite(direction.x)
                    || !Double.isFinite(direction.y) || !Double.isFinite(direction.z)) {
                return new Vec3(1.0, 0.0, 0.0);
            }
            var right = new Vec3(-direction.z, 0.0, direction.x);
            return right.lengthSqr() < 1.0E-8
                    ? new Vec3(1.0, 0.0, 0.0)
                    : right.normalize();
        }

        private static final class ChargeContext extends AeromanipChargeContext {
            private ChargeContext(ServerPlayer player) {
                super(player, Skills.LAMINAR_CUTTER.get());
            }

            @Override
            protected void onReleased(AeromanipChargeTier tier, long chargeTicks) {
                tryCast(player, player.getLookAngle(), -1.0f, 1.0f, -1.0f, tier);
            }

            @Override
            protected void onTierReached(AeromanipChargeTier tier) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.AIRFLOW_IMPACT.get(), SoundSource.PLAYERS,
                        0.45f, tier == AeromanipChargeTier.FULL ? 1.55f : 1.3f);
            }

            @Override
            protected void onChargeEnded(boolean released) {
                CHARGES.remove(player, this);
            }
        }

        private static boolean tryCast(
                ServerPlayer player,
                Vec3 direction,
                float maximumRange,
                float damageScale,
                float baseCost,
                AeromanipChargeTier tier
        ) {
            if (player == null || direction == null
                    || !Double.isFinite(direction.x)
                    || !Double.isFinite(direction.y)
                    || !Double.isFinite(direction.z)
                    || direction.lengthSqr() < 1.0E-12
                    || !Float.isFinite(maximumRange)
                    || !Float.isFinite(damageScale)
                    || !Float.isFinite(baseCost)
                    || (maximumRange != -1.0f
                    && (maximumRange <= 0.0f || maximumRange > 64.0f))
                    || damageScale < 0.0f
                    || damageScale > 2.0f || tier == null) {
                return false;
            }
            var skill = Skills.LAMINAR_CUTTER.get();
            var normalizedDirection = direction.normalize();
            var tierCp = switch (tier) {
                case INSTANT -> 20.0f;
                case HALF -> 35.0f;
                case FULL -> 50.0f;
            };
            var tierAir = switch (tier) {
                case INSTANT -> 20.0f;
                case HALF -> 36.0f;
                case FULL -> 56.0f;
            };
            return skill.executeActiveWithResource(player, context -> (baseCost < 0.0f
                            ? tierCp : baseCost)
                            * AeromanipConfig.cpMultiplier(player, SkillNames.LAMINAR_CUTTER),
                    _ -> tierAir,
                    (context, _) -> executeCut(
                            player,
                            normalizedDirection,
                            maximumRange,
                            damageScale,
                            context,
                            tier
                    ));
        }

        private static void executeCut(
                ServerPlayer player,
                Vec3 direction,
                float maximumRange,
                float damageScale,
                Skill.SkillContext context,
                AeromanipChargeTier tier
        ) {
            if (!(player.level() instanceof ServerLevel level)) return;
            var skill = Skills.LAMINAR_CUTTER.get();
            var eye = player.getEyePosition();
            var cutterLevel = Math.max(0, Math.min(2, skill.getLevel(player)));
            var length = (24.0 + cutterLevel * 4.0)
                    * AeromanipConfig.rangeMultiplier(player, SkillNames.LAMINAR_CUTTER);
            if (context.milestone() >= 2) length *= 1.2;
            if (maximumRange > 0.0f) length = Math.min(length, maximumRange);
            var resolvedLength = length;
            var end = eye.add(direction.scale(length));
            var bladeRight = bladeRight(direction);
            var bladeNormal = direction.cross(bladeRight).normalize();
            var halfWidth = bladeHalfWidth(tier);
            var bladeArea = new BladeArea(bladeRight, bladeNormal, halfWidth);
            var source = SkillDamageSource.of(player, Skills.LAMINAR_CUTTER.get(), DamageTypes.LAMINAR_CUT);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.AIRFLOW_IMPACT.get(),
                    SoundSource.PLAYERS, 0.65f, 1.15f);
            var damage = baseDamage(tier) * AeromanipConfig.damageMultiplier(player, SkillNames.LAMINAR_CUTTER)
                    * context.system().getPlayerAbilityPowerMultiplier(player.getUUID())
                    * context.system().getPlayerDamageMultiplier(player.getUUID())
                    * damageScale;
            for (var target : ViewTargetScanner.scan(
                    level,
                    LivingEntity.class,
                    eye,
                    direction,
                    resolvedLength,
                    bladeArea,
                    living -> living != player
                            && living.isAlive()
                            && AeromanipTargeting.canAffectNegatively(player, living)
                            && player.hasLineOfSight(living)
            )) {
                var hurt = tier == AeromanipChargeTier.FULL
                        ? SkillDamageUtil.applyDirect(level, target, source, damage)
                        : target.hurtServer(level, source, damage);
                if (hurt) {
                    if (tier != AeromanipChargeTier.FULL) {
                        Skills.LAMINAR_CUTTER.get().onHurt(player, target, damage);
                    }
                    var force = AeromanipTargeting.forceMultiplier(player, target);
                    if (force > 0.0) {
                        AeromanipTargeting.addClampedVelocity(
                                target, direction.scale(knockback(tier) * force));
                    }
                    if (tier == AeromanipChargeTier.FULL) {
                        damageEquipment(target, 80);
                        if (context.milestone() >= 2) disarm(player, target);
                    }
                    if (context.milestone() >= 3) applyFracture(player, target);
                }
            }
            if (tier != AeromanipChargeTier.INSTANT) {
                clearSoftBlocks(player, level, eye, end, direction, bladeRight, bladeNormal, tier);
            }
            spawnBladeVisual(level, eye, direction, bladeRight, length);
        }

        private static boolean intersectsBlade(LivingEntity target, Vec3 start, Vec3 direction,
                                               Vec3 bladeRight, Vec3 bladeNormal, double range) {
            return intersectsBlade(target, start, direction, bladeRight, bladeNormal, range,
                    BLADE_HALF_WIDTH);
        }

        private static boolean intersectsBlade(LivingEntity target, Vec3 start, Vec3 direction,
                                               Vec3 bladeRight, Vec3 bladeNormal, double range,
                                               double halfWidth) {
            return ViewTargetScanner.matches(
                    start,
                    direction,
                    range,
                    new BladeArea(bladeRight, bladeNormal, halfWidth),
                    target.getBoundingBox()
            );
        }

        private record BladeArea(
                Vec3 bladeRight,
                Vec3 bladeNormal,
                double halfWidth
        ) implements ViewTargetScanner.Shape {
            @Override
            public AABB searchBounds(Vec3 origin, Vec3 normalizedDirection, double range) {
                return new AABB(origin, origin.add(normalizedDirection.scale(range)))
                        .inflate(halfWidth, 1.5, halfWidth);
            }

            @Override
            public double matchDistance(
                    Vec3 origin,
                    Vec3 normalizedDirection,
                    double range,
                    AABB targetBounds
            ) {
                var relative = targetBounds.getCenter().subtract(origin);
                var targetWidth = Math.max(targetBounds.getXsize(), targetBounds.getZsize());
                var forward = relative.dot(normalizedDirection);
                if (forward < -targetWidth || forward > range + targetWidth) {
                    return Double.POSITIVE_INFINITY;
                }
                if (Math.abs(relative.dot(bladeRight)) > halfWidth + targetWidth * 0.5
                        || Math.abs(relative.dot(bladeNormal))
                        > BLADE_HALF_THICKNESS + targetBounds.getYsize() * 0.5) {
                    return Double.POSITIVE_INFINITY;
                }
                return Math.max(0.0, forward);
            }
        }

        private static void spawnBladeVisual(ServerLevel level, Vec3 start, Vec3 direction,
                                             Vec3 bladeRight, double range) {
            AeromanipVfx.blade(level, start, direction, bladeRight, range);
        }

        private static void clearSoftBlocks(net.minecraft.server.level.ServerPlayer player, ServerLevel level,
                                             Vec3 start, Vec3 end, Vec3 direction,
                                             Vec3 bladeRight, Vec3 bladeNormal,
                                             AeromanipChargeTier tier) {
            var settings = AeromanipConfig.settings(player);
            if (!settings.allowSoftBlockInteraction
                    || !DestroyBlocksSetting.canDestroyBlocks(player, Skills.LAMINAR_CUTTER.get())) return;
            var min = new Vec3(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z));
            var max = new Vec3(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
            var padding = Mth.ceil(BLADE_HALF_WIDTH) + 1;
            var from = BlockPos.containing(min).offset(-padding, -2, -padding);
            var to = BlockPos.containing(max).offset(padding, 2, padding);
            for (var pos : BlockPos.betweenClosed(from, to)) {
                var center = Vec3.atCenterOf(pos);
                var relative = center.subtract(start);
                var projection = relative.dot(direction);
                if (projection < -0.5 || projection > start.distanceTo(end) + 0.5) continue;
                if (Math.abs(relative.dot(bladeRight)) > bladeHalfWidth(tier) + 0.5) continue;
                if (Math.abs(relative.dot(bladeNormal)) > BLADE_HALF_THICKNESS + 0.5) continue;
                var state = level.getBlockState(pos);
                if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                    level.removeBlock(pos, false);
                    continue;
                }
                var predefinedSoft = state.is(Blocks.COBWEB)
                        || state.is(Blocks.VINE)
                        || state.is(Blocks.WEEPING_VINES)
                        || state.is(Blocks.TWISTING_VINES)
                        || state.is(Blocks.SHORT_GRASS)
                        || state.is(Blocks.TALL_GRASS)
                        || state.is(Blocks.FERN)
                        || state.is(Blocks.LARGE_FERN)
                        || state.is(Blocks.DEAD_BUSH)
                        || state.is(Blocks.SEAGRASS)
                        || state.is(Blocks.TALL_SEAGRASS)
                        || state.is(Blocks.NETHER_SPROUTS)
                        || state.is(BlockTags.LEAVES);
                if (!predefinedSoft && (tier != AeromanipChargeTier.FULL || state.getDestroySpeed(level, pos) < 0.0f
                        || state.getDestroySpeed(level, pos) > 1.5f)) continue;
                level.destroyBlock(pos.immutable(), true, player);
            }
        }

        private static void damageEquipment(LivingEntity target, int amount) {
            for (var slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                var stack = target.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.isDamageableItem()) {
                    stack.hurtAndBreak(amount, target, slot);
                }
            }
        }

        private static void disarm(ServerPlayer owner, LivingEntity target) {
            if (!EntityMotionGuard.canManipulateEquipmentFrom(owner, target)) return;
            var held = target.getMainHandItem();
            if (held.isEmpty()) return;
            var droppedStack = held.copy();
            target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            var dropped = new ItemEntity(
                    target.level(), target.getX(), target.getEyeY(), target.getZ(), droppedStack);
            dropped.setPickUpDelay(30);
            dropped.setDeltaMovement(owner.getLookAngle().normalize().scale(0.35).add(0.0, 0.2, 0.0));
            target.level().addFreshEntity(dropped);
        }

        private static void applyFracture(net.minecraft.server.level.ServerPlayer owner, LivingEntity target) {
            var armor = target.getAttribute(Attributes.ARMOR);
            if (armor == null) return;
            var amount = target instanceof Player ? -0.1 : -0.2;
            var current = armor.getModifier(LAMINAR_FRACTURE_ID);
            if (current != null) armor.removeModifier(LAMINAR_FRACTURE_ID);
            armor.addTransientModifier(new AttributeModifier(
                    LAMINAR_FRACTURE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            var skill = Skills.LAMINAR_CUTTER.get();
            TimedSkillEffectRuntime.put(owner, target.getUUID(), skill, "fracture", 100, (float) -amount);
            TimedSkillEffectRuntime.schedule(owner, 100, () -> {
                if (TimedSkillEffectRuntime.get(owner.getUUID(), target.getUUID(), skill,
                        "fracture", owner.level().getGameTime()).isEmpty()) {
                    var targetArmor = target.getAttribute(Attributes.ARMOR);
                    if (targetArmor != null) targetArmor.removeModifier(LAMINAR_FRACTURE_ID);
                }
            });
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
            return PacketTypes.LAMINAR_CUTTER_START.get();
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
            return PacketTypes.LAMINAR_CUTTER_STOP.get();
        }
    }
}
