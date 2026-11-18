package com.rieno.gadgetsandgizmos.lib.script;

import java.util.List;
import java.util.Map;

// Supply an engine-independent remote peripheral network to a controller binding
public interface ScriptPeripheralNetwork{
    Map<String, String> getPeripherals();
    String peripheral(String id);
    List<String> getNamesRemote();
    boolean isPresentRemote(String name);
    Object[] getTypeRemote(String name);
    Boolean hasTypeRemote(String name, String type);
    List<String> getMethodsRemote(String name);
    Object[] callRemote(ScriptArguments args);
}
