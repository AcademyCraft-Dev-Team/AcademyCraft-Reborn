package org.academy.internal.server.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.time.TemporalScale;
import org.academy.api.server.time.TemporalScope;
import org.academy.internal.server.time.TemporalRuntime;
import org.academy.internal.server.time.TemporalTickDiagnostics;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** Operator-only control and diagnostics for temporal tick channels. */
final class TemporalTickDebugCommands {
    private static final DynamicCommandExceptionType UNKNOWN_DIMENSION =
            new DynamicCommandExceptionType(value -> Component.literal(
                    "Unknown or unloaded dimension: " + value
            ));
    private static final DynamicCommandExceptionType INVALID_CHANNEL =
            new DynamicCommandExceptionType(value -> Component.literal(
                    "Unknown temporal channel set: " + value
            ));
    private static final DynamicCommandExceptionType INVALID_SOURCE =
            new DynamicCommandExceptionType(value -> Component.literal(
                    "Unknown pause source set: " + value
            ));
    private static final DynamicCommandExceptionType INVALID_CONTROL_ID =
            new DynamicCommandExceptionType(value -> Component.literal(
                    "Invalid debug control UUID: " + value
            ));

    private TemporalTickDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        var root = LiteralArgumentBuilder.<CommandSourceStack>literal("tick")
                .executes(context -> inspectSource(context, null));

