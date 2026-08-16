package org.academy.internal.common.world.level.material;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Converts dropped item batches that remain submerged in imag phase fluid. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ImagPhaseItemFusion {
    private static final String PROGRESS_TAG = "academy.imag_phase_fusion_ticks";
    private static final List<FusionRule> RULES = List.of(
            rule(net.minecraft.world.item.Items.CLAY_BALL, 1,
                    Items.IMAG_PHASE_POLYMER, 10),
            rule(net.minecraft.world.item.Items.IRON_NUGGET, 36,
                    Items.IMAG_PHASE_INGOT, 10),
            rule(net.minecraft.world.item.Items.DIAMOND, 4,
                    Items.IMAG_PHASE_CRYSTAL, 40),
            rule(net.minecraft.world.item.Items.EMERALD, 4,
                    Items.IMAG_PHASE_CRYSTAL, 40),
            rule(net.minecraft.world.item.Items.AMETHYST_SHARD, 32,
                    () -> net.minecraft.world.item.Items.NETHERITE_SCRAP, 40),
            rule(net.minecraft.world.item.Items.GLOW_INK_SAC, 1,
                    () -> net.minecraft.world.item.Items.GLOWSTONE_DUST, 20)
    );

    private ImagPhaseItemFusion() {
    }

    /** JEI-facing immutable views generated from the same rules used by the world ticker. */
    public static List<DisplayRecipe> displayRecipes() {
        return RULES.stream().map(rule -> {
            var input = new ItemStack(rule.input().get().asItem(), rule.inputCount());
            var output = new ItemStack(rule.output().get().asItem());
            var inputId = BuiltInRegistries.ITEM.getKey(input.getItem());
            return new DisplayRecipe(
                    input,
                    output,
                    rule.intervalTicks(),
                    AcademyCraft.academy("imag_phase_fusion/" + inputId.getPath())
            );
        }).toList();
    }

    @SubscribeEvent
    public static void onItemTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item)
                || !(item.level() instanceof ServerLevel level)
                || item.isRemoved()) {
            return;
        }

        var rule = findRule(item.getItem()).orElse(null);
        if (rule == null || !isInImagPhaseFluid(item)) {
            item.getPersistentData().remove(PROGRESS_TAG);
            return;
        }

        var data = item.getPersistentData();
        var progress = data.getInt(PROGRESS_TAG).orElse(0) + 1;
        if (progress < rule.intervalTicks()) {
            data.putInt(PROGRESS_TAG, progress);
            return;
        }

        data.remove(PROGRESS_TAG);
        convertOneBatch(level, item, rule);
    }

    static Optional<FusionRule> findRule(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return RULES.stream()
                .filter(rule -> rule.matches(stack))
                .findFirst();
    }

    private static boolean isInImagPhaseFluid(ItemEntity item) {
        return item.level().getFluidState(item.blockPosition()).is(Fluids.IMAG_PHASE.get());
    }

    private static void convertOneBatch(ServerLevel level, ItemEntity item, FusionRule rule) {
        var input = item.getItem();
        var result = new ItemStack(rule.output().get().asItem());
        if (input.getCount() == rule.inputCount()) {
            item.setItem(result);
            return;
        }

        input.shrink(rule.inputCount());
        var output = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), result);
        output.setDeltaMovement(item.getDeltaMovement());
        output.setDefaultPickUpDelay();
        level.addFreshEntity(output);
    }

    private static FusionRule rule(
            ItemLike input,
            int inputCount,
            Supplier<? extends ItemLike> output,
            int intervalTicks
    ) {
        return new FusionRule(() -> input, inputCount, output, intervalTicks);
    }

    record FusionRule(
            Supplier<? extends ItemLike> input,
            int inputCount,
            Supplier<? extends ItemLike> output,
            int intervalTicks
    ) {
        FusionRule {
            if (inputCount < 1) throw new IllegalArgumentException("Input count must be positive");
            if (intervalTicks < 1) throw new IllegalArgumentException("Interval must be positive");
        }

        boolean matches(ItemStack stack) {
            return stack.is(input.get().asItem()) && stack.getCount() >= inputCount;
        }
    }

    public record DisplayRecipe(
            ItemStack input,
            ItemStack output,
            int intervalTicks,
            Identifier id
    ) {
        public DisplayRecipe {
            input = input.copy();
            output = output.copy();
            if (input.isEmpty() || output.isEmpty() || intervalTicks < 1 || id == null) {
                throw new IllegalArgumentException("Invalid imag phase fusion display recipe");
            }
        }

        @Override
        public ItemStack input() {
            return input.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }
}
