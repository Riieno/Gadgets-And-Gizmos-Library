package com.rieno.gadgetsandgizmos.lib.graph.math;

import com.rieno.gadgetsandgizmos.lib.control.math.Kinematics;
import com.rieno.gadgetsandgizmos.lib.control.math.Matrix3;
import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.rieno.gadgetsandgizmos.lib.control.math.SpatialTransforms;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.GraphExecutionContext;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeExecutor;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Publish reusable math nodes; the consuming mod chooses and registers its namespace
public final class MathGraphNodes{
    private MathGraphNodes(){}

    public record Node(GraphNodeDefinition definition, GraphNodeExecutor executor,
                       Map<String, GraphValue> defaults, String title, String summary){
        public Node{ defaults = Map.copyOf(defaults); }
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static List<Node> nodes(String namespace){
        if(namespace == null || !namespace.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("A mod namespace is required");
        List<Node> res = new ArrayList<>();
        add(res, namespace, "pi", Map.of(), ports("value:number"), "Return pi (3.141592653589793), the circle constant.");
        add(res, namespace, "vector_scale", ports("vector:map", "scalar:number"), ports("value:map"), "Multiply every vector component by a number.");
        add(res, namespace, "vector_normalize", ports("vector:map"), ports("value:map"), "Return a unit vector, or zero for a zero vector.");
        add(res, namespace, "vector_dot", ports("a:map", "b:map"), ports("value:number"), "Return the vector dot product.");
        add(res, namespace, "vector_cross", ports("a:map", "b:map"), ports("value:map"), "Return the right-handed vector cross product.");
        add(res, namespace, "matrix_create", ports("row_x:map", "row_y:map", "row_z:map"), ports("matrix:map"), "Create a row-major 3x3 matrix from three row vectors.");
        add(res, namespace, "matrix_diagonal", ports("diagonal:map"), ports("matrix:map"), "Create a diagonal matrix from an XYZ vector.");
        add(res, namespace, "matrix_identity", Map.of(), ports("matrix:map"), "Return the identity linear transform.");
        for(String op : List.of("add", "subtract", "multiply")){
            add(res, namespace, "matrix_" + op, ports("a:map", "b:map"), ports("matrix:map"),
                    op.equals("multiply") ? "Compose A times B; B acts first on column vectors." : "Apply matrix " + op + " component by component.");
        }
        add(res, namespace, "matrix_scale", ports("matrix:map", "scalar:number"), ports("matrix:map"), "Multiply a matrix by a number.");
        add(res, namespace, "matrix_transpose", ports("matrix:map"), ports("matrix:map"), "Swap matrix rows and columns.");
        add(res, namespace, "matrix_determinant", ports("matrix:map"), ports("value:number"), "Return the matrix determinant.");
        add(res, namespace, "matrix_inverse", ports("matrix:map"), ports("matrix:map"), "Invert a matrix; valid is false for singular or ill-conditioned matrices.");
        add(res, namespace, "matrix_transform", ports("matrix:map", "vector:map"), ports("value:map"), "Apply a 3x3 linear transform to an XYZ vector.");
        add(res, namespace, "quaternion_to_matrix", ports("quaternion:map"), ports("matrix:map"), "Convert a quaternion rotation to a 3x3 matrix.");
        add(res, namespace, "matrix_to_quaternion", ports("matrix:map"), ports("quaternion:map"), "Convert a proper orthonormal rotation matrix to a quaternion; scale, shear and reflection are rejected.");
        for(String id : List.of("local_to_world", "world_to_local", "local_direction_to_world", "world_direction_to_local")){
            add(res, namespace, id, ports("vector:map", "origin:map", "orientation:map"), ports("value:map"),
                    "Convert " + id.replace('_', ' ') + ". Orientation maps local axes to world axes; directions ignore origin.");
        }
        for(String id : List.of("tensor_to_world", "tensor_to_local")){
            add(res, namespace, id, ports("tensor:map", "orientation:map"), ports("matrix:map"), "Rotate a tensor between body and world frames using R D R transpose.");
        }
        add(res, namespace, "quaternion_create", ports("x:number", "y:number", "z:number", "w:number"), ports("quaternion:map"), "Create raw XYZW quaternion components without normalizing them.");
        for(String op : List.of("normalize", "conjugate", "inverse", "magnitude")){
            add(res, namespace, "quaternion_" + op, ports("quaternion:map"),
                    ports(op.equals("magnitude") ? "value:number" : "quaternion:map"), "Quaternion " + op + "; inverse preserves non-unit algebra.");
        }
        for(String op : List.of("multiply", "add", "subtract", "dot")){
            add(res, namespace, "quaternion_" + op, ports("a:map", "b:map"),
                    ports(op.equals("dot") ? "value:number" : "quaternion:map"),
                    op.equals("multiply") ? "Hamilton product A times B; B rotates first. Raw magnitude is preserved." : "Return quaternion " + op + " without implicit normalization.");
        }
        add(res, namespace, "quaternion_scale", ports("quaternion:map", "scalar:number"), ports("quaternion:map"), "Scale raw quaternion components by a number.");
        add(res, namespace, "quaternion_rotate_vector", ports("quaternion:map", "vector:map"), ports("value:map"), "Rotate an XYZ vector using the normalized quaternion.");
        add(res, namespace, "quaternion_from_axis_angle", ports("axis:map", "angle:number"), ports("quaternion:map"), "Create a rotation from an axis and an angle in radians.");
        add(res, namespace, "quaternion_to_axis_angle", ports("quaternion:map"), ports("axis:map", "angle:number"), "Return a unit axis and shortest rotation angle in radians.");
        add(res, namespace, "quaternion_slerp", ports("a:map", "b:map", "amount:number"), ports("quaternion:map"), "Interpolate normalized rotations along the shortest arc.");
        add(res, namespace, "degrees_to_radians", ports("angles:map"), ports("angles:map"), "Convert numeric map fields from degrees to radians, preserving keys and nested maps.");
        add(res, namespace, "radians_to_degrees", ports("angles:map"), ports("angles:map"), "Convert numeric map fields from radians to degrees, preserving keys and nested maps.");
        for(String id : List.of("linear_predict", "linear_integrator", "rigid_body_predict", "rigid_body_integrator")){
            boolean rigid = id.startsWith("rigid"), stateful = id.endsWith("integrator");
            Map<String, String> inputs = new LinkedHashMap<>(ports("position:map", "velocity:map", "acceleration:map", "jerk:map", "delta_time:number"));
            Map<String, String> outputs = new LinkedHashMap<>(ports("position:map", "velocity:map", "acceleration:map"));
            if(rigid){
                inputs.putAll(ports("orientation:map", "angular_velocity:map", "angular_acceleration:map", "angular_jerk:map", "drag:map", "angular_drag:map", "steps:number"));
                outputs.putAll(ports("orientation:map", "angular_velocity:map", "angular_acceleration:map"));
            }
            if(stateful){ inputs.putAll(ports("exec:exec", "reset:boolean")); outputs.put("exec", "exec"); }
            add(res, namespace, id, inputs, outputs, rigid
                    ? "RK4 motion: position/velocity are world values; acceleration, jerk, angular values and drag tensors use body axes. Drag is a damping rate in 1/s. Time is seconds."
                    : "Exact linear prediction with acceleration and optional constant jerk. Time is seconds. Integrators retain state on exec; reset restores input state.");
        }
        return List.copyOf(res);
    }

    // Supply additive named outputs for existing unqualified rotation node ids
    public static Node rotationNode(String id){
        if(!List.of("quaternion_to_euler", "quaternion_to_tait_bryan", "euler_to_quaternion", "tait_bryan_to_quaternion",
                "euler_to_tait_bryan", "tait_bryan_to_euler").contains(id)) throw new IllegalArgumentException("Unknown rotation node");
        String input = id.startsWith("quaternion") ? "quaternion" : id.startsWith("euler") ? "euler" : "tait_bryan";
        String output = id.endsWith("quaternion") ? "quaternion" : id.endsWith("euler") ? "euler" : "tait_bryan";
        Map<String, String> outputs = new LinkedHashMap<>(ports(output + ":map"));
        if(!output.equals("quaternion")) outputs.putAll(output.equals("euler")
                ? ports("alpha:number", "beta:number", "gamma:number") : ports("roll:number", "pitch:number", "yaw:number"));
        return node(id, ports(input + ":map"), outputs, "Angles use radians. ZXZ Euler: alpha (Z), beta (X), gamma (Z). Tait-Bryan: roll (X), pitch (Y), yaw (Z).");
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void add(List<Node> nodes, String namespace, String id, Map<String, String> inputs,
                            Map<String, String> outputs, String summary){
        nodes.add(node(namespace + ":" + id, inputs, outputs, summary));
    }

    private static Node node(String id, Map<String, String> inputs, Map<String, String> outputs, String summary){
        String op = id.substring(id.indexOf(':') + 1);
        boolean stateful = op.endsWith("integrator");
        Map<String, String> schema = new LinkedHashMap<>(outputs);
        schema.put("valid", "boolean");
        Map<String, GraphValue> defaults = defaults(op);
        GraphNodeExecutor executor = (ctx, supplied) -> {
            Map<String, GraphValue> in = new LinkedHashMap<>(defaults);
            in.putAll(supplied);
            try{
                Map<String, GraphValue> res = new LinkedHashMap<>(evaluate(op, ctx, in));
                res.put("valid", GraphValue.bool(true));
                return Map.copyOf(res);
            }catch(IllegalArgumentException err){
                Map<String, GraphValue> res = new LinkedHashMap<>();
                schema.forEach((key, type) -> {
                    if(!type.equals("exec")) res.put(key, type.equals("map") ? GraphValue.map(Map.of())
                            : type.equals("boolean") ? GraphValue.bool(false) : GraphValue.number(0));
                });
                res.put("valid", GraphValue.bool(false));
                return Map.copyOf(res);
            }
        };
        String title = java.util.Arrays.stream(op.split("_")).map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
        return new Node(new GraphNodeDefinition(id, "math", inputs, schema, stateful), executor, defaults, title, summary);
    }

    private static Map<String, GraphValue> defaults(String op){
        Map<String, GraphValue> res = new LinkedHashMap<>();
        for(String key : List.of("quaternion", "orientation")) res.put(key, MathGraphValues.quaternion(Quaternion.IDENTITY));
        if(op.startsWith("quaternion")){ res.put("a", res.get("quaternion")); res.put("b", res.get("quaternion")); }
        res.put("w", GraphValue.number(1));
        res.put("scalar", GraphValue.number(1));
        res.put("amount", GraphValue.number(0.5));
        res.put("delta_time", GraphValue.number(0.05));
        res.put("steps", GraphValue.number(8));
        res.put("axis", MathGraphValues.vector(new Vector3(1, 0, 0)));
        res.put("matrix", MathGraphValues.matrix(Matrix3.IDENTITY));
        res.put("diagonal", MathGraphValues.vector(new Vector3(1, 1, 1)));
        res.put("row_x", MathGraphValues.vector(new Vector3(1, 0, 0)));
        res.put("row_y", MathGraphValues.vector(new Vector3(0, 1, 0)));
        res.put("row_z", MathGraphValues.vector(new Vector3(0, 0, 1)));
        return Map.copyOf(res);
    }

    private static Map<String, GraphValue> evaluate(String op, GraphExecutionContext ctx, Map<String, GraphValue> in){
        return switch(op){
            case "pi" -> out("value", MathGraphValues.number(Math.PI));
            case "vector_scale" -> out("value", MathGraphValues.vector(v(in, "vector").scale(n(in, "scalar"))));
            case "vector_normalize" -> out("value", MathGraphValues.vector(v(in, "vector").normalized()));
            case "vector_dot" -> out("value", MathGraphValues.number(v(in, "a").dot(v(in, "b"))));
            case "vector_cross" -> out("value", MathGraphValues.vector(v(in, "a").cross(v(in, "b"))));
            case "matrix_create" -> out("matrix", MathGraphValues.matrix(Matrix3.fromRows(v(in, "row_x"), v(in, "row_y"), v(in, "row_z"))));
            case "matrix_diagonal" -> out("matrix", MathGraphValues.matrix(Matrix3.diagonal(v(in, "diagonal"))));
            case "matrix_identity" -> out("matrix", MathGraphValues.matrix(Matrix3.IDENTITY));
            case "matrix_add" -> out("matrix", MathGraphValues.matrix(m(in, "a").add(m(in, "b"))));
            case "matrix_subtract" -> out("matrix", MathGraphValues.matrix(m(in, "a").subtract(m(in, "b"))));
            case "matrix_multiply" -> out("matrix", MathGraphValues.matrix(m(in, "a").multiply(m(in, "b"))));
            case "matrix_scale" -> out("matrix", MathGraphValues.matrix(m(in, "matrix").scale(n(in, "scalar"))));
            case "matrix_transpose" -> out("matrix", MathGraphValues.matrix(m(in, "matrix").transpose()));
            case "matrix_determinant" -> out("value", MathGraphValues.number(m(in, "matrix").determinant()));
            case "matrix_inverse" -> out("matrix", MathGraphValues.matrix(m(in, "matrix").inverse()));
            case "matrix_transform" -> out("value", MathGraphValues.vector(m(in, "matrix").transform(v(in, "vector"))));
            case "quaternion_to_matrix" -> out("matrix", MathGraphValues.matrix(q(in, "quaternion").rotationMatrix()));
            case "matrix_to_quaternion" -> out("quaternion", MathGraphValues.quaternion(Quaternion.fromRotationMatrix(m(in, "matrix"))));
            case "local_to_world" -> out("value", MathGraphValues.vector(SpatialTransforms.localToWorld(v(in, "vector"), v(in, "origin"), q(in, "orientation"))));
            case "world_to_local" -> out("value", MathGraphValues.vector(SpatialTransforms.worldToLocal(v(in, "vector"), v(in, "origin"), q(in, "orientation"))));
            case "local_direction_to_world" -> out("value", MathGraphValues.vector(q(in, "orientation").rotate(v(in, "vector"))));
            case "world_direction_to_local" -> out("value", MathGraphValues.vector(q(in, "orientation").normalized().conjugate().rotate(v(in, "vector"))));
            case "tensor_to_world" -> out("matrix", MathGraphValues.matrix(SpatialTransforms.tensorToWorld(m(in, "tensor"), q(in, "orientation"))));
            case "tensor_to_local" -> out("matrix", MathGraphValues.matrix(SpatialTransforms.tensorToLocal(m(in, "tensor"), q(in, "orientation"))));
            case "quaternion_create" -> out("quaternion", MathGraphValues.quaternion(new Quaternion(n(in, "x"), n(in, "y"), n(in, "z"), n(in, "w"))));
            case "quaternion_normalize" -> out("quaternion", MathGraphValues.quaternion(q(in, "quaternion").normalized()));
            case "quaternion_conjugate" -> out("quaternion", MathGraphValues.quaternion(q(in, "quaternion").conjugate()));
            case "quaternion_inverse" -> out("quaternion", MathGraphValues.quaternion(q(in, "quaternion").inverse()));
            case "quaternion_magnitude" -> out("value", MathGraphValues.number(q(in, "quaternion").magnitude()));
            case "quaternion_multiply" -> out("quaternion", MathGraphValues.quaternion(q(in, "a").multiply(q(in, "b"))));
            case "quaternion_add" -> out("quaternion", MathGraphValues.quaternion(q(in, "a").add(q(in, "b"))));
            case "quaternion_subtract" -> out("quaternion", MathGraphValues.quaternion(q(in, "a").subtract(q(in, "b"))));
            case "quaternion_dot" -> out("value", MathGraphValues.number(q(in, "a").dot(q(in, "b"))));
            case "quaternion_scale" -> out("quaternion", MathGraphValues.quaternion(q(in, "quaternion").scale(n(in, "scalar"))));
            case "quaternion_rotate_vector" -> out("value", MathGraphValues.vector(q(in, "quaternion").rotate(v(in, "vector"))));
            case "quaternion_from_axis_angle" -> out("quaternion", MathGraphValues.quaternion(Quaternion.fromAxisAngle(v(in, "axis"), n(in, "angle"))));
            case "quaternion_to_axis_angle" -> {
                Quaternion.AxisAngle val = q(in, "quaternion").axisAngle();
                yield Map.of("axis", MathGraphValues.vector(val.axis()), "angle", MathGraphValues.number(val.radians()));
            }
            case "quaternion_slerp" -> out("quaternion", MathGraphValues.quaternion(q(in, "a").slerp(q(in, "b"), n(in, "amount"))));
            case "degrees_to_radians", "radians_to_degrees" -> out("angles", MathGraphValues.convertAngles(in.get("angles"), op.equals("degrees_to_radians")));
            case "linear_predict", "linear_integrator", "rigid_body_predict", "rigid_body_integrator" -> motion(op, ctx, in);
            case "quaternion_to_euler", "tait_bryan_to_euler" -> angleOutputs("euler", RotationMath.quaternionToEulerZxz(op.startsWith("quaternion")
                    ? q(in, "quaternion") : RotationMath.taitBryanXyzToQuaternion(MathGraphValues.taitBryan(in.get("tait_bryan")))));
            case "quaternion_to_tait_bryan", "euler_to_tait_bryan" -> angleOutputs("tait_bryan", RotationMath.quaternionToTaitBryanXyz(op.startsWith("quaternion")
                    ? q(in, "quaternion") : RotationMath.eulerZxzToQuaternion(MathGraphValues.euler(in.get("euler")))));
            case "euler_to_quaternion" -> out("quaternion", MathGraphValues.quaternion(RotationMath.eulerZxzToQuaternion(MathGraphValues.euler(in.get("euler")))));
            case "tait_bryan_to_quaternion" -> out("quaternion", MathGraphValues.quaternion(RotationMath.taitBryanXyzToQuaternion(MathGraphValues.taitBryan(in.get("tait_bryan")))));
            default -> throw new IllegalArgumentException("Unknown math operation: " + op);
        };
    }

    private static Map<String, GraphValue> motion(String op, GraphExecutionContext ctx, Map<String, GraphValue> in){
        boolean stateful = op.endsWith("integrator"), rigid = op.startsWith("rigid");
        GraphValue stored = stateful ? ctx.state("motion") : null;
        boolean reset = in.getOrDefault("reset", GraphValue.bool(false)).asBoolean();
        Map<String, GraphValue> state = in;
        if(stateful && !reset && stored != null && stored.value() instanceof Map<?, ?> values && !values.isEmpty()){
            state = new LinkedHashMap<>(in);
            for(String key : List.of("position", "velocity", "acceleration", "orientation", "angular_velocity", "angular_acceleration")){
                Object val = values.get(key);
                if(val instanceof GraphValue graph) state.put(key, graph);
            }
        }
        Map<String, GraphValue> res = new LinkedHashMap<>();
        boolean advance = !stateful || ctx.executionTriggered();
        double dt = !advance || reset && stateful ? 0 : n(in, "delta_time");
        if(rigid){
            double steps = n(in, "steps");
            if(steps < 1 || steps > 4096 || steps != Math.floor(steps)) throw new IllegalArgumentException("Steps must be an integer between 1 and 4096");
            Kinematics.RigidState val = Kinematics.predict(new Kinematics.RigidState(v(state, "position"), v(state, "velocity"), v(state, "acceleration"),
                    q(state, "orientation"), v(state, "angular_velocity"), v(state, "angular_acceleration")), v(in, "jerk"), v(in, "angular_jerk"),
                    m(in, "drag"), m(in, "angular_drag"), dt, (int) steps);
            res.put("position", MathGraphValues.vector(val.position()));
            res.put("velocity", MathGraphValues.vector(val.velocity()));
            res.put("acceleration", MathGraphValues.vector(val.acceleration()));
            res.put("orientation", MathGraphValues.quaternion(val.orientation()));
            res.put("angular_velocity", MathGraphValues.vector(val.angularVelocity()));
            res.put("angular_acceleration", MathGraphValues.vector(val.angularAcceleration()));
        }else{
            Kinematics.LinearState val = Kinematics.predict(new Kinematics.LinearState(v(state, "position"), v(state, "velocity"), v(state, "acceleration")), v(in, "jerk"), dt);
            res.put("position", MathGraphValues.vector(val.position()));
            res.put("velocity", MathGraphValues.vector(val.velocity()));
            res.put("acceleration", MathGraphValues.vector(val.acceleration()));
        }
        if(stateful && advance) ctx.state("motion", GraphValue.map(res));
        return Map.copyOf(res);
    }

    private static Map<String, GraphValue> angleOutputs(String output, Vector3 val){
        boolean euler = output.equals("euler");
        return Map.of(output, euler ? MathGraphValues.euler(val) : MathGraphValues.taitBryan(val),
                euler ? "alpha" : "roll", MathGraphValues.number(val.x()), euler ? "beta" : "pitch", MathGraphValues.number(val.y()),
                euler ? "gamma" : "yaw", MathGraphValues.number(val.z()));
    }

    private static Map<String, GraphValue> out(String key, GraphValue val){ return Map.of(key, val); }
    private static Vector3 v(Map<String, GraphValue> in, String key){ return MathGraphValues.vector(in.get(key)); }
    private static Quaternion q(Map<String, GraphValue> in, String key){ return MathGraphValues.quaternion(in.get(key)); }
    private static Matrix3 m(Map<String, GraphValue> in, String key){ return MathGraphValues.matrix(in.get(key)); }
    private static double n(Map<String, GraphValue> in, String key){
        double val = in.getOrDefault(key, GraphValue.number(0)).asNumber();
        MathGraphValues.number(val);
        return val;
    }

    private static Map<String, String> ports(String... entries){
        Map<String, String> res = new LinkedHashMap<>();
        for(String entry : entries){
            int split = entry.indexOf(':');
            res.put(entry.substring(0, split), entry.substring(split + 1));
        }
        return Map.copyOf(res);
    }
}