        root.then(RequiredArgumentBuilder
                .<CommandSourceStack, EntitySelector>argument(
                        "targets",
                        EntityArgument.entities()
                )
                .executes(TemporalTickDebugCommands::inspectTargets));

        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("inspect")
                .executes(context -> inspectSource(context, null))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("entities")
                        .then(RequiredArgumentBuilder
                                .<CommandSourceStack, EntitySelector>argument(
                                        "targets",
                                        EntityArgument.entities()
                                )
                                .executes(TemporalTickDebugCommands::inspectTargets)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("position")
                        .then(RequiredArgumentBuilder
                                .<CommandSourceStack, Identifier>argument(
                                        "dimension",
                                        IdentifierArgument.id()
                                )
                                .then(RequiredArgumentBuilder
                                        .<CommandSourceStack, Coordinates>argument(
                                                "position",
                                                Vec3Argument.vec3()
                                        )
                                        .executes(TemporalTickDebugCommands::inspectPosition)))));

        root.then(fieldCommands());
        root.then(immunityCommands());
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                .executes(TemporalTickDebugCommands::listControls));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                .executes(TemporalTickDebugCommands::clearControls));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("help")
                .executes(TemporalTickDebugCommands::help));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> fieldCommands() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("field")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
                        .then(addSaveField())
                        .then(addDimensionField())
                        .then(addSphereField())
                        .then(addEntityField()))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                        .executes(TemporalTickDebugCommands::listFields))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                        .then(RequiredArgumentBuilder
                                .<CommandSourceStack, String>argument(
                                        "control_id",
                                        StringArgumentType.word()
                                )
                                .executes(TemporalTickDebugCommands::removeField)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                        .executes(TemporalTickDebugCommands::clearFields));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> immunityCommands() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("immunity")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
                        .then(RequiredArgumentBuilder
                                .<CommandSourceStack, EntitySelector>argument(
                                        "targets",
                                        EntityArgument.entities()
                                )
                                .executes(context -> addImmunity(
                                        context,
                                        allPauseSources()
                                ))
                                .then(RequiredArgumentBuilder
                                        .<CommandSourceStack, String>argument(
                                                "sources",
                                                StringArgumentType.word()
                                        )
                                        .suggests(TemporalTickDebugCommands::suggestSources)
                                        .executes(context -> addImmunity(
                                                context,
                                                parseSources(StringArgumentType.getString(
                                                        context,
                                                        "sources"
                                                ))
                                        )))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                        .executes(TemporalTickDebugCommands::listImmunities))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                        .then(RequiredArgumentBuilder
                                .<CommandSourceStack, String>argument(
                                        "control_id",
                                        StringArgumentType.word()
                                )
                                .executes(TemporalTickDebugCommands::removeImmunity)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                        .executes(TemporalTickDebugCommands::clearImmunities));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addSaveField() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("save")
                .then(scaleAndChannels(context -> TemporalScope.save()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addDimensionField() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("dimension")
                .then(RequiredArgumentBuilder
                        .<CommandSourceStack, Identifier>argument(
                                "dimension",
                                IdentifierArgument.id()
                        )
                        .then(scaleAndChannels(
                                TemporalTickDebugCommands::dimensionScope
                        )));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addSphereField() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("sphere")
                .then(RequiredArgumentBuilder
                        .<CommandSourceStack, Identifier>argument(
                                "dimension",
                                IdentifierArgument.id()
                        )
                        .then(RequiredArgumentBuilder
                                .<CommandSourceStack, Coordinates>argument(
                                        "center",
                                        Vec3Argument.vec3()
                                )
                                .then(RequiredArgumentBuilder
                                        .<CommandSourceStack, Double>argument(
                                                "radius",
                                                DoubleArgumentType.doubleArg(0.001D)
                                        )
                                        .then(scaleAndChannels(context ->
                                                TemporalScope.sphere(
                                                        dimensionKey(context),
                                                        Vec3Argument.getVec3(
                                                                context,
                                                                "center"
                                                        ),
                                                        DoubleArgumentType.getDouble(
                                                                context,
                                                                "radius"
                                                        )
                                                ))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addEntityField() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("entities")
                .then(RequiredArgumentBuilder
                        .<CommandSourceStack, EntitySelector>argument(
                                "targets",
                                EntityArgument.entities()
                        )
                        .then(RequiredArgumentBuilder
                                .<CommandSourceStack, Double>argument(
                                        "scale",
                                        DoubleArgumentType.doubleArg(
                                                0.0D,
                                                TemporalScale.DEFAULT_MAX_SCALE
                                        )
                                )
                                .executes(context -> addEntityField(
                                        context,
                                        TemporalPauseSource.ACADEMY_PAUSE
                                ))
                                .then(RequiredArgumentBuilder
                                        .<CommandSourceStack, String>argument(
                                                "pause_source",
                                                StringArgumentType.word()
                                        )
                                        .suggests(TemporalTickDebugCommands::suggestSources)
                                        .executes(context -> addEntityField(
                                                context,
                                                parseSingleSource(
                                                        StringArgumentType.getString(
                                                                context,
                                                                "pause_source"
                                                        )
                                                )
                                        )))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double>
    scaleAndChannels(ScopeResolver scopeResolver) {
        return RequiredArgumentBuilder
                .<CommandSourceStack, Double>argument(
                        "scale",
                        DoubleArgumentType.doubleArg(
                                0.0D,
                                TemporalScale.DEFAULT_MAX_SCALE
                        )
                )
                .then(RequiredArgumentBuilder
                        .<CommandSourceStack, String>argument(
                                "channels",
                                StringArgumentType.word()
                        )
                        .suggests(TemporalTickDebugCommands::suggestChannels)
                        .executes(context -> addField(
                                context,
                                scopeResolver.resolve(context),
                                TemporalPauseSource.ACADEMY_PAUSE
                        ))
                        .then(RequiredArgumentBuilder
                                .<CommandSourceStack, String>argument(
                                        "pause_source",
                                        StringArgumentType.word()
                                )
                                .suggests(TemporalTickDebugCommands::suggestSources)
                                .executes(context -> addField(
                                        context,
                                        scopeResolver.resolve(context),
                                        parseSingleSource(StringArgumentType.getString(
                                                context,
                                                "pause_source"
                                        ))
                                ))));
    }

    private static int inspectSource(
            CommandContext<CommandSourceStack> context,
            Entity target
    ) {
        var source = context.getSource();
        var level = target != null && target.level() instanceof ServerLevel targetLevel
                ? targetLevel : source.getLevel();
        var position = target == null
                ? BlockPos.containing(source.getPosition())
                : target.blockPosition();
        sendSnapshot(source, runtime(source).debugSnapshot(level, position, target));
        return 1;
    }

    private static int inspectTargets(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        var targets = EntityArgument.getEntities(context, "targets");
        for (var target : targets) inspectSource(context, target);
        return targets.size();
    }

    private static int inspectPosition(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        var level = dimensionLevel(context);
        var position = BlockPos.containing(Vec3Argument.getVec3(
                context,
                "position"
        ));
        sendSnapshot(
                context.getSource(),
                runtime(context.getSource()).debugSnapshot(level, position, null)
        );
        return 1;
    }

    private static int addField(
            CommandContext<CommandSourceStack> context,
            TemporalScope scope,
            TemporalPauseSource pauseSource
    ) throws CommandSyntaxException {
        var scale = DoubleArgumentType.getDouble(context, "scale");
        var channelExpression = StringArgumentType.getString(
                context,
                "channels"
        );
        var channels = scope instanceof TemporalScope.Sphere
                && channelExpression.equalsIgnoreCase("integrated")
                ? spatialChannels()
                : parseChannels(channelExpression);
        var field = new TemporalField(scope, channels, scale, pauseSource);
        var id = runtime(context.getSource()).addDebugField(field);
        context.getSource().sendSuccess(
                () -> Component.literal("Added temporal field " + id
                        + ": " + describeField(field)),
                false
        );
        return 1;
    }

    private static int addEntityField(
            CommandContext<CommandSourceStack> context,
            TemporalPauseSource pauseSource
    ) throws CommandSyntaxException {
        var targets = EntityArgument.getEntities(context, "targets");
        var scope = TemporalScope.entities(targets.stream()
                .map(Entity::getUUID)
                .toList());
        var field = new TemporalField(
                scope,
                Set.of(TemporalChannel.ENTITY),
                DoubleArgumentType.getDouble(context, "scale"),
                pauseSource
        );
        var id = runtime(context.getSource()).addDebugField(field);
        context.getSource().sendSuccess(
                () -> Component.literal("Added entity temporal field " + id
                        + " for " + targets.size() + " target(s): "
                        + describeField(field)),
                false
        );
        return targets.size();
    }

    private static int removeField(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        var id = controlId(context);
        if (!runtime(context.getSource()).removeDebugField(id)) {
            context.getSource().sendFailure(Component.literal(
                    "No debugger-owned temporal field exists with ID " + id
            ));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.literal("Removed temporal field " + id),
                false
        );
        return 1;
    }

    private static int clearFields(CommandContext<CommandSourceStack> context) {
        var count = runtime(context.getSource()).clearDebugFields();
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared " + count
                        + " debugger-owned temporal field(s)."),
                false
        );
        return count;
    }

    private static int addImmunity(
            CommandContext<CommandSourceStack> context,
            Set<TemporalPauseSource> sources
    ) throws CommandSyntaxException {
        var targets = EntityArgument.getEntities(context, "targets");
        var id = runtime(context.getSource()).addDebugImmunity(targets, sources);
        context.getSource().sendSuccess(
                () -> Component.literal("Added immunity control " + id
                        + " to " + targets.size() + " target(s): " + sources),
                false
        );
        return targets.size();
    }

    private static int removeImmunity(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        var id = controlId(context);
        if (!runtime(context.getSource()).removeDebugImmunity(id)) {
            context.getSource().sendFailure(Component.literal(
                    "No debugger-owned immunity exists with ID " + id
            ));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.literal("Removed immunity control " + id),
                false
        );
        return 1;
    }

    private static int clearImmunities(
            CommandContext<CommandSourceStack> context
    ) {
        var count = runtime(context.getSource()).clearDebugImmunities();
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared " + count
                        + " debugger-owned immunity group(s)."),
                false
        );
        return count;
    }

    private static int clearControls(
            CommandContext<CommandSourceStack> context
    ) {
        var count = runtime(context.getSource()).clearDebugControls();
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared " + count
                        + " tick debug control(s)."),
                false
        );
        return count;
    }

    private static int listControls(
            CommandContext<CommandSourceStack> context
    ) {
        return list(context, true, true);
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        var message = """
                §e[AC Tick Debug Commands]§r
                inspect entities <selector>
                inspect position <dimension> <x y z>
                field add entities <selector> <scale> [pause_source]
                field add sphere <dimension> <x y z> <radius> <scale> <channels> [pause_source]
                field add dimension <dimension> <scale> <channels> [pause_source]
                field add save <scale> <channels> [pause_source]
                field list | field remove <uuid> | field clear
                immunity add <selector> [sources] | immunity list | immunity remove <uuid> | immunity clear
                list | clear
                channels: integrated, spatial, world, all, or comma-separated channel names
                scale: 0=pause, 0..1=slow, 1=normal, 1..8=accelerate
                """.stripTrailing();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int listFields(
            CommandContext<CommandSourceStack> context
    ) {
        return list(context, true, false);
    }

    private static int listImmunities(
            CommandContext<CommandSourceStack> context
    ) {
        return list(context, false, true);
    }

    private static int list(
            CommandContext<CommandSourceStack> context,
            boolean includeFields,
            boolean includeImmunities
    ) {
        var source = context.getSource();
        var snapshot = runtime(source).debugSnapshot(
                source.getLevel(),
                BlockPos.containing(source.getPosition()),
                source.getEntity()
        );
        var text = new StringBuilder("§e[AC Tick Controls]§r\n");
        var count = 0;
        if (includeFields) {
            text.append("Fields:\n");
            for (var field : snapshot.fields()) {
                if (!field.debugControlled()) continue;
                count++;
                appendField(text, field);
            }
        }
        if (includeImmunities) {
            text.append("Immunity groups:\n");
            for (var immunity : snapshot.debugImmunities()) {
                count++;
                text.append("  ").append(immunity.id())
                        .append(" targets=").append(immunity.entityNames())
                        .append(" ids=").append(immunity.entityIds())
                        .append(" sources=").append(immunity.sources())
                        .append('\n');
            }
        }
        if (count == 0) text.append("  none\n");
        source.sendSuccess(() -> Component.literal(text.toString().stripTrailing()), false);
        return count;
    }

    private static void sendSnapshot(
            CommandSourceStack source,
            TemporalTickDiagnostics snapshot
    ) {
        source.sendSuccess(() -> Component.literal(format(snapshot)), false);
    }

    static String format(TemporalTickDiagnostics snapshot) {
        var vanilla = snapshot.vanilla();
        var text = new StringBuilder()
                .append("§e[AC Tick Debug]§r ")
                .append(snapshot.dimension())
                .append(" @ ")
                .append(formatPosition(snapshot.position()))
                .append(" game=").append(snapshot.gameTime())
                .append(" clock=").append(snapshot.clockTime())
                .append(" heartbeat=").append(snapshot.heartbeat())
                .append(" runtime=")
                .append(snapshot.runtimeStopped() ? "§cstopped§r" : "§arunning§r")
                .append('\n')
                .append("Vanilla: rate=")
                .append(String.format(Locale.ROOT, "%.2f", vanilla.tickRate()))
                .append(" frozen=").append(vanilla.frozen())
                .append(" normal=").append(vanilla.runsNormally())
                .append(" stepping=").append(vanilla.stepping())
                .append(" stepLeft=").append(vanilla.frozenTicksToRun())
                .append(" sprinting=").append(vanilla.sprinting())
                .append('\n');

        if (snapshot.entity() != null) {
            var entity = snapshot.entity();
            text.append("Entity: ").append(entity.name())
                    .append(" [").append(entity.id()).append(']')
                    .append(" immune=").append(entity.timeStopImmune())
                    .append(" vanillaFrozenEffective=")
                    .append(entity.frozenByEffectiveVanillaManager())
                    .append(" sources=").append(entity.immuneSources())
                    .append('\n');
        }

        text.append("Channels (level/local");
        if (snapshot.entity() != null) text.append("/entity");
        text.append("):\n");
        for (var channel : snapshot.channels()) {
            text.append("  ")
                    .append(channel.integrated() ? "§aactive§r " : "§8planned§r ")
                    .append(channel.channel()).append(' ')
                    .append(formatScale(channel.levelScale())).append('/')
                    .append(formatScale(channel.localScale()));
            if (channel.entityScale() != null) {
                text.append('/').append(formatScale(channel.entityScale()));
            }
            text.append('\n');
        }

        text.append("Fields:\n");
        for (var field : snapshot.fields()) appendField(text, field);

        appendQueue(text, snapshot.scheduledBlocks());
        appendQueue(text, snapshot.scheduledFluids());
        var accumulators = snapshot.accumulators();
        text.append("Accumulators: entity=")
                .append(accumulators.entities())
                .append(" blockEntity=").append(accumulators.blockEntities())
                .append(" levelClock=").append(accumulators.levelClocks())
                .append(" debugImmunityGroups=")
                .append(snapshot.debugImmunities().size());
        return text.toString();
    }

    private static void appendField(
            StringBuilder text,
            TemporalTickDiagnostics.FieldState field
    ) {
        text.append("  ")
                .append(field.matchesPosition() ? "§bmatch§r " : "§8outside§r ")
                .append(field.debugControlled() ? "debug " : "owned ")
                .append(field.id()).append(' ')
                .append(formatScale(field.scale()))
                .append(" source=").append(field.pauseSource())
                .append(" scope=").append(field.scope())
                .append(" channels=")
                .append(field.channels().stream()
                        .map(Enum::name)
                        .sorted()
                        .collect(Collectors.joining(",")))
                .append('\n');
    }

    private static void appendQueue(
            StringBuilder text,
            TemporalTickDiagnostics.QueueState queue
    ) {
        text.append("Queue ").append(queue.channel())
                .append(": pending=").append(queue.pendingTicks())
                .append(" frozen=").append(queue.frozenTicks())
                .append(" bound=").append(queue.bound())
                .append(" dispatching=").append(queue.dispatching())
                .append(" applicableFields=").append(queue.applicableFieldCount())
                .append('\n');
    }

    private static TemporalRuntime runtime(CommandSourceStack source) {
        return (TemporalRuntime) TemporalApi.get(source.getServer());
    }

    private static ServerLevel dimensionLevel(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        var identifier = IdentifierArgument.getId(context, "dimension");
        var level = context.getSource().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, identifier)
        );
        if (level == null) throw UNKNOWN_DIMENSION.create(identifier);
        return level;
    }

    private static ResourceKey<Level> dimensionKey(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        return dimensionLevel(context).dimension();
    }

    private static TemporalScope dimensionScope(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        return TemporalScope.dimension(dimensionKey(context));
    }

    private static UUID controlId(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        var value = StringArgumentType.getString(context, "control_id");
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            throw INVALID_CONTROL_ID.create(value);
        }
    }

    static Set<TemporalChannel> parseChannels(
            String value
    ) throws CommandSyntaxException {
        var normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("all")) {
            return Set.copyOf(EnumSet.allOf(TemporalChannel.class));
        }
        if (normalized.equals("world")) {
            return TemporalChannel.worldSimulation();
        }
        if (normalized.equals("integrated")) {
            return EnumSet.of(
                    TemporalChannel.LEVEL_CLOCK,
                    TemporalChannel.ENTITY,
                    TemporalChannel.BLOCK_ENTITY,
                    TemporalChannel.SCHEDULED_BLOCK,
                    TemporalChannel.SCHEDULED_FLUID,
                    TemporalChannel.RANDOM_TICK
            );
        }
        if (normalized.equals("spatial")) return spatialChannels();
        var channels = EnumSet.noneOf(TemporalChannel.class);
        for (var token : normalized.split(",")) {
            try {
                channels.add(TemporalChannel.valueOf(
                        token.strip().replace('-', '_').toUpperCase(Locale.ROOT)
                ));
            } catch (IllegalArgumentException exception) {
                throw INVALID_CHANNEL.create(token);
            }
        }
        if (channels.isEmpty()) throw INVALID_CHANNEL.create(value);
        return channels;
    }

    static Set<TemporalPauseSource> parseSources(
            String value
    ) throws CommandSyntaxException {
        if (value.equalsIgnoreCase("all")) return allPauseSources();
        var sources = EnumSet.noneOf(TemporalPauseSource.class);
        for (var token : value.split(",")) {
            sources.add(parseSingleSource(token));
        }
        return sources;
    }

    private static TemporalPauseSource parseSingleSource(
            String value
    ) throws CommandSyntaxException {
        try {
            return TemporalPauseSource.valueOf(
                    value.strip().replace('-', '_').toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw INVALID_SOURCE.create(value);
        }
    }

    private static Set<TemporalPauseSource> allPauseSources() {
        return Set.copyOf(EnumSet.allOf(TemporalPauseSource.class));
    }

    private static Set<TemporalChannel> spatialChannels() {
        return Set.copyOf(EnumSet.of(
                TemporalChannel.ENTITY,
                TemporalChannel.BLOCK_ENTITY,
                TemporalChannel.SCHEDULED_BLOCK,
                TemporalChannel.SCHEDULED_FLUID,
                TemporalChannel.RANDOM_TICK
        ));
    }

    private static CompletableFuture<Suggestions> suggestChannels(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        var values = new LinkedHashSet<String>();
        values.add("integrated");
        values.add("spatial");
        values.add("world");
        values.add("all");
        for (var channel : TemporalChannel.values()) {
            values.add(channel.name().toLowerCase(Locale.ROOT));
        }
        return SharedSuggestionProvider.suggest(values, builder);
    }

    private static CompletableFuture<Suggestions> suggestSources(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        var values = new LinkedHashSet<String>();
        values.add("all");
        for (var source : TemporalPauseSource.values()) {
            values.add(source.name().toLowerCase(Locale.ROOT));
        }
        return SharedSuggestionProvider.suggest(values, builder);
    }

    private static String describeField(TemporalField field) {
        return formatScale(field.scale()) + " " + field.scope()
                + " channels=" + field.channels()
                + " source=" + field.pauseSource();
    }

    private static String formatPosition(BlockPos position) {
        return "[" + position.getX() + ", "
                + position.getY() + ", " + position.getZ() + "]";
    }

    private static String formatScale(double scale) {
        if (scale == 0.0D) return "§cPAUSED§r";
        return String.format(Locale.ROOT, "%.3fx", scale);
    }

    @FunctionalInterface
    private interface ScopeResolver {
        TemporalScope resolve(
                CommandContext<CommandSourceStack> context
        ) throws CommandSyntaxException;
    }
}
