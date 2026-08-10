package org.academy.internal.common.ability.mentalout.skills;

import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.ability.mentalout.PrecisionOperationClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;
import org.academy.internal.common.skilldata.SkillData;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public final class PrecisionOperation extends Skill {
    public PrecisionOperation() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(0)
                .iterationTicks(0)
                .maxStacks(1)
                .dependsOn(Skills.IMPRESSION_MANIPULATION, Skills.MENTAL_STUPOR)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Impression Manipulation", "academy:impression_manipulation"))
                .devCondition(new DevCondition.DependencyCondition(
                        "Mental Stupor", "academy:mental_stupor"))
                .withCustomData(Data.ID, Data.class, _ -> new Data())
        );
    }

    public static Data normalizeData(Data data) {
        if (data == null) data = new Data();
        var migrateFlow = data.schemaVersion == 1;
        var legacy = data.schemaVersion > 0 && data.schemaVersion < Data.SCHEMA_VERSION;
        data.revision = Math.max(0L, data.revision);
        var normalized = new ArrayList<PrecisionGraph>(4);
        var source = data.slots == null ? List.<PrecisionGraph>of() : data.slots;
        for (var slot = 0; slot < 4; slot++) {
            var graph = slot < source.size() && source.get(slot) != null
                    ? source.get(slot)
                    : PrecisionGraph.EMPTY;
            if (migrateFlow) {
                var migration = PrecisionGraph.migrateLegacy(graph);
                normalized.add(migration.valid() ? migration.graph() : PrecisionGraph.EMPTY);
            } else {
                var validation = graph.validate();
                normalized.add(validation.valid() ? validation.normalized() : PrecisionGraph.EMPTY);
            }
        }
        data.slots = normalized;
        if (legacy) data.revision++;
        data.schemaVersion = Data.SCHEMA_VERSION;
        return data;
    }

    @Override
    public void initClient() {
        PrecisionOperationManager.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        var selectedTemplate = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_G,
                InputConstants.PRESS,
                0
        );
        var editorTemplate = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_BACKSLASH,
                InputConstants.PRESS,
                0
        );
        InputSystem.addKeyBinding(
                Client.KEY_NAME_EXECUTE_SELECTED,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_EXECUTE_SELECTED,
                        selectedTemplate
                ),
                context -> {
                    if (context.action() == InputConstants.PRESS) PrecisionOperationClient.executeSelected();
                }
        );
        InputSystem.addKeyBinding(Client.KEY_NAME_EDIT, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_EDIT,
                editorTemplate
        ), _ -> PrecisionOperationClient.openEditor());
        var keys = new int[]{
                InputConstants.KEY_1,
                InputConstants.KEY_2,
                InputConstants.KEY_3,
                InputConstants.KEY_4
        };
        for (var slot = 0; slot < 4; slot++) {
            var currentSlot = slot;
            var keyName = Client.KEY_NAME_SLOT_PREFIX + (slot + 1);
            InputSystem.addKeyBinding(keyName, Client.CONFIG.getKeyBinding(
                    keyName,
                    InputSystem.combo(
                            InputSystem.InputType.KEYBOARD,
                            keys[slot],
                            InputConstants.PRESS,
                            InputConstants.MOD_ALT
                    )
            ), _ -> PrecisionOperationClient.execute(currentSlot));
        }
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        PrecisionOperationManager.initServer();
    }

    public static final class Data extends SkillData {
        public static final int SCHEMA_VERSION = 3;
        public static final Identifier ID = AcademyCraft.academy("precision_operation");

        @SerializedName("schemaVersion")
        private int schemaVersion = SCHEMA_VERSION;
        @SerializedName("revision")
        private long revision;
        @SerializedName("slots")
        private List<PrecisionGraph> slots = new ArrayList<>(List.of(
                PrecisionGraph.EMPTY,
                PrecisionGraph.EMPTY,
                PrecisionGraph.EMPTY,
                PrecisionGraph.EMPTY
        ));

        public long revision() {
            return revision;
        }

        public int schemaVersion() {
            return schemaVersion;
        }

        public PrecisionGraph slot(int slot) {
            normalizeData(this);
            return slots.get(Mth.clamp(slot, 0, 3));
        }

        public void replaceSlot(int slot, PrecisionGraph graph) {
            normalizeData(this);
            slots.set(Mth.clamp(slot, 0, 3), graph);
            revision++;
        }

        public Data copy() {
            normalizeData(this);
            var copy = new Data();
            copy.setProficiency(getProficiency());
            copy.setEnabled(isEnabled());
            copy.schemaVersion = schemaVersion;
            copy.revision = revision;
            copy.slots = new ArrayList<>(slots);
            return copy;
        }

        @Override
        public Identifier getType() {
            return ID;
        }
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.PRECISION_OPERATION.get(),
                        List.of(
                                ImpressionManipulation.Client.SKILL_INFO,
                                MentalStupor.Client.SKILL_INFO
                        ),
                        R.textures.ability.mentalout.skill.precision_operation.icon,
                        116,
                        148
                )
        );
        public static final String KEY_NAME_EXECUTE_SELECTED = SkillNames.PRECISION_OPERATION + "_execute_selected";
        public static final String KEY_NAME_EDIT = SkillNames.PRECISION_OPERATION + "_edit";
        public static final String KEY_NAME_SLOT_PREFIX = SkillNames.PRECISION_OPERATION + "_slot_";
        public static Config CONFIG = new Config();

        private Client() {
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
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
}
