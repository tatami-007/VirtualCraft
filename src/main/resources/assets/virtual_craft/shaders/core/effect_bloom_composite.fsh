#version 150
uniform sampler2D NearSampler;
uniform sampler2D MiddleSampler;
uniform sampler2D FarSampler;
uniform float Strength;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 halo = texture(NearSampler,texCoord).rgb * 0.24
              + texture(MiddleSampler,texCoord).rgb * 0.52
              + texture(FarSampler,texCoord).rgb * 0.72;
    // Compress by a single luminance bound, preserving the gold hue of overlapping highlights.
    float peak = max(max(halo.r,halo.g),halo.b);
    halo /= 1.0 + peak * 0.85;
    fragColor = vec4(halo * Strength,0.0);
}
