package org.academy.api.client.hud.terminal;

import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.common.gson.TypeHandler;

public final class TerminalConfig extends KeyBindingConfig {
    public static final class Action implements TypeHandler<TerminalConfig> {
        public static final TypeHandler<TerminalConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public TerminalConfig getDefault() {
            return new TerminalConfig();
        }

        @Override
        public Class<TerminalConfig> getTypeClass() {
            return TerminalConfig.class;
        }
    }
}
