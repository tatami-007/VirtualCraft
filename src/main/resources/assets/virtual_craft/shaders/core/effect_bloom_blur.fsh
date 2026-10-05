#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 Direction;
uniform float Extract;
in vec2 texCoord;
out vec4 fragColor;
vec3 sampleEmission(vec2 uv) {
    vec3 color = texture(DiffuseSampler, uv).rgb;
    // Exposure belongs to the emission pyramid, not the visible sprite or the world.
    // RGBA16F outputs retain the hot highlights instead of clamping every level to white.
    float bright = max(max(color.r,color.g),color.b);
    float energy = smoothstep(0.025,0.20,bright) * (1.6 + 2.4 * smoothstep(0.22,0.85,bright));
    return color * mix(1.0, energy,Extract);
}
void main() {
    // Isotropic tent downsample: no horizontal/vertical ping-pong pair per scale.
    vec3 color = sampleEmission(texCoord) * 0.5;
    color += (sampleEmission(texCoord + Direction)
           + sampleEmission(texCoord - Direction)
           + sampleEmission(texCoord + vec2(Direction.x,-Direction.y))
           + sampleEmission(texCoord + vec2(-Direction.x,Direction.y))) * 0.125;
    fragColor = vec4(color,1.0);
}
