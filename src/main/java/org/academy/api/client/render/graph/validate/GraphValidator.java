package org.academy.api.client.render.graph.validate;

import java.util.List;
import org.academy.api.client.render.graph.model.Graph;

/**
 * 图校验器（契约）。类型检查、环检测、非法图诊断。
 */
public interface GraphValidator {
    List<GraphIssue> validate(Graph graph);
}
