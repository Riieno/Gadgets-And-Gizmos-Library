package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WaypointSplineCacheTest{
    // Exercise warm entries, eviction and exact dyadic distances across a larger route working set
    @Test
    void cachedFractionsMatchTheOriginalUncachedSearchAfterEviction(){
        List<WaypointSpline.Segment> segments = new ArrayList<>();
        for(int idx = 0; idx < 160; idx++){
            segments.addAll(WaypointSpline.of(List.of(new Vec3(idx, 0, 0),
                    new Vec3(idx + 3, 4, 1), new Vec3(idx + 8, 2, 6))).segments());
        }
        for(int pass = 0; pass < 3; pass++){
            for(var segment : segments){
                for(double fraction : new double[]{0, .13, .5, .87, 1, 1.5}){
                    double distance = segment.length() * fraction;
                    double low = 0, high = 1;
                    double target = Math.max(0, Math.min(segment.length(), distance));
                    for(int idx = 0; idx < 12; idx++){
                        double middle = (low + high) * .5;
                        if(segment.lengthToFraction(middle) < target) low = middle;
                        else high = middle;
                    }
                    assertEquals(Double.doubleToLongBits((low + high) * .5),
                            Double.doubleToLongBits(segment.fractionAtDistance(distance)));
                }
            }
        }
    }
}
