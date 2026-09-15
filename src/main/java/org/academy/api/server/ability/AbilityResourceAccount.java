package org.academy.api.server.ability;

/** Server-thread resource ledger. Amounts are final costs, with discounts already applied. */
public interface AbilityResourceAccount {
    double current();
    double capacity();
    boolean tryConsume(double amount);
    double recover(double amount);

    default double consumeUpTo(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return 0;
        var accepted = Math.min(amount, current());
        return tryConsume(accepted) ? accepted : 0;
    }
}
