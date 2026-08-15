package org.academy.internal.client.ability.program;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.gson.TypeHandler;
import org.academy.internal.client.ability.mentalout.ModularProgramEditorSession;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import org.academy.internal.common.ability.program.ProgramBookCodec;
import org.academy.internal.common.ability.program.ProgramEditorDocument;
import org.academy.internal.common.ability.program.ProgramVmDiagnostic;
import org.academy.internal.client.ability.mentalout.PrecisionOperationClient;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Shared precision-operation entry and category program cache, including Mentalout routing. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class AbilityProgramEditorClient {
    public static final String CONFIG_KEY = "ability_program_editor";
    public static final String KEY_NAME_OPEN = "ability_program_editor_open";
    public static final String KEY_NAME_SLOT_PREFIX = "ability_program_slot_";
    public static final int SLOT_COUNT = AbilityProgramManager.SLOT_COUNT;
    private static final Map<String, State> STATES = new HashMap<>();

    private static Config config;
    private static ModularProgramScreen screen;
    private static long executionSequence;

    private AbilityProgramEditorClient() {
    }

    public static void init() {
        AbilityProgramManager.initClient();
        PrecisionOperationManager.initClient();
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, Config.Action.INSTANCE);
        config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY);
        var openKey = config.getKeyBinding(KEY_NAME_OPEN, defaultOpenKey());
        if (legacyDefaultOpenKey().equals(openKey)) {
            openKey = defaultOpenKey();
            config.setKeyBinding(KEY_NAME_OPEN, openKey);
            AcademyCraftClient.Config.INSTANCE.setConfig(CONFIG_KEY, config);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addKeyBinding(
                KEY_NAME_OPEN,
                openKey,
                context -> {
                    if (context.action() == InputConstants.PRESS) openEditor();
                }
        );
        var slotKeys = new int[]{
                InputConstants.KEY_1,
                InputConstants.KEY_2,
                InputConstants.KEY_3,
                InputConstants.KEY_4,
                InputConstants.KEY_5,
                InputConstants.KEY_6,
                InputConstants.KEY_7,
                InputConstants.KEY_8,
                InputConstants.KEY_9,
                InputConstants.KEY_0
        };
        for (var slot = 0; slot < SLOT_COUNT; slot++) {
            var selectedSlot = slot;
            var keyName = KEY_NAME_SLOT_PREFIX + (slot + 1);
            InputSystem.addKeyBinding(
                    keyName,
                    config.getKeyBinding(keyName, defaultExecuteKey(slotKeys[slot])),
                    context -> {
                        if (context.action() == InputConstants.PRESS) execute(selectedSlot);
                    }
            );
        }
    }

    public static void openEditor() {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var category = AbilitySystemClient.category;
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || minecraft.gui.screen() != null
                || !isSupportedCategory(category)) {
            return;
        }
        if (AbilitySystemClient.getLevel().getLevelCode() < 5) return;
        if (category.getKey().equals(AcademyCraft.academy(AbilityCategoryNames.MENTALOUT))) {
            PrecisionOperationClient.openEditor();
            return;
        }
        var state = state(player.getUUID(), category.getKey());
        screen = new ModularProgramScreen(new Session(category, state));
        minecraft.gui.setScreen(screen);
        MisakaNetworkClient.send(new AbilityProgramManager.RequestPacket(category.getKey()));
    }

    public static boolean isSupportedCategory(@Nullable AbilityCategory category) {
        return category != null && isSupportedCategoryId(category.getKey());
    }

    public static boolean isSupportedCategoryId(@Nullable Identifier category) {
        return category != null
                && !category.equals(AcademyCraft.academy(AbilityCategoryNames.LEVEL0))
                && AbilityProgramDefinitions.find(category) != null;
    }

    public static boolean canUsePrecisionOperation() {
        return Minecraft.getInstance().player != null
                && AbilitySystemClient.getLevel().getLevelCode() >= 5
                && isSupportedCategory(AbilitySystemClient.category);
    }

    public static InputSystem.KeyCombination defaultOpenKey() {
        return InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_BACKSLASH,
                InputConstants.PRESS,
                0
        );
    }

    static InputSystem.KeyCombination legacyDefaultOpenKey() {
        return InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                GLFW.GLFW_KEY_EQUAL,
                InputConstants.PRESS,
                0
        );
    }

    public static InputSystem.KeyCombination defaultExecuteKey(int key) {
        return InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                key,
                InputConstants.PRESS,
                InputConstants.MOD_ALT
        );
    }

    public static void executeSelected() {
        var player = Minecraft.getInstance().player;
        var category = AbilitySystemClient.category;
        if (player == null || category == null || !isSupportedCategory(category)) return;
        if (category.getKey().equals(AcademyCraft.academy(AbilityCategoryNames.MENTALOUT))) {
            PrecisionOperationClient.executeSelected();
            return;
        }
        var state = state(player.getUUID(), category.getKey());
        execute(state.saved.selectedSlot());
    }

    public static void execute(int slot) {
        var minecraft = Minecraft.getInstance();
        var category = AbilitySystemClient.category;
        if (minecraft.player == null
                || minecraft.gui.screen() != null
                || !isSupportedCategory(category)) {
            return;
        }
        if (AbilitySystemClient.getLevel().getLevelCode() < 5) return;
        if (category.getKey().equals(AcademyCraft.academy(AbilityCategoryNames.MENTALOUT))) {
            PrecisionOperationClient.execute(slot);
            return;
        }
        MisakaNetworkClient.send(new AbilityProgramManager.ExecutePacket(
                category.getKey(),
                Math.clamp(slot, 0, SLOT_COUNT - 1),
                executionSequence++
        ));
    }

    static boolean validBook(Identifier category, ProgramBook book) {
        if (book == null
                || book.schemaVersion() != ProgramBook.CURRENT_SCHEMA_VERSION
                || book.slots().size() != SLOT_COUNT) {
            return false;
        }
        return book.slots().stream().allMatch(slot -> slot.empty()
                || slot.program().schemaVersion() == AbilityProgram.CURRENT_SCHEMA_VERSION
                && slot.program().category().equals(category));
    }

    private static boolean emptyBook(ProgramBook book) {
        return book.revision() == 0L && book.slots().stream().allMatch(ProgramBook.Slot::empty);
    }

    static boolean shouldImportCachedBook(ProgramBook server, ProgramBook cached) {
        return emptyBook(server)
                && cached != null
                && cached.slots().stream().anyMatch(slot -> !slot.empty());
    }

    static ProgramBook decodeBook(Identifier category, @Nullable String encoded) {
        if (encoded == null || encoded.isBlank()) return ProgramBook.empty(SLOT_COUNT);
        try {
            var decoded = ProgramBookCodec.decode(Base64.getDecoder().decode(encoded));
            if (decoded.valid()) {
                var book = decoded.book();
                if (book.schemaVersion() == ProgramBook.CURRENT_SCHEMA_VERSION
                        && book.slots().size() > 0
                        && book.slots().size() <= SLOT_COUNT
                        && book.slots().stream().allMatch(slot -> slot.empty()
                        || slot.program().schemaVersion() == AbilityProgram.CURRENT_SCHEMA_VERSION
                        && slot.program().category().equals(category))) {
                    return book.resize(SLOT_COUNT);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid local configuration is isolated to this category and replaced with an empty book.
        }
        return ProgramBook.empty(SLOT_COUNT);
    }

    private static State state(UUID playerId, Identifier category) {
        var key = storageKey(playerId, category);
        return STATES.computeIfAbsent(key, ignored -> {
            var book = decodeBook(category, config.programBooks.get(key));
            return new State(key, book);
        });
    }

    static String storageKey(UUID playerId, Identifier category) {
        return playerId + "|" + category;
    }

    private static Set<Identifier> learnedCapabilities() {
        return AbilitySystemClient.LEARNED_SKILLS.stream()
                .filter(Objects::nonNull)
                .map(Skill::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static AbilityProgram emptyProgram(UUID playerId, Identifier category, int slot) {
        slot = Math.clamp(slot, 0, SLOT_COUNT - 1);
        var id = UUID.nameUUIDFromBytes((
                "academy:ability_program:" + playerId + ":" + category + ":" + slot
        ).getBytes(StandardCharsets.UTF_8));
        return new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                id,
                category.getPath() + " " + (slot + 1),
                category,
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        );
    }

    private static void persist(State state) {
        var books = new LinkedHashMap<>(config.programBooks);
        books.put(state.storageKey, Base64.getEncoder().encodeToString(
                ProgramBookCodec.encode(state.saved)));
        config.programBooks = books;
        AcademyCraftClient.Config.INSTANCE.setConfig(CONFIG_KEY, config);
        AcademyCraftClient.Config.INSTANCE.save();
    }

    public static void handleSync(Identifier category, byte[] encoded) {
        if (!isSupportedCategoryId(category)) return;
        var decoded = ProgramBookCodec.decode(encoded);
        if (!decoded.valid() || !validBook(category, decoded.book())) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var state = state(player.getUUID(), category);
        if (!state.serverSynchronized
                && shouldImportCachedBook(decoded.book(), state.saved)) {
            if (!state.importPending) {
                state.importPending = true;
                MisakaNetworkClient.send(new AbilityProgramManager.ImportPacket(
                        category, decoded.book().revision(), ProgramBookCodec.encode(state.saved)));
            }
            return;
        }
        var selected = Math.clamp(state.saved.selectedSlot(), 0, SLOT_COUNT - 1);
        state.install(decoded.book().select(selected));
        state.serverSynchronized = true;
        state.importPending = false;
        persist(state);
        var currentCategory = AbilitySystemClient.category;
        if (screen != null
                && Minecraft.getInstance().gui.screen() == screen
                && currentCategory != null
                && currentCategory.getKey().equals(category)) {
            screen.applyServerState(
                    selected,
                    state.drafts[selected] == null
                            ? emptyProgram(player.getUUID(), category, selected)
                            : state.drafts[selected],
                    state.saved.revision()
            );
        }
    }

    public static void handleResult(
            Identifier category,
            int slot,
            AbilityProgramManager.FeedbackType type,
            long revision,
            AbilityProgramManager.ResultCode code,
            @Nullable ProgramDiagnosticCode diagnostic,
            int nodeId,
            ProgramVmDiagnostic vmDiagnostic
    ) {
        if (!isSupportedCategoryId(category)) return;
        slot = Math.clamp(slot, 0, SLOT_COUNT - 1);
        var player = Minecraft.getInstance().player;
        var state = player == null ? null : STATES.get(storageKey(player.getUUID(), category));
        if (state != null && state.importPending) {
            state.importPending = false;
            if (type == AbilityProgramManager.FeedbackType.ERROR) {
                var selected = state.saved.selectedSlot();
                state.saved = ProgramBook.empty(SLOT_COUNT).select(selected);
                state.serverSynchronized = true;
            }
        }
        if (type == AbilityProgramManager.FeedbackType.SAVE) {
            notify("message.academy.program.editor.saved", ChatFormatting.WHITE, slot + 1);
        } else if (type == AbilityProgramManager.FeedbackType.IMPORT) {
            notify("message.academy.program.editor.migrated", ChatFormatting.WHITE);
        } else if (type == AbilityProgramManager.FeedbackType.COMPLETED) {
            notify("message.academy.program.execution.completed", ChatFormatting.WHITE, slot + 1);
        } else {
            var key = switch (code) {
                case INVALID_CATEGORY -> "message.academy.program.editor.invalid_category";
                case REVISION_CONFLICT -> "message.academy.program.editor.revision_conflict";
                case TOO_LARGE -> "message.academy.program.editor.too_large";
                case EMPTY_PROGRAM -> "message.academy.program.execution.empty_program";
                case EXECUTION_UNSUPPORTED -> "message.academy.program.execution.unsupported";
                case EXECUTION_FAILED -> "message.academy.program.execution.failed";
                case INVALID_PROGRAM, OK -> "message.academy.program.editor.invalid_program";
            };
            notify(key, ChatFormatting.RED, slot + 1, Component.translatable(
                    "message.academy.program.execution.diagnostic."
                            + vmDiagnostic.name().toLowerCase(java.util.Locale.ROOT)));
        }
        var currentCategory = AbilitySystemClient.category;
        if (screen != null
                && Minecraft.getInstance().gui.screen() == screen
                && currentCategory != null
                && currentCategory.getKey().equals(category)) {
            screen.applyProgramResult(
                    slot,
                    revision,
                    type == AbilityProgramManager.FeedbackType.ERROR ? diagnostic : null,
                    nodeId,
                    type != AbilityProgramManager.FeedbackType.ERROR
            );
        }
    }

    private static void notify(
            String key,
            ChatFormatting formatting,
            Object... arguments
    ) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(key, arguments).withStyle(formatting));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        screen = null;
        executionSequence = 0L;
    }

    private static final class Session implements ModularProgramEditorSession {
        private final AbilityCategory category;
        private final State state;
        private final UUID playerId;
        private final Set<Identifier> capabilities;

        private Session(AbilityCategory category, State state) {
            this.category = category;
            this.state = state;
            var player = Minecraft.getInstance().player;
            playerId = player == null ? new UUID(0L, 0L) : player.getUUID();
            capabilities = learnedCapabilities();
        }

        @Override
        public Component title() {
            return Component.translatable(
                    "screen.academy.program.editor.title",
                    Component.translatable("ability_category." + category.getKey().toString()
                            .replace(':', '.'))
            );
        }

        @Override
        public int slotCount() {
            return SLOT_COUNT;
        }

        @Override
        public int selectedSlot() {
            return state.saved.selectedSlot();
        }

        @Override
        public long revision() {
            return state.saved.revision();
        }

        @Override
        public AbilityProgram editableProgram(int slot) {
            slot = Math.clamp(slot, 0, SLOT_COUNT - 1);
            var draft = state.drafts[slot];
            return draft == null ? emptyProgram(slot) : draft;
        }

        @Override
        public AbilityProgram emptyProgram(int slot) {
            return AbilityProgramEditorClient.emptyProgram(playerId, category.getKey(), slot);
        }

        @Override
        public @Nullable AbilityProgram restoredProgram(int slot) {
            return state.saved.slot(Math.clamp(slot, 0, SLOT_COUNT - 1)).program();
        }

        @Override
        public Set<Identifier> capabilities() {
            return capabilities;
        }

        @Override
        public void updateLocalProgram(int slot, AbilityProgram program) {
            if (program.category().equals(category.getKey())) {
                state.drafts[Math.clamp(slot, 0, SLOT_COUNT - 1)] = program;
            }
        }

        @Override
        public void selectSlot(int slot) {
            state.saved = state.saved.select(Math.clamp(slot, 0, SLOT_COUNT - 1));
            persist(state);
        }

        @Override
        public void saveProgram(
                int slot,
                @Nullable AbilityProgram program,
                long expectedRevision
        ) {
            slot = Math.clamp(slot, 0, SLOT_COUNT - 1);
            if (state.saved.revision() != expectedRevision) {
                AbilityProgramEditorClient.notify(
                        "message.academy.program.editor.revision_conflict", ChatFormatting.RED);
                return;
            }
            if (program != null) {
                if (!program.category().equals(category.getKey())) {
                    AbilityProgramEditorClient.notify(
                            "message.academy.program.editor.invalid_category", ChatFormatting.RED);
                    return;
                }
                var validation = new ProgramEditorDocument(
                        program,
                        AbilityProgramDefinitions.require(category.getKey()),
                        capabilities
                ).validation();
                if (!validation.valid()) {
                    AbilityProgramEditorClient.notify(
                            "message.academy.program.editor.invalid_program", ChatFormatting.RED);
                    return;
                }
            }
            final byte[] encoded;
            try {
                encoded = ProgramBookCodec.encodeProgram(program);
            } catch (IllegalArgumentException exception) {
                AbilityProgramEditorClient.notify(
                        "message.academy.program.editor.too_large", ChatFormatting.RED);
                return;
            }
            state.drafts[slot] = program;
            MisakaNetworkClient.send(new AbilityProgramManager.SavePacket(
                    category.getKey(), slot, expectedRevision, encoded));
        }

        @Override
        public void closed(ModularProgramScreen closed) {
            if (screen == closed) screen = null;
        }

    }

    private static final class State {
        private final String storageKey;
        private final AbilityProgram[] drafts;
        private ProgramBook saved;
        private boolean serverSynchronized;
        private boolean importPending;

        private State(String storageKey, ProgramBook saved) {
            this.storageKey = storageKey;
            drafts = new AbilityProgram[SLOT_COUNT];
            install(saved);
        }

        private void install(ProgramBook saved) {
            this.saved = saved;
            for (var slot = 0; slot < SLOT_COUNT; slot++) {
                drafts[slot] = saved.slot(slot).program();
            }
        }
    }

    public static final class Config extends KeyBindingConfig {
        private Map<String, String> programBooks = new LinkedHashMap<>();

        public Map<String, String> programBooks() {
            return Map.copyOf(programBooks);
        }

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
