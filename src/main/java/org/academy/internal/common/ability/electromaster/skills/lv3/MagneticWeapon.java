package org.academy.internal.common.ability.electromaster.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfx.ElectromasterWeaponVfxClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBlade;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;

public class MagneticWeapon extends Skill {
    private static final float RADIUS = 16.0f;
    private static final int ATTACK_COOLDOWN = 10;

    public MagneticWeapon() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(40)
                .iterationTicks(40)
                .dependsOn(Skills.MAGNET_MANIPULATION));
    }

    public static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.SWORDS);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                                InputConstants.PRESS, 0)),
                _ -> Client.onToggle());
        ToggleStatusHud.Companion.registerStateProvider(Skills.MAGNETIC_WEAPON.get(), Client::isActive);
        ElectromasterWeaponVfxClient.register();
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY = SkillNames.MAGNETIC_WEAPON + "_toggle";
        public static Config CONFIG = new Config();

        private static void onToggle() {
            if (AbilitySystemClient.beginToggleRequest(Skills.MAGNETIC_WEAPON.get())) {
                MisakaNetworkClient.send(TogglePacket.INSTANCE);
            }
        }

        private static boolean isActive() {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get()).active();
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

        private Server() {
        }

        public static float calculateDamage(float attackDamage, float playerMultiplier) {
            return Math.max(0.0f, attackDamage) * 0.6f * Math.max(0.0f, playerMultiplier);
        }

        static float calculateWeaponAttackDamage(ServerPlayer player, ItemStack stack) {
            var playerAttack = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (playerAttack == null) return 0.0f;

            var heldModifierIds = new HashSet<Identifier>();
            player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                    heldModifierIds.add(modifier.id());
                }
            });

            var calculated = new AttributeInstance(Attributes.ATTACK_DAMAGE, _ -> {
            });
            calculated.setBaseValue(playerAttack.getBaseValue());
            for (var modifier : playerAttack.getModifiers()) {
                if (!heldModifierIds.contains(modifier.id())) {
                    calculated.addOrUpdateTransientModifier(modifier);
                }
            }
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                    calculated.addOrUpdateTransientModifier(modifier);
                }
            });
            return (float) Math.max(0.0, calculated.getValue());
        }

        public static boolean isActive(ServerPlayer player) {
            return CONTEXT_MAP.containsKey(player) && Skills.MAGNETIC_WEAPON.get().isEnabled(player);
        }

        public static void forceDisable(ServerPlayer player) {
            var context = CONTEXT_MAP.remove(player);
            if (context != null) context.end(false);
            var skill = Skills.MAGNETIC_WEAPON.get();
            if (skill.isEnabled(player)) skill.toggle(player);
            clearData(player);
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (isActive(player)) {
                forceDisable(player);
                return;
            }
            var weaponSlot = findFirstHotbarSwordSlot(player);
            if (weaponSlot < 0) return;

            IronSandArsenal.Server.forceDisable(player);
            var skill = Skills.MAGNETIC_WEAPON.get();
            if (!skill.isEnabled(player)) skill.toggle(player);
            if (!skill.isEnabled(player)) return;
            var context = new Context(player, weaponSlot);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        private static int findFirstHotbarSwordSlot(ServerPlayer player) {
            for (var slot = 0; slot < 9; slot++) {
                if (isSword(player.getInventory().getItem(slot))) return slot;
            }
            return -1;
        }

        private static void clearData(ServerPlayer player) {
            player.setData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get(), Data.DEFAULT);
            player.syncData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get());
        }
    }

    public static final class Context extends ServerContext {
        private final MagneticWeaponBlade blade;
        private int weaponSlot;
        private int attackCooldown;
        private int attackSequence;
        private PendingAttack pendingAttack;
        private boolean ended;

        private Context(ServerPlayer player, int weaponSlot) {
            super(player);
            this.weaponSlot = weaponSlot;
            blade = new MagneticWeaponBlade(EntityTypes.MAGNETIC_WEAPON_BLADE.get(), player.level());
            blade.configure(player, player.getInventory().getItem(weaponSlot));
            player.level().addFreshEntity(blade);
            syncData();
        }

        private static boolean isEnemy(ServerPlayer player, LivingEntity entity) {
            if (entity == player || !entity.isAlive() || player.isAlliedTo(entity)) return false;
            if (entity instanceof ServerPlayer target
                    && (target.isCreative() || target.isSpectator())) return false;
            return player.getLastHurtByMob() == entity
                    || player.getLastHurtByPlayer() == entity
                    || entity instanceof Mob mob && mob.getTarget() == player;
        }

        private static void playArcSound(ServerLevel level, Vec3 position,
                                         float volume, float pitch) {
            level.playSound(
                    null,
                    position.x,
                    position.y,
                    position.z,
                    SoundEvents.ARC_WEAK.get(),
                    SoundSource.PLAYERS,
                    volume,
                    pitch
            );
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.MAGNETIC_WEAPON.get();
            if (!skill.isEnabled(player)
                    || !player.isAlive()
                    || player.hasDisconnected()
                    || blade.isRemoved()) {
                end(true);
                return;
            }

            var system = AbilitySystemServer.getSystem(player);
            if (!system.ensurePermanentOccupation(
                    player.getUUID(), skill.getMaintenanceCost(skill.getLevel(player)), skill)) {
                end(true);
                return;
            }

            if (attackCooldown > 0) attackCooldown--;
            if (pendingAttack != null && player.level() instanceof ServerLevel level) {
                advanceAttack(level, system);
            }

            if (pendingAttack == null) {
                weaponSlot = Server.findFirstHotbarSwordSlot(player);
                if (weaponSlot < 0) {
                    end(true);
                    return;
                }
                blade.setWeapon(player.getInventory().getItem(weaponSlot));
            }

            var stack = player.getInventory().getItem(weaponSlot);
            if (!stack.isEmpty()) {
                player.getCooldowns().addCooldown(stack, 3);
            }

            if (pendingAttack == null && attackCooldown <= 0
                    && player.level() instanceof ServerLevel level) {
                var target = level.getEntitiesOfClass(
                                LivingEntity.class,
                                player.getBoundingBox().inflate(RADIUS),
                                entity -> isEnemy(player, entity) && player.hasLineOfSight(entity)
                        ).stream()
                        .min(Comparator.comparingDouble(player::distanceToSqr))
                        .orElse(null);
                if (target != null) startAttack(target, stack);
            }
            syncData();
        }

        private void startAttack(LivingEntity target, ItemStack stack) {
            attackSequence++;
            pendingAttack = new PendingAttack(
                    target.getId(),
                    weaponSlot,
                    stack.copyWithCount(1)
            );
            attackCooldown = ATTACK_COOLDOWN;
            blade.startAttack(target.getId(), attackSequence);
        }

        private void advanceAttack(ServerLevel level, AbilitySystemServer system) {
            var pending = pendingAttack;
            if (pending == null) return;

            var attackTick = pending.timeline.advance();
            if (pending.timeline.isFinished()) {
                blade.finishAttack();
                pendingAttack = null;
                return;
            }

            blade.setAttackTick(attackTick);
            if (attackTick <= MagneticWeaponBladeMotion.IMPACT_TICK
                    && !matchesBoundWeapon(pending)) {
                pending.timeline.cancel();
            }
            if (attackTick == MagneticWeaponBladeMotion.PREP_END_TICK + 1) {
                playArcSound(level, blade.position(), 0.35f, 1.3f);
            }
            if (attackTick == MagneticWeaponBladeMotion.IMPACT_TICK) {
                resolveImpact(level, system, pending);
            }
        }

        private boolean matchesBoundWeapon(PendingAttack pending) {
            var current = player.getInventory().getItem(pending.weaponSlot);
            return isSword(current)
                    && ItemStack.isSameItemSameComponents(current, pending.weaponSnapshot);
        }

        private void resolveImpact(ServerLevel level, AbilitySystemServer system, PendingAttack pending) {
            if (!matchesBoundWeapon(pending)) pending.timeline.cancel();
            if (!pending.timeline.claimImpact()) return;
            var entity = level.getEntity(pending.targetId);
            if (!(entity instanceof LivingEntity target)
                    || target.level() != level
                    || !isEnemy(player, target)
                    || !player.hasLineOfSight(target)) {
                return;
            }

            var stack = player.getInventory().getItem(pending.weaponSlot);
            var damage = Server.calculateDamage(
                    Server.calculateWeaponAttackDamage(player, stack),
                    system.getPlayerDamageMultiplier(player.getUUID())
            );
            if (target.hurtServer(level,
                    SkillDamageSource.of(player, Skills.MAGNETIC_WEAPON.get()), damage)) {
                stack.hurtEnemy(target, player);
                stack.postHurtEnemy(target, player);
            }
            playArcSound(level, target.getBoundingBox().getCenter(), 0.55f, 0.95f);
        }

        private void syncData() {
            var hideMainHand = weaponSlot >= 0
                    && player.getInventory().getSelectedSlot() == weaponSlot
                    && isSword(player.getInventory().getItem(weaponSlot));
            var data = new Data(true, weaponSlot, hideMainHand);
            if (data.equals(player.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get()))) return;
            player.setData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get(), data);
            player.syncData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get());
        }

        private void end(boolean disableSkill) {
            if (ended) return;
            ended = true;
            Server.CONTEXT_MAP.remove(player, this);
            if (disableSkill && Skills.MAGNETIC_WEAPON.get().isEnabled(player)) {
                Skills.MAGNETIC_WEAPON.get().toggle(player);
            }
            blade.discard();
            Server.clearData(player);
            unregister();
        }

        private static final class PendingAttack {
            private final int targetId;
            private final int weaponSlot;
            private final ItemStack weaponSnapshot;
            private final MagneticWeaponAttackTimeline timeline = new MagneticWeaponAttackTimeline();

            private PendingAttack(int targetId, int weaponSlot, ItemStack weaponSnapshot) {
                this.targetId = targetId;
                this.weaponSlot = weaponSlot;
                this.weaponSnapshot = weaponSnapshot;
            }
        }
    }

    public record Data(boolean active, int weaponSlot, boolean hideMainHand) {
        public static final Data DEFAULT = new Data(false, -1, false);
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Data::active,
                ByteBufCodecs.INT, Data::weaponSlot,
                ByteBufCodecs.BOOL, Data::hideMainHand,
                Data::new
        );
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.MAGNETIC_WEAPON_TOGGLE.get();
        }
    }
}
