package org.academy.internal.common.ability.level0.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.gui.screen.OutputControlScreen;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.client.gui.screen.OutputControlScreen;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.OutputControlData;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class OutputControl extends Skill {
    private static final Identifier MOVEMENT_SCALE_ID =
            AcademyCraft.academy("output_control.movement_speed");
    private static final Identifier JUMP_SCALE_ID =
            AcademyCraft.academy("output_control.jump_height");
    private static final float DEFAULT_VALUE = 1.0f;
    private static final double SPRINT_SPEED_MULTIPLIER = 1.3;
    private static final double ATTRIBUTE_EPSILON = 1.0E-6;
    private static final ThreadLocal<Deque<DamageContext>> DAMAGE_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> DAMAGE_SCALING_BYPASS_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> OUTPUT_ADJUSTMENT_BYPASS_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    public OutputControl() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .common()
                .level(AbilityLevel.LEVEL3)
                .passive()
                .maintenanceCost(0)
                .iterationTicks(40)
                .energyCost(30_000)
                .dependsOn(Skills.BRAIN_DOMAIN_DEVELOPMENT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .withCustomData(OutputControlData.ID, OutputControlData.class, OutputControlData::new)
        );
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static float adjustCpCost(
            AbilitySystemServer system,
            UUID playerId,
            Skill chargedSkill,
            float amount
    ) {
        if (!(amount > 0.0f) || !Float.isFinite(amount)
                || chargedSkill == null
                || !chargedSkill.isOutputAdjustableDamage()
                || isOutputAdjustmentBypassed()) {
            return amount;
        }
        var playerData = system.getPlayerData(playerId);
        var raw = playerData.getSkillDataMap().get(Skills.OUTPUT_CONTROL.get().getKeyString());
        if (!(raw instanceof OutputControlData data) || !data.isEnabled()) return amount;
        return amount * cpMultiplier(data.getAbilityOutput());
    }

    static float cpMultiplier(float abilityOutput) {
        return ProgramPowerScale.costMultiplier(Mth.clamp(
                abilityOutput, ProgramPowerScale.MIN, ProgramPowerScale.MAX));
    }

    static float scaleDamage(float damage, float abilityOutput) {
        if (!(damage > 0.0f) || !Float.isFinite(damage)) return damage;
        var output = Mth.clamp(
                abilityOutput, ProgramPowerScale.MIN, ProgramPowerScale.MAX);
        return damage * ProgramPowerScale.effectMultiplier(output);
    }

    public static float adjustDamage(DamageSource source, float damage) {
        var output = abilityOutput(source);
        return Float.isFinite(output) ? scaleDamage(damage, output) : damage;
    }

    public static float finalizeDamage(DamageSource source, float damage) {
        if (DAMAGE_SCALING_BYPASS_DEPTH.get() > 0) return damage;
        var context = currentDamageContext(source);
        var output = context == null ? abilityOutput(source) : context.abilityOutput;
        if (!Float.isFinite(output)) return damage;
        if (context != null) context.finalized = true;
        return scaleDamage(damage, output);
    }

    public static void pushDamageContext(DamageSource source) {
        DAMAGE_CONTEXT.get().push(new DamageContext(source, abilityOutput(source)));
    }

    public static void popDamageContext() {
        var stack = DAMAGE_CONTEXT.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) DAMAGE_CONTEXT.remove();
    }

    public static float modifyHealthWrite(LivingEntity entity, float requestedHealth) {
        if (DAMAGE_SCALING_BYPASS_DEPTH.get() > 0 || !Float.isFinite(requestedHealth)) {
            return requestedHealth;
        }
        var stack = DAMAGE_CONTEXT.get();
        if (stack.isEmpty()) {
            DAMAGE_CONTEXT.remove();
            return requestedHealth;
        }
        var context = stack.peek();
        if (context == null || !Float.isFinite(context.abilityOutput)) return requestedHealth;

        var currentHealth = entity.getHealth();
        if (!(requestedHealth < currentHealth)) return requestedHealth;
        var healthLoss = currentHealth - requestedHealth;
        return currentHealth - scaleHealthLoss(
                healthLoss, context.abilityOutput, context.finalized
        );
    }

    public static void runWithoutDamageScaling(Runnable action) {
        DAMAGE_SCALING_BYPASS_DEPTH.set(DAMAGE_SCALING_BYPASS_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            var depth = DAMAGE_SCALING_BYPASS_DEPTH.get() - 1;
            if (depth <= 0) DAMAGE_SCALING_BYPASS_DEPTH.remove();
            else DAMAGE_SCALING_BYPASS_DEPTH.set(depth);
        }
    }

    /** Executes Precision Operation code without stacking Tab output onto its own power setting. */
    public static <T> T callWithoutOutputAdjustment(Supplier<T> action) {
        OUTPUT_ADJUSTMENT_BYPASS_DEPTH.set(OUTPUT_ADJUSTMENT_BYPASS_DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            var depth = OUTPUT_ADJUSTMENT_BYPASS_DEPTH.get() - 1;
            if (depth <= 0) OUTPUT_ADJUSTMENT_BYPASS_DEPTH.remove();
            else OUTPUT_ADJUSTMENT_BYPASS_DEPTH.set(depth);
        }
    }

    public static void runWithoutOutputAdjustment(Runnable action) {
        callWithoutOutputAdjustment(() -> {
            action.run();
            return null;
        });
    }

    public static boolean isOutputAdjustmentBypassed() {
        return OUTPUT_ADJUSTMENT_BYPASS_DEPTH.get() > 0;
    }

    private static float abilityOutput(DamageSource source) {
        if (isOutputAdjustmentBypassed()
                || !(source instanceof SkillDamageSource skillSource)
                || skillSource.getSkill() == Skills.PRECISION_OPERATION.get()
                || !(skillSource.getEntity() instanceof ServerPlayer player)) {
            return Float.NaN;
        }
        var data = enabledData(player);
        return data == null ? Float.NaN : data.getAbilityOutput();
    }

    private static DamageContext currentDamageContext(DamageSource source) {
        var stack = DAMAGE_CONTEXT.get();
        if (stack.isEmpty()) {
            DAMAGE_CONTEXT.remove();
            return null;
        }
        var context = stack.peek();
        return context != null && context.source == source ? context : null;
    }

    static float scaleHealthLoss(float healthLoss, float abilityOutput, boolean finalized) {
        if (!(healthLoss > 0.0f) || !Float.isFinite(healthLoss)) return healthLoss;
        var output = Mth.clamp(abilityOutput, 0.0f, 2.0f);
        if (finalized) return healthLoss;
        return scaleDamage(healthLoss, output);
    }

    private static OutputControlData enabledData(ServerPlayer player) {
        var raw = AbilitySystemServer.getSystem(player)
                .getPlayerData(player.getUUID())
                .getSkillDataMap()
                .get(Skills.OUTPUT_CONTROL.get().getKeyString());
        return raw instanceof OutputControlData data && data.isEnabled() ? data : null;
    }

    private static void syncAttributeScales(ServerPlayer player) {
        var data = enabledData(player);
        var movementFactor = data == null ? DEFAULT_VALUE : data.getMovementSpeed();
        var jumpFactor = data == null ? DEFAULT_VALUE : data.getJumpHeight();

        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            var baseline = movement.getBaseValue()
                    * (player.isSprinting() ? SPRINT_SPEED_MULTIPLIER : 1.0);
            syncPositiveBonusScale(movement, MOVEMENT_SCALE_ID, movementFactor, baseline);
        }
        var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            syncPositiveBonusScale(jump, JUMP_SCALE_ID, jumpFactor, jump.getBaseValue());
        }
    }

    private static void syncPositiveBonusScale(
            AttributeInstance attribute,
            Identifier modifierId,
            float factor,
            double baseline
    ) {
        var current = attribute.getModifier(modifierId);
        var currentScale = current != null
                && current.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ? 1.0 + current.amount()
                : 1.0;
        var unscaled = Math.abs(currentScale) > ATTRIBUTE_EPSILON
                ? attribute.getValue() / currentScale
                : attribute.getValue();
        var clampedFactor = Mth.clamp(factor, 0.0f, 1.0f);

        if (clampedFactor >= 1.0f - ATTRIBUTE_EPSILON
                || !(unscaled > baseline + ATTRIBUTE_EPSILON)
                || !(unscaled > ATTRIBUTE_EPSILON)) {
            if (current != null) attribute.removeModifier(modifierId);
            return;
        }

        var target = Mth.lerp(clampedFactor, baseline, unscaled);
        var modifierAmount = target / unscaled - 1.0;
        if (current != null
                && current.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                && Math.abs(current.amount() - modifierAmount) <= ATTRIBUTE_EPSILON) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(
                modifierId,
                modifierAmount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilitySystemClient.addCommonSkillInfo(new AbilitySystemClient.SkillInfo(
                        Skills.OUTPUT_CONTROL.get(),
                        List.of(BrainDomainDevelopment.Client.SKILL_INFO),
                        R.textures.ability.level0.skill.absolute_self_control.icon,
                        120,
                        100
                ));
        public static final String KEY_NAME_OPEN = SkillNames.OUTPUT_CONTROL + "_open";

        private Client() {
        }

        private static void initialize() {
            InputSystem.addKeyBinding(
                    KEY_NAME_OPEN,
                    InputSystem.combo(
                            InputSystem.InputType.KEYBOARD,
                            GLFW.GLFW_KEY_TAB,
                            InputConstants.PRESS,
                            0
                    ),
                    Client::open
            );
        }

        private static void open(InputSystem.BindingContext context) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.gui.screen() != null) return;
            var data = AbilitySystemClient.getSkillData(
                    Skills.OUTPUT_CONTROL.get(), OutputControlData.class
            ).filter(OutputControlData::isEnabled).orElse(null);
            if (data == null || !AbilitySystemClient.isSkillLearned(Skills.OUTPUT_CONTROL.get())) return;
            minecraft.gui.setScreen(new OutputControlScreen(
                    data.getAbilityOutput(), data.getMovementSpeed(), data.getJumpHeight(),
                    context.type(), context.input()
            ));
        }

        public static void sendSettings(float abilityOutput, float movementSpeed, float jumpHeight) {
            MisakaNetworkClient.send(new SettingsPacket(abilityOutput, movementSpeed, jumpHeight));
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(SettingsPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.OUTPUT_CONTROL.get();
            if (!skill.isEnabled(player)) return;
            AbilitySystemServer.getSystem(player).updatePlayerSkillData(
                    player.getUUID(),
                    skill,
                    OutputControlData.class,
                    data -> {
                        data.setAbilityOutput(packet.abilityOutput);
                        data.setMovementSpeed(packet.movementSpeed);
                        data.setJumpHeight(packet.jumpHeight);
                    }
            );
            syncAttributeScales(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onDamagePre(LivingDamageEvent.Pre event) {
            event.setNewDamage(finalizeDamage(event.getSource(), event.getNewDamage()));
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) syncAttributeScales(player);
        }
    }

    private static final class DamageContext {
        private final DamageSource source;
        private final float abilityOutput;
        private boolean finalized;

        private DamageContext(DamageSource source, float abilityOutput) {
            this.source = source;
            this.abilityOutput = abilityOutput;
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SettingsPacket
            extends Packet<ServerGamePacketListenerImpl, SettingsPacket> {
        public static final StreamCodec<ByteBuf, SettingsPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.FLOAT.encode(buf, packet.abilityOutput);
                    ByteBufCodecs.FLOAT.encode(buf, packet.movementSpeed);
                    ByteBufCodecs.FLOAT.encode(buf, packet.jumpHeight);
                },
                buf -> new SettingsPacket(
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf)
                )
        );

        private final float abilityOutput;
        private final float movementSpeed;
        private final float jumpHeight;

        public SettingsPacket(float abilityOutput, float movementSpeed, float jumpHeight) {
            this.abilityOutput = abilityOutput;
            this.movementSpeed = movementSpeed;
            this.jumpHeight = jumpHeight;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SettingsPacket> getPacketType() {
            return PacketTypes.OUTPUT_CONTROL_SETTINGS.get();
        }
    }
}
