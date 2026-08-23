package org.academy.internal.common.ability.darkmatter.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Comparator;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
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
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterPhase;
import org.academy.internal.common.ability.darkmatter.DarkmatterLawMark;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class DarkmatterDisassemble extends Skill {
    static final double RANGE = 32.0;
    static final float BASE_DAMAGE = 4.0f;
    static final int CP_COST = 20;
    private static final Identifier ARMOR_PENETRATION_ID =
            AcademyCraft.academy("darkmatter_disassemble_penetration");

    public DarkmatterDisassemble() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(CP_COST)
                .iterationTicks(5)
                .maxStacks(20)
                .proficiencyProfile(SkillProficiencyProfile.builder()
                        .costs(SkillProficiencyProfile.CostKind.CAST,
                                1.0f, 0.9f, 0.9f, 0.9f)
                        .iterationTicks(5, 5, 4, 4)
                        .build())
                .dependsOn(Skills.DARKMATTER_SHAPING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Shaping", "academy:darkmatter_shaping"))
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
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), context -> Client.cast());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_DISASSEMBLE.get(),
                        List.of(DarkmatterShaping.Client.SKILL_INFO),
                        R.textures.darkmatter_disassemble_icon,
                        98,
                        104
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_DISASSEMBLE + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_DISASSEMBLE.get())) return;
            MisakaNetworkClient.send(CastPacket.INSTANCE);
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
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level() instanceof ServerLevel level)) return;
            var hit = pick(level, player);
            if (hit == null) return;
            disassembleAt(player, hit.location(),
                    hit.block() == null ? null : hit.block().getBlockPos(),
                    hit.block() == null ? Direction.UP : hit.block().getDirection());
        }

        public static boolean tryDestroyBlock(ServerPlayer player, BlockPos position) {
            return tryDestroyBlock(player, position, Direction.UP);
        }

        public static boolean tryDestroyBlock(
                ServerPlayer player,
                BlockPos position,
                Direction hitFace
        ) {
            if (player == null || position == null
                    || !(player.level() instanceof ServerLevel level)
                    || Vec3.atCenterOf(position).distanceToSqr(player.getEyePosition())
                    > maximumRange(player) * maximumRange(player)
                    || !DestroyBlocksSetting.canDestroyBlocks(
                    player, Skills.DARKMATTER_DISASSEMBLE.get())
                    || !isStructurallyBreakable(level, player, position,
                    DarkmatterPhase.alpha(player),
                    Skills.DARKMATTER_DISASSEMBLE.get()
                            .getEffectiveProficiencyMilestone(player))) {
                return false;
            }
            return disassembleAt(player, Vec3.atCenterOf(position), position,
                    hitFace == null ? Direction.UP : hitFace);
        }

        public static boolean canProgramDestroyBlock(
                ServerPlayer player,
                BlockPos position,
                double maximumRange
        ) {
            if (player == null
                    || position == null
                    || !Double.isFinite(maximumRange)
                    || maximumRange <= 0.0
                    || maximumRange > RANGE
                    || !(player.level() instanceof ServerLevel level)
                    || Vec3.atCenterOf(position).distanceToSqr(player.getEyePosition())
                    > maximumRange * maximumRange
                    || !DestroyBlocksSetting.canDestroyBlocks(
                    player, Skills.DARKMATTER_DISASSEMBLE.get())) {
                return false;
            }
            return canDestroy(level, player, position);
        }

        public static boolean tryProgramDestroyBlock(
                ServerPlayer player,
                BlockPos position,
                double maximumRange,
                float baseCost
        ) {
            if (!Float.isFinite(baseCost)
                    || baseCost < 0.0f
                    || !canProgramDestroyBlock(player, position, maximumRange)) {
                return false;
            }
            var level = (ServerLevel) player.level();
            var destroyed = new boolean[1];
            var skill = Skills.DARKMATTER_DISASSEMBLE.get();
            var executed = skill.executeActive(player, _ -> baseCost, (context, _) -> {
                if (!canProgramDestroyBlock(player, position, maximumRange)) return;
                var phase = DarkmatterPhase.weights(player);
                destroyed[0] = destroyWithPhaseDrops(
                        level,
                        player,
                        position.immutable(),
                        fortuneLevel(phase.beta()),
                        context.milestone() >= 3);
                if (destroyed[0]) {
                    player.level().playSound(
                            null,
                            position,
                            SoundEvents.GRAVEL_BREAK,
                            SoundSource.PLAYERS,
                            1.0f,
                            0.9f
                    );
                }
            });
            return executed && destroyed[0];
        }

        private static Hit pick(ServerLevel level, ServerPlayer player) {
            var range = RANGE;
            var start = player.getEyePosition();
            var direction = player.getLookAngle().normalize();
            var end = start.add(direction.scale(range));
            var block = level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            var bestDistance = block.getType() == HitResult.Type.MISS
                    ? range * range
                    : start.distanceToSqr(block.getLocation());
            LivingEntity best = null;
            // A 0.5-block-wide ray: 0.25 block on each side of its center line.
            var search = new AABB(start, end).inflate(0.25);
            for (var candidate : level.getEntitiesOfClass(LivingEntity.class, search,
                    target -> validTarget(player, target))) {
                var clipped = candidate.getBoundingBox().inflate(0.25).clip(start, end);
                if (clipped.isEmpty()) continue;
                var distance = start.distanceToSqr(clipped.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            if (best != null) {
                var clipped = best.getBoundingBox().inflate(0.25).clip(start, end);
                return new Hit(best,
                        block.getType() == HitResult.Type.MISS ? null : block,
                        clipped.orElse(best.getBoundingBox().getCenter()));
            }
            if (block.getType() == HitResult.Type.MISS) return null;
            return new Hit(null, block, block.getLocation());
        }

        static boolean validTarget(ServerPlayer player, LivingEntity target) {
            return DarkmatterTargeting.isAttackableBy(player, target);
        }

        public static boolean tryAutomatedAttack(ServerPlayer player, LivingEntity target) {
            if (!(player.level() instanceof ServerLevel level)
                    || target == null || target.level() != level || !target.isAlive()
                    || player.distanceToSqr(target) > RANGE * RANGE
                    || !player.hasLineOfSight(target)) return false;
            return disassembleAt(player, target.getBoundingBox().getCenter(), null, Direction.UP);
        }

        public static boolean tryProgramAttack(
                ServerPlayer player,
                LivingEntity target,
                double maximumRange,
                float damageScale,
                float baseCost
        ) {
            if (!Float.isFinite(damageScale)
                    || damageScale < 0.0f
                    || damageScale > 2.0f
                    || !Float.isFinite(baseCost)
                    || baseCost < 0.0f
                    || !canProgramAttack(player, target, maximumRange)
                    || !(player.level() instanceof ServerLevel level)) {
                return false;
            }
            var attacked = new boolean[1];
            var skill = Skills.DARKMATTER_DISASSEMBLE.get();
            return skill.executeActive(player, _ -> baseCost, (context, actualCost) -> {
                if (!canProgramAttack(player, target, maximumRange)) return;
                attacked[0] = true;
                var system = AbilitySystemServer.getSystem(player);
                var phase = DarkmatterPhase.weights(player);
                var damage = (4.0f + 2.0f * phase.alpha())
                        * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                        * system.getPlayerDamageMultiplier(player.getUUID())
                        * damageScale;
                var source = SkillDamageSource.of(player, skill);
                var hurt = hurtWithArmorPenetration(
                        target, level, source, damage,
                        penetration(phase.beta(), context.milestone()));
                if (hurt) {
                    level.sendParticles(
                            ParticleTypes.CLOUD,
                            target.getX(),
                            target.getY() + target.getBbHeight() * 0.5,
                            target.getZ(),
                            16,
                            0.45, 0.45, 0.45,
                            0.03
                    );
                }
                level.playSound(
                        null,
                        target.blockPosition(),
                        SoundEvents.GRAVEL_BREAK,
                        SoundSource.PLAYERS,
                        1.0f,
                        0.95f
                );
            }) && attacked[0];
        }

        public static boolean canProgramAttack(
                ServerPlayer player,
                LivingEntity target,
                double maximumRange
        ) {
            return player != null
                    && target != null
                    && Double.isFinite(maximumRange)
                    && maximumRange > 0.0
                    && maximumRange <= RANGE
                    && player.level() instanceof ServerLevel level
                    && target.level() == level
                    && validTarget(player, target)
                    && player.distanceToSqr(target) <= maximumRange * maximumRange
                    && player.hasLineOfSight(target);
        }

        private static double maximumRange(ServerPlayer player) {
            return RANGE;
        }

        static double maximumRange(float alpha, int milestone) {
            return RANGE;
        }

        static double gammaRadius(float gamma, int milestone, int sixWingsMilestone) {
            if (!(gamma > 0.0f)) return 0.0;
            return 1.0 + gamma;
        }

        static double gammaRadius(int milestone, int sixWingsMilestone) {
            return gammaRadius(1.0f, milestone, sixWingsMilestone);
        }

        static float penetration(float beta, int milestone) {
            return Math.min(0.5f, Math.max(0.0f, beta) * 0.10f);
        }

        static int corrosionTicks(float beta, int milestone) {
            return Math.round(40.0f + Math.max(0.0f, beta) * 20.0f);
        }

        private static boolean hurtWithArmorPenetration(LivingEntity target, ServerLevel level,
                                                        net.minecraft.world.damagesource.DamageSource source,
                                                        float damage, float penetration) {
            var armor = target.getAttribute(Attributes.ARMOR);
            if (armor == null) return DarkmatterTargeting.hurt(level, target, source, damage);
            var existing = armor.getModifier(ARMOR_PENETRATION_ID);
            if (existing != null) armor.removeModifier(ARMOR_PENETRATION_ID);
            armor.addTransientModifier(new AttributeModifier(
                    ARMOR_PENETRATION_ID,
                    -Math.clamp(penetration, 0.0f, 0.5f),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            try {
                return DarkmatterTargeting.hurt(level, target, source, damage);
            } finally {
                armor.removeModifier(ARMOR_PENETRATION_ID);
                if (existing != null) armor.addTransientModifier(existing);
            }
        }

        private static boolean isStructurallyBreakable(
                ServerLevel level,
                ServerPlayer player,
                BlockPos pos,
                float alpha,
                int milestone
        ) {
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()
                    || !level.mayInteract(player, pos)) return false;
            var state = level.getBlockState(pos);
            var hardness = state.getDestroySpeed(level, pos);
            var maximumHardness = maximumHardness(alpha, milestone);
            if (state.isAir() || hardness < 0 || hardness > maximumHardness) return false;
            return !player.blockActionRestricted(
                    level, pos, player.gameMode.getGameModeForPlayer())
                    && (!(state.getBlock() instanceof GameMasterBlock)
                    || player.canUseGameMasterBlocks());
        }

        private static boolean canDestroy(ServerLevel level, ServerPlayer player, BlockPos pos) {
            var phase = DarkmatterPhase.weights(player);
            var milestone = Skills.DARKMATTER_DISASSEMBLE.get()
                    .getEffectiveProficiencyMilestone(player);
            if (!isStructurallyBreakable(level, player, pos, phase.alpha(), milestone)) {
                return false;
            }
            var state = level.getBlockState(pos);
            var event = new BreakBlockEvent(level, pos.immutable(), state, player);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }

        static float maximumHardness(float alpha, int milestone) {
            return 10.0f + Math.max(0.0f, alpha) * 10.0f;
        }

        private static boolean disassembleAt(
                ServerPlayer player,
                Vec3 impact,
                BlockPos requestedSeed,
                Direction hitFace
        ) {
            if (player == null || impact == null
                    || !(player.level() instanceof ServerLevel level)
                    || impact.distanceToSqr(player.getEyePosition()) > RANGE * RANGE) {
                return false;
            }
            var skill = Skills.DARKMATTER_DISASSEMBLE.get();
            var applied = new boolean[1];
            var executed = skill.executeActive(player, (context, actualCost) -> {
                var phase = DarkmatterPhase.weights(player);
                var damageRadius = damageRadius(phase.alpha());
                var targets = level.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(impact, impact).inflate(damageRadius),
                        target -> validTarget(player, target)
                                && target.getBoundingBox().distanceToSqr(impact)
                                <= damageRadius * damageRadius
                );
                var system = AbilitySystemServer.getSystem(player);
                var multiplier = system.getPlayerAbilityPowerMultiplier(player.getUUID())
                        * system.getPlayerDamageMultiplier(player.getUUID());
                var damage = damage(phase.alpha()) * multiplier;
                var source = SkillDamageSource.of(player, skill);
                for (var target : targets) {
                    var wasAlive = target.isAlive();
                    var detonation = DarkmatterLawMark.detonate(player, target);
                    if (detonation > 0.0f) {
                        target.invulnerableTime = 0;
                        SkillDamageUtil.applyDirect(level, target, source, detonation);
                        target.invulnerableTime = 0;
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
                    }
                    if (target.isAlive()) {
                        var hurt = hurtWithArmorPenetration(
                                target, level, source, damage,
                                penetration(phase.beta(), context.milestone()));
                        if (hurt) {
                            target.addEffect(new MobEffectInstance(
                                    MobEffects.WEAKNESS,
                                    corrosionTicks(phase.beta(), context.milestone()), 0));
                            applied[0] = true;
                        }
                    }
                    if (wasAlive && !target.isAlive()) {
                        system.getDarkmatterResourceManager().creditEarnedMatter(
                                player, target.getMaxHealth() / 10.0f);
                    }
                    level.sendParticles(ParticleTypes.CLOUD,
                            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            16, 0.45, 0.45, 0.45, 0.03);
                }

                if (DestroyBlocksSetting.canDestroyBlocks(player, skill)) {
                    var seed = requestedSeed != null
                            ? requestedSeed.immutable()
                            : nearestBreakableSeed(level, player, impact, phase.alpha(),
                            context.milestone());
                    if (seed != null) {
                        var candidates = collectBlockTargets(
                                level, player, seed,
                                hitFace == null ? Direction.UP : hitFace,
                                phase.alpha(), phase.gamma(), context.milestone(),
                                Skills.DARKMATTER_SIX_WINGS.get()
                                        .getEffectiveProficiencyMilestone(player));
                        var fortune = fortuneLevel(phase.beta());
                        for (var candidate : candidates) {
                            // Permission and break events are deliberately checked per block.
                            if (!canDestroy(level, player, candidate)) continue;
                            applied[0] |= destroyWithPhaseDrops(
                                    level, player, candidate, fortune,
                                    context.milestone() >= 3);
                        }
                    }
                }
                if (!applied[0]) return;
                for (var item : level.getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(impact, impact).inflate(
                                phase.gamma() > 0.0f ? 2.0 + phase.gamma() : 1.5),
                        ItemEntity::isAlive)) {
                    item.teleportTo(player.getX(), player.getY() + 0.5, player.getZ());
                    item.setDeltaMovement(Vec3.ZERO);
                }
                level.playSound(null, BlockPos.containing(impact), SoundEvents.GRAVEL_BREAK,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
            });
            return executed && applied[0];
        }

        private static BlockPos nearestBreakableSeed(
                ServerLevel level,
                ServerPlayer player,
                Vec3 impact,
                float alpha,
                int milestone
        ) {
            var center = BlockPos.containing(impact);
            var candidates = new ArrayList<BlockPos>();
            for (var pos : BlockPos.betweenClosed(center.offset(-2, -2, -2),
                    center.offset(2, 2, 2))) {
                if (isStructurallyBreakable(level, player, pos, alpha, milestone)) {
                    candidates.add(pos.immutable());
                }
            }
            return candidates.stream()
                    .min(Comparator.comparingDouble(pos ->
                            Vec3.atCenterOf(pos).distanceToSqr(impact)))
                    .orElse(null);
        }

        static double damageRadius(float alpha) {
            return 1.0 + 2.0 * Math.max(0.0f, alpha);
        }

        static float damage(float alpha) {
            return BASE_DAMAGE + 2.0f * Math.max(0.0f, alpha);
        }

        private static List<BlockPos> collectBlockTargets(
                ServerLevel level,
                ServerPlayer player,
                BlockPos origin,
                Direction hitFace,
                float alpha,
                float gamma,
                int milestone,
                int sixWingsMilestone
        ) {
            if (!isStructurallyBreakable(
                    level, player, origin, alpha, milestone)) return List.of();
            var originBlock = level.getBlockState(origin).getBlock();
            var ordered = new LinkedHashSet<BlockPos>();
            ordered.addAll(connectedTargets(
                    origin,
                    alphaChainTargetLimit(alpha),
                    prioritizedDirections(hitFace),
                    candidate -> level.hasChunkAt(candidate)
                            && level.getBlockState(candidate).is(originBlock)
                            && isStructurallyBreakable(
                            level, player, candidate, alpha, milestone)
            ));
            if (gamma > 0.0f) {
                var radius = gammaRadius(gamma, milestone, sixWingsMilestone);
                var intRadius = (int) Math.ceil(radius);
                for (var candidate : BlockPos.betweenClosed(
                        origin.offset(-intRadius, -intRadius, -intRadius),
                        origin.offset(intRadius, intRadius, intRadius))) {
                    if (candidate.distSqr(origin) <= radius * radius
                            && isStructurallyBreakable(
                            level, player, candidate, alpha, milestone)) {
                        ordered.add(candidate.immutable());
                    }
                }
            }
            // A deterministic center-out order makes large fields expand as a sphere instead
            // of carving an axis-aligned box one scan line at a time.
            return ordered.stream()
                    .sorted(Comparator
                            .comparingDouble((BlockPos candidate) -> candidate.distSqr(origin))
                            .thenComparingInt(BlockPos::getY)
                            .thenComparingInt(BlockPos::getX)
                            .thenComparingInt(BlockPos::getZ))
                    .toList();
        }

        static int alphaChainTargetLimit(float alpha) {
            if (!Float.isFinite(alpha)) return 1;
            return 1 + 2 * Math.max(0, (int) Math.floor(alpha));
        }

        static List<BlockPos> connectedTargets(
                BlockPos origin,
                int limit,
                Predicate<BlockPos> matches
        ) {
            return traverseConnected(
                    origin, limit, List.of(Direction.values()), matches);
        }

        static List<BlockPos> connectedTargets(
                BlockPos origin,
                int limit,
                List<Direction> neighborOrder,
                Predicate<BlockPos> matches
        ) {
            if (neighborOrder == null || neighborOrder.size() != Direction.values().length
                    || new HashSet<>(neighborOrder).size() != Direction.values().length) {
                return traverseConnected(
                        origin, limit, List.of(Direction.values()), matches);
            }
            var surface = traverseConnected(
                    origin, limit,
                    neighborOrder.subList(0, Direction.values().length - 2), matches);
            if (surface.size() >= limit) return surface;
            var result = new LinkedHashSet<BlockPos>(surface);
            result.addAll(traverseConnected(origin, limit, neighborOrder, matches));
            return result.stream().limit(limit).toList();
        }

        private static List<BlockPos> traverseConnected(
                BlockPos origin,
                int limit,
                List<Direction> neighborOrder,
                Predicate<BlockPos> matches
        ) {
            if (origin == null || matches == null || neighborOrder == null
                    || neighborOrder.isEmpty() || limit <= 0 || !matches.test(origin)) {
                return List.of();
            }
            var result = new ArrayList<BlockPos>(limit);
            var queued = new HashSet<BlockPos>();
            var queue = new ArrayDeque<BlockPos>();
            var immutableOrigin = origin.immutable();
            queued.add(immutableOrigin);
            queue.add(immutableOrigin);
            while (!queue.isEmpty() && result.size() < limit) {
                var current = queue.removeFirst();
                if (!matches.test(current)) continue;
                result.add(current);
                for (var direction : neighborOrder) {
                    var next = current.relative(direction).immutable();
                    if (queued.add(next)) queue.addLast(next);
                }
            }
            return List.copyOf(result);
        }

        static List<Direction> prioritizedDirections(Direction hitFace) {
            var face = hitFace == null ? Direction.UP : hitFace;
            var result = new ArrayList<Direction>(Direction.values().length);
            for (var direction : Direction.values()) {
                if (direction.getAxis() != face.getAxis()) result.add(direction);
            }
            result.add(face);
            result.add(face.getOpposite());
            return List.copyOf(result);
        }

        static int fortuneLevel(float beta) {
            return Math.max(0, Math.round(Float.isFinite(beta) ? beta : 0.0f));
        }

        private static boolean destroyWithPhaseDrops(
                ServerLevel level,
                ServerPlayer player,
                BlockPos pos,
                int fortune,
                boolean directToInventory
        ) {
            var state = level.getBlockState(pos);
            if (state.isAir()) return false;
            var tool = createLootTool(level.registryAccess(), fortune);
            var drops = Block.getDrops(
                    state, level, pos, level.getBlockEntity(pos), player, tool);
            if (!level.destroyBlock(pos, false, player)) return false;
            for (var drop : drops) {
                if (drop.isEmpty()) continue;
                if (directToInventory) player.getInventory().add(drop);
                if (!drop.isEmpty()) Block.popResource(level, pos, drop);
            }
            return true;
        }

        public static ItemStack createLootTool(RegistryAccess access, int fortune) {
            var tool = new ItemStack(
                    org.academy.internal.common.world.item.Items.DARKMATTER_TOOL.get());
            if (access != null && fortune > 0) {
                DarkmatterItemUtil.setEnchantmentLevel(
                        access, tool, Enchantments.FORTUNE, fortune);
            }
            return tool;
        }

        private record Hit(LivingEntity entity, BlockHitResult block, Vec3 location) {
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.DARKMATTER_DISASSEMBLE_CAST.get();
        }
    }
}
