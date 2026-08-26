package org.academy.internal.common.ability.meltdowner.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackExecutor;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackPayload;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.ability.accelerator.reflection.ResolvedLinearAttack;
import org.academy.internal.common.ability.meltdowner.ContinuousBeamReflection;
import org.academy.internal.common.ability.meltdowner.ContinuousReflectionSession;
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamActions;
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.academy.internal.common.ability.meltdowner.skills.ContinuousBeam;
import org.academy.internal.common.ability.meltdowner.skills.lv1.SingleHighSpeedElectronBeam;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;

public final class MiningBeam extends Skill {
    static final int CP_INTERVAL_TICKS = 20;
    static final int BREAK_INTERVAL_TICKS = 3;
    static final int DAMAGE_INTERVAL_TICKS = 20;
    static final float MAX_LENGTH = 48.0f;
    static final float DAMAGE_RADIUS = 0.6f;
    static final float BASE_DAMAGE = 12.0f;
    static final float BREAK_RADIUS = 0.35f;
    static final int MINING_TIER = 4;

    public MiningBeam() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(20)
                .iterationTicks(5)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition(
                        "Single High-Speed Electron Beam",
                        "academy:single_high_speed_electron_beam"
                ))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_M,
                        InputConstants.PRESS,
                        0
                )
        ), ctx -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_STOP, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_STOP,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_M,
                        InputConstants.RELEASE,
                        0
                )
        ), ctx -> Client.stop());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MELTDOWNER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.MINING_BEAM.get(),
                        List.of(SingleHighSpeedElectronBeam.Client.SKILL_INFO),
                        R.textures.mining_beam_icon,
                        75,
                        75
                )
        );
        public static final String KEY_NAME_START = SkillNames.MINING_BEAM + "_start";
        public static final String KEY_NAME_STOP = SkillNames.MINING_BEAM + "_stop";
        public static Config CONFIG = new Config();

        public static void start() {
            if (!AbilitySystemClient.canUseSkill(Skills.MINING_BEAM.get())) return;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        public static void stop() {
            MisakaNetworkClient.send(StopPacket.INSTANCE);
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

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.MINING_BEAM.get();
            if (!skill.isEnabled(player) || CONTEXT_MAP.containsKey(player)) return;
            var context = new Context(player);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
            skill.reportTrigger(player);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var context = CONTEXT_MAP.get(player);
            if (context != null) context.end();
        }
    }

    public static final class Context extends ServerContext {
        private final ServerLevel initialLevel;
        private boolean ended;
        private int ticks;
        private final int proficiencyMilestone;
        private final float maximumLength;
        private final float breakRadius;
        private float currentLength;
        private final HighSpeedElectronBeam visual;
        private final ContinuousReflectionSession reflectionSession = new ContinuousReflectionSession();

        private Context(ServerPlayer player) {
            super(player);
            initialLevel = player.level();
            proficiencyMilestone = Skills.MINING_BEAM.get().getEffectiveProficiencyMilestone(player);
            maximumLength = proficiencyMilestone >= 2 ? 56.0f : MAX_LENGTH;
            breakRadius = proficiencyMilestone >= 2 ? BREAK_RADIUS * 1.2f : BREAK_RADIUS;
            currentLength = maximumLength;
            visual = ContinuousBeam.spawnFromMainHand(initialLevel, player, 1.0f, maximumLength);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.MINING_BEAM.get();
            if (player.level() != initialLevel
                    || !skill.isEnabled(player)
                    || !player.isAlive()
                    || player.hasDisconnected()) {
                end();
                return;
            }

            ticks++;
            skill.reportActivity(player, false);
            if (!ContinuousBeam.follow(player, visual, currentLength)) {
                end();
                return;
            }
            var start = player.getEyePosition();
            visual.setPos(start);
            if (ticks % CP_INTERVAL_TICKS == 0
                    && !skill.executeContinuous(player, (_, _) -> {
            }, false)) {
                end();
                return;
            }

            var breakTick = ticks % BREAK_INTERVAL_TICKS == 0;
            var damageTick = ticks % DAMAGE_INTERVAL_TICKS == 0;
            var destroyBlocks = DestroyBlocksSetting.canDestroyBlocks(
                    player,
                    Skills.MINING_BEAM.get()
            );
            if (breakTick) {
                var result = LevelUtil.destroyBlocksAlongPath(
                        initialLevel,
                        start,
                        start.add(player.getLookAngle().scale(maximumLength)),
                        breakRadius,
                        MINING_TIER,
                        true,
                        true,
                        true,
                        true,
                        player
                );
                currentLength = (float) result.getValue().doubleValue();
                visual.setBeamLength(currentLength);
            }

            var system = AbilitySystemServer.getSystem(player);
            var damage = calculateDamage(
                    system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                    system.getPlayerDamageMultiplier(player.getUUID())
            );
            var end = start.add(player.getLookAngle().scale(currentLength));
            var payload = LinearAttackPayload.builder(
                            player,
                            skill,
                            SkillDamageSource.of(player, skill),
                            proficiencyMilestone >= 2 ? DAMAGE_RADIUS * 1.2f : DAMAGE_RADIUS
                    )
                    .damage(_ -> damage)
                    .build();
            var attack = ContinuousBeamReflection.resolve(
                    initialLevel,
                    new LinearSegment(start, end),
                    payload,
                    reflectionSession,
                    ticks,
                    DAMAGE_INTERVAL_TICKS,
                    damageTick
            );

            if (breakTick && destroyBlocks) {
                skill.reportActivity(player, true);
                MeltdownerBeamActions.destroyBlocksAlongSegment(
                        initialLevel,
                        attack.outbound(),
                        breakRadius,
                        MINING_TIER,
                        true,
                        true,
                        true,
                        player
                );
            }
            LinearAttackExecutor.SegmentExecutionResult outboundResult = null;
            if (damageTick) {
                outboundResult = LinearAttackExecutor.executeOutbound(initialLevel, attack, payload);
            }
            if (attack.isReflected()) {
                var returnSegment = attack.returnSegment().orElseThrow();
                var returnLength = MeltdownerBeamActions.executeBlocksAlongSegment(
                        initialLevel,
                        returnSegment,
                        breakRadius,
                        MINING_TIER,
                        true,
                        true,
                        true,
                        !(breakTick && destroyBlocks),
                        attack.reflectionCandidate().orElseThrow().reflector()
                );
                attack = attack.limitReturnLength(returnLength);
            }
            updateVisual(attack);
            if (damageTick) {
                LinearAttackExecutor.executeReturn(initialLevel, attack, payload, outboundResult);
            }
        }

        private void updateVisual(ResolvedLinearAttack attack) {
            if (attack.isReflected()) {
                var returnSegment = attack.returnSegment().orElseThrow();
                visual.setReflection(
                        (float) attack.outbound().length(),
                        (float) attack.returnVisualLength(),
                        returnSegment.direction()
                );
            } else visual.clearReflection();
        }

        private void end() {
            if (ended) return;
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            Server.CONTEXT_MAP.remove(player, this);
            ContinuousBeam.kill(visual);
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

    public static boolean dropRefinedResources(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            ServerPlayer player
    ) {
        var skill = Skills.MINING_BEAM.get();
        if (!skill.hasProficiencyMilestone(player, 3)
                || !ProficiencyPolicy.server(player).allowMiningBeamSmelting()) {
            return false;
        }
        var drops = Block.getDrops(
                state, level, pos, blockEntity, player, ItemStack.EMPTY);
        if (drops.isEmpty()) return false;
        var refinedAny = false;
        for (var drop : drops) {
            var input = new SingleRecipeInput(drop.copyWithCount(1));
            var recipe = level.getServer().getRecipeManager().getRecipeFor(
                    RecipeType.SMELTING, input, level).orElse(null);
            if (recipe == null) {
                Block.popResource(level, pos, drop);
                continue;
            }
            var output = recipe.value().assemble(input);
            if (output.isEmpty()) {
                Block.popResource(level, pos, drop);
                continue;
            }
            output.setCount(output.getCount() * drop.getCount());
            Block.popResource(level, pos, output);
            var experience = Math.round(recipe.value().experience() * drop.getCount());
            if (experience > 0) ExperienceOrb.award(level,
                    Vec3.atCenterOf(pos), experience);
            refinedAny = true;
        }
        return true;
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.MINING_BEAM_START.get();
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
            return PacketTypes.MINING_BEAM_STOP.get();
        }
    }
}
