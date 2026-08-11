package org.academy.internal.common.ability.teleport.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportDamage;
import org.academy.internal.common.ability.teleport.TeleportTargeting;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.teleport.skills.lv1.SpaceFoldingTheorem;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class FleshRipping extends Skill {
    private static final double MAX_RANGE = 32.0;
    private static final float BASE_DAMAGE = 12.0f;
    private static final Identifier FLESH_RIPPING_ARMOR_ID = AcademyCraft.academy("flesh_ripping_armor");

    public FleshRipping() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(20)
                .iterationTicks(10)
                .maxStacks(10)
                .dependsOn(Skills.CUT_THROUGH)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition("Cut Through", "academy:cut_through"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT,
                        InputConstants.PRESS, InputConstants.MOD_ALT)
        ), ctx -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_END, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_END,
                InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), ctx -> Client.end());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.FLESH_RIPPING.get(),
                        List.of(CutThrough.Client.SKILL_INFO),
                        R.textures.flesh_ripping_icon,
                        130,
                        12
                )
        );
        public static final String KEY_NAME_START = SkillNames.FLESH_RIPPING + "_start";
        public static final String KEY_NAME_END = SkillNames.FLESH_RIPPING + "_end";
        public static Config CONFIG = new Config();
        private static RippingContext currentContext;

        private static void start() {
            if (ClientUtil.hasScreen() || currentContext != null
                    || !AbilitySystemClient.canUseSkill(Skills.FLESH_RIPPING.get())) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            currentContext = new RippingContext(player);
            AbilitySystemClient.registerContext(currentContext);
        }

        private static void end() {
            var context = currentContext;
            if (context == null) return;
            var targetId = context.targetEntityId;
            context.cleanup();
            if (!ClientUtil.hasScreen() && targetId >= 0) {
                MisakaNetworkClient.send(new CastPacket(targetId));
            }
        }

        public static final class RippingContext extends ClientContext {
            private final LocalPlayer player;
            private int targetEntityId = -1;

            private RippingContext(LocalPlayer player) {
                this.player = player;
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (currentContext != this || player.isRemoved()
                        || !AbilitySystemClient.canUseSkill(Skills.FLESH_RIPPING.get())) {
                    cleanup();
                    return;
                }
                var minecraft = Minecraft.getInstance();
                AABB preview;
                var living = findTarget(player);
                if (living != null) {
                    targetEntityId = living.getId();
                    preview = living.getBoundingBox().inflate(0.2);
                } else {
                    targetEntityId = -1;
                    var point = player.getEyePosition(event.getPartialTick())
                            .add(player.getViewVector(event.getPartialTick()).scale(range(player)));
                    preview = new AABB(point.x - 0.5, point.y - 0.5, point.z - 0.5,
                            point.x + 0.5, point.y + 0.5, point.z + 0.5);
                }

                var renderType = Render.RenderTypes.MINE_DETECT_LINES;
                var camera = minecraft.gameRenderer.mainCamera().position();
                var matrices = event.getMatrixStack();
                matrices.pushPose();
                matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
                event.submitCustomGeometry(renderType, (snapshot, consumer) ->
                        LineBoxRenderer.renderWireframeBox(snapshot, consumer, preview,
                                targetEntityId >= 0 ? 0.6f : 1.0f,
                                targetEntityId >= 0 ? 0.2f : 0.1f,
                                targetEntityId >= 0 ? 1.0f : 0.1f,
                                1.0f));
                matrices.popPose();
            }

            private static LivingEntity findTarget(LocalPlayer player) {
                return TeleportTargeting.findFirstLivingEntity(player, range(player));
            }

            private static double range(LocalPlayer player) {
                return AbilitySystemClient.getSkillProficiencyMilestone(Skills.FLESH_RIPPING.get()) >= 2
                        ? MAX_RANGE * 1.2 : MAX_RANGE;
            }

            private void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (currentContext == this) currentContext = null;
            }
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
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.FLESH_RIPPING.get();
            var maxRange = skill.hasProficiencyMilestone(player, 2) ? MAX_RANGE * 1.2 : MAX_RANGE;
            if (!(player.level().getEntity(packet.getTargetEntityId()) instanceof LivingEntity target)
                    || target == player || !target.isAlive()
                    || CtaFriendlyFireWhitelist.shouldProtect(player, target)
                    || player.distanceToSqr(target) > maxRange * maxRange) return;

            skill.executeActive(player, (ctx, actualCost) -> {
                if (!target.isAlive() || target.level() != player.level()
                        || player.distanceToSqr(target) > maxRange * maxRange) return;
                var damage = TeleportDamage.fleshRipping(
                        BASE_DAMAGE,
                        target.getMaxHealth(),
                        ctx.system().getPlayerAbilityPowerMultiplier(player.getUUID()),
                        SpaceFoldingTheorem.damageMultiplier(player)
                ) * ctx.system().getPlayerDamageMultiplier(player.getUUID());
                player.level().playSound(null, target.blockPosition(), SoundEvents.FLESH_RIPPING.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                var wasAlive = target.isAlive();
                target.hurtServer(
                        player.level(),
                        SkillDamageSource.of(player, skill),
                        damage
                );
                if (wasAlive && !target.isAlive()) {
                    SpaceFoldingTheorem.refundKillCost(player, actualCost);
                } else if (ctx.milestone() >= 3) {
                    applyArmorRend(player, target);
                }
            });
        }

        private static void applyArmorRend(net.minecraft.server.level.ServerPlayer owner, LivingEntity target) {
            var armor = target.getAttribute(Attributes.ARMOR);
            if (armor == null) return;
            if (armor.getModifier(FLESH_RIPPING_ARMOR_ID) != null) {
                armor.removeModifier(FLESH_RIPPING_ARMOR_ID);
            }
            var reduction = target instanceof Player ? -0.10 : -0.20;
            armor.addTransientModifier(new AttributeModifier(
                    FLESH_RIPPING_ARMOR_ID,
                    reduction,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
            TimedSkillEffectRuntime.put(owner, target.getUUID(), Skills.FLESH_RIPPING.get(),
                    "armor_rend", 100, (float) -reduction);
            TimedSkillEffectRuntime.schedule(owner, 100, () -> {
                if (TimedSkillEffectRuntime.get(owner.getUUID(), target.getUUID(),
                        Skills.FLESH_RIPPING.get(), "armor_rend", owner.level().getGameTime()).isEmpty()) {
                    var current = target.getAttribute(Attributes.ARMOR);
                    if (current != null) current.removeModifier(FLESH_RIPPING_ARMOR_ID);
                }
            });
        }

        public static float adjustHealing(LivingEntity target, float amount) {
            if (target == null || !Float.isFinite(amount) || amount <= 0.0f) return amount;
            var armorReduction = TimedSkillEffectRuntime.maxValueForTarget(
                    target.getUUID(), Skills.FLESH_RIPPING.get(), "armor_rend",
                    target.level().getGameTime());
            if (armorReduction <= 0.0f) return amount;
            // The milestone pairs 10%/20% armour reduction with 12.5%/25% healing reduction.
            return amount * Math.max(0.0f, 1.0f - armorReduction * 1.25f);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = ByteBufCodecs.VAR_INT
                .map(CastPacket::new, CastPacket::getTargetEntityId);
        private final int targetEntityId;

        public CastPacket(int targetEntityId) {
            this.targetEntityId = targetEntityId;
        }

        public int getTargetEntityId() {
            return targetEntityId;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.FLESH_RIPPING_CAST.get();
        }
    }
}
