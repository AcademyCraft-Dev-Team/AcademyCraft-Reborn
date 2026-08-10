package org.academy.internal.common.ability.electromaster.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.ElectromasterWeaponEffectRenderer;
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
                .iterationTicks(20)
                .dependsOn(Skills.MAGNET_MANIPULATION));
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
        ToggleStatusHud.registerStateProvider(Skills.MAGNETIC_WEAPON.get(), Client::isActive);
        RendererManager.registerEffectRenderer(ElectromasterWeaponEffectRenderer.INSTANCE);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static boolean isSupportedWeapon(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SPEARS)
                || stack.is(ItemTags.TRIDENT_ENCHANTABLE)
                || stack.is(ItemTags.MACE_ENCHANTABLE));
    }

    static float maceFallDistance(double attackDistance) {
        return Double.isFinite(attackDistance) ? (float) Math.max(0.0, attackDistance) : 0.0f;
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
            var player = net.minecraft.client.Minecraft.getInstance().player;
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
            var weaponSlot = findFirstHotbarWeaponSlot(player);
            if (weaponSlot < 0) return;

            IronSandArsenal.Server.forceDisable(player);
            var skill = Skills.MAGNETIC_WEAPON.get();
            if (!skill.isEnabled(player)) skill.toggle(player);
            if (!skill.isEnabled(player)) return;
            var context = new Context(player, weaponSlot);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        private static int findFirstHotbarWeaponSlot(ServerPlayer player) {
            for (var slot = 0; slot < 9; slot++) {
                if (isSupportedWeapon(player.getInventory().getItem(slot))) return slot;
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
        private ItemStack magnetizedWeapon = ItemStack.EMPTY;
        private boolean temporaryMagnetized;
        private boolean ended;

        private Context(ServerPlayer player, int weaponSlot) {
            super(player);
            this.weaponSlot = weaponSlot;
            bindWeapon(weaponSlot);
            blade = new MagneticWeaponBlade(EntityTypes.MAGNETIC_WEAPON_BLADE.get(), player.level());
            blade.configure(player, player.getInventory().getItem(weaponSlot));
            player.level().addFreshEntity(blade);
            syncData();
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
                    player.getUUID(), skill.getMaintenanceCost(player), skill)) {
                end(true);
                return;
            }

            if (attackCooldown > 0) attackCooldown--;
            if (pendingAttack != null && player.level() instanceof ServerLevel level) {
                advanceAttack(level, system);
            }

            if (pendingAttack == null) {
                var nextWeaponSlot = Server.findFirstHotbarWeaponSlot(player);
                if (nextWeaponSlot < 0) {
                    end(true);
                    return;
                }
                if (nextWeaponSlot != weaponSlot
                        || player.getInventory().getItem(nextWeaponSlot) != magnetizedWeapon) {
                    bindWeapon(nextWeaponSlot);
                }
                blade.setWeapon(player.getInventory().getItem(weaponSlot));
            }

            var stack = player.getInventory().getItem(weaponSlot);

            if (pendingAttack == null && attackCooldown <= 0
                    && player.level() instanceof ServerLevel level) {
                var milestone = skill.getEffectiveProficiencyMilestone(player);
                var radius = milestone >= 2 ? 20.0f : RADIUS;
                if (milestone >= 3 && player.tickCount % 20 == 0 && interceptProjectile(level, stack)) {
                    syncData();
                    return;
                }
                var target = level.getEntitiesOfClass(
                                LivingEntity.class,
                                player.getBoundingBox().inflate(radius),
                                entity -> isEnemy(player, entity) && player.hasLineOfSight(entity)
                        ).stream()
                        .min(Comparator.comparingDouble(player::distanceToSqr))
                        .orElse(null);
                if (target != null) startAttack(target, stack);
            }
            syncData();
        }

        private static boolean isEnemy(ServerPlayer player, LivingEntity entity) {
            if (entity == player || !entity.isAlive() || player.isAlliedTo(entity)) return false;
            if (entity instanceof net.minecraft.server.level.ServerPlayer target
                    && (target.isCreative() || target.isSpectator())) return false;
            return player.getLastHurtByMob() == entity
                    || player.getLastHurtByPlayer() == entity
                    || entity instanceof Mob mob && mob.getTarget() == player;
        }

        private void startAttack(LivingEntity target, ItemStack stack) {
            attackSequence++;
            pendingAttack = new PendingAttack(
                    target.getId(),
                    weaponSlot,
                    stack.copyWithCount(1)
            );
            attackCooldown = Skills.MAGNETIC_WEAPON.get().hasProficiencyMilestone(player, 2) ? 8 : ATTACK_COOLDOWN;
            blade.startAttack(target.getId(), attackSequence);
        }

        private boolean interceptProjectile(ServerLevel level, ItemStack stack) {
            var look = player.getLookAngle();
            var projectile = level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(20.0),
                            shot -> shot.isAlive() && shot.getOwner() != player
                                    && shot.position().subtract(player.position()).dot(look) > 0.0
                                    && player.position().subtract(shot.position()).dot(shot.getDeltaMovement()) > 0.0)
                    .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
            if (projectile == null) return false;
            var source = projectile.getOwner();
            projectile.discard();
            if (source instanceof LivingEntity living && isEnemy(player, living)) startAttack(living, stack);
            return true;
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
            return isSupportedWeapon(current)
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

            var weapon = player.getInventory().getItem(pending.weaponSlot);
            var syntheticFallDistance = weapon.is(ItemTags.MACE_ENCHANTABLE)
                    ? maceFallDistance(player.distanceTo(target)) : -1.0f;
            try (var playerState = MagneticWeaponPlayerState.open(
                    player, pending.weaponSlot, syntheticFallDistance);
                 var attackContext = MagneticWeaponAttackContext.open(
                         player,
                         target,
                         system.getPlayerDamageMultiplier(player.getUUID())
                 )) {
                player.attack(target);
                var weaponAfterAttack = player.getInventory().getItem(pending.weaponSlot);
                if (!weaponAfterAttack.isEmpty()) {
                    weaponAfterAttack.onEntitySwing(player, InteractionHand.MAIN_HAND);
                }
            }
            playArcSound(level, target.getBoundingBox().getCenter(), 0.55f, 0.95f);
        }

        private static void playArcSound(ServerLevel level, net.minecraft.world.phys.Vec3 position,
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

        private void syncData() {
            var hideMainHand = weaponSlot >= 0
                    && player.getInventory().getSelectedSlot() == weaponSlot
                    && isSupportedWeapon(player.getInventory().getItem(weaponSlot));
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
            clearTemporaryMagnetization();
            blade.discard();
            Server.clearData(player);
            unregister();
        }

        private void bindWeapon(int slot) {
            clearTemporaryMagnetization();
            weaponSlot = slot;
            magnetizedWeapon = player.getInventory().getItem(slot);
            temporaryMagnetized = MagneticWeaponEnchantments.addTemporary(
                    player.registryAccess(), magnetizedWeapon);
            player.getInventory().setChanged();
        }

        private void clearTemporaryMagnetization() {
            if (temporaryMagnetized && !magnetizedWeapon.isEmpty()) {
                MagneticWeaponEnchantments.removeTemporary(player.registryAccess(), magnetizedWeapon);
                player.getInventory().setChanged();
            }
            magnetizedWeapon = ItemStack.EMPTY;
            temporaryMagnetized = false;
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
