package org.academy.api.server.ability.external;

/** Only verified inputs are advertised. Generation changes invalidate prepared executions. */
public record ExternalAbilityDescriptor(String id, String displayName, int spatialInputs,
                                        int maximumDurationTicks, long bindingGeneration) {
    public ExternalAbilityDescriptor {
        if (id == null || id.isBlank() || id.length() > 256 || displayName == null
                || displayName.length() > 512 || (spatialInputs & ~15) != 0
                || maximumDurationTicks < 1 || maximumDurationTicks > 1200 || bindingGeneration < 0)
            throw new IllegalArgumentException("Invalid external ability descriptor");
    }
}
