#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main(){
    vec3 color = texture(Sampler0, texCoord0).rgb * vertexColor.rgb * ColorModulator.rgb;
    // Distribute the final 8-bit rounding error without moving noise between frames
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    fragColor = vec4(clamp(color + (noise - 0.5) / 255.0, 0.0, 1.0), 1.0);
}
