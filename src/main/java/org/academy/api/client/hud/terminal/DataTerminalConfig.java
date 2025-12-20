package org.academy.api.client.hud.terminal;

import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.common.gson.TypeHandler;

public class DataTerminalConfig extends KeyBindingConfig {
    public static final class Action implements TypeHandler<DataTerminalConfig> {
        public static final TypeHandler<DataTerminalConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public DataTerminalConfig getDefault() {
            return new DataTerminalConfig();
        }

        @Override
        public Class<DataTerminalConfig> getTypeClass() {
            return DataTerminalConfig.class;
        }
    }
}
