package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphLogicTest{
    @Test void gatesMatchTheirCompleteTruthTables(){
        boolean[][] cases = {{false,false,false,true,true,true},{false,true,true,false,true,false},
                {true,false,true,false,true,false},{true,true,false,false,false,true}};
        for(boolean[] row : cases){
            assertEquals(row[2], GraphLogic.xor(row[0], row[1]));
            assertEquals(row[3], GraphLogic.nor(row[0], row[1]));
            assertEquals(row[4], GraphLogic.nand(row[0], row[1]));
            assertEquals(row[5], GraphLogic.xnor(row[0], row[1]));
        }
    }

    @Test void setResetLatchRetainsStateAndAcceptsRepeatedPulses(){
        boolean state = false;
        assertFalse(GraphLogic.srLatch(state, false, false));
        state = GraphLogic.srLatch(state, true, false);
        assertTrue(state);
        assertTrue(GraphLogic.srLatch(state, true, false));
        assertTrue(GraphLogic.srLatch(state, false, false));
        state = GraphLogic.srLatch(state, false, true);
        assertFalse(state);
        assertFalse(GraphLogic.srLatch(state, false, true));
        assertFalse(GraphLogic.srLatch(state, false, false));
    }

    @Test void resetTakesPriorityWhenBothInputsAreAsserted(){
        assertFalse(GraphLogic.srLatch(false, true, true));
        assertFalse(GraphLogic.srLatch(true, true, true));
    }
}
