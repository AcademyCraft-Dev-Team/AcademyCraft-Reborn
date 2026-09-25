package org.academy.internal.server.ability.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.internal.common.attribute.BlockLootPlayerContext;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.attribute.PropsMath;
import org.academy.internal.common.gametest.GameTestData;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.level.block.MultiBlock;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.common.world.level.block.entity.OmniCraftingTableBlockEntity;
import org.academy.internal.server.ability.AbilityBlockDrops;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.ability.PropsManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real player events and ability loot tables, including controlled breakers without tools.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class PropsGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("props_regression_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : List.of("attributes", "experience_orbs", "activity", "damage", "ability_loot", "structures", "loaded_devices")) {
            var environment = event.registerEnvironment(AcademyCraft.academy("props/" + scenario),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(AcademyCraft.academy("props_" + scenario), new Instance(GameTestData.of(
                    environment, Identifier.withDefaultNamespace("empty"), 40, 0, true,
                    Rotation.NONE, false, 1, 1, false, 16), scenario));
        }
    }

    private static ServerPlayer createPlayer(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "props-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        var manager = AbilitySystemServer.getSystem(player).getPropsManager();
        manager.reset(player);
        manager.start(player);
        return player;
    }

    private static void runScenario(GameTestHelper helper, String scenario) {
        var player = createPlayer(helper);
        try {
            switch (scenario) {
                case "attributes" -> attributes(helper, player);
                case "experience_orbs" -> experienceOrbs(helper, player);
                case "activity" -> activity(helper, player);
                case "damage" -> damage(helper, player);
                case "ability_loot" -> abilityLoot(helper, player);
                case "structures" -> structures(helper, player);
                case "loaded_devices" -> loadedDevices(helper);
                default -> throw new IllegalArgumentException(scenario);
            }
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static void loadedDevices(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 2, 4));
        var missing = pos.offset(16_384, 0, 16_384);
        var main = MultiBlock.MultiBlockType.MAIN;
        var subject = MultiBlock.MultiBlockType.SUBJECT;
        var type = MultiBlock.TYPE;
        var developerState = org.academy.internal.common.world.level.block.Blocks.ABILITY_DEVELOPER.get()
                .defaultBlockState().setValue(type, main);
        var developer = new AbilityDeveloperBlockEntity(pos, developerState);
        developer.setLevel(level);
        developer.setConnectedNodePosition(missing);
        var tableState = org.academy.internal.common.world.level.block.Blocks.OMNI_CRAFTING_TABLE.get()
                .defaultBlockState().setValue(type, main);
        var table = new OmniCraftingTableBlockEntity(pos, tableState);
        table.setLevel(level);
        table.setConnectedNodePosition(missing);
        var part = new AbilityDeveloperBlockEntity(
                pos, developerState.setValue(type, subject));
        part.setLevel(level);
        part.mainPos = missing;
        helper.assertTrue(level.getChunkSource().getChunkNow(missing.getX() >> 4, missing.getZ() >> 4) == null,
                "Device endpoints must initially be unloaded");
        developer.setEnergyStored(100);
        table.setEnergyStored(100);
        for (var tick = 0; tick < 100; tick++) {
            developer.receiveEnergy(1, false);
            table.receiveEnergy(1, false);
            developer.serverTick(level);
            table.tick();
            helper.assertTrue(part.getMain() == null, "An unloaded master must resolve without waiting");
        }
        helper.assertTrue(developer.getEnergyStored() == 200 && table.getEnergyStored() == 200,
                "Packet coalescing must not delay or lose energy mutations");
        helper.assertTrue(missing.equals(developer.getConnectedNodePosition()),
                "A temporarily unloaded developer node must remain connected");
        helper.assertTrue(missing.equals(table.getConnectedNodePosition()),
                "A temporarily unloaded crafting-table node must remain connected");
        helper.assertTrue(level.getChunkSource().getChunkNow(missing.getX() >> 4, missing.getZ() >> 4) == null,
                "Repeated device ticks must not load the endpoint chunk");
        var tablePart = new OmniCraftingTableBlockEntity(
                pos, tableState.setValue(type, subject));
        tablePart.setLevel(level);
        tablePart.mainPos = missing;
        helper.assertTrue(tablePart.receiveEnergy(100, false) == 0 && tablePart.extractEnergy(100, false) == 0,
                "A part with an unloaded master must not create an independent energy store");
        tablePart.setItem(0, new ItemStack(Items.DIAMOND));
        helper.assertTrue(tablePart.isEmpty() && tablePart.getItem(0).isEmpty(),
                "An unloaded master must not redirect inventory writes to the part");
        part.mainPos = null;
        helper.assertTrue(part.getMain() == null, "Missing master metadata must not throw");
        developer.setConnectedNodePosition(pos);
        table.setConnectedNodePosition(pos);
        developer.serverTick(level);
        table.tick();
        helper.assertTrue(developer.getConnectedNodePosition() == null && table.getConnectedNodePosition() == null,
                "Loaded invalid endpoints must still be disconnected");
    }

    private static void structures(GameTestHelper helper, ServerPlayer player) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        var chunk = level.getChunkAt(pos);
        var originalStarts = new HashMap<>(chunk.getAllStarts());
        var originalReferences = new HashMap<>(chunk.getAllReferences());
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(
                ResourceKey.create(Registries.STRUCTURE,
                        Identifier.withDefaultNamespace("buried_treasure"))).value();
        var ownPos = chunk.getPos();
        var missingPos = new ChunkPos(ownPos.x() + 1_024, ownPos.z() + 1_024);
        var ownReference = ChunkPos.pack(ownPos.x(), ownPos.z());
        var missingReference = ChunkPos.pack(missingPos.x(), missingPos.z());
        var keyPrefix = level.dimension().identifier() + "|minecraft:buried_treasure|";
        var system = AbilitySystemServer.getSystem(player);
        var manager = system.getPropsManager();
        var data = system.getPlayerData(player.getUUID()).getPropsData();
        try {
            helper.assertTrue(level.getChunkSource().getChunkNow(missingPos.x(), missingPos.z()) == null,
                    "The referenced start chunk must initially be unloaded");
            chunk.setAllReferences(Map.of(structure,
                    new LongOpenHashSet(new long[]{missingReference, ownReference})));
            chunk.setAllStarts(Map.of(structure, new StructureStart(
                    structure, ownPos, 0, new PiecesContainer(
                    List.of(new BuriedTreasurePieces.BuriedTreasurePiece(pos))))));

            manager.reset(player);
            scanStructures(player);
            close(helper, data.total(), 0, "Inactive P.R.O.P.S must not discover structures");
            helper.assertTrue(!data.hasVisitedStructure(keyPrefix + ownPos.x() + "," + ownPos.z()),
                    "Inactive scanning must not consume the one-time reward");
            manager.start(player);
            player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
            scanStructures(player);
            close(helper, data.total(), 0, "A chunk reference alone does not mean the player is inside its structure");
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            scanStructures(player);
            close(helper, data.total(), 20, "A loaded start containing the player awards discovery");
            scanStructures(player);
            close(helper, data.total(), 20, "Repeated discovery must not award twice");
            helper.assertTrue(level.getChunkSource().getChunkNow(missingPos.x(), missingPos.z()) == null,
                    "Structure discovery must never load a referenced chunk");
            helper.assertTrue(!data.hasVisitedStructure(keyPrefix + missingPos.x() + "," + missingPos.z()),
                    "Skipped unloaded references must remain eligible for a later retry");
        } finally {
            chunk.setAllStarts(originalStarts);
            chunk.setAllReferences(originalReferences);
        }
    }

    private static void scanStructures(ServerPlayer player) {
        try {
            // Keep the private injection ABI used by addons; exercise the actual production scan.
            var scan = PropsManager.class
                    .getDeclaredMethod("checkStructures", ServerPlayer.class);
            scan.setAccessible(true);
            scan.invoke(AbilitySystemServer.getSystem(player).getPropsManager(), player);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Structure scan failed", error);
        }
    }

    private static void attributes(GameTestHelper helper, ServerPlayer player) {
        var step = player.getAttribute(Attributes.STEP_HEIGHT).getBaseValue();
        var knockback = player.getAttribute(Attributes.ATTACK_KNOCKBACK);
        var legacyKnockback = AcademyCraft.academy("attribute_bonus.muscle_knockback");
        var otherKnockback = AcademyCraft.academy("props_test.other_knockback");
        knockback.addOrReplacePermanentModifier(new AttributeModifier(
                legacyKnockback, 1.0, AttributeModifier.Operation.ADD_VALUE));
        knockback.addTransientModifier(new AttributeModifier(
                otherKnockback, 0.25, AttributeModifier.Operation.ADD_VALUE));
        var cases = new double[][]{
                {799.0, 3.995}, {800.0, 5.0}, {1_199.0, 6.995},
                {1_200.0, 8.0}, {1_599.0, 8.0}, {1_600.0, 9.0},
                {2_000.0, 9.0}, {0.0, 0.0}
        };
        for (var sample : cases) {
            var value = sample[0];
            player.getAttribute(PlayerAttributes.MUSCLE_STRENGTH).setBaseValue(value);
            player.getAttribute(PlayerAttributes.DEXTERITY).setBaseValue(value);
            PlayerAttributeRuntime.syncPlayer(player);
            close(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                    1.0 + sample[1], "Melee damage cap/milestones");
            close(helper, player.getAttributeValue(Attributes.ATTACK_KNOCKBACK),
                    0.25, "Remove muscle knockback while preserving other modifiers");
            helper.assertTrue(knockback.getModifier(legacyKnockback) == null,
                    "Old permanent muscle knockback must be removed");
            helper.assertTrue(knockback.getModifier(otherKnockback) != null,
                    "Other knockback bonuses must remain intact");
            close(helper, player.getAttributeValue(Attributes.STEP_HEIGHT),
                    step + PropsMath.dexterityStepHeightBonus(value), "Step threshold/removal");
        }
        player.getAttribute(PlayerAttributes.DEXTERITY).setBaseValue(2_000.0);
        PlayerAttributeRuntime.syncPlayer(player);
        close(helper, player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 6.0,
                "Dexterity extends the safe fall distance with its jump height");
        helper.assertTrue(!player.causeFallDamage(5.1, 1.0f, player.damageSources().fall()),
                "Landing from a full dexterity jump must not cause fall damage");
        player.getAttribute(PlayerAttributes.ENDURANCE).setBaseValue(50.0);
        PlayerAttributeRuntime.syncPlayer(player);
        close(helper, player.getMaxHealth(), 21.0, "50 endurance adds one health");
        player.getAttribute(PlayerAttributes.NEURAL_ACTIVITY).setBaseValue(100.0);
        close(helper, PlayerAttributeRuntime.neuralIterationMultiplier(player), 1.01, "Neural iteration");
    }

    private static void experienceOrbs(GameTestHelper helper, ServerPlayer player) {
        var data = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID()).getPropsData();
        player.giveExperiencePoints(100);
        close(helper, data.total(), 0.0, "Direct XP is not an orb pickup");
        for (var value : new int[]{1, 17, 2477}) {
            var before = data.total();
            var accumulatedExperience = data.getPerceptionExperienceRemainder() + value;
            var expectedReward = accumulatedExperience / 10 * 0.1;
            var expectedRemainder = accumulatedExperience % 10;
            player.takeXpDelay = 0;
            var orb = new ExperienceOrb(helper.getLevel(), 0.0, 5.0, 0.0, value);
            orb.playerTouch(player);
            close(helper, data.total() - before,
                    PropsMath.awardedAmount(before, expectedReward, false),
                    "Experience value determines the perception reward");
            helper.assertTrue(data.getPerceptionExperienceRemainder() == expectedRemainder,
                    "Sub-ten experience remainder is preserved");
            helper.assertTrue(orb.isRemoved(), "Successful pickup consumes the orb");
        }
    }

    private static void activity(GameTestHelper helper, ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var manager = system.getPropsManager();
        var data = system.getPlayerData(player.getUUID()).getPropsData();
        player.awardStat(Stats.SPRINT_ONE_CM, 75);
        manager.tick(player);
        close(helper, data.total(), 0.0, "Partial blocks carry over");
        player.awardStat(Stats.SPRINT_ONE_CM, 25);
        manager.tick(player);
        close(helper, data.total(), 0.004, "Sprint one block");
        var before = data.total();
        player.awardStat(Stats.SWIM_ONE_CM, 100);
        manager.tick(player);
        close(helper, data.total() - before, PropsMath.awardedAmount(before, 0.01, false), "Swim one block");
        before = data.total();
        player.awardStat(Stats.JUMP, 10);
        manager.tick(player);
        close(helper, data.total() - before, PropsMath.awardedAmount(before, 0.1, false), "Jump bursts reward once");
        before = data.total();
        player.awardStat(Stats.JUMP, 10);
        manager.tick(player);
        close(helper, data.total(), before, "Repeated jumps in the same tick are suppressed");
        player.getFoodData().setFoodLevel(16);
        manager.tick(player);
        before = data.total();
        player.getFoodData().eat(4, 0.0f);
        manager.tick(player);
        close(helper, data.total() - before, PropsMath.awardedAmount(before, 2.0, false), "Restored hunger");
        before = data.total();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MAP));
        Items.MAP.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        manager.tick(player);
        close(helper, data.total() - before, PropsMath.awardedAmount(before, 5.0, false), "Successful blank map use");
    }

    private static void damage(GameTestHelper helper, ServerPlayer player) {
        var data = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID()).getPropsData();
        var target = helper.spawn(EntityTypes.COW, new BlockPos(2, 2, 2));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        target.setHealth(100.0f);
        target.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 18.0f);
        close(helper, data.total(), 3.6, "Damage above ten is not capped");
        var before = data.total();
        target.setInvulnerableTime(0);
        var source = new DamageSource(helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.ELECTRO_DAMAGE), player);
        target.hurtServer(helper.getLevel(), source, 4.0f);
        close(helper, data.total() - before, PropsMath.awardedAmount(before, 0.8, false), "Ability damage rewards muscle");
    }

    private static void abilityLoot(GameTestHelper helper, ServerPlayer player) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var state = Blocks.DIAMOND_ORE.defaultBlockState();
        var subject = helper.spawn(EntityTypes.COW, new BlockPos(3, 2, 2));
        player.getAttribute(PlayerAttributes.PERCEPTION).setBaseValue(2_000.0);
        var total = 0;
        for (var sample = 0; sample < 256; sample++) {
            total += AbilityBlockDrops.getDrops(player, state, level, pos, null, subject, ItemStack.EMPTY)
                    .stream().mapToInt(ItemStack::getCount).sum();
        }
        helper.assertTrue(total > 350, "Ability block loot must receive the caster's Fortune II");
        helper.assertTrue(BlockLootPlayerContext.current() == null, "Ability loot attribution must not leak");
        try (var ignored = AbilityBlockDrops.capture(player)) {
            helper.assertTrue(BlockLootPlayerContext.current() == player,
                    "Direct destruction and secondary drops inherit the caster");
        }
        player.getAttribute(PlayerAttributes.PERCEPTION).setBaseValue(100.0);
        var looting = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
        var source = new DamageSource(level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.ELECTRO_DAMAGE), player);
        player.getRandom().setSeed(42);
        var extras = 0;
        for (var sample = 0; sample < 1_000; sample++) {
            var event = new EnchantedEntityLootEvent(subject, source, looting, 3);
            PlayerAttributeRuntime.onEnchantedEntityLoot(event);
            helper.assertTrue(event.getEnchantmentLevel() == 3 || event.getEnchantmentLevel() == 4,
                    "Perception adds to existing enchantments");
            extras += event.getEnchantmentLevel() - 3;
        }
        helper.assertTrue(extras > 60 && extras < 140, "100 perception rolls an extra level with 10 percent chance");
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-5, message + ": " + actual + " != " + expected);
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TestData.CODEC.forGetter(Instance::info),
                Codec.STRING.fieldOf("scenario").forGetter(value -> value.scenario)
        ).apply(instance, Instance::new));
        private final String scenario;

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info, String scenario) {
            super(info);
            this.scenario = scenario;
        }

        @Override
        public void run(GameTestHelper helper) {
            runScenario(helper, scenario);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("P.R.O.P.S regression");
        }
    }
}
