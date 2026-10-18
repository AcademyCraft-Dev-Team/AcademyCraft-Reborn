package org.academy.api.server.ability;

public interface AbilityResourceUsage extends AutoCloseable {
    @Override
    void close();
}
