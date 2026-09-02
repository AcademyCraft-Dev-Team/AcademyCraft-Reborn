package org.academy.internal.common.ability.darkmatter.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterEnchantments;
import org.academy.internal.common.ability.darkmatter.DarkmatterLawMark;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;
import org.academy.internal.common.ability.darkmatter.skills.lv2.DarkmatterCut;
import org.academy.internal.common.ability.darkmatter.skills.lv2.DarkmatterPhaseTuning;
import org.academy.internal.common.ability.darkmatter.skills.lv3.DarkmatterRadiation;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterRepair;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;
import org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.academy.internal.common.world.item.DarkmatterNativeItemSupport;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end gates for the server-owned dark-matter MP and CP ledger.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class DarkmatterResourceGameTests {
    private static final Identifier TEST_INSTANCE_TYPE =
            AcademyCraft.academy("darkmatter_resource_function");
    private static final AtomicInteger NEXT_PLAYER_ID = new AtomicInteger();

    private DarkmatterResourceGameTests() {
    }

    @SubscribeEvent
    private static void registerTestInstanceType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TEST_INSTANCE_TYPE,
                () -> DarkmatterResourceTestInstance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : Scenario.values()) {
            var environment = event.registerEnvironment(
                    AcademyCraft.academy("darkmatter/resource/" + scenario.serializedName),
                    new TestEnvironmentDefinition.AllOf(List.of())
            );
            var data = new TestData<>(
                    environment,
                    Identifier.withDefaultNamespace("empty"),
                    scenario.maxTicks,
                    0,
                    true,
                    Rotation.NONE,
                    false,
                    1,
                    1,
                    false,
                    16
            );
            event.registerTest(
                    AcademyCraft.academy("darkmatter_resource_" + scenario.serializedName),
                    new DarkmatterResourceTestInstance(scenario, data)
            );
        }
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, int level) {
        return createPlayer(helper, level, true);
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, int level, boolean allowPvp) {
        var profile = new GameProfile(
                UUID.randomUUID(),
                "dm-test-" + NEXT_PLAYER_ID.incrementAndGet()
        );
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), profile,
                cookie.clientInformation()
        ) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }

            @Override
            public boolean canHarmPlayer(Player other) {
                return allowPvp && super.canHarmPlayer(other);
            }
        };
        var position = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 2, 1)));
        player.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        helper.getLevel().setBlockAndUpdate(
                player.blockPosition().below(), Blocks.STONE.defaultBlockState());

        var system = AbilitySystemServer.getSystem(player);
        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.DARKMATTER.get());
        system.setPlayerLevel(player.getUUID(), level);
        system.setPlayerBaseMaxCP(player.getUUID(), 500.0f);
        system.setPlayerAvailableCP(player.getUUID(), 500.0f);
        system.addPlayerSkill(player, Skills.DARKMATTER_GENERATION.get().getKeyString());
        system.getDarkmatterResourceManager().reconcile(player);
        return player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void assertClose(GameTestHelper helper, float expected, float actual,
                                    String message) {
        helper.assertTrue(Math.abs(expected - actual) <= 1.0e-3f,
                message + "; expected=" + expected + ", actual=" + actual);
    }

    private static void pinTarget(LivingEntity target, Vec3 position) {
        target.snapTo(position);
        target.setDeltaMovement(Vec3.ZERO);
    }

    private enum Scenario {
        CREATED_POOL_RELEASES_CP_DEBT("created_pool_releases_cp_debt", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 3);
                var system = AbilitySystemServer.getSystem(player);
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 20.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize the resource ledger");
                helper.assertTrue(manager.create(player, 10.0f, 2.0f),
                        "Created MP was not added immediately");
                var created = manager.getView(player);
                assertClose(helper, 10.0f, created.createdMatter(), "Created pool mismatch");
                assertClose(helper, 20.0f, created.createdCpDebt(), "CP debt mismatch");

                helper.assertTrue(manager.consume(player, 6.0f,
                                Skills.DARKMATTER_GENERATION.get(), 20),
                        "Created MP could not be consumed");
                var consumed = manager.getView(player);
                assertClose(helper, 4.0f, consumed.createdMatter(),
                        "Consumption did not use created MP first");
                assertClose(helper, 8.0f, consumed.createdCpDebt(),
                        "Consumption released the wrong CP debt share");
                assertClose(helper, 20.0f, consumed.naturalMatter(),
                        "Natural MP was consumed before created MP");
                helper.assertTrue(manager.debugSetPools(player, 0.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to reset the resource ledger");
                helper.assertTrue(manager.create(player, 2.0f, 0.1f),
                        "Created MP rejected a caller cost below the public floor");
                assertClose(helper, 2.0f, manager.getView(player).createdCpDebt(),
                        "Public creation bypassed the one-CP-per-MP debt floor");
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        NATURAL_RECOVERY_RESPECTS_EFFECTIVE_CAPACITY(
                "natural_recovery_respects_effective_capacity", 80) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 1);
                var manager = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 99.0f, 0.0f, 0.0f, 8.0f),
                        "Failed to initialize a reservation-limited natural pool");
                helper.runAtTickTime(25, () -> {
                    assertClose(helper, 100.0f, manager.getView(player).naturalMatter(),
                            "Natural MP did not recover to the effective capacity");
                    helper.assertTrue(manager.debugSetPools(
                                    player, 110.0f, 0.0f, 0.0f, 8.0f),
                            "Failed to initialize natural MP above the effective capacity");
                });
                helper.runAtTickTime(50, () -> {
                    assertClose(helper, 110.0f, manager.getView(player).naturalMatter(),
                            "Reservation incorrectly truncated existing MP");
                    removePlayer(helper, player);
                    helper.succeed();
                });
            }
        },
        DAMAGE_CONVERSION_PRECEDES_DEFENSE("damage_conversion_precedes_defense", 100) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 5);
                var manager = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
                // A freshly connected ServerPlayer has vanilla login invulnerability. Waiting for
                // it to expire ensures this gate reaches the real incoming-damage event pipeline.
                helper.runAfterDelay(65, () -> {
                    helper.assertTrue(player.isAlive(),
                            "Embedded player died before the damage assertion");
                    helper.assertTrue(manager.debugSetPools(player, 10.0f, 0.0f, 0.0f, 0.0f),
                            "Failed to initialize natural MP");
                    player.setHealth(20.0f);
                    player.setInvulnerable(false);
                    player.invulnerableTime = 0;
                    var attacker = helper.spawn(EntityTypes.ZOMBIE, 2, 2, 1);
                    var source = helper.getLevel().damageSources().mobAttack(attacker);
                    helper.assertTrue(!player.isInvulnerableTo(helper.getLevel(), source),
                            "Embedded player remained invulnerable to mob damage; creative="
                                    + player.isCreative() + ", spectator=" + player.isSpectator());
                    helper.assertTrue(player.hurtServer(helper.getLevel(),
                                    source, 10.0f),
                            "Mob damage was rejected after login invulnerability expired");
                    assertClose(helper, 15.0f, player.getHealth(),
                            "Level-five conversion did not remove 50% of raw damage");
                    assertClose(helper, 5.0f, manager.getView(player).totalMatter(),
                            "Damage conversion consumed the wrong MP amount");
                    removePlayer(helper, player);
                    helper.succeed();
                });
            }
        },
        GENERIC_KILL_CONSUMES_AVAILABLE_MP("generic_kill_consumes_available_mp", 100) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 5);
                var manager = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 7.0f, 3.0f, 6.0f, 0.0f),
                        "Failed to initialize mixed MP pools");
                helper.runAfterDelay(65, () -> {
                    player.hurtServer(helper.getLevel(),
                            helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
                    assertClose(helper, 0.0f, manager.getView(player).totalMatter(),
                            "Generic-kill damage did not pass through MP conversion");
                    removePlayer(helper, player);
                    helper.succeed();
                });
            }
        },
        PHASE_TUNING_USES_LEVEL_RELATIVE_POINTS(
                "phase_tuning_uses_level_relative_points", 240) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 3);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_PHASE_TUNING.get().getKeyString());
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.setAlphaPoints(player, 0),
                        "Failed to initialize the alpha allocation");
                helper.assertTrue(DarkmatterPhaseTuning.Server.beginTuning(
                                player, DarkmatterPhaseTuning.Direction.ALPHA),
                        "Server rejected phase tuning despite the skill being learned and enabled");
                // GameTest's embedded players are connected to the PlayerList but do not emit
                // PlayerTickEvent. Advance the same server entry explicitly while preserving
                // the maintained-key heartbeat schedule used by a real client.
                for (var tick = 1; tick <= 200; tick++) {
                    var elapsed = tick;
                    helper.runAtTickTime(elapsed, () -> {
                        if (elapsed % 40 == 0) {
                            helper.assertTrue(DarkmatterPhaseTuning.Server.beginTuning(
                                            player, DarkmatterPhaseTuning.Direction.ALPHA),
                                    "Server rejected a phase-tuning heartbeat");
                        }
                        DarkmatterPhaseTuning.Server.tick(player);
                        if (elapsed == 5) {
                            helper.assertTrue(DarkmatterPhaseTuning.Server.isTuning(player),
                                    "Phase tuning lease was removed before its first heartbeat");
                            helper.assertTrue(manager.getPhaseSnapshot(player).alphaPoints() > 0,
                                    "Server tick did not advance the active phase tuning lease");
                        }
                    });
                }
                helper.runAtTickTime(205, () -> {
                    var snapshot = manager.getPhaseSnapshot(player);
                    helper.assertTrue(snapshot.totalPoints() == 150,
                            "Level-three tuning used the wrong point capacity");
                    helper.assertTrue(snapshot.alphaPoints() == 150,
                            "A ten-second hold did not reach the alpha extreme: "
                                    + snapshot.alphaPoints());
                    DarkmatterPhaseTuning.Server.endTuning(
                            player, DarkmatterPhaseTuning.Direction.ALPHA);
                });
                helper.runAtTickTime(225, () -> {
                    helper.assertTrue(manager.getPhaseSnapshot(player).alphaPoints() == 150,
                            "Phase allocation continued changing after key release");
                    removePlayer(helper, player);
                    helper.succeed();
                });
            }
        },
        PHASE_UPGRADE_PRESERVES_PERCENTAGE("phase_upgrade_preserves_percentage", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 1);
                var system = AbilitySystemServer.getSystem(player);
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.setAlphaPoints(player, 40),
                        "Failed to initialize an 80% alpha allocation");
                system.setPlayerLevel(player.getUUID(), 5);
                var upgraded = manager.getPhaseSnapshot(player);
                helper.assertTrue(upgraded.totalPoints() == 250,
                        "Level-five point capacity did not become 250");
                helper.assertTrue(upgraded.alphaPoints() == 200 && upgraded.betaPoints() == 50,
                        "Ability upgrade did not preserve the 80/20 phase ratio");
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        SHAPING_CONTEXTS_CONSUME_AND_MUTATE_SERVER_INVENTORY(
                "shaping_contexts_consume_and_mutate_server_inventory", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 1);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 20.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize shaping MP");

                helper.assertTrue(DarkmatterShaping.Server.createShaped(
                                player, DarkmatterShape.TOOL,
                                50, Map.of()),
                        "Empty-hand tool shaping was rejected");
                helper.assertTrue(player.getMainHandItem().is(
                                org.academy.internal.common.world.item.Items.DARKMATTER_TOOL.get()),
                        "Tool shaping did not replace the main hand");

                var targetSlot = 9;
                player.getInventory().setItem(targetSlot, ItemStack.EMPTY);
                helper.assertTrue(DarkmatterShaping.Server.createMaterialForTesting(
                                player, targetSlot),
                        "Empty inventory-slot shaping was rejected");
                helper.assertTrue(DarkmatterItemUtil.isDarkmatter(
                                player.getInventory().getItem(targetSlot)),
                        "Material shaping did not fill the requested inventory slot");
                var tool = player.getMainHandItem();
                var previousVisualStack = tool.copy();
                DarkmatterItemUtil.decayIntegrity(tool, 12_000);
                helper.assertTrue(!tool.getItem().shouldCauseReequipAnimation(
                                previousVisualStack, tool, false)
                                && !previousVisualStack.shouldCauseBlockBreakReset(tool),
                        "Integrity synchronization still causes a hand swap or mining reset");
                var bow = new ItemStack(
                        org.academy.internal.common.world.item.Items.DARKMATTER_BOW.get());
                var arrow = new ItemStack(
                        org.academy.internal.common.world.item.Items.DARKMATTER_ARROW.get());
                helper.assertTrue(((ProjectileWeaponItem) bow.getItem())
                                .getAllSupportedProjectiles(bow).test(arrow),
                        "Native bow does not accept the dark-matter arrow");
                var fallback = DarkmatterNativeItemSupport
                        .infiniteDarkmatterArrow(bow);
                helper.assertTrue(fallback.is(
                                org.academy.internal.common.world.item.Items.DARKMATTER_ARROW.get())
                                && fallback.has(DataComponents.INTANGIBLE_PROJECTILE),
                        "No-ammo fallback is not an intangible dark-matter arrow");
                var darkmatterItems = player.registryAccess().lookupOrThrow(Registries.ITEM)
                        .getOrThrow(TagKey.create(
                                Registries.ITEM, AcademyCraft.academy("darkmatter_items")));
                helper.assertTrue(darkmatterItems.size() == 16,
                        "Dark-matter enchantable tag does not contain every item: "
                                + darkmatterItems.size());
                var darkmatterEnchantment = player.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(DarkmatterEnchantments.DARKMATTER);
                for (var item : darkmatterItems) {
                    var enchantable = new ItemStack(item.value());
                    helper.assertTrue(enchantable.isEnchantable()
                                    && enchantable.supportsEnchantment(darkmatterEnchantment),
                            "Dark-matter item is not enchantable: " + item.getKey());
                }
                var sword = new ItemStack(
                        org.academy.internal.common.world.item.Items.DARKMATTER_SWORD.get());
                helper.assertTrue(sword.getItem().canPerformAction(
                                sword, ItemAbilities.SWORD_SWEEP),
                        "Native sword does not expose the sweep attack action");
                assertClose(helper, 15.0f, manager.getView(player).totalMatter(),
                        "Shaped tool plus inventory material consumed the wrong MP total");
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        NATIVE_TOOL_PHASE_AND_INTEGRITY_ARE_OPERATIONAL(
                "native_tool_phase_and_integrity_are_operational", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 3);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 20.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize native-tool MP");
                helper.assertTrue(DarkmatterShaping.Server.createShaped(
                                player, DarkmatterShape.TOOL,
                                100, Map.of()),
                        "Failed to shape the native tool");
                var tool = player.getMainHandItem();
                helper.assertTrue(tool.getItem().canPerformAction(tool,
                                ItemAbilities.AXE_STRIP)
                                && tool.getItem().canPerformAction(tool,
                                ItemAbilities.SHOVEL_FLATTEN)
                                && tool.getItem().canPerformAction(tool,
                                ItemAbilities.HOE_TILL),
                        "Native tool does not expose axe, shovel and hoe actions");
                var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                var efficiency = enchantments.getOrThrow(Enchantments.EFFICIENCY);
                var fortune = enchantments.getOrThrow(Enchantments.FORTUNE);
                var mending = enchantments.getOrThrow(Enchantments.MENDING);

                DarkmatterShaping.Server.refreshHeldNativeProfile(player);
                helper.assertTrue(tool.getEnchantmentLevel(efficiency) == 5
                                && tool.getEnchantmentLevel(fortune) == 0,
                        "Level-three alpha tool did not become efficiency 5 / fortune 0");
                manager.setAlphaPoints(player, 0);
                DarkmatterShaping.Server.refreshHeldNativeProfile(player);
                helper.assertTrue(tool.getEnchantmentLevel(efficiency) == 5
                                && tool.getEnchantmentLevel(fortune) == 0,
                        "Item phase changed after player phase state changed");
                helper.assertTrue(!tool.getItem().supportsEnchantment(tool, mending),
                        "Native dark-matter equipment still accepts Mending");

                for (var step = 0; step < 10; step++) {
                    DarkmatterItemUtil.decayIntegrity(tool, 10);
                }
                helper.assertTrue(!DarkmatterItemUtil.isOperational(tool)
                                && tool.get(DataComponents.TOOL) == null,
                        "Zero integrity did not disable the native tool component");
                helper.assertTrue(DarkmatterItemUtil.repairIntegrity(tool, 1.0f)
                                && DarkmatterItemUtil.isOperational(tool)
                                && tool.get(DataComponents.TOOL) != null,
                        "Integrity repair did not restore the native tool component");
                var nativeItems = List.of(
                        org.academy.internal.common.world.item.Items.DARKMATTER_TOOL.get(),
                        org.academy.internal.common.world.item.Items.DARKMATTER_SPEAR.get(),
                        org.academy.internal.common.world.item.Items.DARK_MATTER_HELMET.get(),
                        org.academy.internal.common.world.item.Items.DARK_MATTER_CHESTPLATE.get(),
                        org.academy.internal.common.world.item.Items.DARK_MATTER_LEGGINGS.get(),
                        org.academy.internal.common.world.item.Items.DARK_MATTER_BOOTS.get());
                for (var nativeItem : nativeItems) {
                    var stack = new ItemStack(nativeItem);
                    for (var step = 0; step < 12; step++) {
                        DarkmatterItemUtil.decayIntegrity(stack, 12);
                    }
                    helper.assertTrue(!DarkmatterItemUtil.isOperational(stack)
                                    && stack.get(DataComponents.ATTRIBUTE_MODIFIERS) == null,
                            "Native equipment did not fully disable at zero integrity: "
                                    + nativeItem);
                }
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        DISASSEMBLE_ALPHA_CHAIN_REACHES_REAL_BLOCKS(
                "disassemble_alpha_chain_reaches_real_blocks", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 5);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_DISASSEMBLE.get().getKeyString());
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 100.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize disassemble MP");
                helper.assertTrue(manager.setAlphaPoints(player, 250),
                        "Failed to initialize the level-five alpha extreme");

                var origin = helper.absolutePos(new BlockPos(3, 2, 1));
                for (var offset = 0; offset < 11; offset++) {
                    helper.getLevel().setBlockAndUpdate(
                            origin.east(offset), Blocks.STONE.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(
                            origin.east(offset).below(), Blocks.STONE.defaultBlockState());
                }
                helper.assertTrue(DarkmatterDisassemble.Server.tryDestroyBlock(
                                player, origin, net.minecraft.core.Direction.UP),
                        "Server rejected a valid high-alpha disassemble cast");
                for (var offset = 0; offset < 11; offset++) {
                    helper.assertTrue(helper.getLevel().getBlockState(origin.east(offset)).isAir(),
                            "High-alpha disassemble stopped before target " + offset);
                    helper.assertTrue(helper.getLevel().getBlockState(
                                    origin.east(offset).below()).is(Blocks.STONE),
                            "Chain budget was spent in hidden depth before the visible plane");
                }
                assertClose(helper, 100.0f, manager.getView(player).totalMatter(),
                        "Deconstruction must not consume MP");
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        DISASSEMBLE_BETA_AND_ENTITY_PHASES_ARE_OPERATIONAL(
                "disassemble_beta_and_entity_phases_are_operational", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 5);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_DISASSEMBLE.get().getKeyString());
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 100.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize entity-disassemble MP");
                var fortune = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.FORTUNE);
                var betaLootTool = DarkmatterDisassemble.Server.createLootTool(
                        player.registryAccess(), 5);
                helper.assertTrue(betaLootTool.getEnchantmentLevel(fortune) == 5,
                        "Level-five beta did not reach effective Fortune V in the real loot tool");

                var alphaTarget = helper.spawn(EntityTypes.ZOMBIE, 3, 2, 1);
                alphaTarget.setNoAi(true);
                manager.setAlphaPoints(player, 250);
                helper.assertTrue(DarkmatterDisassemble.Server.tryAutomatedAttack(
                                player, alphaTarget),
                        "Alpha entity disassembly was rejected");
                var alphaRemainingHealth = alphaTarget.getHealth();

                var betaTarget = helper.spawn(EntityTypes.ZOMBIE, 4, 2, 1);
                betaTarget.setNoAi(true);
                manager.setAlphaPoints(player, 0);
                helper.assertTrue(DarkmatterDisassemble.Server.tryAutomatedAttack(
                                player, betaTarget),
                        "Beta entity disassembly was rejected");
                helper.assertTrue(alphaRemainingHealth < betaTarget.getHealth(),
                        "Alpha and beta entity damage did not produce distinct server results");
                helper.assertTrue(betaTarget.hasEffect(MobEffects.WEAKNESS),
                        "Beta entity disassembly did not apply corrosion/weakness");
                assertClose(helper, 100.0f, manager.getView(player).totalMatter(),
                        "Entity deconstruction must not consume MP");
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        DISASSEMBLE_KILL_CREDITS_UNCAPPED_MATTER(
                "disassemble_kill_credits_uncapped_matter", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 5);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_DISASSEMBLE.get().getKeyString());
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 300.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize uncapped deconstruction MP");
                manager.setAlphaPoints(player, 0);
                var target = helper.spawn(EntityTypes.ZOMBIE, 3, 2, 1);
                target.setNoAi(true);
                target.setHealth(1.0f);
                var reward = target.getMaxHealth() / 10.0f;
                helper.assertTrue(DarkmatterDisassemble.Server.tryAutomatedAttack(player, target),
                        "Lethal deconstruction was rejected");
                assertClose(helper, 300.0f + reward, manager.getView(player).totalMatter(),
                        "Lethal deconstruction did not credit max-health/10 uncapped MP");
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        DISASSEMBLE_M3_SENDS_DROPS_TO_INVENTORY(
                "disassemble_m3_sends_drops_to_inventory", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 5);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_DISASSEMBLE.get().getKeyString());
                helper.assertTrue(system.setPlayerSkillProficiency(
                                player.getUUID(), Skills.DARKMATTER_DISASSEMBLE.get(), 3_000.0f),
                        "Failed to initialize disassemble M3");
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 100.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize M3 disassemble MP");
                helper.assertTrue(manager.setAlphaPoints(player, 250),
                        "Failed to initialize the M3 alpha extreme");
                var origin = helper.absolutePos(new BlockPos(3, 2, 1));
                for (var offset = 0; offset < 11; offset++) {
                    helper.getLevel().setBlockAndUpdate(
                            origin.east(offset), Blocks.STONE.defaultBlockState());
                }
                helper.assertTrue(DarkmatterDisassemble.Server.tryDestroyBlock(
                                player, origin, net.minecraft.core.Direction.UP),
                        "M3 block disassembly was rejected");
                var cobblestone = 0;
                for (var slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    var stack = player.getInventory().getItem(slot);
                    if (stack.is(net.minecraft.world.item.Items.COBBLESTONE)) {
                        cobblestone += stack.getCount();
                    }
                }
                helper.assertTrue(cobblestone == 11,
                        "M3 did not send all eleven legal drops to inventory: " + cobblestone);
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                                ItemEntity.class,
                                new AABB(origin).inflate(14.0)).isEmpty(),
                        "M3 left disassembly drops on the ground despite inventory space");
                assertClose(helper, 100.0f, manager.getView(player).totalMatter(),
                        "M3 deconstruction must not consume MP");
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        CUT_PHASES_AND_SHARED_LAW_MARK_ARE_OPERATIONAL(
                "cut_phases_and_shared_law_mark_are_operational", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 3);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_DISASSEMBLE.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_CUT.get().getKeyString());
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 100.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize cut MP");

                var alphaTarget = helper.spawn(EntityTypes.ZOMBIE, 3, 2, 1);
                alphaTarget.setNoAi(true);
                manager.setAlphaPoints(player, 150);
                helper.assertTrue(DarkmatterCut.Server.tryCast(
                                player, new Vec3(1.0, 0.0, 0.0)),
                        "Alpha cut was rejected");
                var alphaRemainingHealth = alphaTarget.getHealth();

                var betaTarget = helper.spawn(EntityTypes.ZOMBIE, 4, 2, 1);
                betaTarget.setNoAi(true);
                manager.setAlphaPoints(player, 0);
                helper.assertTrue(DarkmatterCut.Server.tryCast(
                                player, new Vec3(1.0, 0.0, 0.0)),
                        "Beta cut was rejected");
                helper.assertTrue(alphaRemainingHealth < betaTarget.getHealth(),
                        "Alpha and beta cut damage did not diverge on the server");
                helper.assertTrue(DarkmatterLawMark.isMarkedBy(player, betaTarget),
                        "Beta cut did not apply the shared abnormal-law mark");

                betaTarget.invulnerableTime = 0;
                helper.assertTrue(DarkmatterDisassemble.Server.tryAutomatedAttack(
                                player, betaTarget),
                        "Disassemble could not consume the cut law mark");
                helper.assertTrue(!DarkmatterLawMark.isMarkedBy(player, betaTarget),
                        "Disassemble did not consume the shared abnormal-law mark");
                helper.assertTrue(betaTarget.hasEffect(MobEffects.WEAKNESS),
                        "Law-mark detonation did not apply weakness");
                assertClose(helper, 94.0f, manager.getView(player).totalMatter(),
                        "Deconstruction consumed MP in addition to the two cuts");
                removePlayer(helper, player);
                helper.succeed();
            }
        },
        INTERFERENCE_CHANNEL_FEATHERS_AND_EXPOSURE_ARE_OPERATIONAL(
                "interference_channel_feathers_and_exposure_are_operational", 60) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 3);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_PHASE_TUNING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_RADIATION.get().getKeyString());
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 172.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize interference MP");
                player.setYRot(-90.0f);
                player.setYHeadRot(-90.0f);
                player.setXRot(0.0f);

                var alphaTarget = helper.spawn(EntityTypes.ZOMBIE, 3, 2, 1);
                alphaTarget.setNoAi(true);
                manager.setAlphaPoints(player, 150);
                helper.assertTrue(DarkmatterRadiation.Server.beginChannel(player),
                        "Server rejected interference channel start");
                DarkmatterRadiation.Server.tick(player);
                helper.assertTrue(DarkmatterRadiation.Server.isChanneling(player),
                        "Interference channel ended after its first paid tick");
                helper.assertTrue(alphaTarget.getHealth() < alphaTarget.getMaxHealth(),
                        "Alpha interference pulse did not damage a target in its narrow cone");
                var feathers = helper.getLevel().getEntitiesOfClass(
                        DarkmatterFeatherProjectile.class,
                        player.getBoundingBox().inflate(20.0));
                helper.assertTrue(feathers.size() == 2,
                        "Level-three alpha did not launch two entity feather blades: "
                                + feathers.size());
                alphaTarget.discard();

                var betaTarget = helper.spawn(EntityTypes.ZOMBIE, 4, 2, 1);
                betaTarget.setNoAi(true);
                manager.setAlphaPoints(player, 0);
                var betaPosition = helper.absoluteVec(new Vec3(4.5, 2.0, 1.5));
                var outsidePosition = helper.absoluteVec(new Vec3(30.5, 2.0, 1.5));
                var healthBeforeBurst = new float[1];
                var observedNewExposure = new boolean[1];
                helper.startSequence().thenWaitUntil(() -> {
                            pinTarget(betaTarget, betaPosition);
                            DarkmatterRadiation.Server.tick(player);
                            helper.assertTrue(DarkmatterRadiation.Server.getExposureTicks(
                                            player, betaTarget) >= 10,
                                    "Beta exposure did not accumulate to ten ticks");
                        }).thenWaitUntil(() -> {
                            pinTarget(betaTarget, outsidePosition);
                            DarkmatterRadiation.Server.tick(player);
                            helper.assertTrue(DarkmatterRadiation.Server.getExposureTicks(
                                            player, betaTarget) == 0,
                                    "Exposure remained after leaving the beta field");
                        }).thenExecute(() -> healthBeforeBurst[0] = betaTarget.getHealth())
                        .thenWaitUntil(() -> {
                            pinTarget(betaTarget, betaPosition);
                            DarkmatterRadiation.Server.tick(player);
                            var exposure = DarkmatterRadiation.Server.getExposureTicks(
                                    player, betaTarget);
                            observedNewExposure[0] |= exposure > 0;
                            helper.assertTrue(observedNewExposure[0] && exposure == 0,
                                    "Twenty consecutive beta-exposure ticks did not burst: "
                                            + exposure);
                            helper.assertTrue(betaTarget.getHealth() < healthBeforeBurst[0],
                                    "Exposure burst did not deal server-authoritative damage");
                        }).thenExecute(() -> {
                            helper.assertTrue(betaTarget.hasEffect(
                                            MobEffects.GLOWING)
                                            && betaTarget.hasEffect(
                                            MobEffects.WEAKNESS),
                                    "Beta interference did not apply glowing and weakness");
                            var remaining = manager.getView(player).totalMatter();
                            helper.assertTrue(remaining >= 168.9f && remaining <= 170.1f,
                                    "Interference maintenance did not charge 2 MP/second: "
                                            + remaining);
                            DarkmatterRadiation.Server.endChannel(player);
                            helper.assertTrue(!DarkmatterRadiation.Server.isChanneling(player),
                                    "Interference remained active after release");
                            removePlayer(helper, player);
                        }).thenSucceed();
            }
        },
        DARKMATTER_NETWORK_MEMBERS_ARE_PROTECTED(
                "darkmatter_network_members_are_protected", 40) {
            @Override
            void run(GameTestHelper helper) {
                var owner = createPlayer(helper, 4);
                var beetle = new DarkmatterBeetle(
                        org.academy.internal.common.world.entity.EntityTypes.DARKMATTER_BEETLE.get(),
                        helper.getLevel());
                beetle.setOwnerUUID(owner.getUUID());
                helper.assertTrue(MentalControlRuntime.isProtectedTarget(beetle),
                        "A dark-matter summon was accepted by psychological control");
                helper.assertTrue(!DarkmatterTargeting.isAttackableBy(owner, beetle),
                        "A dark-matter summon entered its owner's hostile target pool");

                var health = beetle.getHealth();
                var source = SkillDamageSource.of(owner, Skills.DARKMATTER_CUT.get());
                helper.assertTrue(!beetle.hurtServer(helper.getLevel(), source, 8.0f),
                        "A dark-matter skill damaged a dark-matter network member");
                helper.assertTrue(!SkillDamageUtil.applyDirect(
                                helper.getLevel(), beetle, source, 8.0f),
                        "Direct skill damage bypassed dark-matter network immunity");
                assertClose(helper, health, beetle.getHealth(),
                        "Dark-matter immunity changed summon health");
                removePlayer(helper, owner);
                helper.succeed();
            }
        },
        NON_TEAM_PLAYERS_IGNORE_GLOBAL_PVP_FOR_DARKMATTER(
                "non_team_players_ignore_global_pvp_for_darkmatter", 100) {
            @Override
            void run(GameTestHelper helper) {
                var attacker = createPlayer(helper, 3, false);
                var target = createPlayer(helper, 1, false);
                target.snapTo(helper.absoluteVec(new Vec3(3.5, 2.0, 1.5)));
                helper.assertTrue(!attacker.canHarmPlayer(target),
                        "GameTest attacker did not emulate disabled server PVP");
                helper.assertTrue(DarkmatterTargeting.isEnemyTarget(attacker, target),
                        "A non-team player was omitted from hostile-only dark-matter targeting");

                helper.runAfterDelay(65, () -> {
                    target.setInvulnerable(false);
                    target.invulnerableTime = 0;
                    target.setHealth(20.0f);
                    var source = SkillDamageSource.of(
                            attacker, Skills.DARKMATTER_RADIATION.get());
                    helper.assertTrue(DarkmatterTargeting.hurt(
                                    helper.getLevel(), target, source, 4.0f),
                            "Dark-matter damage was rejected solely because PVP was disabled");
                    helper.assertTrue(target.getHealth() < 20.0f,
                            "PVP-bypassed dark-matter damage did not reach the target");
                    var team = helper.getLevel().getScoreboard()
                            .addPlayerTeam("dm_pvp_team");
                    helper.getLevel().getScoreboard().addPlayerToTeam(
                            attacker.getScoreboardName(), team);
                    helper.getLevel().getScoreboard().addPlayerToTeam(
                            target.getScoreboardName(), team);
                    helper.assertTrue(!DarkmatterTargeting.isEnemyTarget(attacker, target),
                            "A same-team player remained in hostile dark-matter targeting");
                    var alliedHealth = target.getHealth();
                    helper.assertTrue(!DarkmatterTargeting.hurt(
                                    helper.getLevel(), target, source, 4.0f),
                            "PVP bypass ignored same-team protection");
                    assertClose(helper, alliedHealth, target.getHealth(),
                            "Same-team protection changed target health");
                    removePlayer(helper, target);
                    removePlayer(helper, attacker);
                    helper.succeed();
                });
            }
        },
        REPAIR_PRODUCTIVE_PULSES_ARE_OPERATIONAL(
                "repair_productive_pulses_are_operational", 40) {
            @Override
            void run(GameTestHelper helper) {
                var player = createPlayer(helper, 4);
                var system = AbilitySystemServer.getSystem(player);
                system.addPlayerSkill(player, Skills.DARKMATTER_SHAPING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_PHASE_TUNING.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_RADIATION.get().getKeyString());
                system.addPlayerSkill(player, Skills.DARKMATTER_REPAIR.get().getKeyString());
                helper.assertTrue(DarkmatterRepair.Server.toggle(player),
                        "Server rejected the repair toggle request");
                var manager = system.getDarkmatterResourceManager();
                helper.assertTrue(manager.debugSetPools(player, 50.0f, 0.0f, 0.0f, 0.0f),
                        "Failed to initialize repair MP");
                helper.assertTrue(manager.setAlphaPoints(player, 200),
                        "Failed to initialize repair alpha extreme");

                var tool = new ItemStack(
                        org.academy.internal.common.world.item.Items.DARKMATTER_TOOL.get());
                DarkmatterItemUtil.setIntegrity(tool, 0.0f);
                player.getInventory().setItem(0, tool);
                helper.assertTrue(DarkmatterRepair.Server.tryPulse(player),
                        "Alpha repair rejected a zero-integrity tool");
                assertClose(helper, 0.03f, DarkmatterItemUtil.integrity(tool),
                        "Alpha repair restored the wrong integrity fraction");
                helper.assertTrue(player.getAbsorptionAmount() > 0.0f,
                        "Successful alpha repair did not grant structural absorption");
                assertClose(helper, 49.0f, manager.getView(player).totalMatter(),
                        "Alpha repair consumed the wrong MP amount");

                DarkmatterItemUtil.setIntegrity(tool, 1.0f);
                player.setAbsorptionAmount(0.0f);
                helper.assertTrue(!DarkmatterRepair.Server.tryPulse(player),
                        "Repair consumed a pulse for absorption without damaged equipment");
                assertClose(helper, 49.0f, manager.getView(player).totalMatter(),
                        "Idle repair consumed MP");

                helper.assertTrue(manager.setAlphaPoints(player, 0),
                        "Failed to initialize repair beta extreme");
                player.setHealth(10.0f);
                player.addEffect(new MobEffectInstance(
                        MobEffects.POISON, 120));
                helper.assertTrue(DarkmatterRepair.Server.tryPulse(player),
                        "Beta repair rejected real health/status work");
                assertClose(helper, 12.5f, player.getHealth(),
                        "Beta repair healed the wrong amount");
                var poison = player.getEffect(MobEffects.POISON);
                helper.assertTrue(poison != null && poison.getDuration() == 60,
                        "Beta repair shortened poison by the wrong duration");

                helper.assertTrue(system.setPlayerSkillProficiency(
                                player.getUUID(), Skills.DARKMATTER_REPAIR.get(), 3_000.0f),
                        "Failed to initialize repair M3");
                helper.assertTrue(manager.setAlphaPoints(player, 200),
                        "Failed to restore repair alpha extreme");
                var spear = new ItemStack(
                        org.academy.internal.common.world.item.Items.DARKMATTER_SPEAR.get());
                DarkmatterItemUtil.setIntegrity(tool, 0.0f);
                DarkmatterItemUtil.setIntegrity(spear, 0.0f);
                player.getInventory().setItem(1, spear);
                helper.assertTrue(DarkmatterRepair.Server.tryPulse(player),
                        "M3 repair rejected two damaged targets");
                helper.assertTrue(DarkmatterItemUtil.integrity(tool) > 0.0f
                                && DarkmatterItemUtil.integrity(spear) > 0.0f,
                        "M3 did not repair its additional equipment target");

                helper.assertTrue(manager.setAlphaPoints(player, 0),
                        "Failed to restore repair beta extreme");
                player.setHealth(10.0f);
                player.addEffect(new MobEffectInstance(
                        MobEffects.POISON, 200));
                helper.assertTrue(DarkmatterRepair.Server.tryPulse(player),
                        "M3 fourth productive pulse was rejected");
                player.setHealth(10.0f);
                helper.assertTrue(DarkmatterRepair.Server.tryPulse(player),
                        "M3 fifth productive pulse was rejected");
                helper.assertTrue(!player.hasEffect(MobEffects.POISON),
                        "M3 fifth productive pulse did not remove a harmful effect");
                helper.assertTrue(DarkmatterRepair.Server.productivePulses(
                                player.getUUID()) == 5,
                        "Repair productive-pulse ledger diverged from actual work");
                assertClose(helper, 45.6f, manager.getView(player).totalMatter(),
                        "Repair proficiency costs did not use 1/0.8 MP");
                removePlayer(helper, player);
                helper.succeed();
            }
        };

        private static final Codec<Scenario> CODEC = Codec.STRING.comapFlatMap(
                name -> {
                    for (var scenario : values()) {
                        if (scenario.serializedName.equals(name)) return DataResult.success(scenario);
                    }
                    return DataResult.error(() -> "Unknown darkmatter resource scenario: " + name);
                },
                scenario -> scenario.serializedName
        );

        private final String serializedName;
        private final int maxTicks;

        Scenario(String serializedName, int maxTicks) {
            this.serializedName = serializedName;
            this.maxTicks = maxTicks;
        }

        abstract void run(GameTestHelper helper);
    }

    private static final class DarkmatterResourceTestInstance extends GameTestInstance {
        private static final MapCodec<DarkmatterResourceTestInstance> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Scenario.CODEC.fieldOf("scenario").forGetter(test -> test.scenario),
                        TestData.CODEC.forGetter(DarkmatterResourceTestInstance::info)
                ).apply(instance, DarkmatterResourceTestInstance::new));

        private final Scenario scenario;

        private DarkmatterResourceTestInstance(
                Scenario scenario,
                TestData<Holder<TestEnvironmentDefinition<?>>> info
        ) {
            super(info);
            this.scenario = scenario;
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(1, 1, 1, Blocks.STONE);
            scenario.run(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Darkmatter resource "
                    + scenario.name().toLowerCase(Locale.ROOT));
        }
    }
}
