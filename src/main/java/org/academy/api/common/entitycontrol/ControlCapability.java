package org.academy.api.common.entitycontrol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum ControlCapability {
    FORCE_TARGET(ControlDomain.TARGET),
    FREEZE_AI(ControlDomain.MOVEMENT, ControlDomain.ACTION),
    RELATION_CONTROL(ControlDomain.RELATION);

    private final Set<ControlDomain> domains;

    ControlCapability(ControlDomain firstDomain, ControlDomain... additionalDomains) {
        var values = EnumSet.of(firstDomain, additionalDomains);
        domains = Collections.unmodifiableSet(values);
    }

    public Set<ControlDomain> domains() {
        return domains;
    }
}
