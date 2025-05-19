package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.ClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.ClassPacketHandler;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.vanilla.EnvType;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.api.server.tick.ServerTickEvent;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.Smoke;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class DirStrike extends Skill {
    public static final Skill INSTANCE = new DirStrike();

    private DirStrike() {
        super(SkillNames.DIR_STRIKE, 3, List.of(VectorReflection.INSTANCE));
    }

    @Override
    public void initClient() {
        AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(name, Client.CONFIG);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(Client.KEY_NAME_START, new InputSystem.InputPair(
                InputSystem.InputType.KEYBOARD,
                new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                        GLFW.GLFW_PRESS,
                        new LinkedHashSet<>(
                                Set.of(
                                        GLFW.GLFW_MOD_ALT
                                )
                        )
                )
        )), Client::onStart);
        InputSystem.addKeyBinding(Client.KEY_NAME_END, Client.CONFIG.getKeyBinding(Client.KEY_NAME_END, new InputSystem.InputPair(
                InputSystem.InputType.KEYBOARD,
                new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(
                                Set.of(
                                )
                        )
                )
        )), Client::onEnd);
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.registerPacketHandlerClass(Server.class);
    }

    public static final class Client {
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Accelerator.INSTANCE, INSTANCE, List.of(VectorReflection.Client.SKILL_INFO),
                        TextureResources.TEXTURE_DIR_STRIKE_ICON, 100, 110);
        public static Context context;
        public static final String KEY_NAME_START = SkillNames.DIR_STRIKE + "_start";
        public static final String KEY_NAME_END = SkillNames.DIR_STRIKE + "_end";
        public static final Config CONFIG = new Config();

        public static void onStart() {
            if (context != null) return;
            if (Minecraft.getInstance().player == null) return;
            context = new Client.Context(Minecraft.getInstance().player);
            AbilitySystemClient.registerContext(context);
            NetworkSystemClient.sendPacket(new C2SPacket(new StartPacket()));
        }

        public static void onEnd() {
            if (Minecraft.getInstance().player == null) return;
            if (context != null) {
                context.release();
            }
        }

        public static final class Config extends ClientConfig.KeyBindingConfig {
            private Config() {
            }
        }

        public static final class Context implements ClientContext {
            public LocalPlayer player;
            public int ticks;
            public float originPitch;
            public float maxPitch;
            private boolean released = false;
            private float targetPitch;

            public Context(LocalPlayer player) {
                this.player = player;
                originPitch = player.getXRot();
                maxPitch = originPitch - 20;
            }

            public void release() {
                if (!released) {
                    released = true;
                    targetPitch = originPitch;
                }
            }

            @SubscribeEvent
            public void onClientTick(ClientTickEvent event) {
                ticks++;
                if (ticks > 40) release();

                float currentPitch = player.getXRot();

                if (!released) {
                    targetPitch = maxPitch;
                    if (currentPitch > targetPitch) {
                        float newPitch = currentPitch - 0.35f;
                        player.setXRot(newPitch);
                    }
                } else {
                    float newPitch = currentPitch + 5;
                    if (newPitch > targetPitch) newPitch = targetPitch;
                    player.setXRot(newPitch);

                    if (Math.abs(newPitch - targetPitch) < 1e-3) {
                        AbilitySystemClient.unregisterContext(this);
                        context = null;
                        NetworkSystemClient.sendPacket(new C2SPacket(new EndPacket()));
                    }
                }
            }
        }
    }

    public static final class Server {
        public static final Map<UUID, ServerContext> CONTEXT_MAP = new HashMap<>();

        @ClassPacketHandler
        public static void onStart(StartPacket packet) {
            ServerPlayer serverPlayer = packet.packetListenerSupplier.get().getPlayer();
            if (CONTEXT_MAP.containsKey(serverPlayer.getUUID())) {
                ServerContext context = CONTEXT_MAP.get(serverPlayer.getUUID());
                AbilitySystemServer.unregisterContext(context);
            }
            Context context = new Context(serverPlayer);
            CONTEXT_MAP.put(serverPlayer.getUUID(), context);
            AbilitySystemServer.registerContext(context);
        }

        @ClassPacketHandler
        public static void onEnd(EndPacket packet) {
            ServerPlayer serverPlayer = packet.packetListenerSupplier.get().getPlayer();
            UUID uuid = serverPlayer.getUUID();
            if (CONTEXT_MAP.containsKey(uuid)) {
                ServerContext context = CONTEXT_MAP.get(uuid);
                AbilitySystemServer.unregisterContext(context);
                CONTEXT_MAP.remove(uuid);

                Vec3 basePos = serverPlayer.position();
                Vec3 lookDir = Vec3.directionFromRotation(0, serverPlayer.getYRot()).normalize();
                Level level = serverPlayer.level();

                for (int i = 1; i <= 5; i++) {
                    Vec3 targetPos = basePos.add(lookDir.scale(i));
                    Smoke smoke = new Smoke(EntityTypes.SMOKE_ENTITY_TYPE, level);
                    smoke.setPos(targetPos.x, targetPos.y + 0.5, targetPos.z);
                    level.addFreshEntity(smoke);
                }

                var attackArea = new AABB(basePos, basePos.add(lookDir.scale(5))).inflate(1.0);
                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, attackArea, e ->
                        e != serverPlayer && e.isAlive());

                for (LivingEntity target : targets) {
                    target.hurt(level.damageSources().playerAttack(serverPlayer), 6.0f);
                }
            }
        }

        public static final class Context implements ServerContext {
            public final ServerPlayer serverPlayer;

            private Context(ServerPlayer serverPlayer) {
                this.serverPlayer = serverPlayer;
            }

            @SubscribeEvent
            public void onTickEvent(ServerTickEvent event) {
            }
        }
    }

    @PacketTarget(EnvType.SERVER)
    public static final class StartPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }

    @PacketTarget(EnvType.SERVER)
    public static final class EndPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }
}