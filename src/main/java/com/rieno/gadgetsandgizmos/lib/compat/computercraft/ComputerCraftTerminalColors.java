package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptHexColors;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import com.rieno.gadgetsandgizmos.lib.script.TerminalColors;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.core.apis.TermMethods;
import dan200.computercraft.core.methods.LuaMethod;
import dan200.computercraft.core.methods.PeripheralMethod;
import dan200.computercraft.core.terminal.Terminal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Adapt optional CC terminals to the shared direct-color and snapshot APIs
public final class ComputerCraftTerminalColors{
    private ComputerCraftTerminalColors(){}

    public static TerminalColors colors(Terminal terminal){ return ((ComputerCraftTerminalAccess)terminal).terminalColors(); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Intercept color arguments before CC's generated numeric argument conversion
    public static Object wrap(String name, Object method){
        if(!switch(name){
            case "setTextColor", "setTextColour", "setBackgroundColor", "setBackgroundColour",
                    "getTextColor", "getTextColour", "getBackgroundColor", "getBackgroundColour", "blit" -> true;
            default -> false;
        }) return method;
        if(method instanceof LuaMethod original) return (LuaMethod)(target, ctx, args) -> {
            MethodResult res = call((TermMethods)target, name, args);
            return res == null ? original.apply(target, ctx, args) : res;
        };
        if(method instanceof PeripheralMethod original) return (PeripheralMethod)(target, ctx, computer, args) -> {
            MethodResult res = call((TermMethods)target, name, args);
            return res == null ? original.apply(target, ctx, computer, args) : res;
        };
        return method;
    }

    private static MethodResult call(TermMethods target, String name, IArguments args) throws LuaException{
        Terminal terminal = target.getTerminal();
        synchronized(terminal){
            TerminalColors colors = colors(terminal);
            boolean background = name.contains("Background");
            if(name.startsWith("get")){
                long val = background ? colors.background() : colors.foreground();
                return val == TerminalColors.PALETTE ? null : MethodResult.of(ScriptHexColors.format((int)val));
            }
            if(name.equals("blit")){
                if(args.get(1) instanceof String front && !front.startsWith("#")
                        && args.get(2) instanceof String back && !back.startsWith("#")) return null;
                blit(terminal, args); return MethodResult.of();
            }
            if(!(args.get(0) instanceof String text)) return null;
            long val = parse(text);
            if(background){ terminal.setBackgroundColour(15); colors.background(val); }
            else{ terminal.setTextColour(0); colors.foreground(val); }
            terminal.setChanged();
            return MethodResult.of();
        }
    }

    private static long parse(String text) throws LuaException{
        try{ return Integer.toUnsignedLong(ScriptHexColors.parse(text)); }
        catch(IllegalArgumentException err){ throw new LuaException(err.getMessage()); }
    }

    // Accept palette strings, a uniform hex ink or a per-character list of inks
    public static void blit(Terminal terminal, IArguments args) throws LuaException{
        ByteBuffer text = args.getBytes(0);
        int count = text.remaining();
        if(count > 32768) throw new LuaException("Expected bounded text");
        Object front = args.get(1), back = args.get(2);
        long[] fronts = inks(front, count), backs = inks(back, count);
        String fg = indices(front, count), bg = indices(back, count);
        if(args.count() >= 5){
            fronts = plane(args.getString(3), count); backs = plane(args.getString(4), count);
        }
        synchronized(terminal){
            int x = terminal.getCursorX(), y = terminal.getCursorY();
            terminal.blit(text, ByteBuffer.wrap(fg.getBytes(StandardCharsets.US_ASCII)), ByteBuffer.wrap(bg.getBytes(StandardCharsets.US_ASCII)));
            var colors = colors(terminal);
            for(int idx = 0; idx < count; idx++) colors.paint(x + idx, y, 1, fronts[idx], backs[idx]);
            terminal.setCursorPos((int)Math.min(Integer.MAX_VALUE, (long)x + count), y);
            terminal.setChanged();
        }
    }
    private static String indices(Object val, int count) throws LuaException{
        if(val instanceof String text && !text.startsWith("#")){
            if(text.length() != count || !text.matches("[0-9a-f]*")) throw new LuaException("Invalid blit data");
            return text;
        }
        StringBuilder res = new StringBuilder(count);
        for(int idx = 0; idx < count; idx++){
            Object ink = val instanceof Map<?, ?> map ? map.get((double)idx + 1) : val;
            if(ink instanceof Number num) res.append(Character.forDigit(TermMethods.parseColour(num.intValue()), 16));
            else if(ink instanceof String text && text.startsWith("#")) res.append('0');
            else throw new LuaException("Expected a color");
        }
        return res.toString();
    }
    private static long[] inks(Object val, int count) throws LuaException{
        if(val instanceof Map<?, ?> map && map.size() != count) throw new LuaException("Arguments must be the same length");
        long[] res = new long[count];
        for(int idx = 0; idx < count; idx++){
            Object ink = val instanceof Map<?, ?> map ? map.get((double)idx + 1) : val;
            if(ink == null) throw new LuaException("Arguments must be the same length");
            res[idx] = ink instanceof String text && text.startsWith("#") ? parse(text) : TerminalColors.PALETTE;
        }
        return res;
    }
    private static long[] plane(String text, int count) throws LuaException{
        if(text.length() != count * 8) throw new LuaException("Invalid hex blit width");
        long[] res = new long[count];
        for(int idx = 0; idx < count; idx++){
            String cell = text.substring(idx * 8, idx * 8 + 8);
            res[idx] = cell.equals("--------") ? TerminalColors.PALETTE : parse("#" + cell);
        }
        return res;
    }

    // Retain hex planes when native terminals are displayed through another library surface
    public static ScriptTerminal snapshot(Terminal terminal){
        synchronized(terminal){
            ScriptTerminal base = ScriptTerminal.capture(new ScriptTerminal.Access(){
                @Override public int width(){ return terminal.getWidth(); }
                @Override public int height(){ return terminal.getHeight(); }
                @Override public String text(int row){ return terminal.getLine(row).toString(); }
                @Override public String foreground(int row){ return terminal.getTextColourLine(row).toString(); }
                @Override public String background(int row){ return terminal.getBackgroundColourLine(row).toString(); }
                @Override public int color(int idx){ return terminal.getPalette().getRenderColours(15 - idx); }
                @Override public int cursorX(){ return terminal.getCursorX(); }
                @Override public int cursorY(){ return terminal.getCursorY(); }
                @Override public boolean blink(){ return terminal.getCursorBlink(); }
                @Override public int cursorColor(){ return terminal.getTextColour(); }
            });
            TerminalColors colors = colors(terminal);
            return new ScriptTerminal(base.width(), base.height(), base.text(), base.foreground(), base.background(), base.palette(),
                    base.cursorX(), base.cursorY(), base.blink(), base.cursorColor(), colors.rows(true), colors.rows(false),
                    colors.foreground() == TerminalColors.PALETTE ? "" : TerminalColors.hex(colors.foreground()));
        }
    }
}
