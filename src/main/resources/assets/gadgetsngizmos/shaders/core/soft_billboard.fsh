#version 150

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform int HasSceneDepth;
uniform vec4 ColorModulator;
uniform float EmissionStrength;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 localUv;
in vec4 vertexColor;
in vec3 lightMapColor;

out vec4 fragColor;

// Convert projected depth to view distance for intersection fading
float viewDistance(float depth) {
    float divisor = depth * 2.0 - 1.0 + ProjMat[2][2];
    return abs(ProjMat[3][2] / max(abs(divisor), 0.00001));
}

// Draw the soft cloud texture with a bright center and soft scene intersections
void main() {
    float cloud = texture(Sampler0, localUv).a;
    float alpha = vertexColor.a * cloud;
    if (alpha <= 0.02) discard;

    float depthFade = 1.0;
    if (HasSceneDepth != 0) {
        float sceneDepth = texture(Sampler3, gl_FragCoord.xy / ScreenSize).r;
        depthFade = smoothstep(0.0, 0.7, viewDistance(sceneDepth) - viewDistance(gl_FragCoord.z));
    }

    alpha *= depthFade;
    if (alpha <= 0.003) discard;

    vec3 tint = vertexColor.rgb * ColorModulator.rgb;
    vec3 color = tint * (lightMapColor * (1.0 + cloud * 0.55)
            + vec3(EmissionStrength * cloud));
    fragColor = linear_fog(vec4(color, alpha * ColorModulator.a),
            vertexDistance, FogStart, FogEnd, FogColor);
}
