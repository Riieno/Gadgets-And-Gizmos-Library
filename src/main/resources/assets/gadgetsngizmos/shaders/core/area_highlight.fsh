#version 150

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec4 normal;

out vec4 fragColor;

// Draw the highlighted area with a soft radial edge
void main() {
    vec2 centered = texCoord0 * 2.0 - 1.0;
    float dist = length(centered);
    float falloff = 1.0 - smoothstep(0.72, 1.0, dist);
    float core = 1.0 - smoothstep(0.0, 0.55, dist);
    float alpha = clamp(falloff * 0.9 + core * 0.35, 0.0, 1.0);
    vec4 color = vec4(0.15, 0.95, 0.9, alpha) * vertexColor * ColorModulator;
    color *= lightMapColor;
    if (color.a <= 0.01) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
