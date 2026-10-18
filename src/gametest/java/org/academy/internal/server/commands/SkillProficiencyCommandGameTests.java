package org.academy.internal.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.MapCodec;
import io.netty.channel.embedded.EmbeddedChannel;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.skilldata.SkillData;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Exercises the registered command against real player skill data and proficiency callbacks. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SkillProficiencyCommandGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("skill_proficiency_command_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("set_exp_max"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 40, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void verify(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var profile = new GameProfile(UUID.randomUUID(), "skill-exp-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        try {
            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            var data = system.getPlayerData(uuid);
            var dispatcher = server.getCommands().getDispatcher();
            var source = server.createCommandSourceStack().withEntity(player)
                    .withPermission(PermissionSet.ALL_PERMISSIONS);
            helper.assertTrue(data.getSkillDataMap().isEmpty(), "New test player must have no learned skills");
            helper.assertTrue(dispatcher.execute("academy set_exp max", source) == 0,
                    "Empty learned skill list must report zero affected skills");
            helper.assertTrue(data.getSkillDataMap().isEmpty(), "Max must not learn skills for an empty player");

            system.setPlayerAbilityCategory(uuid, AbilityCategories.ELECTROMASTER.get());
            system.setPlayerLevel(uuid, 5);
            var skills = List.of(Skills.ARC_GENERATE.get(), Skills.ELECTRICAL_CONTACT.get(), Skills.OUTPUT_CONTROL.get());
            for (var skill : skills) {
                system.addPlayerSkill(player, skill.getKeyString());
            }
            var arc = Skills.ARC_GENERATE.get();
            helper.assertTrue(dispatcher.execute("academy set_exp " + arc.getKeyString() + " 1200", source) == 1,
                    "Existing single-skill command must still execute");
            helper.assertTrue(data.getSkillDataMap().get(arc.getKeyString()).getProficiency() == 1200,
                    "Single-skill command must retain the requested proficiency");
            system.toggleSkill(uuid, Skills.ELECTRICAL_CONTACT.get().getKeyString());
            Map<String, Boolean> enabledStates = data.getSkillDataMap().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().isEnabled()));
            var oldAbilityLevel = system.getPlayerLevel(uuid);

            helper.assertTrue(dispatcher.execute("academy set_exp max", source) == skills.size(),
                    "Max must affect every learned category and common skill");
            helper.assertTrue(data.getSkillDataMap().keySet().equals(enabledStates.keySet()),
                    "Max must preserve exactly the learned skill set");
            for (var skill : skills) {
                var skillData = data.getSkillDataMap().get(skill.getKeyString());
                helper.assertTrue(skillData.getProficiency() == SkillData.MAX_PROFICIENCY,
                        skill.getKeyString() + " must reach 3000 proficiency, even when disabled");
                helper.assertTrue(skillData.isEnabled() == enabledStates.get(skill.getKeyString()),
                        "Max must preserve each skill's enabled state");
                helper.assertTrue(system.getPlayerSkillLevel(uuid, skill.getKeyString()) == skill.getMaxSkillLevel(),
                        "Max proficiency must update the effective skill level");
            }
            helper.assertTrue(system.getPlayerLevel(uuid) == oldAbilityLevel,
                    "Skill proficiency must not change the ability level");
            helper.assertTrue(dispatcher.execute("academy set_exp max", source) == skills.size(),
                    "Repeating max on already-maximized skills must remain valid");
            helper.succeed();
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Skill proficiency command failed to parse or execute", exception);
        } finally {
            server.getPlayerList().remove(player);
        }
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            verify(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Skill proficiency command regression");
        }
    }
}
