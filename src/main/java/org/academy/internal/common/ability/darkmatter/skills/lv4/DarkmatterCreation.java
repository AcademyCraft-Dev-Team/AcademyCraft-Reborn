package org.academy.internal.common.ability.darkmatter.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.gui.screen.DarkmatterCreationScreen;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint;
import org.academy.internal.common.ability.darkmatter.skills.lv2.DarkmatterCut;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.DarkmatterCreationData;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Blueprint-driven dark-matter creature creation and authoritative remote roster.
 */
public final class DarkmatterCreation extends Skill {
    public static final int MAX_CREATURES = 16;
    /**
     * Compatibility name retained for integrations that used the old fixed-beetle limit.
     */
    public static final int MAX_BEETLES = MAX_CREATURES;
    public static final float MIN_INVESTMENT = 5.0f;
    public static final float MATTER_COST_PER_BEETLE = MIN_INVESTMENT;
    /**
     * Every living construct occupies a fixed part of the creator's CP ceiling.
     */
    public static final float RESERVED_CP_PER_BEETLE = 40.0f;
    private static final double MAX_PROGRAM_RANGE = 32.0;

    public static double followSpeed(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 2 ? 1.20 : 1.0;
    }

    public static double targetingRange(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 2 ? 1.20 : 1.0;
    }

    public static float moduleValueMultiplier(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 3 ? 1.25f : 1.0f;
    }

    public static int stuckTeleportTicks(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 2 ? 60 : 100;
    }

    public static int gammaRepeatTicks(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 3 ? 80 : 100;
    }

    /**
     * Old swarm bonus is deliberately gone; each blueprint owns its own module effects.
     */
    public static float swarmDamageMultiplier(int milestone, int attackers) {
        return 1.0f;
    }

    public static boolean unlocksSwarmCommand(int sixWingsMilestone) {
        return false;
    }

