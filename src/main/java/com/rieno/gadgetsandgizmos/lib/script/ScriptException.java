package com.rieno.gadgetsandgizmos.lib.script;

// Report a script-facing argument or device error without depending on an optional VM
public final class ScriptException extends RuntimeException{
    public ScriptException(String message){ super(message); }
}
