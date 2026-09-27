package org.academy.internal.common.ability.accelerator.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.data.SkillData;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
        normalizeData(data);
        var id = effectId(effect);
        var category = effect.getEffect().value().getCategory();
        return shouldAcceptNormalizedEffect(data, id, category);
    }

    static boolean shouldAcceptNormalizedEffect(Data data, @Nullable String effectId, MobEffectCategory category) {
        if (effectId != null && data.blacklist.contains(effectId)) return false;
        if (effectId != null && data.whitelist.contains(effectId)) return true;
        return switch (Mode.byName(data.mode)) {
            case REFLECT_ALL -> false;
            case POSITIVE_FILTER -> category == MobEffectCategory.BENEFICIAL;
            case NEUTRAL_FILTER -> category != MobEffectCategory.HARMFUL;
        };
    }

    public static boolean shouldAcceptEffect(ServerPlayer player, MobEffectInstance effect) {
        if (EntityMotionGuard.currentMotionSourceEntity() == player) return true;
        var filter = Skills.REFLECTION_FILTER.get();
        if (!filter.isEnabled(player)) return shouldAcceptEffect(new Data(), effect);
        return shouldAcceptEffect(Server.getOrCreateData(player), effect);
    }

    public static boolean shouldReflectEffect(ServerPlayer player, MobEffectInstance effect) {
        return !shouldAcceptEffect(player, effect);
    }

    public static boolean isForcedMovementProtectionEnabled(ServerPlayer player) {
        return Skills.REFLECTION_FILTER.get().isEnabled(player)
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

    @Nullable
    private static String effectId(MobEffectInstance effect) {
        var id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        return id == null ? null : id.toString();
    }

    static Data normalizeData(Data data) {
        data.mode = Mode.byName(data.mode).name();
        data.whitelist = normalizeEffectIds(data.whitelist);
        data.blacklist = normalizeEffectIds(data.blacklist);
        data.whitelist.removeAll(data.blacklist);
        return data;
    }

    static boolean hasSameConfiguration(Data first, Data second) {
        return first.getMode() == second.getMode()
                && first.getWhitelist().equals(second.getWhitelist())
                && first.getBlacklist().equals(second.getBlacklist())
                && first.isForcedMovementProtectionEnabled()
                == second.isForcedMovementProtectionEnabled();
    }

    static void reportEffectActivity(ServerPlayer player, boolean reflected) {
        var skill = Skills.REFLECTION_FILTER.get();
        if (skill.isEnabled(player)) skill.reportActivity(player, reflected);
    }

    private static List<String> normalizeEffectIds(List<String> input) {
        var result = new LinkedHashSet<String>();
        for (var raw : input) {
            var id = normalizeEffectId(raw);
            if (id == null) continue;
            result.add(id);
            if (result.size() >= MAX_EFFECT_LIST_SIZE) break;
        }
        return new ArrayList<>(result);
    }

    @Nullable
    private static String normalizeEffectId(String raw) {
        if (raw.isBlank()) return null;
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
        var size = Math.min(values.size(), MAX_EFFECT_LIST_SIZE);
        ByteBufCodecs.VAR_INT.encode(buf, size);
        for (var i = 0; i < size; i++) ByteBufCodecs.STRING_UTF8.encode(buf, values.get(i));
    }

    private static List<String> readStringList(ByteBuf buf) {
        int size = ByteBufCodecs.VAR_INT.decode(buf);
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
        AcademyCraftClient.Config.INSTANCE.setConfig(key, Client.CONFIG);
        AcademyCraftClient.Config.INSTANCE.save();
        InputSystem.addKeyBinding(Client.KEY_NAME_OPEN, openKey, _ -> Client.open());
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    static InputSystem.KeyCombination defaultOpenKey() {
        return InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_EQUALS,
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
            for (var mode : values()) {
                if (mode.name().equals(name)) return mode;
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
            copy.whitelist = new ArrayList<>(whitelist);
            copy.blacklist = new ArrayList<>(blacklist);
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

        String rawMode() {
            return mode;
        }

        void rawMode(String value) {
            mode = value;
        }

        List<String> mutableWhitelist() {
            return whitelist;
        }

        List<String> mutableBlacklist() {
            return blacklist;
        }

        boolean forcedMovementProtectionValue() {
            return forcedMovementProtection;
        }

        void forcedMovementProtectionValue(boolean value) {
            forcedMovementProtection = value;
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
            data.whitelist = normalizeEffectIds(packet.whitelist);
            data.blacklist = normalizeEffectIds(packet.blacklist);
            data.forcedMovementProtection = packet.forcedMovementProtection;
            data.whitelist.removeAll(data.blacklist);
            var playerData = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
            playerData.markDirty();
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
            var map = playerData.getMutableSkillDataMap();
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
        @Nullable
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
