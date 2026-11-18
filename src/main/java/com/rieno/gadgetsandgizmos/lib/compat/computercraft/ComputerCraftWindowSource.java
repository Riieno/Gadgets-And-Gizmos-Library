package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

import com.rieno.gadgetsandgizmos.lib.script.ScriptSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Extend the active ROM window API without replacing its source or changing custom terminals
public final class ComputerCraftWindowSource{
    private ComputerCraftWindowSource(){}

    public static byte[] extend(byte[] source) throws IOException{
        try(var stream = ScriptSession.class.getResourceAsStream("terminal.lua")){
            if(stream == null) throw new IOException("Missing terminal window implementation");
            String extension = "\ndo\nlocal createTerminal = (function()\n" + new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    + "\nend)()\nlocal windows = createTerminal({windowOnly = true})\nlocal nativeCreate = create\n"
                    + "create = function(parent, ...)\n"
                    + "    if type(parent) == 'table' and type(parent.blitHex) == 'function' then return windows.create(parent, ...) end\n"
                    + "    return nativeCreate(parent, ...)\nend\nend\n";
            byte[] suffix = extension.getBytes(StandardCharsets.UTF_8);
            byte[] res = java.util.Arrays.copyOf(source, source.length + suffix.length);
            System.arraycopy(suffix, 0, res, source.length, suffix.length);
            return res;
        }
    }
}
