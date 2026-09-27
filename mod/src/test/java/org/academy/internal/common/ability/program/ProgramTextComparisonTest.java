package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramInputView;
import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProgramTextComparisonTest {
    @Test
    void comparesChatTextForBranchConditions() {
        assertTrue(compare("守卫 开始", "守卫", "starts_with", false));
        assertTrue(compare("守卫 开始", "开始", "contains", false));
        assertTrue(compare("Guard", "guard", "equals", true));
        assertFalse(compare("Guard", "guard", "equals", false));
        assertFalse(compare("守卫 开始", "撤退", "ends_with", false));
    }

    private static boolean compare(String left, String right, String mode, boolean ignoreCase) {
        @SuppressWarnings("unchecked")
        var executor = (ProgramNodeExecutor<CommonProgramNodeCatalog.TextComparisonConfiguration>)
                CommonProgramExecutors.INSTANCE.find(CommonProgramNodeIds.TEXT_COMPARE);
        assertNotNull(executor);
        var inputs = new ProgramInputView(Map.of(
                "left", List.of(new ProgramValue<>(ProgramValueTypes.TEXT, left)),
                "right", List.of(new ProgramValue<>(ProgramValueTypes.TEXT, right))
        ));
        var step = executor.execute(null,
                new CommonProgramNodeCatalog.TextComparisonConfiguration(mode, ignoreCase), inputs);
        return (Boolean) step.outputs().get("result").value();
    }
}
