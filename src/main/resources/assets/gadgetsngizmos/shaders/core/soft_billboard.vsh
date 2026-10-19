#version 150

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 localUv;
out vec4 vertexColor;
out vec3 lightMapColor;

// Pass the quad coordinates and tint to the soft particle shader
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(Position, FogShape);
    localUv = UV0;
    vertexColor = Color;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0).rgb;
}
