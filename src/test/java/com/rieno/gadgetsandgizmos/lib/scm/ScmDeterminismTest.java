package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Preserve the captured scalar solver outputs across cache and worker scheduling changes
class ScmDeterminismTest{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Cover asymmetric thrust, steering cones, saturation and retained controls
    @Test
    void precisionMatchesCapturedOutputs(){
        Random random = new Random(0x53434d);
        long hash = 1;
        for(int sample = 0; sample < 96; sample++){
            List<ScmPrecisionAllocator.Unit> units = units(random, sample % 37);
            Vec3 force = vector(random, 250);
            Vec3 torque = vector(random, 80);
            double[] prev = new double[units.size()];
            for(int idx = 0; idx < prev.length; idx++) prev[idx] = random.nextDouble();
            for(int tick = 0; tick < 4; tick++){
                var res = tick % 2 == 0
                        ? ScmPrecisionAllocator.allocate(units, force, torque, tick == 0 ? null : prev)
                        : ScmPrecisionAllocator.allocateCruise(units, force, torque, force, prev);
                hash = hash(hash, res.residual());
                hash = hash * 31 + (res.precisionOnly() ? 1 : 0);
                for(double val : res.controls()){
                    assertTrue(Double.isFinite(val) && val >= 0 && val <= 1);
                    hash = hash(hash, val);
                }
                prev = res.controls();
            }
        }
        assertEquals(-675167327980384197L, hash);
    }

    // Cover both signs, diagonal feedback and more models than the retained cache
    @Test
    void adaptiveMatchesCapturedOutputs(){
        Random random = new Random(0x414343);
        long hash = 1;
        for(int sample = 0; sample < 64; sample++){
            List<ScmAdaptiveStateModel.Actuator> actuators = new ArrayList<>();
            for(int idx = 0; idx < sample % 41; idx++){
                actuators.add(new ScmAdaptiveStateModel.Actuator(vector(random, 8), vector(random, 2), random.nextDouble()));
            }
            var model = ScmAdaptiveStateModel.sample(actuators, .05, .1, .02, 3);
            for(int query = 0; query < 4; query++){
                Vec3 axis = query == 0 ? Vec3.ZERO : vector(random, 1);
                boolean angular = (query & 1) == 0;
                var res = model.feedback(axis, angular, random.nextDouble() * 4 - 2, random.nextDouble() - .5);
                hash = hash(hash, res.acceleration());
                hash = hash(hash, res.positiveAuthority());
                hash = hash(hash, res.negativeAuthority());
                hash = hash(hash, res.firstPole());
                hash = hash(hash, model.velocityFeedback(axis, angular, 2, -.5, .1));
                Vec3 feedback = model.angularFeedback(axis, new Vec3(.1, -.3, .2), .7);
                hash = hash(hash(hash(hash, feedback.x), feedback.y), feedback.z);
            }
        }
        assertEquals(867573443648574158L, hash);
    }

    // Keep carriage order, cargo load sharing and repeated refinement deterministic
    @Test
    void articulatedMatchesCapturedOutputs(){
        Random random = new Random(0x415254);
        long hash = 1;
        for(int sample = 0; sample < 24; sample++){
            List<ScmArticulatedFlightControl.Carriage> carriages = new ArrayList<>();
            List<ScmArticulatedFlightControl.Request> requests = new ArrayList<>();
            for(int idx = 0; idx < 4; idx++){
                UUID id = new UUID(sample, idx);
                carriages.add(new ScmArticulatedFlightControl.Carriage(id, idx == 0, 20,
                        null, new Vec3(idx * 6, 0, 0), Vec3.ZERO, Vec3.ZERO, new Vec3(0, 1, 0),
                        units(random, idx == 3 ? 0 : 24)));
                requests.add(new ScmArticulatedFlightControl.Request(id, vector(random, 150), vector(random, 50)));
            }
            List<double[]> previous = null;
            for(int tick = 0; tick < 3; tick++){
                var res = ScmArticulatedFlightControl.allocate(carriages, requests, previous,
                        tick == 1 ? new Vec3(0, 0, 1) : Vec3.ZERO);
                previous = res.stream().map(ScmPrecisionAllocator.Allocation::controls).toList();
                for(var allocation : res){
                    hash = hash(hash, allocation.residual());
                    for(double val : allocation.controls()) hash = hash(hash, val);
                }
            }
        }
        assertEquals(6502428678050772137L, hash);
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    static List<ScmPrecisionAllocator.Unit> units(Random random, int count){
        List<ScmPrecisionAllocator.Unit> units = new ArrayList<>();
        for(int idx = 0; idx < count; idx++){
            Vec3 force = vector(random, 50);
            Vec3 arm = vector(random, 4);
            units.add(new ScmPrecisionAllocator.Unit(force, arm.cross(force), idx % 3 == 0,
                    arm, idx % 4 == 0 ? 25 : 0));
        }
        return List.copyOf(units);
    }

    private static Vec3 vector(Random random, double scale){
        return new Vec3((random.nextDouble() - .5) * scale,
                (random.nextDouble() - .5) * scale, (random.nextDouble() - .5) * scale);
    }

    private static long hash(long hash, double val){ return hash * 31 + Double.doubleToLongBits(val); }
}
