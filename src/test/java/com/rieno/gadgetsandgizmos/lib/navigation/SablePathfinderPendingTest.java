package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SablePathfinderPendingTest{
    @Test void pendingDirectChecksStayPendingAndKeepTheirSearchState(){
        Map<SablePathfinder.Query,Integer> visits = new HashMap<>();
        var validator = new SablePathfinder.Validator(){
            @Override public boolean incremental(){ return true; }
            @Override public SablePathfinder.Traversal validate(SablePathfinder.Query query){
                int attempt = visits.merge(query,1,Integer::sum);
                return attempt < 4 ? SablePathfinder.Traversal.pending() : SablePathfinder.Traversal.clear(query.mode());
            }
        };
        var plan = SablePathfinder.queue(request(new Vec3(0,0,6),validator));
        assertTrue(visits.isEmpty(), "Queue construction must not execute the full sweep");
        for(int i=0;i<3;i++){
            plan.advance(1);
            assertFalse(plan.finished());
            assertTrue(plan.result().waypoints().isEmpty());
            assertEquals(1, plan.result().expandedNodes(), "Suspending a node must not consume it repeatedly");
        }
        plan.advance(1);
        assertTrue(plan.finished());
        assertTrue(plan.result().reachedDestination());
    }

    @Test void pendingBlockedEdgesResumeAndFindTheSafeDetour(){
        Map<SablePathfinder.Query,Integer> visits = new HashMap<>();
        var validator = new SablePathfinder.Validator(){
            @Override public boolean incremental(){ return true; }
            @Override public SablePathfinder.Traversal validate(SablePathfinder.Query query){
                if(visits.merge(query,1,Integer::sum)<2) return SablePathfinder.Traversal.pending();
                Vec3 start=query.start(), end=query.end();
                if(start.z<=2 && end.z>=2 && end.z-start.z>1e-9){
                    double x=start.x+(end.x-start.x)*(2-start.z)/(end.z-start.z);
                    if(Math.abs(x)<1.1) return SablePathfinder.Traversal.blocked();
                }
                return SablePathfinder.Traversal.clear(query.mode());
            }
        };
        var combined=SablePathfinder.allOf(query->SablePathfinder.Traversal.clear(query.mode()),validator);
        assertTrue(combined.incremental());
        var plan=SablePathfinder.queue(request(new Vec3(0,0,6),combined));
        for(int i=0;i<4000 && !plan.finished();i++) plan.advance(2);
        assertTrue(plan.finished());
        assertTrue(plan.result().reachedDestination());
        assertTrue(plan.result().waypoints().stream().anyMatch(point->Math.abs(point.position().x)>1));
    }

    @Test void longShortcutSweepsAreSparseAfterTheInitialBlockedRay(){
        java.util.concurrent.atomic.AtomicInteger longChecks=new java.util.concurrent.atomic.AtomicInteger();
        var end=new Vec3(0,0,40);
        var validator=new SablePathfinder.Validator(){
            @Override public boolean incremental(){return true;}
            @Override public SablePathfinder.Traversal validate(SablePathfinder.Query query){
                double distance=query.start().distanceTo(query.end());
                if(distance>2){longChecks.incrementAndGet();return SablePathfinder.Traversal.blocked();}
                return SablePathfinder.Traversal.clear(query.mode());
            }
        };
        var plan=SablePathfinder.queue(new SablePathfinder.Request(null,SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(end),SablePathfinder.Safety.DEFAULT,SablePathfinder.Movement.FLIGHT,
                1,50,2048,0.25,validator));
        for(int i=0;i<4096 && !plan.finished();i++) plan.advance(2);
        assertTrue(plan.result().reachedDestination());
        assertTrue(longChecks.get()<=2+plan.result().expandedNodes()/8,"Every node performed another full destination sweep");
    }

    private static SablePathfinder.Request request(Vec3 end,SablePathfinder.Validator validator){
        return new SablePathfinder.Request(null,SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(end),SablePathfinder.Safety.DEFAULT,SablePathfinder.Movement.FLIGHT,
                1,16,1024,0.25,validator);
    }
}
