package org.academy.api.server.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-thread CP occupations. Timed occupations release together after their iteration period. */
public final class AbilityCpAccount implements AbilityResourceAccount {
    public record Occupation(String token, double amount, int remainingTicks) {
        public static final Codec<Occupation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("token").forGetter(Occupation::token),
                Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(Occupation::amount),
                Codec.intRange(-1, Integer.MAX_VALUE).fieldOf("ticks").forGetter(Occupation::remainingTicks)
        ).apply(i, Occupation::new));
    }

    public static final Codec<AbilityCpAccount> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("capacity").forGetter(AbilityCpAccount::capacity),
            Occupation.CODEC.listOf().fieldOf("occupations").forGetter(AbilityCpAccount::occupations)
    ).apply(i, AbilityCpAccount::new));
    private final double capacity;
    private final List<Occupation> occupations;

    public AbilityCpAccount(double capacity) { this(capacity, List.of()); }

    private AbilityCpAccount(double capacity, List<Occupation> occupations) {
        if (!Double.isFinite(capacity) || capacity < 0) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
        this.occupations = new ArrayList<>(occupations);
    }

    public List<Occupation> occupations() { return List.copyOf(occupations); }
    public double capacity() { return capacity; }
    public double current() { return Math.max(0, capacity - occupations.stream().mapToDouble(Occupation::amount).sum()); }

    /** Null means rejected. -1 is a permanent reservation; all other durations must be positive. */
    public String reserve(double amount, int ticks) {
        if (!Double.isFinite(amount) || amount < 0 || (ticks != -1 && ticks <= 0) || amount > current()) return null;
        String token = UUID.randomUUID().toString();
        occupations.add(new Occupation(token, amount, ticks));
        return token;
    }

    public boolean release(String token) { return occupations.removeIf(o -> o.token().equals(token)); }
    public boolean releasePermanent() { return occupations.removeIf(o -> o.remainingTicks() == -1); }

    public boolean tick() {
        boolean changed = false;
        for (int i = occupations.size() - 1; i >= 0; i--) {
            var o = occupations.get(i);
            if (o.remainingTicks() < 0) continue;
            changed = true;
            if (o.remainingTicks() <= 1) occupations.remove(i);
            else occupations.set(i, new Occupation(o.token(), o.amount(), o.remainingTicks() - 1));
        }
        return changed;
    }

    public boolean tryConsume(double amount) { return reserve(amount, 20) != null; }
    /** Frees timed occupation only. Maintenance remains reserved until its handle closes. */
    public double recover(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return 0;
        double left = amount;
        for (int i = occupations.size() - 1; i >= 0 && left > 0; i--) {
            var o = occupations.get(i);
            if (o.remainingTicks() < 0) continue;
            double released = Math.min(left, o.amount());
            left -= released;
            if (released == o.amount()) occupations.remove(i);
            else occupations.set(i, new Occupation(o.token(), o.amount() - released, o.remainingTicks()));
        }
        return amount - left;
    }
}
