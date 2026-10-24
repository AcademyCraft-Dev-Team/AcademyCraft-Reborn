package org.academy.internal.common.structure.sable.reflect;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SableField { String value() default ""; }
