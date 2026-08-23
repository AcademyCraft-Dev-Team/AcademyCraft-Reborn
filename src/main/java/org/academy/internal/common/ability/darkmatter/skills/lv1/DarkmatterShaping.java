package org.academy.internal.common.ability.darkmatter.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.darkmatter.DarkmatterModifiers;
import org.academy.api.common.ability.darkmatter.DarkmatterBlockProfile;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingProfile;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingRegistries;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.gui.screen.DarkmatterShapingScreen;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.academy.internal.common.world.item.Items;
import org.academy.mixin.client.AbstractContainerScreenAccessor;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DarkmatterShaping extends Skill {
    private static final float MATERIAL_MATTER_COST = 1.0f;

    public DarkmatterShaping() {
        super(Builder.of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(50)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.DARKMATTER_GENERATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Generation", "academy:darkmatter_generation")));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var configured = Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_U,
                        InputConstants.PRESS, 0, true)
        );
        // Older saves kept this one-shot skill disabled while a container screen was open,
        // which made the inventory-slot material action unreachable. Preserve the selected
        // physical key while migrating the action and screen policy required by this skill.
        configured = new InputSystem.KeyCombination(
                configured.type(), configured.keys(), InputConstants.PRESS,
                configured.modifiers(), true, configured.unbound());
        Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, configured);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, configured, _ -> Client.cast());
        MisakaNetworkClient.NETWORK_MANAGER.register(ClientPackets.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        DarkmatterModifiers.bootstrap();
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_SHAPING.get(),
                        List.of(DarkmatterGeneration.Client.SKILL_INFO),
                        R.textures.darkmatter_shaping_icon,
                        58, 72));
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_SHAPING + "_cast";
        public static Config CONFIG = new Config();

        private Client() { }

        private static void cast() {
            if (!AbilitySystemClient.canUseSkill(Skills.DARKMATTER_SHAPING.get())) return;
            var minecraft = Minecraft.getInstance();
            var current = minecraft.gui.screen();
            if (current instanceof DarkmatterShapingScreen) return;
            if (current instanceof AbstractContainerScreen<?> screen
                    && (screen instanceof InventoryScreen
                    || screen instanceof CreativeModeInventoryScreen)
                    && minecraft.player != null) {
                var slot = ((AbstractContainerScreenAccessor) screen).academy$getHoveredSlot();
                if (slot != null && slot.getItem().isEmpty() && slot.mayPlace(
                        new ItemStack(Items.DARKMATTER.get()))) {
                    MisakaNetworkClient.send(CastPacket.material(slot.getContainerSlot()));
                    return;
                }
            }
            minecraft.gui.setScreen(new DarkmatterShapingScreen());
        }

        public static void shape(DarkmatterShape shape, int alphaPercent,
                                 Map<String, Integer> modifiers) {
            shape(shape, alphaPercent, modifiers, DarkmatterBlockProfile.DEFAULT);
        }

        public static void shape(DarkmatterShape shape, int alphaPercent,
                                 Map<String, Integer> modifiers,
                                 DarkmatterBlockProfile blockProfile) {
            MisakaNetworkClient.send(CastPacket.shape(
                    shape, alphaPercent, modifiers, blockProfile));
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() { }
                @Override public Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private Server() { }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var result = packet.usage() == Usage.MATERIAL_SLOT
                    ? createMaterialResult(player, packet.slotIndex())
                    : createShapedResult(player, packet.shape(), packet.alphaPercent(),
                    packet.modifiers(), packet.blockProfile());
            MisakaNetworkServer.send(player, new ResultPacket(result));
        }

        public static boolean createShaped(ServerPlayer player, DarkmatterShape shape,
                                           int alphaPercent, Map<String, Integer> requestedModifiers) {
            return createShapedResult(player, shape, alphaPercent, requestedModifiers)
                    == Result.SHAPED;
        }

        private static Result createShapedResult(ServerPlayer player, DarkmatterShape shape,
                                                 int alphaPercent,
                                                 Map<String, Integer> requestedModifiers) {
            return createShapedResult(player, shape, alphaPercent, requestedModifiers,
                    DarkmatterBlockProfile.DEFAULT);
        }

        private static Result createShapedResult(ServerPlayer player, DarkmatterShape shape,
                                                 int alphaPercent,
                                                 Map<String, Integer> requestedModifiers,
                                                 DarkmatterBlockProfile blockProfile) {
            var skill = Skills.DARKMATTER_SHAPING.get();
            if (!skill.isEnabled(player)) return Result.UNAVAILABLE;
            var system = AbilitySystemServer.getSystem(player);
            var level = Math.clamp(system.getPlayerLevel(player.getUUID()), 1, 5);
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var validation = validateModifiers(shape, requestedModifiers, level, milestone);
            if (!validation.valid()) {
                return validation.hasLevelGateError() ? Result.LOCKED_LEVEL : Result.INVALID_PROFILE;
            }

            var total = level * 50;
            var alpha = Math.round(total * Math.clamp(alphaPercent, 0, 100) / 100.0f);
            var profile = new DarkmatterShapingProfile(
                    level, alpha, total - alpha, validation.modifiers());
            var cost = shapingCost(shape.baseMatterCost()
                    + validation.usedPoints() * 0.5f, milestone);
            var resource = system.getDarkmatterResourceManager();
            if (!resource.consume(player, cost, skill, skill.getIterationTicks(player))) {
                return Result.INSUFFICIENT_MP;
            }

            for (var output : createOutputs(shape, profile, blockProfile)) {
                player.getInventory().placeItemBackInInventory(output);
            }
            skill.reportTrigger(player);
            skill.reportActivity(player, true);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return Result.SHAPED;
        }

        private static List<ItemStack> createOutputs(DarkmatterShape shape,
                                                     DarkmatterShapingProfile profile,
                                                     DarkmatterBlockProfile blockProfile) {
            var output = new ArrayList<ItemStack>();
            if (shape == DarkmatterShape.ARMOR) {
                output.add(profile(new ItemStack(Items.DARK_MATTER_HELMET.get()), profile));
                output.add(profile(new ItemStack(Items.DARK_MATTER_CHESTPLATE.get()), profile));
                output.add(profile(new ItemStack(Items.DARK_MATTER_LEGGINGS.get()), profile));
                output.add(profile(new ItemStack(Items.DARK_MATTER_BOOTS.get()), profile));
                return output;
            }
            var stack = new ItemStack(switch (shape) {
                case TOOL -> Items.DARKMATTER_TOOL.get();
                case SWORD -> Items.DARKMATTER_SWORD.get();
                case SPEAR -> Items.DARKMATTER_SPEAR.get();
                case TRIDENT -> Items.DARKMATTER_TRIDENT.get();
                case BOW -> Items.DARKMATTER_BOW.get();
                case CROSSBOW -> Items.DARKMATTER_CROSSBOW.get();
                case MACE -> Items.DARKMATTER_MACE.get();
                case ARROW -> Items.DARKMATTER_ARROW.get();
                case COATING -> Items.DARKMATTER_COATING.get();
                case BLOCK -> Items.DARKMATTER_BLOCK.get();
                case ARMOR -> throw new IllegalStateException("Armor handled above");
            }, shape.outputCount());
            if (shape == DarkmatterShape.BLOCK) {
                org.academy.internal.common.world.item.DarkmatterBlockItem.setProfile(
                        stack, blockProfile);
            }
            output.add(profile(stack, profile));
            return output;
        }

        private static ItemStack profile(ItemStack stack, DarkmatterShapingProfile profile) {
            DarkmatterItemUtil.setShapingProfile(stack, profile);
            return stack;
        }

        public static ModifierValidation validateModifiers(
                DarkmatterShape shape, Map<String, Integer> requested,
                int abilityLevel, int milestone
        ) {
            DarkmatterModifiers.bootstrap();
            var normalized = new LinkedHashMap<String, Integer>();
            var errors = new ArrayList<String>();
            var used = 0;
            var level = Math.clamp(abilityLevel, 1, 5);
            if (shape == null) {
                errors.add("shape:missing");
            } else if (!shape.isUnlockedAt(level)) {
                errors.add("locked_shape:" + shape.id() + ":" + shape.requiredAbilityLevel());
            }
            if (requested != null) for (var entry : requested.entrySet()) {
                var type = DarkmatterShapingRegistries.modifier(entry.getKey()).orElse(null);
                var requestedLevel = entry.getValue() == null ? 0 : entry.getValue();
                if (type == null || requestedLevel < 0 || requestedLevel > type.maxLevel()) {
                    errors.add("modifier:" + entry.getKey());
                    continue;
                }
                if (requestedLevel == 0) continue;
                if (!type.isUnlockedAt(level)) {
                    errors.add("locked_modifier:" + type.id() + ":" + type.requiredAbilityLevel());
                    continue;
                }
                if (!type.supports(shape)) {
                    errors.add("incompatible:" + entry.getKey());
                    continue;
                }
                normalized.put(type.id(), requestedLevel);
                used += type.pointCost() * requestedLevel;
            }
            for (var entry : normalized.entrySet()) {
                var type = DarkmatterShapingRegistries.modifier(entry.getKey()).orElseThrow();
                if (type.conflicts().stream().anyMatch(normalized::containsKey)) {
                    errors.add("conflict:" + type.id());
                }
            }
            var budget = modifierBudget(level, milestone);
            if (used > budget) errors.add("budget:" + used + "/" + budget);
            return new ModifierValidation(errors.isEmpty(), Map.copyOf(normalized),
                    used, budget, List.copyOf(errors));
        }

        public static int modifierBudget(int abilityLevel, int milestone) {
            return 2 + 2 * Math.clamp(abilityLevel, 1, 5) + Math.clamp(milestone, 0, 3);
        }

        private static Result createMaterialResult(ServerPlayer player, int slotIndex) {
            var skill = Skills.DARKMATTER_SHAPING.get();
            if (!skill.isEnabled(player)) return Result.UNAVAILABLE;
            var inventory = player.getInventory();
            if (!isMaterialInventorySlot(slotIndex)
                    || !inventory.getItem(slotIndex).isEmpty()) return Result.INVALID_SLOT;
            var stack = new ItemStack(Items.DARKMATTER.get());
            var resource = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
            if (!resource.consume(player, shapingCost(MATERIAL_MATTER_COST,
                    skill.getEffectiveProficiencyMilestone(player)), skill,
                    skill.getIterationTicks(player))) return Result.INSUFFICIENT_MP;
            inventory.setItem(slotIndex, stack);
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            skill.reportTrigger(player);
            return Result.MATERIAL;
        }

        static boolean isMaterialInventorySlot(int slotIndex) {
            // Player inventory indices 0..35 are storage/hotbar; 40 is the offhand.
            // Armor indices deliberately remain invalid even if a forged request targets them.
            return slotIndex >= 0 && slotIndex < 36 || slotIndex == 40;
        }

        public static boolean createMaterialForTesting(ServerPlayer player, int slotIndex) {
            return createMaterialResult(player, slotIndex) == Result.MATERIAL;
        }

        public static void refreshHeldNativeProfile(ServerPlayer player) {
            var held = player.getMainHandItem();
            if (!DarkmatterItemUtil.hasNativeItemEffects(held)) return;
            if (!DarkmatterItemUtil.isOperational(held)) {
                DarkmatterItemUtil.setEnchantmentLevel(player.registryAccess(), held,
                        Enchantments.EFFICIENCY, 0);
                DarkmatterItemUtil.setEnchantmentLevel(player.registryAccess(), held,
                        Enchantments.FORTUNE, 0);
                return;
            }
            var alpha = DarkmatterItemUtil.effectAlphaPower(held);
            var beta = DarkmatterItemUtil.effectBetaPower(held);
            var fortune = toolFortune(beta)
                    + DarkmatterItemUtil.modifierLevel(held, DarkmatterModifiers.LUCKY);
            var changed = false;
            if (DarkmatterItemUtil.shape(held) == DarkmatterShape.TOOL) {
                changed |= DarkmatterItemUtil.setEnchantmentLevel(player.registryAccess(), held,
                        Enchantments.EFFICIENCY, toolEfficiency(alpha));
                changed |= DarkmatterItemUtil.setEnchantmentLevel(player.registryAccess(), held,
                        Enchantments.FORTUNE, fortune);
            }
            if (DarkmatterItemUtil.shape(held).isOffensive()
                    && DarkmatterItemUtil.shape(held) != DarkmatterShape.TOOL) {
                changed |= DarkmatterItemUtil.setEnchantmentLevel(player.registryAccess(), held,
                        Enchantments.LOOTING,
                        DarkmatterItemUtil.modifierLevel(held, DarkmatterModifiers.LUCKY));
            }
            if (changed) player.getInventory().setChanged();
        }

        public static int toolEfficiency(float alphaPower) {
            var alpha = finitePower(alphaPower);
            return Math.round(1.5f * alpha + 0.1f * alpha * alpha);
        }
        public static int toolFortune(float betaPower) { return Math.round(finitePower(betaPower)); }
        public static float miningSpeedBonus(float alphaPower) {
            var efficiency = toolEfficiency(alphaPower);
            return efficiency <= 0 ? 0.0f : efficiency * efficiency + 1.0f;
        }
        public static float spearDamage(float alphaPower) { return 5.0f + 2.0f * finitePower(alphaPower); }
        public static float spearRange(float alphaPower) { return 8.0f + 2.0f * finitePower(alphaPower); }
        public static float spearSpeed(float betaPower) { return 1.5f + 0.2f * finitePower(betaPower); }
        public static float spearPenetration(float betaPower) {
            return Math.min(0.50f, 0.10f * finitePower(betaPower));
        }
        public static float directDamage(DarkmatterShape shape, float alphaPower) {
            var base = switch (shape) {
                case TOOL -> 6.0f;
                case SWORD -> 7.0f;
                case SPEAR -> 5.0f;
                case TRIDENT -> 8.0f;
                case MACE -> 0.0f;
                case BOW, CROSSBOW, ARROW -> 3.0f;
                case ARMOR -> 1.0f;
                case COATING, BLOCK -> 0.0f;
            };
            return base + phaseDamageBonus(alphaPower);
        }
        public static float phaseDamageBonus(float alphaPower) {
            return 2.0f * finitePower(alphaPower);
        }
        public static float penetration(DarkmatterShape shape, float betaPower) {
            var scale = shape == DarkmatterShape.SPEAR || shape == DarkmatterShape.TRIDENT
                    ? 0.10f : 0.08f;
            return Math.min(shape == DarkmatterShape.SPEAR || shape == DarkmatterShape.TRIDENT
                    ? 0.50f : 0.40f, scale * finitePower(betaPower));
        }
        public static float armorReduction(float alphaPower) {
            return Math.min(0.20f, 0.04f * finitePower(alphaPower));
        }
        public static int armorWeaknessTicks(float betaPower) {
            return 20 + Math.round(10.0f * finitePower(betaPower));
        }
        private static float finitePower(float power) {
            return Float.isFinite(power) ? Math.max(0.0f, power) : 0.0f;
        }
        static float shapingCost(float base, int milestone) {
            return Math.max(0.0f, base) * (Math.clamp(milestone, 0, 3) >= 1 ? 0.9f : 1.0f);
        }
        public static float gammaShapingMultiplier(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 3 ? 1.25f : 1.0f;
        }
        static boolean repairsOnEnchant(int milestone) { return false; }
        static boolean unlocksAutoRepair(int milestone) { return false; }

        public record ModifierValidation(boolean valid, Map<String, Integer> modifiers,
                                         int usedPoints, int budget, List<String> errors) {
            public boolean hasLevelGateError() {
                return errors.stream().anyMatch(error -> error.startsWith("locked_"));
            }
        }
    }

    public static final class ClientPackets {
        private ClientPackets() { }

        @SubscribePacket
        public static void handle(ResultPacket packet) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof DarkmatterShapingScreen screen) {
                screen.acceptServerResult(packet.result());
            } else if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable(
                        packet.result().translationKey()));
            }
        }
    }

    public enum Result {
        SHAPED("screen.academy.darkmatter_shaping.result.shaped"),
        MATERIAL("screen.academy.darkmatter_shaping.result.material"),
        UNAVAILABLE("screen.academy.darkmatter_shaping.result.unavailable"),
        INVALID_SLOT("screen.academy.darkmatter_shaping.result.invalid_slot"),
        INVALID_PROFILE("screen.academy.darkmatter_shaping.result.invalid_profile"),
        LOCKED_LEVEL("screen.academy.darkmatter_shaping.result.locked_level"),
        INSUFFICIENT_MP("screen.academy.darkmatter_shaping.result.insufficient_mp");

        private final String translationKey;

        Result(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum Usage { SHAPE, MATERIAL_SLOT }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buffer, packet.usage.ordinal());
                    ByteBufCodecs.STRING_UTF8.encode(buffer, packet.shape.id());
                    ByteBufCodecs.VAR_INT.encode(buffer, packet.alphaPercent);
                    ByteBufCodecs.map(LinkedHashMap::new,
                            ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)
                            .encode(buffer, new LinkedHashMap<>(packet.modifiers));
                    DarkmatterBlockProfile.STREAM_CODEC.encode(buffer, packet.blockProfile);
                    ByteBufCodecs.VAR_INT.encode(buffer, packet.slotIndex);
                },
                buffer -> new CastPacket(
                        Usage.values()[Math.clamp(ByteBufCodecs.VAR_INT.decode(buffer),
                                0, Usage.values().length - 1)],
                        DarkmatterShape.byId(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.map(LinkedHashMap::new,
                                ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT).decode(buffer),
                        DarkmatterBlockProfile.STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer)));

        private final Usage usage;
        private final DarkmatterShape shape;
        private final int alphaPercent;
        private final Map<String, Integer> modifiers;
        private final DarkmatterBlockProfile blockProfile;
        private final int slotIndex;

        private CastPacket(Usage usage, DarkmatterShape shape, int alphaPercent,
                           Map<String, Integer> modifiers,
                           DarkmatterBlockProfile blockProfile, int slotIndex) {
            this.usage = usage;
            this.shape = shape;
            this.alphaPercent = alphaPercent;
            this.modifiers = Map.copyOf(modifiers);
            this.blockProfile = blockProfile == null
                    ? DarkmatterBlockProfile.DEFAULT : blockProfile;
            this.slotIndex = slotIndex;
        }

        public static CastPacket shape(DarkmatterShape shape, int alphaPercent,
                                       Map<String, Integer> modifiers) {
            return shape(shape, alphaPercent, modifiers, DarkmatterBlockProfile.DEFAULT);
        }
        public static CastPacket shape(DarkmatterShape shape, int alphaPercent,
                                       Map<String, Integer> modifiers,
                                       DarkmatterBlockProfile blockProfile) {
            return new CastPacket(Usage.SHAPE, shape, alphaPercent, modifiers,
                    blockProfile, -1);
        }
        public static CastPacket material(int slotIndex) {
            return new CastPacket(Usage.MATERIAL_SLOT, DarkmatterShape.TOOL,
                    50, Map.of(), DarkmatterBlockProfile.DEFAULT, slotIndex);
        }
        public Usage usage() { return usage; }
        public DarkmatterShape shape() { return shape; }
        public int alphaPercent() { return alphaPercent; }
        public Map<String, Integer> modifiers() { return modifiers; }
        public DarkmatterBlockProfile blockProfile() { return blockProfile; }
        public int slotIndex() { return slotIndex; }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.DARKMATTER_SHAPING_CAST.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ResultPacket extends Packet<ClientPacketListener, ResultPacket> {
        public static final StreamCodec<ByteBuf, ResultPacket> CODEC = StreamCodec.of(
                (buffer, packet) -> ByteBufCodecs.VAR_INT.encode(
                        buffer, packet.result.ordinal()),
                buffer -> {
                    var ordinal = ByteBufCodecs.VAR_INT.decode(buffer);
                    if (ordinal < 0 || ordinal >= Result.values().length) {
                        throw new IllegalArgumentException(
                                "Invalid darkmatter shaping result: " + ordinal);
                    }
                    return new ResultPacket(Result.values()[ordinal]);
                });

        private final Result result;

        public ResultPacket(Result result) {
            this.result = result;
        }

        public Result result() {
            return result;
        }

        @Override
        public PacketType<ClientPacketListener, ResultPacket> getPacketType() {
            return PacketTypes.DARKMATTER_SHAPING_RESULT.get();
        }
    }
}
