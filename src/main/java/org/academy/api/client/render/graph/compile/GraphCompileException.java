package org.academy.api.client.render.graph.compile;

import java.util.List;
import org.academy.api.client.render.graph.validate.GraphIssue;

/**
 * 图编译失败（契约）。携带全部 ERROR 级诊断。
 */
public final class GraphCompileException extends RuntimeException {
    private final List<GraphIssue> issues;

    public GraphCompileException(List<GraphIssue> issues) {
        super("graph compile failed with " + issues.size() + " error(s)");
        this.issues = List.copyOf(issues);
    }

    public List<GraphIssue> issues() {
        return issues;
    }
}
