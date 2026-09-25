package org.academy.api.client.render.graph.validate;

import java.util.Optional;

/**
 * 校验诊断（契约）。{@code nodeId} 为关联节点 id，全局问题时为空。
 */
public record GraphIssue(Severity severity, String message, Optional<String> nodeId) {
    public enum Severity {
        ERROR,
        WARNING
    }

    public static GraphIssue error(String message, String nodeId) {
        return new GraphIssue(Severity.ERROR, message, Optional.of(nodeId));
    }

    public static GraphIssue error(String message) {
        return new GraphIssue(Severity.ERROR, message, Optional.empty());
    }

    public static GraphIssue warning(String message, String nodeId) {
        return new GraphIssue(Severity.WARNING, message, Optional.of(nodeId));
    }
}
