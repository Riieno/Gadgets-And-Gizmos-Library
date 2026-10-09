#version 150

uniform sampler2D Sampler0;
uniform sampler2D SceneDepth;
uniform mat4 InverseProjectionMat;
uniform vec4 DepthViewport;
uniform int HasSceneDepth;

in vec4 vertexColor;
in vec2 texCoord;
in vec3 surfacePosition;
out vec4 fragColor;

void main(){
    if(HasSceneDepth != 0){
        vec2 screen = (gl_FragCoord.xy - DepthViewport.xy) / DepthViewport.zw;
        float depth = texture(SceneDepth, screen).r;
        if(depth >= 1.0) discard;
        vec4 scene = InverseProjectionMat * vec4(screen * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        float sceneDistance = -scene.z / scene.w;
        float surfaceDistance = -surfacePosition.z;
        float tolerance = 0.01 + 2.0 * (abs(dFdx(surfaceDistance)) + abs(dFdy(surfaceDistance)));
        if(sceneDistance + tolerance < surfaceDistance) discard;
    }
    vec4 material = texture(Sampler0, texCoord);
    if(material.a < 0.1 || vertexColor.a <= 0.0) discard;
    fragColor = vec4(material.rgb * vertexColor.rgb * vec3(1.0, 0.95, 0.82) * 2.8,
            material.a * vertexColor.a);
}
