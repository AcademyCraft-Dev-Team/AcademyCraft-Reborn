package org.academy.internal.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.api.common.profiler.ProfileDump;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorCompatProfileRegistry;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorCompatibilityDiagnostics;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorCompatibilityMode;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber
public final class AcademyCraftCommand {
    static final int MIN_COMMAND_ABILITY_LEVEL = 0;
    static final int MAX_COMMAND_ABILITY_LEVEL = 5;
    static final Identifier SELF_SERVICE_CATEGORY =
            AcademyCraft.academy(AbilityCategoryNames.LEVEL0);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("academy")
                .then(Commands.literal("learn_all")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(AcademyCraftCommand::learnAllSkills))
                .then(Commands.literal("learned")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(AcademyCraftCommand::listLearnedSkills))
                .then(Commands.literal("learn")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("skill_name", IdentifierArgument.id())
                                .suggests(AcademyCraftCommand::suggestLearnableSkills)
                                .executes(AcademyCraftCommand::learnSingleSkill)))
                .then(Commands.literal("set_category")
                        .then(Commands.argument("category_name", IdentifierArgument.id())
                                .suggests(AcademyCraftCommand::suggestAbilityCategories)
                                .executes(AcademyCraftCommand::setAbilityCategory)))
                .then(Commands.literal("level")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument(
                                        "level",
                                        abilityLevelArgument()
                                )
                                .executes(AcademyCraftCommand::setAbilityLevel)))
                .then(Commands.literal("set_exp")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("skill_name", IdentifierArgument.id())
                                .suggests(AcademyCraftCommand::suggestLearnedSkills)
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0, 3000))
                                        .executes(AcademyCraftCommand::setSkillExp)))
                )
                .then(propsCommands())
                .then(Commands.literal("debug")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("god")
                                .executes(AcademyCraftCommand::toggleSkillDebugMode))
                        .then(CPDebugCommands.register())
                        .then(DarkmatterDebugCommands.register())
                )
                .then(Commands.literal("dev")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("state", BoolArgumentType.bool())
                                .executes(AcademyCraftCommand::toggleDevMode))
                )
                .then(Commands.literal("ability_exp")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("get")
                                .executes(ctx -> AbilityExpCommands.get(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> AbilityExpCommands.get(ctx, EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0))
                                                .executes(ctx -> AbilityExpCommands.set(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "amount"))))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg())
                                                .executes(ctx -> AbilityExpCommands.add(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "amount"))))))
                        .then(Commands.literal("info")
                                .executes(ctx -> AbilityExpCommands.info(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> AbilityExpCommands.info(ctx, EntityArgument.getPlayer(ctx, "target")))))
                )
                .then(ProfileCommands.register())
                .then(VectorCompatibilityCommands.register())
        );
    }

    static IntegerArgumentType abilityLevelArgument() {
        return IntegerArgumentType.integer(MIN_COMMAND_ABILITY_LEVEL, MAX_COMMAND_ABILITY_LEVEL);
    }

    static LiteralArgumentBuilder<CommandSourceStack> propsCommands() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("props")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset")
                        .executes(AcademyCraftCommand::resetProps));
    }

    private static int resetProps(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        if (!CommandUtils.getSystem(context).resetProps(player)) {
            context.getSource().sendFailure(Component.translatable(
                    "command.academy.props.reset.failed"
            ));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("command.academy.props.reset.success"),
                false
        );
        return 1;
    }

    private static int toggleDevMode(CommandContext<CommandSourceStack> context) {
        var enabled = BoolArgumentType.getBool(context, "state");
        AbilitySystemServer.setDevMode(enabled);
        context.getSource().sendSuccess(
                () -> Component.literal("§e[AC Dev]§r Dev mode: " + (enabled ? "§aON" : "§cOFF")),
                true);
        return 1;
    }

    private static int toggleSkillDebugMode(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var enabled = CommandUtils.getSystem(context).togglePlayerSkillDebugMode(player.getUUID());
        context.getSource().sendSuccess(
                () -> Component.literal("§e[AC Debug]§r Skill god mode: "
                        + (enabled ? "§aON" : "§cOFF")),
                false
        );
        return 1;
    }

    private static int learnAllSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var value = 1;

        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var serverContext = player.level().getServer();
        var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
        var currentCategory = abilitySystemServer.getPlayerAbilityCategory(playerUuid);
        var categoryKey = Registries.ABILITY_CATEGORIES.getKey(currentCategory);
        var categoryName = categoryKey != null ? categoryKey.toString() : "Unknown";

        var availableSkills = LearningHelper.getAvailableSkillsForCategory(currentCategory);
        if (availableSkills.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Current ability category " + categoryName + " has no skills to learn."), false);
            return value;
        }

        for (var skill : availableSkills) {
            abilitySystemServer.addPlayerSkill(player, skill.getKeyString());
        }

        context.getSource().sendSuccess(() -> Component.literal("All skills from ability category " + categoryName + " have been learned."), true);
        return value;
    }

    private static int listLearnedSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var serverContext = player.level().getServer();

        var learnedSkills = serverContext.getAcademyCraftServer()
                .getAbilitySystemServer().getPlayerData(playerUuid).getSkillDataMap();

        if (learnedSkills.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have not learned any skills yet."), false);
        } else {
            var skillsString = String.join(", ", learnedSkills.keySet());
            context.getSource().sendSuccess(() -> Component.literal("Learned skills: " + skillsString), false);
        }
        return 1;
    }

    private static int learnSingleSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var skillIdentifier = IdentifierArgument.getId(context, "skill_name");

        var skillToLearn = Registries.SKILLS.get(skillIdentifier);

        if (skillToLearn.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Skill '" + skillIdentifier + "' not found."));
            return 0;
        }

        var serverContext = player.level().getServer();
        var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();

        var playerCategory = abilitySystemServer.getPlayerAbilityCategory(playerUuid);
        if (!LearningHelper.isSkillAvailableForCategory(playerCategory, skillToLearn.get().value())) {
            var playerCategoryKey = Registries.ABILITY_CATEGORIES.getKey(playerCategory);
            var playerCategoryName = playerCategoryKey != null ? playerCategoryKey.toString() : "None";
            context.getSource().sendFailure(Component.literal("Skill '" + skillIdentifier + "' does not belong to your current ability category (" + playerCategoryName + ")."));
            return 0;
        }

        if (
                serverContext.getAcademyCraftServer().getAbilitySystemServer().getPlayerData(playerUuid)
                        .isSkillLearned(skillIdentifier.toString())
        ) {
            context.getSource().sendFailure(Component.literal("You have already learned skill '" + skillIdentifier + "'."));
            return 0;
        }

        abilitySystemServer.addPlayerSkill(player, skillIdentifier.toString());
        context.getSource().sendSuccess(() -> Component.literal("Successfully learned skill: " + skillIdentifier), true);
        return 1;
    }

    private static int setAbilityCategory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var categoryIdentifier = IdentifierArgument.getId(context, "category_name");

        var categoryToSet = Registries.ABILITY_CATEGORIES.get(categoryIdentifier);

        if (categoryToSet.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Ability category '" + categoryIdentifier + "' not found."));
            return 0;
        }

        var isGameMaster = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)
                .test(context.getSource());
        if (!canSetAbilityCategory(categoryIdentifier, isGameMaster)) {
            context.getSource().sendFailure(Component.literal(
                    "Operator permission is required to set an ability category other than "
                            + SELF_SERVICE_CATEGORY + "."));
            return 0;
        }

        var serverContext = player.level().getServer();
        var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
        abilitySystemServer.replacePlayerAbilityCategory(playerUuid, categoryToSet.get().value());

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Ability category set to: " + categoryIdentifier +
                                ". Previous category skills have been cleared; common skills were preserved."
                ), true
        );
        return 1;
    }

    private static int setAbilityLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var level = IntegerArgumentType.getInteger(context, "level");
        var abilitySystemServer = CommandUtils.getSystem(context);
        var previousLevel = abilitySystemServer.getPlayerLevel(playerUuid);

        abilitySystemServer.setPlayerLevel(playerUuid, level);
        context.getSource().sendSuccess(
                () -> Component.literal("Ability level set to " + level + " (was " + previousLevel + ")."),
                true
        );
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestLearnableSkills(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var player = context.getSource().getPlayerOrException();
            var playerUuid = player.getUUID();
            var serverContext = player.level().getServer();
            var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
            var currentCategory = abilitySystemServer.getPlayerAbilityCategory(playerUuid);
            var learnedSkills = abilitySystemServer.getPlayerData(playerUuid).getSkillDataMap();

            return SharedSuggestionProvider.suggest(
                    LearningHelper.getAvailableSkillsForCategory(currentCategory).stream()
                            .map(skill -> skill.getKey().toString())
                            .filter(skillName -> !learnedSkills.containsKey(skillName)),
                    builder
            );
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    }

    private static int setSkillExp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var skillIdentifier = IdentifierArgument.getId(context, "skill_name");
        var amount = FloatArgumentType.getFloat(context, "amount");

        var serverContext = player.level().getServer();
        var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();

        var playerData = abilitySystemServer.getPlayerData(playerUuid);
        var skillKey = skillIdentifier.toString();

        if (!playerData.isSkillLearned(skillKey)) {
            context.getSource().sendFailure(Component.literal("You do not have skill '" + skillIdentifier + "'."));
            return 0;
        }

        var skill = Registries.SKILLS.get(skillIdentifier).map(reference -> reference.value()).orElse(null);
        if (skill == null || !abilitySystemServer.setPlayerSkillProficiency(playerUuid, skill, amount)) {
            context.getSource().sendFailure(Component.literal("Unable to set proficiency for '" + skillIdentifier + "'."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Set proficiency for " + skillIdentifier + " to " + amount), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestAbilityCategories(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var isGameMaster = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)
                .test(context.getSource());
        return SharedSuggestionProvider.suggest(
                Registries.ABILITY_CATEGORIES.keySet().stream()
                        .filter(identifier -> canSetAbilityCategory(identifier, isGameMaster))
                        .map(Identifier::toString),
                builder
        );
    }

    static boolean canSetAbilityCategory(Identifier category, boolean isGameMaster) {
        return isGameMaster || SELF_SERVICE_CATEGORY.equals(category);
    }

    private static CompletableFuture<Suggestions> suggestLearnedSkills(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var player = context.getSource().getPlayerOrException();
            var playerUuid = player.getUUID();
            var serverContext = player.level().getServer();
            var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
            var learnedSkills = abilitySystemServer.getPlayerData(playerUuid).getSkillDataMap();

            return SharedSuggestionProvider.suggest(
                    learnedSkills.keySet(),
                    builder
            );
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    }

    private static final class VectorCompatibilityCommands {
        private static final String PROFILE_TEMPLATE = """
                {"damage_type":["thirdparty:beam"],"direct_entity":[],"shape":"hitscan","direction":"source_position","range":96.0,"radius":0.25,"piercing":false,"continuous":false,"safe_motion_redirect":false,"visual":"energy","block_policy":"clip_no_break","priority":0}
                """.strip();

        static LiteralArgumentBuilder<CommandSourceStack> register() {
            return Commands.literal("vectorcompat")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("inspect")
                            .executes(VectorCompatibilityCommands::inspect))
                    .then(Commands.literal("mode")
                            .executes(VectorCompatibilityCommands::showMode)
                            .then(Commands.argument("value", StringArgumentType.word())
                                    .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                            Arrays.stream(VectorCompatibilityMode.values())
                                                    .map(value -> value.name().toLowerCase(Locale.ROOT)),
                                            builder
                                    ))
                                    .executes(VectorCompatibilityCommands::setMode)));
        }

        private static int inspect(CommandContext<CommandSourceStack> context) {
            var recent = VectorCompatibilityDiagnostics.recent();
            var builder = new StringBuilder()
                    .append("[Vector Compat] mode=")
                    .append(VectorCompatProfileRegistry.mode())
                    .append(", profiles=")
                    .append(VectorCompatProfileRegistry.profiles().size())
                    .append('\n');
            if (recent.isEmpty()) {
                builder.append("No external linear damage has been inspected yet.\n");
            } else {
                for (var entry : recent.stream().skip(Math.max(0, recent.size() - 8)).toList()) {
                    builder.append(entry.damageType())
                            .append(" direct=").append(entry.directEntityType())
                            .append(" direction=").append(String.format(
                                    Locale.ROOT,
                                    "(%.3f, %.3f, %.3f)",
                                    entry.direction().x,
                                    entry.direction().y,
                                    entry.direction().z
                            ))
                            .append(" confidence=").append(entry.confidence())
                            .append(" tier=").append(entry.tier())
                            .append(" outcome=").append(entry.outcome())
                            .append('\n');
                }
            }
            builder.append("Profile template: ").append(PROFILE_TEMPLATE);
            context.getSource().sendSuccess(() -> Component.literal(builder.toString()), false);
            return recent.size();
        }

        private static int showMode(CommandContext<CommandSourceStack> context) {
            context.getSource().sendSuccess(
                    () -> Component.literal("Vector compatibility mode: " + VectorCompatProfileRegistry.mode()),
                    false
            );
            return 1;
        }

        private static int setMode(CommandContext<CommandSourceStack> context) {
            var value = StringArgumentType.getString(context, "value");
            try {
                var mode = VectorCompatibilityMode.valueOf(value.toUpperCase(Locale.ROOT));
                VectorCompatProfileRegistry.setMode(mode);
                context.getSource().sendSuccess(
                        () -> Component.literal("Vector compatibility mode set to " + mode),
                        true
                );
                return 1;
            } catch (IllegalArgumentException exception) {
                context.getSource().sendFailure(Component.literal("Unknown vector compatibility mode: " + value));
                return 0;
            }
        }
    }

    public static final class CommandUtils {
        private CommandUtils() {
        }

        public static AcademyCraftServer getServer(CommandContext<CommandSourceStack> context) {
            var server = context.getSource().getServer();
            return (server).getAcademyCraftServer();
        }

        public static AbilitySystemServer getSystem(CommandContext<CommandSourceStack> context) {
            return getServer(context).getAbilitySystemServer();
        }

        public static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
            try {
                return context.getSource().getPlayerOrException();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class DarkmatterDebugCommands {
        static LiteralArgumentBuilder<CommandSourceStack> register() {
            return Commands.literal("darkmatter")
                    .then(Commands.literal("info")
                            .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target")))))
                    .then(Commands.literal("level")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("value", abilityLevelArgument())
                                            .executes(ctx -> level(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                    IntegerArgumentType.getInteger(ctx, "value"))))))
                    .then(Commands.literal("phase")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("alpha_points", IntegerArgumentType.integer(0, 250))
                                            .executes(ctx -> phase(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                    IntegerArgumentType.getInteger(ctx, "alpha_points"))))))
                    .then(Commands.literal("mp")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("natural", FloatArgumentType.floatArg(0))
                                            .then(Commands.argument("created", FloatArgumentType.floatArg(0))
                                                    .then(Commands.argument("cp_debt", FloatArgumentType.floatArg(0))
                                                            .then(Commands.argument("reserved", FloatArgumentType.floatArg(0))
                                                                    .executes(ctx -> pools(ctx,
                                                                            EntityArgument.getPlayer(ctx, "target"),
                                                                            FloatArgumentType.getFloat(ctx, "natural"),
                                                                            FloatArgumentType.getFloat(ctx, "created"),
                                                                            FloatArgumentType.getFloat(ctx, "cp_debt"),
                                                                            FloatArgumentType.getFloat(ctx, "reserved")))))))))
                    .then(Commands.literal("gamma")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("active", BoolArgumentType.bool())
                                            .executes(ctx -> gamma(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                    BoolArgumentType.getBool(ctx, "active"))))))
                    .then(Commands.literal("proficiency")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("skill", IdentifierArgument.id())
                                            .suggests(AcademyCraftCommand::suggestLearnedSkills)
                                            .then(Commands.argument("value", FloatArgumentType.floatArg(0, 3000))
                                                    .executes(DarkmatterDebugCommands::proficiency)))));
        }

        private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
            var manager = CommandUtils.getSystem(ctx).getDarkmatterResourceManager();
            var phase = manager.getPhaseSnapshot(player);
            var mp = manager.getView(player);
            var message = Component.literal(String.format(Locale.ROOT,
                    "§e[Darkmatter: %s]§r\nL%d points=%d α=%d(%.2f/%.0f%%) β=%d(%.2f/%.0f%%) γ=%d(%.2f/%.0f%%) active=%s\nMP natural=%.2f created=%.2f total=%.2f / effective=%.2f base=%.2f\nCP debt=%.2f reserved-limit=%.2f",
                    player.getName().getString(), phase.abilityLevel(), phase.totalPoints(),
                    phase.alphaPoints(), phase.alphaPower(), phase.alphaRatio() * 100,
                    phase.betaPoints(), phase.betaPower(), phase.betaRatio() * 100,
                    phase.gammaPoints(), phase.gammaPower(), phase.gammaRatio() * 100,
                    phase.gammaActive(), mp.naturalMatter(), mp.createdMatter(), mp.totalMatter(),
                    mp.effectiveCapacity(), mp.baseCapacity(), mp.createdCpDebt(), mp.reservedMatter()));
            ctx.getSource().sendSuccess(() -> message, false);
            return 1;
        }

        private static int level(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int value) {
            CommandUtils.getSystem(ctx).setPlayerLevel(player.getUUID(), value);
            CommandUtils.getSystem(ctx).getDarkmatterResourceManager().requestSync(player);
            return info(ctx, player);
        }

        private static int phase(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int points) {
            if (!CommandUtils.getSystem(ctx).getDarkmatterResourceManager().setAlphaPoints(player, points)) {
                ctx.getSource().sendFailure(Component.literal("Unable to set darkmatter phase points."));
                return 0;
            }
            return info(ctx, player);
        }

        private static int pools(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                 float natural, float created, float debt, float reserved) {
            if (!CommandUtils.getSystem(ctx).getDarkmatterResourceManager()
                    .debugSetPools(player, natural, created, debt, reserved)) {
                ctx.getSource().sendFailure(Component.literal(
                        "Unable to apply pools; check category, learned generation, CP and base capacity."));
                return 0;
            }
            return info(ctx, player);
        }

        private static int gamma(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                 boolean active) {
            if (!DarkmatterSixWings.Server.debugSetActive(player, active)) {
                ctx.getSource().sendFailure(Component.literal("Unable to set γ state."));
                return 0;
            }
            return info(ctx, player);
        }

        private static int proficiency(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
            var player = EntityArgument.getPlayer(ctx, "target");
            var id = IdentifierArgument.getId(ctx, "skill");
            var value = FloatArgumentType.getFloat(ctx, "value");
            var skill = Registries.SKILLS.get(id).map(reference -> reference.value()).orElse(null);
            if (skill == null || skill.getCategory() != AbilityCategories.DARKMATTER.get()
                    || !CommandUtils.getSystem(ctx).setPlayerSkillProficiency(player.getUUID(), skill, value)) {
                ctx.getSource().sendFailure(Component.literal("Unable to set darkmatter proficiency for " + id));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Set " + id + " proficiency to " + value), false);
            return 1;
        }
    }

    private static class CPDebugCommands {

        static LiteralArgumentBuilder<CommandSourceStack> register() {
            return Commands.literal("cp")
                    .then(Commands.literal("info")
                            .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException(), false))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target"), false))
                                    .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                            .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target"), BoolArgumentType.getBool(ctx, "broadcast"))))))

                    .then(Commands.literal("get")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.literal("value").executes(ctx -> get(ctx, "value")))
                                    .then(Commands.literal("max").executes(ctx -> get(ctx, "max")))
                                    .then(Commands.literal("curr_sp").executes(ctx -> get(ctx, "curr_sp")))
                                    .then(Commands.literal("max_sp").executes(ctx -> get(ctx, "max_sp")))
                                    .then(Commands.literal("level").executes(ctx -> get(ctx, "level")))
                                    .then(Commands.literal("timer").executes(ctx -> get(ctx, "timer")))
                                    .then(Commands.literal("status").executes(ctx -> get(ctx, "status")))))

                    .then(Commands.literal("set")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("value", FloatArgumentType.floatArg())
                                            .executes(ctx -> set(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "value"), false))
                                            .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                                    .executes(ctx -> set(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "value"), BoolArgumentType.getBool(ctx, "broadcast")))))))

                    .then(Commands.literal("set_max")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                            .executes(ctx -> setMax(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "value"), false))
                                            .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                                    .executes(ctx -> setMax(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "value"), BoolArgumentType.getBool(ctx, "broadcast")))))))

                    .then(Commands.literal("set_status")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("status", StringArgumentType.word())
                                            .suggests(CPDebugCommands::suggestStatus)
                                            .executes(ctx -> setStatus(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "status"), 0, false))
                                            .then(Commands.argument("timer", IntegerArgumentType.integer(0))
                                                    .executes(ctx -> setStatus(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "status"), IntegerArgumentType.getInteger(ctx, "timer"), false))
                                                    .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                                            .executes(ctx -> setStatus(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "status"), IntegerArgumentType.getInteger(ctx, "timer"), BoolArgumentType.getBool(ctx, "broadcast"))))))));
        }

        private static int info(CommandContext<CommandSourceStack> context, ServerPlayer player, boolean broadcast) {
            var uuid = player.getUUID();
            var name = player.getName().getString();
            var system = CommandUtils.getSystem(context);

            var current = system.getPlayerAvailableCP(uuid);
            var max = system.getPlayerMaxCP(uuid);
            var level = system.getPlayerLevel(uuid);
            var currSP = system.getPlayerCurrSP(uuid);
            var maxSP = system.getPlayerMaxSP(uuid);
            var status = system.getPlayerStatus(uuid);
            var timer = system.getPlayerStateTimer(uuid);

            Component message = Component.literal(String.format(
                    """
                            §e[CP Debug: %s]§r
                            §7UUID: %s§r
                            §fLevel: §d%d§r
                            §fCP: §b%.2f§r / §3%.2f§r
                            §fSP: §e%d§r / §6%d§r
                            §fStatus: §a%s§r (Timer: §6%d§r)""",
                    name, uuid, level, current, max, currSP, maxSP, status, timer
            ));
            sendFeedback(context, message, broadcast);
            return 1;
        }

        private static int get(CommandContext<CommandSourceStack> context, String type) throws CommandSyntaxException {
            var target = EntityArgument.getPlayer(context, "target");
            var uuid = target.getUUID();
            var system = CommandUtils.getSystem(context);

            return switch (type) {
                case "value" -> (int) system.getPlayerAvailableCP(uuid);
                case "max" -> (int) system.getPlayerMaxCP(uuid);
                case "curr_sp" -> system.getPlayerCurrSP(uuid);
                case "max_sp" -> system.getPlayerMaxSP(uuid);
                case "level" -> system.getPlayerLevel(uuid);
                case "timer" -> system.getPlayerStateTimer(uuid);
                case "status" -> system.getPlayerStatus(uuid).ordinal();
                default -> 0;
            };
        }

        private static int set(CommandContext<CommandSourceStack> context, ServerPlayer player, float value, boolean broadcast) {
            var uuid = player.getUUID();
            var serverContext = player.level().getServer();
            var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
            abilitySystemServer.setPlayerAvailableCP(uuid, value);

            Component message = Component.literal(String.format("§e[AC Debug]§r Set Available CP for %s to: %.2f", player.getName().getString(), value));
            sendFeedback(context, message, broadcast);
            return 1;
        }

        private static int setMax(CommandContext<CommandSourceStack> context, ServerPlayer player, float value, boolean broadcast) {
            var uuid = player.getUUID();
            var serverContext = player.level().getServer();
            var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
            abilitySystemServer.setPlayerMaxCP(uuid, value);

            Component message = Component.literal(String.format("§e[AC Debug]§r Set Max CP for %s to: %.2f", player.getName().getString(), value));
            sendFeedback(context, message, broadcast);
            return 1;
        }

        private static int setStatus(CommandContext<CommandSourceStack> context, ServerPlayer player, String statusName, int timer, boolean broadcast) {
            var uuid = player.getUUID();
            try {
                var status = AbilityData.Status.valueOf(statusName.toUpperCase());
                var serverContext = player.level().getServer();
                var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
                abilitySystemServer.setPlayerStatus(uuid, status);
                abilitySystemServer.setPlayerStateTimer(uuid, timer);

                Component message = Component.literal(String.format("§e[AC Debug]§r Set Status for %s to: %s, Timer: %d", player.getName().getString(), status, timer));
                sendFeedback(context, message, broadcast);
            } catch (IllegalArgumentException e) {
                context.getSource().sendFailure(Component.literal("Invalid status: " + statusName));
                return 0;
            }
            return 1;
        }

        private static void sendFeedback(CommandContext<CommandSourceStack> context, Component message, boolean broadcast) {
            if (broadcast) {
                context.getSource().getServer().getPlayerList().broadcastSystemMessage(message, false);
            } else {
                context.getSource().sendSuccess(() -> message, true);
            }
        }

        private static CompletableFuture<Suggestions> suggestStatus(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            return SharedSuggestionProvider.suggest(
                    Arrays.stream(AbilityData.Status.values()).map(Enum::name),
                    builder
            );
        }
    }

    private static final class ProfileCommands {
        static LiteralArgumentBuilder<CommandSourceStack> register() {
            return Commands.literal("profile")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("start")
                            .executes(ctx -> start(ctx, 1))
                            .then(Commands.argument("interval_ms", IntegerArgumentType.integer(1, 1000))
                                    .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "interval_ms")))))
                    .then(Commands.literal("stop").executes(ctx -> stop(ctx)))
                    .then(Commands.literal("reset").executes(ctx -> reset(ctx)))
                    .then(Commands.literal("status").executes(ctx -> status(ctx)))
                    .then(Commands.literal("zones")
                            .executes(ctx -> zones(ctx, null, 8))
                            .then(Commands.argument("thread", StringArgumentType.word())
                                    .executes(ctx -> zones(ctx, StringArgumentType.getString(ctx, "thread"), 8))
                                    .then(Commands.argument("depth", IntegerArgumentType.integer(1, 20))
                                            .executes(ctx -> zones(
                                                    ctx,
                                                    StringArgumentType.getString(ctx, "thread"),
                                                    IntegerArgumentType.getInteger(ctx, "depth"))))))
                    .then(Commands.literal("sampler")
                            .executes(ctx -> sampler(ctx, 30))
                            .then(Commands.argument("top", IntegerArgumentType.integer(1, 200))
                                    .executes(ctx -> sampler(ctx, IntegerArgumentType.getInteger(ctx, "top")))))
                    .then(Commands.literal("snapshot").executes(ctx -> snapshot(ctx)))
                    .then(Commands.literal("dump").executes(ctx -> dump(ctx)));
        }

        private static int start(CommandContext<CommandSourceStack> ctx, int intervalMs) {
            var server = ctx.getSource().getServer();
            AcademyProfiler.registerThread(server.getRunningThread());
            AcademyProfiler.startSampling(intervalMs * 1000L);
            AcademyProfiler.startZoneCapture();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e[AC Profiler]§r Sampling started (interval " + intervalMs + " ms) + zone capture on."
            ), true);
            return 1;
        }

        private static int stop(CommandContext<CommandSourceStack> ctx) {
            AcademyProfiler.stopSampling();
            AcademyProfiler.stopZoneCapture();
            ctx.getSource().sendSuccess(() -> Component.literal("§e[AC Profiler]§r Stopped."), true);
            return 1;
        }

        private static int reset(CommandContext<CommandSourceStack> ctx) {
            AcademyProfiler.resetSampling();
            AcademyProfiler.resetZones();
            ctx.getSource().sendSuccess(() -> Component.literal("§e[AC Profiler]§r Data cleared."), true);
            return 1;
        }

        private static int status(CommandContext<CommandSourceStack> ctx) {
            ctx.getSource().sendSuccess(() -> Component.literal(ProfileDump.status(AcademyProfiler.snapshot())), true);
            return 1;
        }

        private static int zones(CommandContext<CommandSourceStack> ctx, String thread, int depth) {
            var text = ProfileDump.zonesText(AcademyProfiler.snapshot(), thread, depth);
            ctx.getSource().sendSuccess(() -> Component.literal(text), true);
            return 1;
        }

        private static int sampler(CommandContext<CommandSourceStack> ctx, int top) {
            var text = ProfileDump.samplerText(AcademyProfiler.snapshot(), top);
            ctx.getSource().sendSuccess(() -> Component.literal(text), true);
            return 1;
        }

        private static int snapshot(CommandContext<CommandSourceStack> ctx) {
            var snap = AcademyProfiler.snapshot();
            var sb = new StringBuilder();
            sb.append("§e[AC Profiler Snapshot]§r\n");
            var sampler = snap.getSampler();
            if (sampler != null) {
                sb.append("Sampler: ").append(sampler.totalSamples()).append(" samples, ")
                        .append(String.format("%.1f s", sampler.durationSeconds())).append("\n");
            } else {
                sb.append("Sampler: off (use /academy profile start)\n");
            }
            var zones = snap.getZones();
            if (!zones.isEmpty()) {
                for (var entry : zones.entrySet()) {
                    sb.append("-- ").append(entry.getKey()).append(" --\n");
                    for (var slice : entry.getValue().topSlices(10, true)) {
                        sb.append("  ").append(slice.name())
                                .append(" - ").append(String.format("%.2f%%", slice.getGlobalPercent()))
                                .append(" - ").append(String.format("%.2f ms", slice.getTotalMs()))
                                .append(" - ").append(slice.count()).append(" calls\n");
                    }
                }
            }
            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), true);
            return 1;
        }

        private static int dump(CommandContext<CommandSourceStack> ctx) {
            var snap = AcademyProfiler.snapshot();
            var logsDir = ctx.getSource().getServer().getServerDirectory().resolve("logs").toFile();
            logsDir.mkdirs();
            var file = new File(logsDir, "academy-profile-" + ProfileDump.timestamp() + ".txt");

            var sb = new StringBuilder();
            sb.append("AcademyCraft Performance Profile\n");
            sb.append("Time: ").append(ProfileDump.timestamp()).append("\n\n");
            var sampler = snap.getSampler();
            if (sampler != null) {
                sb.append(ProfileDump.dumpSampler(sampler, 30)).append("\n\n");
            }
            for (var entry : snap.getZones().entrySet()) {
                sb.append(ProfileDump.dumpZones(entry.getValue(), 8)).append("\n\n");
            }
            try {
                Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e[AC Profiler]§r Dumped to " + file.getAbsolutePath()
                ), true);
            } catch (IOException e) {
                ctx.getSource().sendFailure(Component.literal(
                        "§e[AC Profiler]§r Failed to write: " + e.getMessage()
                ));
            }
            return 1;
        }
    }

    private static final class AbilityExpCommands {
        private static int get(CommandContext<CommandSourceStack> context, ServerPlayer player) {
            var uuid = player.getUUID();
            var system = CommandUtils.getSystem(context);
            var exp = system.getPlayerAbilityExp(uuid);
            Component message = Component.literal(
                    String.format("§e[AC]§r Ability exp for %s: §b%.2f", player.getName().getString(), exp));
            context.getSource().sendSuccess(() -> message, true);
            return 1;
        }

        private static int set(CommandContext<CommandSourceStack> context, ServerPlayer player, float amount) {
            var uuid = player.getUUID();
            var system = CommandUtils.getSystem(context);
            system.setPlayerAbilityExp(uuid, amount);
            Component message = Component.literal(
                    String.format("§e[AC]§r Set ability exp for %s to: §b%.2f", player.getName().getString(), amount));
            context.getSource().sendSuccess(() -> message, true);
            return 1;
        }

        private static int add(CommandContext<CommandSourceStack> context, ServerPlayer player, float amount) {
            var uuid = player.getUUID();
            var system = CommandUtils.getSystem(context);
            system.addPlayerAbilityExp(uuid, amount);
            var total = system.getPlayerAbilityExp(uuid);
            Component message = Component.literal(
                    String.format("§e[AC]§r Added §b%.2f§r ability exp for %s. Total: §b%.2f", amount, player.getName().getString(), total));
            context.getSource().sendSuccess(() -> message, true);
            return 1;
        }

        private static int info(CommandContext<CommandSourceStack> context, ServerPlayer player) {
            var uuid = player.getUUID();
            var system = CommandUtils.getSystem(context);
            var exp = system.getPlayerAbilityExp(uuid);
            var canLevelUp = system.canPlayerLevelUp(uuid);
            var level = system.getPlayerLevel(uuid);
            Component message = Component.literal(String.format(
                    """
                            §e[AC]§r Ability exp info for %s:
                            §fLevel: §d%d§r
                            §fExp: §b%.2f§r
                            §fCan Level Up: %s§r""",
                    player.getName().getString(), level, exp,
                    canLevelUp ? "§aYES" : "§cNO"));
            context.getSource().sendSuccess(() -> message, true);
            return 1;
        }
    }
}
