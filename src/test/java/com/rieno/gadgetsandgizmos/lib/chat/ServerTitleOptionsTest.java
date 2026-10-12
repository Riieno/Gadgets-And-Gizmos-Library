package com.rieno.gadgetsandgizmos.lib.chat;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ServerTitleOptionsTest{
    @Test
    void exposesEveryVanillaTitleAction(){
        assertEquals(List.of("title","subtitle","actionbar","times","clear","reset"),ServerTitleApi.Action.options());
        assertEquals(ServerTitleApi.Action.RESET,ServerTitleApi.Action.parse("RESET"));
    }
    @Test
    void convertsEveryVanillaTimeUnit(){
        assertEquals(10,ServerTitleApi.TimeUnit.TICKS.ticks(10));
        assertEquals(10,ServerTitleApi.TimeUnit.SECONDS.ticks(0.5));
        assertEquals(24000,ServerTitleApi.TimeUnit.DAYS.ticks(1));
    }
    @Test
    void clampsInvalidAndOverflowingDurations(){
        assertEquals(0,ServerTitleApi.TimeUnit.TICKS.ticks(-1));
        assertEquals(0,ServerTitleApi.TimeUnit.TICKS.ticks(Double.NaN));
        assertEquals(0,ServerTitleApi.TimeUnit.TICKS.ticks(Double.POSITIVE_INFINITY));
        assertEquals(Integer.MAX_VALUE,ServerTitleApi.TimeUnit.DAYS.ticks(Double.MAX_VALUE));
    }
    @Test
    void animationUsesVanillaDefaultsAndWholeTicks(){
        assertEquals(new ServerTitleApi.Animation(10,70,20),ServerTitleApi.Animation.DEFAULT);
        assertEquals(new ServerTitleApi.Animation(10,70,20),ServerTitleApi.Animation.of(0.5,3.5,1,ServerTitleApi.TimeUnit.SECONDS));
        assertEquals(new ServerTitleApi.Animation(0,0,0),new ServerTitleApi.Animation(-1,-2,-3));
    }
}
