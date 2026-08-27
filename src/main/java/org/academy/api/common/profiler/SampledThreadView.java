package org.academy.api.common.profiler;

public record SampledThreadView(long id, String name, long samples, SampledNode root) {
}
