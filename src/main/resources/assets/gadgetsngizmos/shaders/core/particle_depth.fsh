#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in float vertexAlpha;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a * vertexAlpha * ColorModulator.a < 0.1) discard;
    fragColor = vec4(0.0);
}
