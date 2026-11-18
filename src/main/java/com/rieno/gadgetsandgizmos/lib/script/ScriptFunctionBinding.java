package com.rieno.gadgetsandgizmos.lib.script;

import java.lang.annotation.*;

// Mark an explicitly exposed method and preserve its native execution policy
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScriptFunctionBinding{
    String[] value() default {};
    boolean mainThread() default false;
}
