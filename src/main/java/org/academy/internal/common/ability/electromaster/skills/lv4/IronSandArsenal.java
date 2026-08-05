package org.academy.internal.common.ability.electromaster.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class IronSandArsenal extends Skill {
    public static final int SWORD_COOLDOWN = 10;
    public static final int WHIP_COOLDOWN = 5;
    public static final int HAMMER_COOLDOWN = 40;

    public static final float SWORD_DAMAGE = 15.0f;
    public static final float WHIP_DAMAGE = 8.0f;
    public static final float HAMMER_DAMAGE = 25.0f;

    public static final double SWORD_RANGE = 3.0;
    public static final double WHIP_RANGE = 8.0;
    public static final double HAMMER_RADIUS = 3.0;

    public IronSandArsenal() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(50)
                .iterationTicks(40)
                .dependsOn(Skills.MAGNETIC_WEAPON)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_TOGGLE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_I, InputConstants.PRESS, InputConstants.MOD_ALT | InputConstants.MOD_SHIFT))
                , ctx -> Client.onToggle());

        InputSystem.addKeyBinding(Client.KEY_FORM, Client.CONFIG.getKeyBinding(Client.KEY_FORM,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_J, InputConstants.PRESS, InputConstants.MOD_ALT | InputConstants.MOD_SHIFT))
                , ctx -> Client.onSwitchForm());
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum IronSandForm {
        SWORD, WHIP, HAMMER, SHIELD;

        public IronSandForm next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static final class Client {
        public static final String KEY_TOGGLE = SkillNames.IRON_SAND_ARSENAL + "_toggle";
        public static final String KEY_FORM = SkillNames.IRON_SAND_ARSENAL + "_form";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.IRON_SAND_ARSENAL.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
            var p = Minecraft.getInstance().player;
            if (p == null) return;
        }

        public static void onSwitchForm() {
            if (!AbilitySystemClient.canUseSkill(Skills.IRON_SAND_ARSENAL.get())) return;
            MisakaNetworkClient.send(FormSelectPacket.INSTANCE);
            var p = Minecraft.getInstance().player;
            if (p == null) return;
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public IronSandArsenal.Client.Config getDefault() {
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

        public static float calculateDamage(float baseDamage, float playerMultiplier) {
            return Math.max(0.0f, baseDamage) * Math.max(0.0f, playerMultiplier);
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket p) {
            var player = p.getPacketListener().getPlayer();
            var skill = Skills.IRON_SAND_ARSENAL.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) {
                var ctx = CONTEXT_MAP.remove(player);
                if (ctx != null) ctx.end();
                return;
            }
            if (CONTEXT_MAP.containsKey(player)) return;
            var ctx = new Context(player);
            CONTEXT_MAP.put(player, ctx);
            AbilitySystemServer.registerContext(ctx);
        }

        @SubscribePacket
        public static void handleFormSelect(FormSelectPacket p) {
            var player = p.getPacketListener().getPlayer();
            var ctx = CONTEXT_MAP.get(player);
            if (ctx != null) {
                ctx.currentForm = ctx.currentForm.next();
            }
        }
    }

    public static final class Context extends ServerContext {
        private final Map<IronSandForm, Integer> cooldowns = new HashMap<>();
        private IronSandForm currentForm = IronSandForm.SWORD;
        private boolean ended;

        private Context(ServerPlayer p) {
            super(p);
            for (var form : IronSandForm.values()) {
                cooldowns.put(form, 0);
            }
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre ev) {
            var skill = Skills.IRON_SAND_ARSENAL.get();
            if (!skill.isEnabled(player) || !player.isAlive() || player.hasDisconnected()) {
                end();
                return;
            }
            if (!AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                    player.getUUID(), skill.getMaintenanceCost(skill.getLevel(player)), skill)) {
                if (skill.isEnabled(player)) skill.toggle(player);
                end();
                return;
            }

            for (var entry : new HashMap<>(cooldowns).entrySet()) {
                if (entry.getValue() > 0) {
                    cooldowns.put(entry.getKey(), entry.getValue() - 1);
                }
            }

            if (currentForm == IronSandForm.SHIELD) return;

            var cooldown = cooldowns.get(currentForm);
            if (cooldown > 0) return;

            var level = player.level();
            if (!(level instanceof ServerLevel sl)) return;
            var multiplier = AbilitySystemServer.getSystem(player)
                    .getPlayerDamageMultiplier(player.getUUID());
            var source = SkillDamageSource.of(player, skill);

            switch (currentForm) {
                case SWORD -> {
                    var lookDir = player.getLookAngle();
                    var attackPos = player.getEyePosition().add(lookDir.scale(SWORD_RANGE));
                    var targets = sl.getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().expandTowards(lookDir.scale(SWORD_RANGE)).inflate(1.0),
                            e -> e != player && e.isAlive() && !player.isAlliedTo(e)
                                    && player.hasLineOfSight(e));
                    if (!targets.isEmpty()) {
                        var target = targets.stream()
                                .min(Comparator.comparingDouble(player::distanceToSqr))
                                .orElseThrow();
                        target.hurtServer(sl, source, Server.calculateDamage(SWORD_DAMAGE, multiplier));
                        sl.sendParticles(ParticleTypes.CRIT,
                                target.getX(), target.getY(0.6), target.getZ(),
                                12, 0.25, 0.4, 0.25, 0.08);
                        cooldowns.put(IronSandForm.SWORD, SWORD_COOLDOWN);
                    }
                }
                case WHIP -> {
                    var lookDir = player.getLookAngle();
                    var startPos = player.getEyePosition();
                    for (var dist = 1.0; dist <= WHIP_RANGE; dist += 1.0) {
                        var checkPos = startPos.add(lookDir.scale(dist));
                        var box = new AABB(
                                checkPos.x - 0.5, checkPos.y - 0.5, checkPos.z - 0.5,
                                checkPos.x + 0.5, checkPos.y + 0.5, checkPos.z + 0.5);
                        var targets = sl.getEntitiesOfClass(LivingEntity.class, box,
                                e -> e != player && e.isAlive() && !player.isAlliedTo(e));
                        if (!targets.isEmpty()) {
                            targets.getFirst().hurtServer(sl, source,
                                    Server.calculateDamage(WHIP_DAMAGE, multiplier));
                            var knockback = lookDir.scale(0.5);
                            targets.getFirst().setDeltaMovement(targets.getFirst().getDeltaMovement().add(knockback));
                            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                    targets.getFirst().getX(), targets.getFirst().getY(0.6), targets.getFirst().getZ(),
                                    8, 0.25, 0.4, 0.25, 0.05);
                            cooldowns.put(IronSandForm.WHIP, WHIP_COOLDOWN);
                            break;
                        }
                    }
                }
                case HAMMER -> {
                    var targets = sl.getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(HAMMER_RADIUS),
                            e -> e != player && e.isAlive() && !player.isAlliedTo(e));
                    for (var t : targets) {
                        t.hurtServer(sl, source, Server.calculateDamage(HAMMER_DAMAGE, multiplier));
                        var knockback = t.position().subtract(player.position()).normalize().scale(1.5);
                        t.setDeltaMovement(t.getDeltaMovement().add(knockback.x, 0.4, knockback.z));
                        t.hurtMarked = true;
                    }
                    if (!targets.isEmpty()) {
                        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                player.getX(), player.getY(), player.getZ(),
                                20, HAMMER_RADIUS * 0.5, 0.2, HAMMER_RADIUS * 0.5, 0.05);
                        cooldowns.put(IronSandForm.HAMMER, HAMMER_COOLDOWN);
                    }
                }
            }
        }

        @SubscribeEvent
        public void onLivingHurt(LivingIncomingDamageEvent ev) {
            if (currentForm != IronSandForm.SHIELD) return;
            if (ev.getEntity() != player) return;
            ev.setAmount(ev.getAmount() * 0.5f);
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.CONTEXT_MAP.remove(player);
            unregister();
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
            return PacketTypes.IRON_SAND_ARSENAL_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class FormSelectPacket extends Packet<ServerGamePacketListenerImpl, FormSelectPacket> {
        public static final FormSelectPacket INSTANCE = new FormSelectPacket();
        public static final StreamCodec<ByteBuf, FormSelectPacket> CODEC = StreamCodec.unit(INSTANCE);

        private FormSelectPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, FormSelectPacket> getPacketType() {
            return PacketTypes.IRON_SAND_ARSENAL_FORM_SELECT.get();
        }
    }
}
