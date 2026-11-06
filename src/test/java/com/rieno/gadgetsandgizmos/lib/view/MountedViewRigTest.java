package com.rieno.gadgetsandgizmos.lib.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Check upright feeds and image-relative input without depending on a loaded world
class MountedViewRigTest{
    // Retain lens direction and remove mounting roll for every mounting normal and joint quadrant
    @Test void mountingRollDoesNotTiltTheImage(){
        for(Vector3d normal : normals()){
            var mount = new Quaterniond().rotationTo(new Vector3d(0, 1, 0), normal);
            for(double pan : new double[]{0, 35, 90, 170}) for(double tilt : new double[]{0, -35, -90, 35, 90}){
                var raw = ViewRig.worldOrientation(mount, ViewRig.Orientation.LOCAL, new ViewRig.Angles(pan, tilt));
                var view = ViewRig.uprightOrientation(raw);
                assertTrue(raw.transform(new Vector3d(0, 0, -1)).distance(view.transform(new Vector3d(0, 0, -1))) < 1.0E-9);
                assertTrue(view.transform(new Vector3d(0, 1, 0)).y >= -1.0E-9);
                assertEquals(0, view.transform(new Vector3d(1, 0, 0)).y, 1.0E-9);
            }
        }
    }

    // Both joystick axes must move the sight line instead of rolling around a mount axis
    @Test void eachMountPansAndTiltsAlongTheImage(){
        for(Vector3d normal : normals()) for(var frame : ViewRig.Orientation.values()){
            var mount = new Quaterniond().rotationTo(new Vector3d(0, 1, 0), normal);
            for(double[] input : new double[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}){
                var rig = new ViewRig();
                var before = ViewRig.uprightOrientation(ViewRig.worldOrientation(mount, frame, new ViewRig.Angles(0, 0)));
                Vector3d forward = before.transform(new Vector3d(0, 0, -1));
                Vector3d right = before.transform(new Vector3d(1, 0, 0));
                Vector3d up = before.transform(new Vector3d(0, 1, 0));
                rig.turnInView(mount, frame, input[0], input[1]);
                Vector3d after = ViewRig.worldOrientation(mount, frame, new ViewRig.Angles(rig.pan(), rig.tilt()))
                        .transform(new Vector3d(0, 0, -1));
                assertTrue(forward.distance(after) > .04, "Input only rolled the image for " + normal);
                if(input[0] != 0) assertEquals(-Math.sin(Math.toRadians(input[0])), after.dot(right), 1.0E-9);
                if(input[1] != 0) assertEquals(Math.sin(Math.toRadians(input[1])), after.dot(up), 1.0E-9);
            }
        }
    }

    // Preserve persisted joints on zoom-only input and reject invalid deltas
    @Test void zoomAndInvalidInputDoNotReorientTheLens(){
        var rig = new ViewRig();
        rig.aim(-64, 41);
        var prev = rig.state();
        rig.turnInView(new Quaterniond(), ViewRig.Orientation.LOCAL, Double.NaN, Double.POSITIVE_INFINITY);
        assertEquals(prev, rig.state());
    }

    // Replay image-relative inputs exactly as the owning server source applies them
    @Test void predictionUsesTheSourceInputMapping(){
        var mount = new Quaterniond().rotationTo(0, 1, 0, 0, -1, 0);
        ViewControlPrediction.InputTransform transform = (aim, pan, tilt, zoom) -> {
            var rig = new ViewRig();
            rig.restore(aim);
            rig.turnInView(mount, ViewRig.Orientation.LOCAL, pan, tilt);
            rig.zoom(zoom);
            return rig.state();
        };
        var initial = new ViewRig.State(0, -35, 70);
        var prediction = new ViewControlPrediction(initial);
        var sample = prediction.sample(-3, 2, 1);
        var confirmed = transform.apply(initial, -3, 2, 1);
        var expected = transform.apply(confirmed, 4, -2, 0);
        assertEquals(expected, prediction.preview(4, -2, 0, transform));
        prediction.acknowledge(sample.sequence(), confirmed);
        assertEquals(expected, prediction.preview(4, -2, 0, transform));
    }

    // Supply all six face normals without initializing Minecraft registries
    private static Vector3d[] normals(){
        return new Vector3d[]{new Vector3d(0, 1, 0), new Vector3d(0, -1, 0), new Vector3d(1, 0, 0),
                new Vector3d(-1, 0, 0), new Vector3d(0, 0, 1), new Vector3d(0, 0, -1)};
    }
}
