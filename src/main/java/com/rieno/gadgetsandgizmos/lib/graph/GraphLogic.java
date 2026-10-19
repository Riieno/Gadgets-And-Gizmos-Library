package com.rieno.gadgetsandgizmos.lib.graph;

// Evaluate boolean gates and retained set/reset state without depending on a graph host
public final class GraphLogic{
    private GraphLogic(){}

    public static boolean xor(boolean a, boolean b){ return a != b; }
    public static boolean nor(boolean a, boolean b){ return !(a || b); }
    public static boolean nand(boolean a, boolean b){ return !(a && b); }
    public static boolean xnor(boolean a, boolean b){ return a == b; }

    // Keep the current state without a pulse; reset wins if both inputs are asserted
    public static boolean srLatch(boolean current, boolean set, boolean reset){
        if(reset) return false;
        return set || current;
    }
}
