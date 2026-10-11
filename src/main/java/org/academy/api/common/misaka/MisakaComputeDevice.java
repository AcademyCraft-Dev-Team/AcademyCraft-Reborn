package org.academy.api.common.misaka;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Immutable registration for one Misaka compute device kind.
 * Stack count is never read by the network — presence is boolean only.
 */
public final class MisakaComputeDevice {
    private final Identifier id;
    private final Component displayName;
    private final MisakaComputePresence presence;
    private final MisakaComputeSpendMode spendMode;
    private final Predicate<ItemStack> matches;
    private final @Nullable BiPredicate<ServerPlayer, ItemStack> isLoaded;
    private final @Nullable BiConsumer<ServerPlayer, Float> onCompute;

    private MisakaComputeDevice(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.presence = builder.presence;
        this.spendMode = builder.spendMode;
        this.matches = builder.matches;
        this.isLoaded = builder.isLoaded;
        this.onCompute = builder.onCompute;
    }

    public Identifier id() {
        return id;
    }

    public Component displayName() {
        return displayName;
    }

    public MisakaComputePresence presence() {
        return presence;
    }

    public MisakaComputeSpendMode spendMode() {
        return spendMode;
    }

    public boolean matches(ItemStack stack) {
        return matches.test(stack);
    }

    public boolean isLoaded(ServerPlayer player, ItemStack stack) {
        if (presence != MisakaComputePresence.LOADED) {
            return false;
        }
        return isLoaded != null && isLoaded.test(player, stack);
    }

    public void deliverForce(ServerPlayer player, float msk) {
        if (spendMode != MisakaComputeSpendMode.FORCE || onCompute == null) {
            return;
        }
        onCompute.accept(player, msk);
    }

    public static final class Builder {
        private final Identifier id;
        private @Nullable Component displayName;
        private @Nullable MisakaComputePresence presence;
        private @Nullable MisakaComputeSpendMode spendMode;
        private @Nullable Predicate<ItemStack> matches;
        private @Nullable BiPredicate<ServerPlayer, ItemStack> isLoaded;
        private @Nullable BiConsumer<ServerPlayer, Float> onCompute;

        public Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder displayName(Component displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder presence(MisakaComputePresence presence) {
            this.presence = Objects.requireNonNull(presence, "presence");
            return this;
        }

        public Builder spendMode(MisakaComputeSpendMode spendMode) {
            this.spendMode = Objects.requireNonNull(spendMode, "spendMode");
            return this;
        }

        public Builder matches(Predicate<ItemStack> matches) {
            this.matches = Objects.requireNonNull(matches, "matches");
            return this;
        }

        public Builder isLoaded(BiPredicate<ServerPlayer, ItemStack> isLoaded) {
            this.isLoaded = Objects.requireNonNull(isLoaded, "isLoaded");
            return this;
        }

        public Builder onCompute(BiConsumer<ServerPlayer, Float> onCompute) {
            this.onCompute = Objects.requireNonNull(onCompute, "onCompute");
            return this;
        }

        public MisakaComputeDevice build() {
            if (displayName == null) {
                throw new IllegalStateException("Misaka compute device " + id + " missing displayName");
            }
            if (presence == null) {
                throw new IllegalStateException("Misaka compute device " + id + " missing presence");
            }
            if (spendMode == null) {
                throw new IllegalStateException("Misaka compute device " + id + " missing spendMode");
            }
            if (matches == null) {
                throw new IllegalStateException("Misaka compute device " + id + " missing matches");
            }
            if (presence == MisakaComputePresence.LOADED && isLoaded == null) {
                throw new IllegalStateException("Misaka compute device " + id + " LOADED requires isLoaded");
            }
            if (spendMode == MisakaComputeSpendMode.FORCE && onCompute == null) {
                throw new IllegalStateException("Misaka compute device " + id + " FORCE requires onCompute");
            }
            if (spendMode == MisakaComputeSpendMode.ACTUAL && onCompute != null) {
                throw new IllegalStateException("Misaka compute device " + id + " ACTUAL must not set onCompute");
            }
            return new MisakaComputeDevice(this);
        }
    }
}
