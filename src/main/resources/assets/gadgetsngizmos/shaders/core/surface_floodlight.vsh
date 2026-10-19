#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord;
out vec3 surfacePosition;

void main(){
    vec4 surface = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * surface;
    surfacePosition = surface.xyz;
    vertexColor = Color;
    texCoord = UV0;
}
