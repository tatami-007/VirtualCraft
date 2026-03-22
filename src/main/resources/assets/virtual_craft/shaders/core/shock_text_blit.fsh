#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Alpha;

in vec2 texCoord;

out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float alphaSample = texture(DiffuseSampler, texCoord).a;
    float fillMask = smoothstep(0.02, 0.18, alphaSample);
    if (fillMask <= 0.01) {
        discard;
    }

    float hue = fract(texCoord.x * 7.5 - Time * 0.22 + sin(texCoord.y * 18.0 - Time * 0.7) * 0.02);
    float saturation = 0.98;
    float value = 1.0;
    vec3 rainbow = hsv2rgb(vec3(hue, saturation, value));
    float shine = 0.90 + 0.10 * sin(texCoord.x * 20.0 - Time * 1.4);
    vec3 color = mix(rainbow, vec3(1.0), 0.08) * shine;

    fragColor = vec4(color, fillMask * Alpha);
}
