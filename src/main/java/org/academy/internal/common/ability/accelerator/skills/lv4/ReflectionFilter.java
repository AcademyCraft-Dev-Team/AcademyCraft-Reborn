package org.academy.internal.common.ability.accelerator.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.BlendQuadWidget;
import org.academy.api.client.gui.widget.EmptyWidget;
import org.academy.api.client.gui.widget.FillWidget;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.gui.SerializedUiLayout;
import org.academy.internal.client.gui.debug.SerializedUiDebugHost;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.SkillData;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import net.minecraft.util.Mth;

public final class ReflectionFilter extends Skill {
    static final int MAX_EFFECT_LIST_SIZE = 256;

    public ReflectionFilter() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VECTOR_REFLECTION)
                .withCustomData(Data.ID, Data.class, Data::new)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition(
                        "Vector Reflection",
                        "academy:vector_reflection"
                ))
        );
    }

    public static boolean shouldAcceptEffect(Data data, MobEffectInstance effect) {
        if (effect == null) return true;
        data = normalizeData(data);
        var id = effectId(effect);
        var category = effect.getEffect().value().getCategory();
        return shouldAcceptNormalizedEffect(data, id, category);
    }

    static boolean shouldAcceptNormalizedEffect(Data data, String effectId, MobEffectCategory category) {
        if (effectId != null && data.blacklist.contains(effectId)) return false;
        if (effectId != null && data.whitelist.contains(effectId)) return true;
        return switch (Mode.byName(data.mode)) {
            case REFLECT_ALL -> false;
            case POSITIVE_FILTER -> category == MobEffectCategory.BENEFICIAL;
            case NEUTRAL_FILTER -> category != MobEffectCategory.HARMFUL;
        };
    }

    public static boolean shouldAcceptEffect(ServerPlayer player, MobEffectInstance effect) {
        if (player == null) return shouldAcceptEffect(new Data(), effect);
        var filter = Skills.REFLECTION_FILTER.get();
        if (!filter.isEnabled(player)) return shouldAcceptEffect(new Data(), effect);
        return shouldAcceptEffect(Server.getOrCreateData(player), effect);
    }

    public static boolean shouldReflectEffect(ServerPlayer player, MobEffectInstance effect) {
        return !shouldAcceptEffect(player, effect);
    }

    public static boolean isForcedMovementProtectionEnabled(ServerPlayer player) {
        return player != null
                && Skills.REFLECTION_FILTER.get().isEnabled(player)
                && Server.getOrCreateData(player).isForcedMovementProtectionEnabled();
    }

    public static float getReflectionMaintenanceCost(ServerPlayer player) {
        var data = normalizeData(Server.getOrCreateData(player));
        var modeCost = switch (data.getMode()) {
            case REFLECT_ALL -> 40.0f;
            case POSITIVE_FILTER -> 60.0f;
            case NEUTRAL_FILTER -> 80.0f;
        };
        return modeCost + (data.whitelist.size() + data.blacklist.size()) * 5.0f;
    }

    private static String effectId(MobEffectInstance effect) {
        var id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        return id == null ? null : id.toString();
    }

    static Data normalizeData(Data data) {
        if (data == null) data = new Data();
        data.mode = Mode.byName(data.mode).name();
        data.whitelist = normalizeEffectIds(data.whitelist, null);
        data.blacklist = normalizeEffectIds(data.blacklist, null);
        data.whitelist.removeAll(data.blacklist);
        return data;
    }

    static boolean hasSameConfiguration(Data first, Data second) {
        if (first == null || second == null) return first == second;
        return first.getMode() == second.getMode()
                && first.getWhitelist().equals(second.getWhitelist())
                && first.getBlacklist().equals(second.getBlacklist())
                && first.isForcedMovementProtectionEnabled()
                == second.isForcedMovementProtectionEnabled();
    }

    static void reportEffectActivity(ServerPlayer player, boolean reflected) {
        if (player == null) return;
        var skill = Skills.REFLECTION_FILTER.get();
        if (skill.isEnabled(player)) skill.reportActivity(player, reflected);
    }

    private static List<String> normalizeEffectIds(List<String> input, List<String> excluded) {
        var result = new LinkedHashSet<String>();
        if (input != null) {
            for (var raw : input) {
                var id = normalizeEffectId(raw);
                if (id == null || excluded != null && excluded.contains(id)) continue;
                result.add(id);
                if (result.size() >= MAX_EFFECT_LIST_SIZE) break;
            }
        }
        return new ArrayList<>(result);
    }

    private static String normalizeEffectId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var id = Identifier.tryParse(raw.strip());
        if (id == null || !BuiltInRegistries.MOB_EFFECT.keySet().contains(id)) return null;
        return id.toString();
    }

    private static void writeFilterPayload(
            ByteBuf buf,
            String mode,
            List<String> whitelist,
            List<String> blacklist,
            boolean forcedMovementProtection
    ) {
        ByteBufCodecs.STRING_UTF8.encode(buf, Mode.byName(mode).name());
        writeStringList(buf, whitelist);
        writeStringList(buf, blacklist);
        ByteBufCodecs.BOOL.encode(buf, forcedMovementProtection);
    }

    private static FilterPayload readFilterPayload(ByteBuf buf) {
        return new FilterPayload(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                readStringList(buf),
                readStringList(buf),
                ByteBufCodecs.BOOL.decode(buf)
        );
    }

    private static void writeStringList(ByteBuf buf, List<String> values) {
        var safe = values == null ? List.<String>of() : values;
        var size = Math.min(safe.size(), MAX_EFFECT_LIST_SIZE);
        ByteBufCodecs.VAR_INT.encode(buf, size);
        for (var i = 0; i < size; i++) ByteBufCodecs.STRING_UTF8.encode(buf, safe.get(i));
    }

    private static List<String> readStringList(ByteBuf buf) {
        var size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > MAX_EFFECT_LIST_SIZE) {
            throw new DecoderException("Reflection filter list size out of range: " + size);
        }
        var result = new ArrayList<String>(size);
        for (var i = 0; i < size; i++) result.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        return result;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Handler.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var openKey = Client.CONFIG.getKeyBinding(Client.KEY_NAME_OPEN, defaultOpenKey());
        if (legacyDefaultOpenKey().equals(openKey)) {
            openKey = defaultOpenKey();
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_OPEN, openKey);
            AcademyCraftClient.Config.INSTANCE.setConfig(key, Client.CONFIG);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addKeyBinding(Client.KEY_NAME_OPEN, openKey, _ -> Client.open());
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    static InputSystem.KeyCombination defaultOpenKey() {
        return InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                GLFW.GLFW_KEY_EQUAL,
                InputConstants.PRESS,
                0
        );
    }

    private static InputSystem.KeyCombination legacyDefaultOpenKey() {
        return InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_BACKSLASH,
                InputConstants.PRESS,
                0
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum Mode {
        REFLECT_ALL,
        POSITIVE_FILTER,
        NEUTRAL_FILTER;

        public static Mode byName(String name) {
            if (name != null) {
                for (var mode : values()) {
                    if (mode.name().equals(name)) return mode;
                }
            }
            return REFLECT_ALL;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static final class Data extends SkillData {
        public static final Identifier ID = AcademyCraft.academy("reflection_filter");
        private String mode = Mode.REFLECT_ALL.name();
        private List<String> whitelist = new ArrayList<>();
        private List<String> blacklist = new ArrayList<>();
        private boolean forcedMovementProtection;

        public Data copy() {
            var copy = new Data();
            copy.setProficiency(getProficiency());
            copy.setEnabled(isEnabled());
            copy.mode = mode;
            copy.whitelist = whitelist == null ? new ArrayList<>() : new ArrayList<>(whitelist);
            copy.blacklist = blacklist == null ? new ArrayList<>() : new ArrayList<>(blacklist);
            copy.forcedMovementProtection = forcedMovementProtection;
            return copy;
        }

        public Mode getMode() {
            return Mode.byName(mode);
        }

        public List<String> getWhitelist() {
            return List.copyOf(whitelist);
        }

        public List<String> getBlacklist() {
            return List.copyOf(blacklist);
        }

        public boolean isForcedMovementProtectionEnabled() {
            return forcedMovementProtection;
        }

        @Override
        public Identifier getType() {
            return ID;
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handleRequest(RequestPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.REFLECTION_FILTER.get().isEnabled(player)) return;
            sync(player, getOrCreateData(player));
        }

        @SubscribePacket
        public static void handleUpdate(UpdatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.REFLECTION_FILTER.get();
            if (!skill.isEnabled(player)) return;
            var data = getOrCreateData(player);
            var previous = data.copy();
            data.mode = Mode.byName(packet.mode).name();
            data.whitelist = normalizeEffectIds(packet.whitelist, null);
            data.blacklist = normalizeEffectIds(packet.blacklist, null);
            data.forcedMovementProtection = packet.forcedMovementProtection;
            data.whitelist.removeAll(data.blacklist);
            var playerData = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
            if (playerData != null) playerData.markDirty();
            sync(player, data);
            if (Skills.VECTOR_REFLECTION.get().isEnabled(player)) {
                VectorReflection.Server.purgeProtectedEffects(player);
            }
            if (!hasSameConfiguration(previous, data)) skill.reportTrigger(player);
        }

        public static Data getOrCreateData(ServerPlayer player) {
            var skill = Skills.REFLECTION_FILTER.get();
            var system = AbilitySystemServer.getSystem(player);
            var playerData = system.getPlayerData(player.getUUID());
            if (playerData == null) return new Data();
            var map = playerData.getSkillDataMap();
            var raw = map.get(skill.getKeyString());
            if (raw instanceof Data data) return normalizeData(data);

            var data = (Data) skill.createData();
            if (raw != null) mergeProgress(data, raw);
            map.put(skill.getKeyString(), data);
            playerData.markDirty();
            return data;
        }

        private static void mergeProgress(Data target, SkillData source) {
            target.setProficiency(source.getProficiency());
            target.setEnabled(source.isEnabled());
        }

        private static void sync(ServerPlayer player, Data data) {
            var normalized = normalizeData(data).copy();
            MisakaNetworkServer.send(player, new SyncPacket(
                    normalized.mode,
                    normalized.whitelist,
                    normalized.blacklist,
                    normalized.forcedMovementProtection
            ));
        }
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.REFLECTION_FILTER.get(),
                        List.of(VectorReflection.Client.SKILL_INFO),
                        R.textures.reflection_filter_icon,
                        60,
                        78
                )
        );
        public static final String KEY_NAME_OPEN = SkillNames.REFLECTION_FILTER + "_open";
        public static Config CONFIG = new Config();
        private static Data localData = new Data();
        private static ReflectionFilterScreen lastScreen;

        private Client() {
        }

        private static void open() {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null
                    || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.REFLECTION_FILTER.get())) {
                return;
            }
            var screen = new ReflectionFilterScreen(localData.copy());
            lastScreen = screen;
            minecraft.gui.setScreen(screen);
            MisakaNetworkClient.send(RequestPacket.INSTANCE);
        }

        @SubscribePacket
        public static void handleSync(SyncPacket packet) {
            var data = new Data();
            data.mode = packet.mode;
            data.whitelist = new ArrayList<>(packet.whitelist);
            data.blacklist = new ArrayList<>(packet.blacklist);
            data.forcedMovementProtection = packet.forcedMovementProtection;
            localData = normalizeData(data).copy();
            var screen = lastScreen;
            if (screen != null && Minecraft.getInstance().gui.screen() == screen) {
                screen.setData(localData.copy());
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Handler implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Handler();

                private Handler() {
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

    private static final class ReflectionFilterScreen extends UiScreen implements SerializedUiDebugHost {
        private static final int ACTIVE = 0xFFFFFFFF;
        private static final int SECTION = 0x40000000;
        private static final int CONTROL = 0x28000000;
        private static final int INPUT = 0x5F1F1F1F;
        private static final int INPUT_FOCUSED = 0x5F5A5A5A;
        private static final int ROW = 0x3DFFFFFF;
        private static final int ROW_HOVER = 0x55FFFFFF;
        private static final int ROW_SELECTED = 0x55FFFFFF;
        private static final int BORDER = 0x99FFFFFF;
        private static final int BORDER_DIM = 0x60FFFFFF;
        private static final int TEXT = 0xFFFFFFFF;
        private static final int DIM = 0xBFFFFFFF;
        private static final int DISABLED = 0x33FFFFFF;
        private static final int GOOD = 0xFF25C4FF;
        private static final int BAD = 0xFFFF6C00;
        private static final int NEUTRAL = 0xFF7680DE;
        private static final int ROW_H = 20;
        private static final int GAP = 3;
        private static final int MIN_W = 460;
        private static final int PREFERRED_W = 520;
        private static final int MIN_H = 238;
        private static final int PREFERRED_H = 260;
        private static final int INNER_PAD = 12;
        private static final int COLUMN_GAP = 10;
        private static final int MIDDLE_W = 74;
        private static final int MIDDLE_H = 24;
        private static final int SECTION_TOP = 30;
        private static final int SECTION_BOTTOM_PAD = 14;
        private static final int CONTENT_BOTTOM_PAD = 19;
        private static final int SEARCH_X_PAD = 5;
        private static final int SEARCH_Y = 35;
        private static final int SEARCH_H = 16;
        private static final int SEARCH_FRAME_PAD_X = 3;
        private static final int SEARCH_FRAME_PAD_Y = 2;
        private static final int EFFECTS_LABEL_Y = 56;
        private static final int EFFECT_LIST_Y = 68;
        private static final int MODE_BUTTON_Y = 34;
        private static final int MODE_BUTTON_H = 22;
        private static final int MODE_LABEL_Y = 62;
        private static final int MODE_DESCRIPTION_Y = 74;
        private static final int FORCED_MOVEMENT_Y = 88;
        private static final int FORCED_MOVEMENT_H = 18;
        private static final int SIDE_LIST_Y = 126;
        private static final int RIGHT_CONTENT_INSET = 6;
        private static final int MIDDLE_WHITE_Y = 102;
        private static final int MIDDLE_BLACK_Y = 138;

        private final List<EffectEntry> allEffects = new ArrayList<>();
        private final List<EffectEntry> filteredEffects = new ArrayList<>();
        private final Data data;
        private EditBox searchBox;
        private int panelX;
        private int panelY;
        private int panelW;
        private int panelH;
        private int leftX;
        private int midX;
        private int rightX;
        private int listY;
        private int listBottom;
        private int leftW;
        private int rightW;
        private int whiteX;
        private int blackX;
        private int sideListY;
        private int sideListBottom;
        private int effectScroll;
        private int whiteScroll;
        private int blackScroll;
        private String selectedEffect;
        private String lastSearch = "";
        private Widget panelLayout;
        private Widget leftColumnLayout;
        private Widget middleColumnLayout;
        private Widget rightColumnLayout;
        private FrameLayoutWidget serializedLayout;
        private String serializedLayoutId;

        private ReflectionFilterScreen(Data data) {
            super(Component.translatable("screen.academy.reflection_filter.title"));
            this.data = normalizeData(data);
            rebuildAllEffects();
        }

        private static void addLayoutSlot(
                FrameLayoutWidget panel,
                String name,
                int x,
                int y,
                int width,
                int height
        ) {
            var slot = new EmptyWidget();
            slot.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(width, height).margin(x, y, 0, 0));
            panel.addChild(name, slot);
        }

        private static Component modeDescription(Mode mode) {
            return switch (mode) {
                case REFLECT_ALL -> Component.translatable("screen.academy.reflection_filter.mode.all.desc");
                case POSITIVE_FILTER -> Component.translatable("screen.academy.reflection_filter.mode.positive.desc");
                case NEUTRAL_FILTER -> Component.translatable("screen.academy.reflection_filter.mode.neutral.desc");
            };
        }

        private static int categoryColor(MobEffectCategory category) {
            if (category == MobEffectCategory.BENEFICIAL) return GOOD;
            if (category == MobEffectCategory.HARMFUL) return BAD;
            return NEUTRAL;
        }

        private static void drawScrollBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                          int scroll, int maxScroll) {
            graphics.fill(x, y, x + width, y + height, CONTROL);
            if (maxScroll <= 0) {
                graphics.fill(x, y, x + width, y + height, DISABLED);
                return;
            }
            var thumbHeight = Math.max(10, height / 4);
            var thumbY = y + (int) ((height - thumbHeight) * (scroll / (float) maxScroll));
            graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, DIM);
        }

        private static int maxScroll(int size, int visible) {
            return Math.max(0, size - visible);
        }

        private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private static boolean inside(double mouseX, double mouseY, Rect bounds) {
            return inside(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height);
        }

        private static Rect rect(Widget widget) {
            return new Rect(
                    Math.round(widget.getAbsoluteX()),
                    Math.round(widget.getAbsoluteY()),
                    Math.round(widget.getWidth()),
                    Math.round(widget.getHeight())
            );
        }

        @Override
        protected void onInit() {
            var compact = width < PREFERRED_W + 24 || height < PREFERRED_H + 24;
            serializedLayoutId = "reflection_filter_" + (compact ? "compact" : "wide");
            var layout = SerializedUiLayout.INSTANCE.loadBundled(
                    AcademyCraft.academy("ui/layout/" + serializedLayoutId + ".json"),
                    List.of("panel", "left_column", "middle_column", "right_column"),
                    () -> fallbackLayout(compact)
            );
            serializedLayout = layout;
            getRoot().addChild("serialized_layout", layout);
            panelLayout = SerializedUiLayout.INSTANCE.require(layout, "panel");
            leftColumnLayout = SerializedUiLayout.INSTANCE.require(layout, "left_column");
            middleColumnLayout = SerializedUiLayout.INSTANCE.require(layout, "middle_column");
            rightColumnLayout = SerializedUiLayout.INSTANCE.require(layout, "right_column");

            panelW = Math.min(PREFERRED_W, Math.max(MIN_W, width - 24));
            panelW = Math.min(panelW, width - 12);
            panelH = Math.min(PREFERRED_H, Math.max(MIN_H, height - 24));
            panelH = Math.min(panelH, height - 12);
            panelX = (width - panelW) / 2;
            panelY = (height - panelH) / 2;

            var columnsW = Math.max(1, panelW - INNER_PAD * 2 - MIDDLE_W - COLUMN_GAP * 2);
            leftW = Math.min(170, Math.max(140, (int) (columnsW * 0.4f)));
            if (columnsW - leftW < 196) leftW = Math.max(120, columnsW - 196);
            rightW = Math.max(1, columnsW - leftW);
            leftX = panelX + INNER_PAD;
            midX = leftX + leftW + COLUMN_GAP;
            rightX = midX + MIDDLE_W + COLUMN_GAP;
            listBottom = panelY + panelH - CONTENT_BOTTOM_PAD;

            searchBox = new EditBox(
                    font,
                    leftX + SEARCH_X_PAD,
                    panelY + SEARCH_Y,
                    leftW - SEARCH_X_PAD * 2,
                    SEARCH_H,
                    Component.empty()
            );
            searchBox.setHint(Component.translatable("screen.academy.reflection_filter.search"));
            searchBox.setMaxLength(64);
            searchBox.setBordered(false);
            searchBox.setTextColor(TEXT);
            addRenderableWidget(searchBox);

            var sideListW = sideListWidth();
            whiteX = rightX;
            blackX = rightX + sideListW + 8;
            listY = panelY + EFFECT_LIST_Y;
            sideListY = panelY + SIDE_LIST_Y;
            sideListBottom = panelY + panelH - CONTENT_BOTTOM_PAD;
            rebuildFilteredEffects();
        }

        private FrameLayoutWidget fallbackLayout(boolean compact) {
            var layout = new FrameLayoutWidget();
            layout.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT));
            var panel = new FrameLayoutWidget();
            var fallbackWidth = compact ? MIN_W : PREFERRED_W;
            var fallbackHeight = compact ? MIN_H : PREFERRED_H;
            panel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .size(fallbackWidth, fallbackHeight).gravity(Gravity.CENTER));
            var projection = new BlendQuadWidget();
            projection.setAlpha(0.5f);
            projection.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT).margin(1, 0, 1, 0));
            panel.addChild("panel_background", projection);
            var leftBorder = new FillWidget(BORDER_DIM);
            leftBorder.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .size(1, fallbackHeight - 8).gravity(Gravity.LEFT).marginTop(4));
            panel.addChild("border_left", leftBorder);
            var rightBorder = new FillWidget(BORDER_DIM);
            rightBorder.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .size(1, fallbackHeight - 8).gravity(Gravity.RIGHT).marginTop(4));
            panel.addChild("border_right", rightBorder);
            var titleDivider = new FillWidget(BORDER_DIM);
            titleDivider.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .size(fallbackWidth - 14, 1).margin(7, 24, 7, 0));
            panel.addChild("title_divider", titleDivider);
            var columnsW = fallbackWidth - INNER_PAD * 2 - MIDDLE_W - COLUMN_GAP * 2;
            var leftWidth = Math.min(170, Math.max(120, (int) (columnsW * 0.4f)));
            var rightWidth = Math.max(1, columnsW - leftWidth);
            addLayoutSlot(panel, "left_column", INNER_PAD, 30, leftWidth, fallbackHeight - 30);
            addLayoutSlot(panel, "middle_column", INNER_PAD + leftWidth + COLUMN_GAP,
                    30, MIDDLE_W, fallbackHeight - 30);
            addLayoutSlot(panel, "right_column",
                    INNER_PAD + leftWidth + MIDDLE_W + COLUMN_GAP * 2,
                    30, rightWidth, fallbackHeight - 30);
            layout.addChild("panel", panel);
            return layout;
        }

        private void syncSerializedLayout() {
            if (panelLayout == null || panelLayout.getWidth() <= 0.0f) return;
            var panel = rect(panelLayout);
            var left = rect(leftColumnLayout);
            var middle = rect(middleColumnLayout);
            var right = rect(rightColumnLayout);
            panelX = panel.x;
            panelY = panel.y;
            panelW = panel.width;
            panelH = panel.height;
            leftX = left.x;
            leftW = left.width;
            midX = middle.x;
            rightX = right.x;
            rightW = right.width;
            listBottom = panelY + panelH - CONTENT_BOTTOM_PAD;
            searchBox.setX(leftX + SEARCH_X_PAD);
            searchBox.setY(panelY + SEARCH_Y);
            searchBox.setWidth(Math.max(1, leftW - SEARCH_X_PAD * 2));
            var sideListW = sideListWidth();
            whiteX = rightX;
            blackX = rightX + sideListW + 8;
            listY = panelY + EFFECT_LIST_Y;
            sideListY = panelY + SIDE_LIST_Y;
            sideListBottom = panelY + panelH - CONTENT_BOTTOM_PAD;
        }

        private void rebuildAllEffects() {
            allEffects.clear();
            for (var effect : BuiltInRegistries.MOB_EFFECT) {
                var id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (id == null) continue;
                allEffects.add(new EffectEntry(
                        id.toString(),
                        Component.translatable(effect.getDescriptionId()).getString(),
                        effect.getCategory()
                ));
            }
            allEffects.sort(Comparator
                    .comparing((EffectEntry entry) -> entry.name.toLowerCase(Locale.ROOT))
                    .thenComparing(entry -> entry.id));
        }

        private void rebuildFilteredEffects() {
            filteredEffects.clear();
            var query = searchBox == null ? "" : searchBox.getValue().strip().toLowerCase(Locale.ROOT);
            lastSearch = query;
            for (var entry : allEffects) {
                if (query.isEmpty()
                        || entry.name.toLowerCase(Locale.ROOT).contains(query)
                        || entry.id.toLowerCase(Locale.ROOT).contains(query)) {
                    filteredEffects.add(entry);
                }
            }
            effectScroll = Mth.clamp(effectScroll, 0, maxScroll(filteredEffects.size(), effectVisibleRows()));
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            syncSerializedLayout();
            fillSection(graphics, leftX, panelY + SECTION_TOP, leftW,
                    panelH - SECTION_TOP - SECTION_BOTTOM_PAD);
            fillSection(graphics, rightX - 5, panelY + SECTION_TOP, rightW + 5,
                    panelH - SECTION_TOP - SECTION_BOTTOM_PAD);
            var searchFrame = searchFrame();
            drawInput(graphics, searchFrame.x, searchFrame.y, searchFrame.width, searchFrame.height,
                    searchBox.isFocused());
            searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
            var query = searchBox == null ? "" : searchBox.getValue().strip().toLowerCase(Locale.ROOT);
            if (!query.equals(lastSearch)) rebuildFilteredEffects();
            graphics.centeredText(font, title, panelX + panelW / 2, panelY + 8, TEXT);
            graphics.text(font, Component.translatable("screen.academy.reflection_filter.effects"),
                    leftX + SEARCH_X_PAD, panelY + EFFECTS_LABEL_Y, DIM, false);
            renderEffects(graphics, mouseX, mouseY);
            renderMiddleButtons(graphics, mouseX, mouseY);
            renderModes(graphics, mouseX, mouseY);
            renderForcedMovementProtection(graphics, mouseX, mouseY);
            renderSideLists(graphics, mouseX, mouseY);
        }

        private void renderEffects(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            var visible = effectVisibleRows();
            effectScroll = Mth.clamp(effectScroll, 0, maxScroll(filteredEffects.size(), visible));
            graphics.enableScissor(leftX + 4, listY, leftX + leftW - 4, listBottom);
            for (var row = 0; row < visible && effectScroll + row < filteredEffects.size(); row++) {
                var entry = filteredEffects.get(effectScroll + row);
                var y = listY + row * (ROW_H + GAP);
                var selected = entry.id.equals(selectedEffect);
                var hover = inside(mouseX, mouseY, leftX + 5, y, leftW - 10, ROW_H);
                graphics.fill(leftX + 5, y, leftX + leftW - 5, y + ROW_H,
                        selected ? ROW_SELECTED : hover ? ROW_HOVER : ROW);
                if (selected) graphics.fill(leftX + 5, y + 2, leftX + 7, y + ROW_H - 2, ACTIVE);
                var markerY = y + (ROW_H - 5) / 2;
                graphics.fill(leftX + 10, markerY, leftX + 15, markerY + 5, categoryColor(entry.category));
                graphics.text(font, font.plainSubstrByWidth(entry.name, leftW - 36),
                        leftX + 19, y + 2, TEXT, false);
                graphics.text(font, font.plainSubstrByWidth(entry.id, leftW - 36),
                        leftX + 19, y + 11, DIM, false);
            }
            graphics.disableScissor();
            drawScrollBar(graphics, leftX + leftW - 7, listY, 3, listBottom - listY,
                    effectScroll, maxScroll(filteredEffects.size(), visible));
        }

        private void renderMiddleButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            drawButton(graphics, midX, panelY + MIDDLE_WHITE_Y, MIDDLE_W, MIDDLE_H,
                    Component.translatable("screen.academy.reflection_filter.add_white"),
                    mouseX, mouseY, selectedEffect != null, false);
            drawButton(graphics, midX, panelY + MIDDLE_BLACK_Y, MIDDLE_W, MIDDLE_H,
                    Component.translatable("screen.academy.reflection_filter.add_black"),
                    mouseX, mouseY, selectedEffect != null, false);
        }

        private void renderModes(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            var contentW = rightContentWidth();
            var buttonW = (contentW - 12) / 3;
            var mode = data.getMode();
            drawButton(graphics, rightX, panelY + MODE_BUTTON_Y, buttonW, MODE_BUTTON_H,
                    Component.translatable("screen.academy.reflection_filter.mode.all"),
                    mouseX, mouseY, true, mode == Mode.REFLECT_ALL);
            drawButton(graphics, rightX + buttonW + 6, panelY + MODE_BUTTON_Y, buttonW, MODE_BUTTON_H,
                    Component.translatable("screen.academy.reflection_filter.mode.positive"),
                    mouseX, mouseY, true, mode == Mode.POSITIVE_FILTER);
            drawButton(graphics, rightX + (buttonW + 6) * 2, panelY + MODE_BUTTON_Y,
                    contentW - (buttonW + 6) * 2, MODE_BUTTON_H,
                    Component.translatable("screen.academy.reflection_filter.mode.neutral"),
                    mouseX, mouseY, true, mode == Mode.NEUTRAL_FILTER);
            graphics.text(font, Component.translatable("screen.academy.reflection_filter.mode"),
                    rightX, panelY + MODE_LABEL_Y, DIM, false);
            graphics.text(font, font.plainSubstrByWidth(modeDescription(mode).getString(), contentW),
                    rightX, panelY + MODE_DESCRIPTION_Y, TEXT, false);
        }

        private void renderForcedMovementProtection(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY
        ) {
            var enabled = data.forcedMovementProtection;
            drawButton(
                    graphics,
                    rightX,
                    panelY + FORCED_MOVEMENT_Y,
                    rightContentWidth(),
                    FORCED_MOVEMENT_H,
                    Component.translatable(enabled
                            ? "screen.academy.reflection_filter.forced_movement.on"
                            : "screen.academy.reflection_filter.forced_movement.off"),
                    mouseX,
                    mouseY,
                    true,
                    enabled
            );
        }

        private void renderSideLists(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            var listW = sideListWidth();
            graphics.centeredText(font, Component.translatable("screen.academy.reflection_filter.whitelist"),
                    whiteX + listW / 2, sideListY - 13, TEXT);
            graphics.centeredText(font, Component.translatable("screen.academy.reflection_filter.blacklist"),
                    blackX + listW / 2, sideListY - 13, TEXT);
            renderIdList(graphics, mouseX, mouseY, data.whitelist, whiteX, listW, true);
            renderIdList(graphics, mouseX, mouseY, data.blacklist, blackX, listW, false);
        }

        private void renderIdList(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  List<String> ids, int x, int width, boolean white) {
            var visible = sideVisibleRows();
            var scroll = Mth.clamp(white ? whiteScroll : blackScroll, 0, maxScroll(ids.size(), visible));
            if (white) whiteScroll = scroll;
            else blackScroll = scroll;
            graphics.enableScissor(x, sideListY, x + width, sideListBottom);
            for (var row = 0; row < visible && scroll + row < ids.size(); row++) {
                var id = ids.get(scroll + row);
                var y = sideListY + row * (ROW_H + GAP);
                var hover = inside(mouseX, mouseY, x, y, width, ROW_H);
                graphics.fill(x, y, x + width, y + ROW_H, hover ? ROW_HOVER : ROW);
                graphics.text(font, font.plainSubstrByWidth(effectDisplayName(id), width - 22),
                        x + 5, y + 2, TEXT, false);
                graphics.text(font, font.plainSubstrByWidth(id, width - 22),
                        x + 5, y + 11, DIM, false);
                graphics.text(font, "x", x + width - 12, y + (ROW_H - 8) / 2,
                        hover ? TEXT : BAD, false);
            }
            graphics.disableScissor();
            if (ids.isEmpty()) {
                graphics.centeredText(font, Component.translatable("screen.academy.reflection_filter.empty"),
                        x + width / 2, sideListY + (ROW_H - 8) / 2, DIM);
            }
            drawScrollBar(graphics, x + width - 3, sideListY, 3, sideListBottom - sideListY,
                    scroll, maxScroll(ids.size(), visible));
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            syncSerializedLayout();
            if (event.button() == 0) {
                var mouseX = event.x();
                var mouseY = event.y();
                if (inside(mouseX, mouseY, searchFrame())) {
                    setFocused(searchBox);
                    searchBox.setFocused(true);
                    searchBox.mouseClicked(event, doubleClick);
                    return true;
                }
                searchBox.setFocused(false);
                if (handleEffectClick(mouseX, mouseY)
                        || handleMiddleButtons(mouseX, mouseY)
                        || handleModeButtons(mouseX, mouseY)
                        || handleForcedMovementProtection(mouseX, mouseY)
                        || handleSideListClick(mouseX, mouseY)) {
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (searchBox != null && searchBox.isFocused() && searchBox.keyPressed(event)) {
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(event)) {
                return true;
            }
            return super.charTyped(event);
        }

        private boolean handleEffectClick(double mouseX, double mouseY) {
            if (!inside(mouseX, mouseY, leftX + 5, listY, leftW - 10, listBottom - listY)) return false;
            var offset = mouseY - listY;
            var row = (int) (offset / (ROW_H + GAP));
            if (offset % (ROW_H + GAP) <= ROW_H) {
                var index = effectScroll + row;
                if (index >= 0 && index < filteredEffects.size()) selectedEffect = filteredEffects.get(index).id;
            }
            return true;
        }

        private boolean handleMiddleButtons(double mouseX, double mouseY) {
            if (selectedEffect == null) return false;
            if (inside(mouseX, mouseY, midX, panelY + MIDDLE_WHITE_Y, MIDDLE_W, MIDDLE_H)) {
                addToList(data.whitelist, data.blacklist, selectedEffect);
                sendUpdate();
                return true;
            }
            if (inside(mouseX, mouseY, midX, panelY + MIDDLE_BLACK_Y, MIDDLE_W, MIDDLE_H)) {
                addToList(data.blacklist, data.whitelist, selectedEffect);
                sendUpdate();
                return true;
            }
            return false;
        }

        private boolean handleModeButtons(double mouseX, double mouseY) {
            var contentW = rightContentWidth();
            var buttonW = (contentW - 12) / 3;
            if (inside(mouseX, mouseY, rightX, panelY + MODE_BUTTON_Y, buttonW, MODE_BUTTON_H)) {
                data.mode = Mode.REFLECT_ALL.name();
            } else if (inside(mouseX, mouseY, rightX + buttonW + 6, panelY + MODE_BUTTON_Y,
                    buttonW, MODE_BUTTON_H)) {
                data.mode = Mode.POSITIVE_FILTER.name();
            } else if (inside(mouseX, mouseY, rightX + (buttonW + 6) * 2, panelY + MODE_BUTTON_Y,
                    contentW - (buttonW + 6) * 2, MODE_BUTTON_H)) {
                data.mode = Mode.NEUTRAL_FILTER.name();
            } else {
                return false;
            }
            sendUpdate();
            return true;
        }

        private boolean handleForcedMovementProtection(double mouseX, double mouseY) {
            if (!inside(mouseX, mouseY, rightX, panelY + FORCED_MOVEMENT_Y,
                    rightContentWidth(), FORCED_MOVEMENT_H)) return false;
            data.forcedMovementProtection = !data.forcedMovementProtection;
            sendUpdate();
            return true;
        }

        private boolean handleSideListClick(double mouseX, double mouseY) {
            var listW = sideListWidth();
            if (removeFromList(mouseX, mouseY, data.whitelist, whiteX, listW, true)) return true;
            return removeFromList(mouseX, mouseY, data.blacklist, blackX, listW, false);
        }

        private boolean removeFromList(double mouseX, double mouseY, List<String> ids,
                                       int x, int width, boolean white) {
            if (!inside(mouseX, mouseY, x, sideListY, width, sideListBottom - sideListY)) return false;
            var offset = mouseY - sideListY;
            var row = (int) (offset / (ROW_H + GAP));
            if (offset % (ROW_H + GAP) <= ROW_H) {
                var index = (white ? whiteScroll : blackScroll) + row;
                if (index >= 0 && index < ids.size()) {
                    ids.remove(index);
                    sendUpdate();
                }
            }
            return true;
        }

        private void addToList(List<String> target, List<String> other, String id) {
            other.remove(id);
            if (!target.contains(id) && target.size() < MAX_EFFECT_LIST_SIZE) target.add(id);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            var step = Mth.sign(scrollY);
            if (inside(mouseX, mouseY, leftX, listY, leftW, listBottom - listY)) {
                effectScroll = Mth.clamp(effectScroll - step, 0,
                        maxScroll(filteredEffects.size(), effectVisibleRows()));
                return true;
            }
            var listW = sideListWidth();
            if (inside(mouseX, mouseY, whiteX, sideListY, listW, sideListBottom - sideListY)) {
                whiteScroll = Mth.clamp(whiteScroll - step, 0,
                        maxScroll(data.whitelist.size(), sideVisibleRows()));
                return true;
            }
            if (inside(mouseX, mouseY, blackX, sideListY, listW, sideListBottom - sideListY)) {
                blackScroll = Mth.clamp(blackScroll - step, 0,
                        maxScroll(data.blacklist.size(), sideVisibleRows()));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public void onClose() {
            sendUpdate();
            super.onClose();
        }

        private void sendUpdate() {
            normalizeData(data);
            MisakaNetworkClient.send(new UpdatePacket(
                    data.mode,
                    data.whitelist,
                    data.blacklist,
                    data.forcedMovementProtection
            ));
        }

        private void setData(Data updated) {
            data.mode = updated.mode;
            data.whitelist = new ArrayList<>(updated.whitelist);
            data.blacklist = new ArrayList<>(updated.blacklist);
            data.forcedMovementProtection = updated.forcedMovementProtection;
            normalizeData(data);
        }

        private int effectVisibleRows() {
            return Math.max(1, (listBottom - listY) / (ROW_H + GAP));
        }

        private int sideVisibleRows() {
            return Math.max(1, (sideListBottom - sideListY) / (ROW_H + GAP));
        }

        private int rightContentWidth() {
            return Math.max(1, rightW - RIGHT_CONTENT_INSET);
        }

        private int sideListWidth() {
            return Math.max(1, (rightContentWidth() - 8) / 2);
        }

        private Rect searchFrame() {
            return new Rect(
                    searchBox.getX() - SEARCH_FRAME_PAD_X,
                    searchBox.getY() - SEARCH_FRAME_PAD_Y,
                    searchBox.getWidth() + SEARCH_FRAME_PAD_X * 2,
                    SEARCH_H + SEARCH_FRAME_PAD_Y * 2
            );
        }

        private String effectDisplayName(String id) {
            for (var entry : allEffects) {
                if (entry.id.equals(id)) return entry.name;
            }
            return id;
        }

        private void fillSection(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
            graphics.fill(x, y, x + width, y + height, SECTION);
            border(graphics, x, y, width, height, BORDER_DIM);
        }

        private void drawInput(
                GuiGraphicsExtractor graphics,
                int x,
                int y,
                int width,
                int height,
                boolean focused
        ) {
            graphics.fill(x, y, x + width, y + height, focused ? INPUT_FOCUSED : INPUT);
            border(graphics, x, y, width, height, focused ? ACTIVE : BORDER_DIM);
            if (focused) graphics.fill(x + 1, y + 2, x + 3, y + height - 2, ACTIVE);
        }

        private static void border(
                GuiGraphicsExtractor graphics,
                int x,
                int y,
                int width,
                int height,
                int color
        ) {
            graphics.fill(x + 1, y, x + width - 1, y + 1, color);
            graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
            graphics.fill(x, y + 1, x + 1, y + height - 1, color);
            graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
        }

        private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                Component label, int mouseX, int mouseY, boolean enabled, boolean selected) {
            var hover = enabled && inside(mouseX, mouseY, x, y, width, height);
            var background = !enabled ? 0x18000000
                    : selected ? ROW_SELECTED
                    : hover ? ROW : CONTROL;
            graphics.fill(x, y, x + width, y + height, background);
            border(graphics, x, y, width, height,
                    selected ? ACTIVE : hover ? BORDER : BORDER_DIM);
            if (selected) graphics.fill(x + 1, y + 2, x + 3, y + height - 2, ACTIVE);
            var color = enabled ? TEXT : DISABLED;
            graphics.centeredText(font, font.plainSubstrByWidth(label.getString(), Math.max(1, width - 6)),
                    x + width / 2, y + (height - 8) / 2, color);
        }

        @Override
        public String debugLayoutId() {
            return serializedLayoutId;
        }

        @Override
        public FrameLayoutWidget debugLayoutRoot() {
            return serializedLayout;
        }

        private record EffectEntry(String id, String name, MobEffectCategory category) {
        }

        private record Rect(int x, int y, int width, int height) {
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RequestPacket extends Packet<ServerGamePacketListenerImpl, RequestPacket> {
        public static final RequestPacket INSTANCE = new RequestPacket();
        public static final StreamCodec<ByteBuf, RequestPacket> CODEC = StreamCodec.unit(INSTANCE);

        private RequestPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, RequestPacket> getPacketType() {
            return PacketTypes.REFLECTION_FILTER_REQUEST.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UpdatePacket extends Packet<ServerGamePacketListenerImpl, UpdatePacket> {
        public static final StreamCodec<ByteBuf, UpdatePacket> CODEC = StreamCodec.of(
                (buf, packet) -> writeFilterPayload(
                        buf, packet.mode, packet.whitelist, packet.blacklist,
                        packet.forcedMovementProtection
                ),
                buf -> {
                    var payload = readFilterPayload(buf);
                    return new UpdatePacket(
                            payload.mode,
                            payload.whitelist,
                            payload.blacklist,
                            payload.forcedMovementProtection
                    );
                }
        );
        private final String mode;
        private final List<String> whitelist;
        private final List<String> blacklist;
        private final boolean forcedMovementProtection;

        public UpdatePacket(
                String mode,
                List<String> whitelist,
                List<String> blacklist,
                boolean forcedMovementProtection
        ) {
            this.mode = mode;
            this.whitelist = List.copyOf(whitelist);
            this.blacklist = List.copyOf(blacklist);
            this.forcedMovementProtection = forcedMovementProtection;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UpdatePacket> getPacketType() {
            return PacketTypes.REFLECTION_FILTER_UPDATE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> writeFilterPayload(
                        buf, packet.mode, packet.whitelist, packet.blacklist,
                        packet.forcedMovementProtection
                ),
                buf -> {
                    var payload = readFilterPayload(buf);
                    return new SyncPacket(
                            payload.mode,
                            payload.whitelist,
                            payload.blacklist,
                            payload.forcedMovementProtection
                    );
                }
        );
        private final String mode;
        private final List<String> whitelist;
        private final List<String> blacklist;
        private final boolean forcedMovementProtection;

        public SyncPacket(
                String mode,
                List<String> whitelist,
                List<String> blacklist,
                boolean forcedMovementProtection
        ) {
            this.mode = mode;
            this.whitelist = List.copyOf(whitelist);
            this.blacklist = List.copyOf(blacklist);
            this.forcedMovementProtection = forcedMovementProtection;
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.REFLECTION_FILTER_SYNC.get();
        }
    }

    private record FilterPayload(
            String mode,
            List<String> whitelist,
            List<String> blacklist,
            boolean forcedMovementProtection
    ) {
    }
}
