package org.academy.internal.common.structure.sable.reflect;

import java.lang.annotation.*;

/** Runtime name of an optional API. Merely loading the view does not load that API. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SableType { String value(); }