    public DarkmatterCreation() {
        super(Builder.of(AbilityCategories.DARKMATTER.get())
                .damage()
                .level(AbilityLevel.LEVEL4)
                .energyCost(0).cpCost(0).iterationTicks(15).maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.DARKMATTER_REPAIR).dependsOn(Skills.DARKMATTER_CUT)
                .withCustomData(DarkmatterCreationData.ID, DarkmatterCreationData.class,
                        DarkmatterCreationData::new)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Repair", "academy:darkmatter_repair"))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Cut", "academy:darkmatter_cut")));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_EDITOR, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_EDITOR,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, GLFW.GLFW_KEY_EQUAL,
                        InputConstants.RELEASE, 0)), _ -> Client.openEditor());
        var defaultCastBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                InputConstants.RELEASE, 0
        );
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBindingMigratingDefaults(
                Client.KEY_NAME_CAST,
                defaultCastBinding,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputConstants.RELEASE, 0),
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_K,
                        InputConstants.RELEASE, 0)), _ -> Client.cast());
        MisakaNetworkClient.NETWORK_MANAGER.register(ClientPackets.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(), new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_CREATION.get(),
                        List.of(DarkmatterRepair.Client.SKILL_INFO, DarkmatterCut.Client.SKILL_INFO),
                        R.textures.darkmatter_creation_icon, 205, 72));
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_CREATION + "_cast";
        public static final String KEY_NAME_EDITOR = SkillNames.DARKMATTER_CREATION + "_editor";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(
                    Skills.DARKMATTER_CREATION.get())) return;
            MisakaNetworkClient.send(CastPacket.INSTANCE);
        }

        private static void openEditor() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(
                    Skills.DARKMATTER_CREATION.get())) return;
            Minecraft.getInstance().gui.setScreen(new DarkmatterCreationScreen());
            MisakaNetworkClient.send(EditorRequestPacket.INSTANCE);
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

    public static final class ClientPackets {
        private ClientPackets() {
        }

        @SubscribePacket
        public static void handle(EditorSnapshotPacket packet) {
            DarkmatterCreationScreen.acceptSnapshot(packet);
        }

        @SubscribePacket
        public static void handle(RosterDeltaPacket packet) {
            DarkmatterCreationScreen.acceptRosterDelta(packet);
        }

        @SubscribePacket
        public static void handle(SummonResultPacket packet) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof DarkmatterCreationScreen) {
                DarkmatterCreationScreen.acceptSummonResult(packet);
            } else if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable(
                        packet.result.translationKey()));
            }
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            summonSelected(packet.getPacketListener().getPlayer());
        }

        @SubscribePacket
        public static void handle(EditorRequestPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (isUsable(player)) sendSnapshot(player);
        }

        @SubscribePacket
        public static void handle(SaveBlueprintPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!isUsable(player)) return;
            var level = abilityLevel(player);
            if (!packet.blueprint.validate(level).isEmpty()) {
                sendSnapshot(player);
                return;
            }
            updateData(player, data -> data.setBlueprint(packet.slot, packet.blueprint, level));
            sendSnapshot(player);
        }

        @SubscribePacket
        public static void handle(SummonPacket packet) {
            var player = player(packet);
            var result = prepareAndSummon(player, packet.slot, packet.blueprint);
            if (player != null) {
                sendSnapshot(player);
                MisakaNetworkServer.send(player, new SummonResultPacket(result));
            }
        }

        @SubscribePacket
        public static void handle(DismantlePacket packet) {
            var player = player(packet);
            if (!isUsable(player)) return;
            if (packet.all) discardAll(player);
            else if (packet.uuid != null) discardOne(player, packet.uuid);
            sendSnapshot(player);
        }

        private static ServerPlayer player(Packet<ServerGamePacketListenerImpl, ?> packet) {
            return packet.getPacketListener().getPlayer();
        }

        private static boolean isUsable(ServerPlayer player) {
            return player != null && player.isAlive()
                    && Skills.DARKMATTER_CREATION.get().isEnabled(player);
        }

        private static int abilityLevel(ServerPlayer player) {
            return AbilitySystemServer.getSystem(player).getDarkmatterResourceManager()
                    .getPhaseSnapshot(player).abilityLevel();
        }

        private static void summonSelected(ServerPlayer player) {
            if (player == null) return;
            var slot = data(player).map(DarkmatterCreationData::getSelectedSlot).orElse(0);
            var result = prepareAndSummon(player, slot, null);
            sendSnapshot(player);
            MisakaNetworkServer.send(player, new SummonResultPacket(result));
        }

        private static SummonResult prepareAndSummon(ServerPlayer player, int slot,
                                                     DarkmatterCreatureBlueprint submitted) {
            if (!isUsable(player) || !(player.level() instanceof ServerLevel level)) {
                return SummonResult.UNAVAILABLE;
            }
            var abilityLevel = abilityLevel(player);
            var normalizedSlot = Math.clamp(slot, 0, DarkmatterCreationData.BLUEPRINT_SLOTS - 1);
            if (submitted != null) {
                if (!submitted.validate(abilityLevel).isEmpty()) {
                    return SummonResult.INVALID_BLUEPRINT;
                }
                updateData(player, data -> data.setBlueprint(
                        normalizedSlot, submitted, abilityLevel));
            }
            return summon(player, level, normalizedSlot, abilityLevel);
        }

        private static SummonResult summon(ServerPlayer player, ServerLevel level,
                                           int normalizedSlot, int abilityLevel) {
            var data = data(player).orElse(null);
            if (data == null) return SummonResult.UNAVAILABLE;
            if (data.getSummons().size() >= MAX_CREATURES) return SummonResult.LIMIT_REACHED;
            var blueprint = data.getBlueprint(normalizedSlot, abilityLevel);
            if (!blueprint.validate(abilityLevel).isEmpty()) return SummonResult.INVALID_BLUEPRINT;
            var system = AbilitySystemServer.getSystem(player);
            var resource = system.getDarkmatterResourceManager();
            var investment = blueprint.investment();
            var view = resource.getView(player);
            if (data.getSummons().size() >= MAX_CREATURES
                    || view.totalMatter() + 1.0e-4f < investment) {
                return SummonResult.INSUFFICIENT_MP;
            }

            var creature = createCreature(level, player, player.position()
                    .add(player.getLookAngle().scale(1.5)), blueprint, normalizedSlot);
            if (creature == null) return SummonResult.SPAWN_FAILED;
            if (!placeCreatureSafely(level, player, creature)) return SummonResult.NO_SPAWN_SPACE;
            var desiredCount = data.getSummons().size() + 1;
            if (!replaceCreationOccupation(player, desiredCount)) {
                creature.discard();
                return SummonResult.INSUFFICIENT_CP;
            }
            if (!level.addFreshEntity(creature)) {
                replaceCreationOccupation(player, desiredCount - 1);
                creature.discard();
                return SummonResult.SPAWN_FAILED;
            }
            if (!resource.consume(player, investment, Skills.DARKMATTER_CREATION.get(),
                    Skills.DARKMATTER_CREATION.get().getIterationTicks(player))) {
                replaceCreationOccupation(player, desiredCount - 1);
                creature.discard();
                return SummonResult.INSUFFICIENT_MP;
            }
            updateData(player, mutable -> mutable.addSummon(
                    creature.getUUID(), blueprint.name(), investment, normalizedSlot,
                    level.dimension().identifier().toString(), creature.getX(), creature.getY(), creature.getZ()));
            Skills.DARKMATTER_CREATION.get().reportActivity(player, true);
            return SummonResult.SUMMONED;
        }

        private static boolean placeCreatureSafely(ServerLevel level, ServerPlayer player,
                                                   DarkmatterBeetle creature) {
            var look = player.getLookAngle();
            var horizontal = new Vec3(look.x, 0.0, look.z);
            if (horizontal.lengthSqr() < 1.0e-6) {
                horizontal = Vec3.directionFromRotation(0.0f, player.getYRot());
            }
            horizontal = horizontal.normalize();
            var side = new Vec3(-horizontal.z, 0.0, horizontal.x);
            var origin = player.position();
            var offsets = List.of(
                    horizontal.scale(1.5), horizontal.scale(2.5),
                    side.scale(1.5), side.scale(-1.5), horizontal.scale(-1.5));
            for (var vertical : new double[]{0.0, 1.0, -1.0}) {
                for (var offset : offsets) {
                    var candidate = origin.add(offset).add(0.0, vertical, 0.0);
                    var block = BlockPos.containing(candidate);
                    if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block)
                            || block.getY() < level.getMinY() || block.getY() >= level.getMaxY()) {
                        continue;
                    }
                    creature.snapTo(candidate.x, candidate.y, candidate.z, player.getYRot(), 0.0f);
                    if (level.noCollision(creature)) return true;
                }
            }
            return false;
        }

        private static DarkmatterBeetle createCreature(ServerLevel level, ServerPlayer player,
                                                       Vec3 position,
                                                       DarkmatterCreatureBlueprint blueprint,
                                                       int slot) {
            var creature = EntityTypes.DARKMATTER_BEETLE.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (creature == null) return null;
            creature.setOwnerUUID(player.getUUID());
            creature.applyBlueprint(blueprint, slot, abilityLevel(player),
                    Skills.DARKMATTER_CREATION.get().getEffectiveProficiencyMilestone(player),
                    DarkmatterSixWings.Server.isActive(player));
            creature.snapTo(position.x, position.y, position.z, player.getYRot(), 0);
            return creature;
        }

        public static List<UUID> owned(ServerPlayer player) {
            return data(player).map(DarkmatterCreationData::getOwnedBeetles).orElseGet(List::of);
        }

        public static boolean isRecorded(ServerPlayer player, UUID uuid) {
            return data(player).flatMap(value -> value.getSummon(uuid)).isPresent();
        }

        public static boolean canProgramCreate(ServerPlayer player, Vec3 position, double maximumRange) {
            if (!isUsable(player) || !validProgramPosition(player, position, maximumRange)
                    || data(player).map(value -> value.getSummons().size() >= MAX_CREATURES).orElse(true)
                    || !(player.level() instanceof ServerLevel level)) return false;
            var blueprint = data(player).orElseThrow().getBlueprint(
                    data(player).orElseThrow().getSelectedSlot(), abilityLevel(player));
            var creature = createCreature(level, player, position, blueprint,
                    data(player).orElseThrow().getSelectedSlot());
            return creature != null && level.noCollision(creature)
                    && AbilitySystemServer.getSystem(player).getDarkmatterResourceManager()
                    .getView(player).totalMatter() + 1.0e-4f >= blueprint.investment()
                    && AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID())
                    + 1.0e-4f >= RESERVED_CP_PER_BEETLE;
        }

        public static Optional<UUID> tryProgramCreate(ServerPlayer player, Vec3 position,
                                                      double maximumRange, float ignoredLegacyCost) {
            if (!canProgramCreate(player, position, maximumRange)
                    || !(player.level() instanceof ServerLevel level)) return Optional.empty();
            var creationData = data(player).orElseThrow();
            var slot = creationData.getSelectedSlot();
            var blueprint = creationData.getBlueprint(slot, abilityLevel(player));
            var creature = createCreature(level, player, position, blueprint, slot);
            if (creature == null || !level.noCollision(creature) || !level.addFreshEntity(creature)) {
                return Optional.empty();
            }
            var resource = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
            var desiredCount = creationData.getSummons().size() + 1;
            if (!replaceCreationOccupation(player, desiredCount)) {
                creature.discard();
                return Optional.empty();
            }
            if (!resource.consume(player, blueprint.investment(), Skills.DARKMATTER_CREATION.get(),
                    Skills.DARKMATTER_CREATION.get().getIterationTicks(player))) {
                replaceCreationOccupation(player, desiredCount - 1);
                creature.discard();
                return Optional.empty();
            }
            updateData(player, mutable -> mutable.addSummon(creature.getUUID(), blueprint.name(),
                    blueprint.investment(), slot, level.dimension().identifier().toString(),
                    creature.getX(), creature.getY(), creature.getZ()));
            return Optional.of(creature.getUUID());
        }

        public static void discardProgramCreated(ServerPlayer player, UUID uuid) {
            if (player != null && uuid != null) discardOne(player, uuid);
        }

        public static void removeOwned(ServerPlayer player, UUID uuid) {
            if (player == null || uuid == null) return;
            var removed = new boolean[1];
            updateData(player, data -> removed[0] = data.removeSummon(uuid) > 0.0f);
            if (removed[0]) replaceCreationOccupation(
                    player, data(player).map(value -> value.getSummons().size()).orElse(0));
        }

        private static void discardOne(ServerPlayer player, UUID uuid) {
            var server = player.level().getServer();
            if (server != null) for (var level : server.getAllLevels()) {
                if (level.getEntity(uuid) instanceof DarkmatterBeetle creature
                        && creature.isOwnedBy(player)) {
                    creature.discard();
                    return;
                }
            }
            // Unloaded: release the authoritative record now. The entity self-deletes when loaded.
            removeOwned(player, uuid);
        }

        private static void discardAll(ServerPlayer player) {
            for (var record : data(player).map(DarkmatterCreationData::getSummons).orElseGet(List::of)) {
                record.uuid().ifPresent(uuid -> discardOne(player, uuid));
            }
            var legacy = new ArrayList<UUID>();
            updateData(player, mutable -> legacy.addAll(mutable.consumeLegacyOwned()));
            var server = player.level().getServer();
            if (server != null) for (var uuid : legacy)
                for (var level : server.getAllLevels()) {
                    if (level.getEntity(uuid) instanceof DarkmatterBeetle creature) creature.discard();
                }
            replaceCreationOccupation(player, 0);
        }

        private static void migrateLegacy(ServerPlayer player) {
            if (data(player).map(DarkmatterCreationData::needsLegacyMigration).orElse(false)) {
                AcademyCraft.LOGGER.info("Migrating legacy darkmatter beetles for {} by safe dismantle",
                        player.getGameProfile().name());
                discardAll(player);
            }
        }

        private static void tick(ServerPlayer player) {
            if (player.tickCount % 20 != 0) return;
            migrateLegacy(player);
            migrateReservedMatter(player);
            var records = data(player).map(DarkmatterCreationData::getSummons).orElseGet(List::of);
            replaceCreationOccupation(player, records.size());
            if (records.isEmpty()) return;
            if (!isUsable(player) || player.hasDisconnected()) {
                discardAll(player);
                return;
            }
            var server = player.level().getServer();
            if (server == null) return;
            for (var record : records)
                record.uuid().ifPresent(uuid -> {
                    DarkmatterBeetle loaded = null;
                    for (var level : server.getAllLevels()) {
                        if (level.getEntity(uuid) instanceof DarkmatterBeetle found) {
                            loaded = found;
                            break;
                        }
                    }
                    if (loaded != null) {
                        var creature = loaded;
                        updateData(player, mutable -> mutable.updateSummon(uuid,
                                creature.level().dimension().identifier().toString(),
                                creature.getX(), creature.getY(), creature.getZ(),
                                creature.getHealth(), creature.getMaxHealth(), true));
                    } else updateData(player, mutable -> mutable.updateSummon(uuid,
                            record.dimension(), record.x(), record.y(), record.z(),
                            record.health(), record.maxHealth(), false));
                });
            if (MinecraftServerTick.shouldSyncRoster(player.tickCount)) {
                var baseRevision = data(player).map(DarkmatterCreationData::getRevision).orElse(0L);
                updateData(player, DarkmatterCreationData::bumpRevision);
                sendRosterDelta(player, baseRevision);
            }
        }

        private static void sendSnapshot(ServerPlayer player) {
            var creationData = data(player).orElse(null);
            if (creationData == null) return;
            MisakaNetworkServer.send(player, new EditorSnapshotPacket(creationData.getRevision(),
                    abilityLevel(player), creationData.getSelectedSlot(),
                    creationData.getBlueprints(abilityLevel(player)), roster(player, creationData)));
        }

        private static void sendRosterDelta(ServerPlayer player, long baseRevision) {
            var creationData = data(player).orElse(null);
            if (creationData == null) return;
            MisakaNetworkServer.send(player, new RosterDeltaPacket(baseRevision,
                    creationData.getRevision(), roster(player, creationData)));
        }

        private static List<RosterEntry> roster(ServerPlayer player,
                                                DarkmatterCreationData creationData) {
            var entries = new ArrayList<RosterEntry>();
            for (var record : creationData.getSummons())
                record.uuid().ifPresent(uuid -> {
                    var sameDimension = record.dimension().equals(player.level().dimension().identifier().toString());
                    var distance = sameDimension ? (float) Math.sqrt(player.distanceToSqr(
                            record.x(), record.y(), record.z())) : -1.0f;
                    entries.add(new RosterEntry(uuid, record.name(), record.health(), record.maxHealth(),
                            record.dimension(), distance, record.investment(), record.slot(), record.loaded()));
                });
            return entries;
        }

        private static Optional<DarkmatterCreationData> data(ServerPlayer player) {
            return Skills.DARKMATTER_CREATION.get().getRuntimeData(player);
        }

        private static void updateData(ServerPlayer player,
                                       Consumer<DarkmatterCreationData> updater) {
            AbilitySystemServer.getSystem(player).updatePlayerSkillData(player.getUUID(),
                    Skills.DARKMATTER_CREATION.get(), DarkmatterCreationData.class, updater);
        }

        private static boolean replaceCreationOccupation(ServerPlayer player, int creatureCount) {
            return AbilitySystemServer.getSystem(player).replacePermanentOccupation(
                    player.getUUID(),
                    Math.clamp(creatureCount, 0, MAX_CREATURES) * RESERVED_CP_PER_BEETLE,
                    Skills.DARKMATTER_CREATION.get());
        }

        private static void migrateReservedMatter(ServerPlayer player) {
            var resource = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
            var reserved = resource.getView(player).reservedMatter();
            if (reserved <= 1.0e-4f) return;
            resource.releaseReservation(player, reserved);
            AcademyCraft.LOGGER.info(
                    "Released {} legacy reserved darkmatter MP for {} while migrating creation to fixed CP occupation",
                    reserved, player.getGameProfile().name());
        }

        private static boolean validProgramPosition(ServerPlayer player, Vec3 position,
                                                    double maximumRange) {
            if (!(player.level() instanceof ServerLevel level) || position == null
                    || !Double.isFinite(position.x) || !Double.isFinite(position.y)
                    || !Double.isFinite(position.z) || !Double.isFinite(maximumRange)
                    || maximumRange <= 0.0 || maximumRange > MAX_PROGRAM_RANGE
                    || player.getEyePosition().distanceToSqr(position) > maximumRange * maximumRange) {
                return false;
            }
            var block = BlockPos.containing(position);
            return level.hasChunkAt(block) && level.getWorldBorder().isWithinBounds(block)
                    && block.getY() >= level.getMinY() && block.getY() < level.getMaxY();
        }
    }

    private static final class MinecraftServerTick {
        private static boolean shouldSyncRoster(int tick) {
            return tick % 40 == 0;
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.discardAll(player);
        }

        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.discardAll(player);
        }
    }

    public record RosterEntry(UUID uuid, String name, float health, float maxHealth,
                              String dimension, float distance, int investment,
                              int slot, boolean loaded) {
    }

    private static void encodeBlueprint(ByteBuf buf, DarkmatterCreatureBlueprint value) {
        ByteBufCodecs.STRING_UTF8.encode(buf, value.name());
        ByteBufCodecs.VAR_INT.encode(buf, value.investment());
        ByteBufCodecs.STRING_UTF8.encode(buf, value.head());
        ByteBufCodecs.STRING_UTF8.encode(buf, value.torso());
        ByteBufCodecs.STRING_UTF8.encode(buf, value.limbs());
        ByteBufCodecs.STRING_UTF8.encode(buf, value.additional());
        ByteBufCodecs.VAR_INT.encode(buf, value.headAlpha());
        ByteBufCodecs.VAR_INT.encode(buf, value.torsoAlpha());
        ByteBufCodecs.VAR_INT.encode(buf, value.limbsAlpha());
        ByteBufCodecs.VAR_INT.encode(buf, value.additionalAlpha());
        ByteBufCodecs.VAR_INT.encode(buf, value.modules().size());
        value.modules().forEach(module -> ByteBufCodecs.STRING_UTF8.encode(buf, module));
    }

    private static DarkmatterCreatureBlueprint decodeBlueprint(ByteBuf buf) {
        var name = ByteBufCodecs.STRING_UTF8.decode(buf);
        var investment = ByteBufCodecs.VAR_INT.decode(buf);
        var head = ByteBufCodecs.STRING_UTF8.decode(buf);
        var torso = ByteBufCodecs.STRING_UTF8.decode(buf);
        var limbs = ByteBufCodecs.STRING_UTF8.decode(buf);
        var additional = ByteBufCodecs.STRING_UTF8.decode(buf);
        var headAlpha = ByteBufCodecs.VAR_INT.decode(buf);
        var torsoAlpha = ByteBufCodecs.VAR_INT.decode(buf);
        var limbsAlpha = ByteBufCodecs.VAR_INT.decode(buf);
        var additionalAlpha = ByteBufCodecs.VAR_INT.decode(buf);
        var count = decodeBoundedCount(buf, 32, "darkmatter creature modules");
        var modules = new ArrayList<String>(count);
        for (var i = 0; i < count; i++) modules.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        return new DarkmatterCreatureBlueprint(name, investment, head, torso, limbs, additional,
                headAlpha, torsoAlpha, limbsAlpha, additionalAlpha, modules);
    }

    private static void encodeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID decodeUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    private static int decodeBoundedCount(ByteBuf buf, int maximum, String field) {
        var count = ByteBufCodecs.VAR_INT.decode(buf);
        if (count < 0 || count > maximum) {
            throw new DecoderException(field + " count " + count + " is outside 0.." + maximum);
        }
        return count;
    }

    public enum SummonResult {
        SUMMONED("screen.academy.darkmatter_creation.result.summoned"),
        UNAVAILABLE("screen.academy.darkmatter_creation.result.unavailable"),
        INVALID_BLUEPRINT("screen.academy.darkmatter_creation.result.invalid_blueprint"),
        LIMIT_REACHED("screen.academy.darkmatter_creation.result.limit_reached"),
        INSUFFICIENT_MP("screen.academy.darkmatter_creation.result.insufficient_mp"),
        INSUFFICIENT_CP("screen.academy.darkmatter_creation.result.insufficient_cp"),
        NO_SPAWN_SPACE("screen.academy.darkmatter_creation.result.no_spawn_space"),
        SPAWN_FAILED("screen.academy.darkmatter_creation.result.spawn_failed");

        private final String translationKey;

        SummonResult(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private static void encodeRosterEntry(ByteBuf buf, RosterEntry entry) {
        encodeUuid(buf, entry.uuid());
        ByteBufCodecs.STRING_UTF8.encode(buf, entry.name());
        buf.writeFloat(entry.health());
        buf.writeFloat(entry.maxHealth());
        ByteBufCodecs.STRING_UTF8.encode(buf, entry.dimension());
        buf.writeFloat(entry.distance());
        ByteBufCodecs.VAR_INT.encode(buf, entry.investment());
        ByteBufCodecs.VAR_INT.encode(buf, entry.slot());
        buf.writeBoolean(entry.loaded());
    }

    private static RosterEntry decodeRosterEntry(ByteBuf buf) {
        return new RosterEntry(decodeUuid(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readFloat(), buf.readFloat(), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean());
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_CAST.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class EditorRequestPacket extends Packet<ServerGamePacketListenerImpl, EditorRequestPacket> {
        public static final EditorRequestPacket INSTANCE = new EditorRequestPacket();
        public static final StreamCodec<ByteBuf, EditorRequestPacket> CODEC = StreamCodec.unit(INSTANCE);

        private EditorRequestPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, EditorRequestPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_EDITOR_REQUEST.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SaveBlueprintPacket extends Packet<ServerGamePacketListenerImpl, SaveBlueprintPacket> {
        public static final StreamCodec<ByteBuf, SaveBlueprintPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.slot);
                    encodeBlueprint(buf, packet.blueprint);
                },
                buf -> new SaveBlueprintPacket(ByteBufCodecs.VAR_INT.decode(buf), decodeBlueprint(buf)));
        public final int slot;
        public final DarkmatterCreatureBlueprint blueprint;

        public SaveBlueprintPacket(int slot, DarkmatterCreatureBlueprint blueprint) {
            this.slot = Math.clamp(slot, 0, 3);
            this.blueprint = blueprint.copy();
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SaveBlueprintPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_SAVE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SummonPacket extends Packet<ServerGamePacketListenerImpl, SummonPacket> {
        public static final StreamCodec<ByteBuf, SummonPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.slot);
                    buf.writeBoolean(packet.blueprint != null);
                    if (packet.blueprint != null) encodeBlueprint(buf, packet.blueprint);
                },
                buf -> new SummonPacket(ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readBoolean() ? decodeBlueprint(buf) : null));
        public final int slot;
        public final DarkmatterCreatureBlueprint blueprint;

        public SummonPacket(int slot) {
            this(slot, null);
        }

        public SummonPacket(int slot, DarkmatterCreatureBlueprint blueprint) {
            this.slot = Math.clamp(slot, 0, 3);
            this.blueprint = blueprint == null ? null : blueprint.copy();
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SummonPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_SUMMON.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class DismantlePacket extends Packet<ServerGamePacketListenerImpl, DismantlePacket> {
        public static final StreamCodec<ByteBuf, DismantlePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeBoolean(packet.all);
                    buf.writeBoolean(packet.uuid != null);
                    if (packet.uuid != null) encodeUuid(buf, packet.uuid);
                },
                buf -> new DismantlePacket(buf.readBoolean(), buf.readBoolean() ? decodeUuid(buf) : null));
        public final boolean all;
        public final UUID uuid;

        public DismantlePacket(boolean all, UUID uuid) {
            this.all = all;
            this.uuid = uuid;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, DismantlePacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_DISMANTLE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class EditorSnapshotPacket extends Packet<ClientPacketListener, EditorSnapshotPacket> {
        public static final StreamCodec<ByteBuf, EditorSnapshotPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.revision);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.abilityLevel);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.selectedSlot);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blueprints.size());
                    packet.blueprints.forEach(value -> encodeBlueprint(buf, value));
                    ByteBufCodecs.VAR_INT.encode(buf, packet.roster.size());
                    packet.roster.forEach(entry -> encodeRosterEntry(buf, entry));
                },
                buf -> {
                    var revision = buf.readLong();
                    var level = ByteBufCodecs.VAR_INT.decode(buf);
                    var selected = ByteBufCodecs.VAR_INT.decode(buf);
                    var blueprintCount = decodeBoundedCount(buf, 4, "darkmatter blueprints");
                    var blueprints = new ArrayList<DarkmatterCreatureBlueprint>(blueprintCount);
                    for (var i = 0; i < blueprintCount; i++) blueprints.add(decodeBlueprint(buf));
                    var rosterCount = decodeBoundedCount(buf, MAX_CREATURES, "darkmatter summon roster");
                    var roster = new ArrayList<RosterEntry>(rosterCount);
                    for (var i = 0; i < rosterCount; i++) roster.add(decodeRosterEntry(buf));
                    return new EditorSnapshotPacket(revision, level, selected, blueprints, roster);
                });
        public final long revision;
        public final int abilityLevel;
        public final int selectedSlot;
        public final List<DarkmatterCreatureBlueprint> blueprints;
        public final List<RosterEntry> roster;

        public EditorSnapshotPacket(long revision, int abilityLevel, int selectedSlot,
                                    List<DarkmatterCreatureBlueprint> blueprints,
                                    List<RosterEntry> roster) {
            this.revision = Math.max(0, revision);
            this.abilityLevel = Math.clamp(abilityLevel, 1, 5);
            this.selectedSlot = Math.clamp(selectedSlot, 0, 3);
            this.blueprints = blueprints.stream().limit(4).map(DarkmatterCreatureBlueprint::copy).toList();
            this.roster = roster.stream().limit(MAX_CREATURES).toList();
        }

        @Override
        public PacketType<ClientPacketListener, EditorSnapshotPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_EDITOR_SNAPSHOT.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class RosterDeltaPacket extends Packet<ClientPacketListener, RosterDeltaPacket> {
        public static final StreamCodec<ByteBuf, RosterDeltaPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.baseRevision);
                    buf.writeLong(packet.revision);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.roster.size());
                    packet.roster.forEach(entry -> encodeRosterEntry(buf, entry));
                },
                buf -> {
                    var baseRevision = buf.readLong();
                    var revision = buf.readLong();
                    var count = decodeBoundedCount(buf, MAX_CREATURES,
                            "darkmatter summon roster delta");
                    var roster = new ArrayList<RosterEntry>(count);
                    for (var i = 0; i < count; i++) roster.add(decodeRosterEntry(buf));
                    return new RosterDeltaPacket(baseRevision, revision, roster);
                });
        public final long baseRevision;
        public final long revision;
        public final List<RosterEntry> roster;

        public RosterDeltaPacket(long baseRevision, long revision, List<RosterEntry> roster) {
            this.baseRevision = Math.max(0L, baseRevision);
            this.revision = Math.max(this.baseRevision, revision);
            this.roster = roster.stream().limit(MAX_CREATURES).toList();
        }

        @Override
        public PacketType<ClientPacketListener, RosterDeltaPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_ROSTER_DELTA.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SummonResultPacket extends Packet<ClientPacketListener, SummonResultPacket> {
        public static final StreamCodec<ByteBuf, SummonResultPacket> CODEC = StreamCodec.of(
                (buf, packet) -> ByteBufCodecs.VAR_INT.encode(buf, packet.result.ordinal()),
                buf -> {
                    var ordinal = ByteBufCodecs.VAR_INT.decode(buf);
                    if (ordinal < 0 || ordinal >= SummonResult.values().length) {
                        throw new DecoderException("Invalid darkmatter creation summon result: " + ordinal);
                    }
                    return new SummonResultPacket(SummonResult.values()[ordinal]);
                });
        public final SummonResult result;

        public SummonResultPacket(SummonResult result) {
            this.result = result == null ? SummonResult.SPAWN_FAILED : result;
        }

        @Override
        public PacketType<ClientPacketListener, SummonResultPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_SUMMON_RESULT.get();
        }
    }
}
