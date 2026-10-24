package org.academy.internal.common.ability.electromaster.skills.lv2;

import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.api.server.team.TeamRelations;
import org.academy.api.server.ability.electromaster.ElectromasterActions;
import org.academy.api.server.ability.AbilityExecutionHandle;
import org.academy.api.server.ability.AbilityCpAccount;
import org.academy.api.server.ability.AbilityActorContext;
import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;


public class LightningNova extends Skill {
    private static final int MAX_RADIUS = 16;
    private static final float DAMAGE = 4.0f;
    private static final int PULSE_DURATION = 200;
    private static final int VISUAL_INTERVAL = 10;

    public LightningNova() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .damage()
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(15)
                .iterationTicks(10)
                .maxStacks(1)
                .dependsOn(Skills.THUNDER_LANCE)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBindingMigratingDefaults(
                        Client.KEY_NAME_USE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                                InputConstants.PRESS, InputConstants.MOD_SHIFT),
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.PRESS, InputConstants.MOD_SHIFT))
                , ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.LIGHTNING_NOVA + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            var minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.LIGHTNING_NOVA.get())) return;
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public LightningNova.Client.Config getDefault() {
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
        public static float calculateDamage(float abilityPower, float playerMultiplier) {
            return DAMAGE * Math.max(0.0f, abilityPower) * Math.max(0.0f, playerMultiplier);
        }

        @SubscribePacket
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            tryActivate(player);
        }

        public static boolean tryActivate(ServerPlayer player) {
            return Skills.LIGHTNING_NOVA.get().executeActive(player,
                    (ctx, actualCost) -> AbilitySystemServer.registerContext(new Context(player, ctx.milestone())));
        }
    }

    public static final class Context extends ServerContext {
        private final ServerLevel castLevel;
        private final AbilityExecutionHandle effect;

        private Context(ServerPlayer player, int milestone) {
            super(player);
            castLevel = player.level();
            var actor = AbilityActorContext.of(player, Skills.LIGHTNING_NOVA.get(),
                    new AbilityCpAccount(0));
            effect = ElectromasterActions.lightningNova(actor,
                    target -> target != player && target.isAlive()
                            && !TeamRelations.areAllied(player, target)
                            && !FriendlyFireSetting.shouldPrevent(player, target));
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            if (player.hasDisconnected() || !player.isAlive() || level() != castLevel || !effect.tick()) {
                unregister();
                return;
            }
            Skills.LIGHTNING_NOVA.get().reportActivity(player, false);
        }

        @Override
        protected void onUnregistered() { effect.stop(); }
    }

    static boolean withinWavefront(double distanceSquared, double innerSquared, double outerSquared) {
        return distanceSquared >= innerSquared && distanceSquared <= outerSquared;
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.LIGHTNING_NOVA_ACTIVATE_P4.get();
        }
    }
}
