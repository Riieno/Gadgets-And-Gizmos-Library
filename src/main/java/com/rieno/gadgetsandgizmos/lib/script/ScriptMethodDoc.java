package com.rieno.gadgetsandgizmos.lib.script;

import java.lang.annotation.*;

// Describe the shared method contract independently of the executing Lua engine
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScriptMethodDoc{
    String name();
    String signature();
    String description();
    String[] examples() default {};
    String since() default "2";
    String deprecatedBy() default "";
    boolean hidden() default false;
}
