#version 150
in vec2 texCoord;
// Per-quad data: stable seed, facing highlight, normalized age, opacity (not an RGB tint).
in vec4 vertexColor;
out vec4 fragColor;

float hash(float n) { return fract(sin(n * 127.1 + 311.7) * 43758.5453); }

// Signed distance outside a counterclockwise polygon edge.
float edge(vec2 p, vec2 a, vec2 b) {
    vec2 e = b - a;
    return dot(p - a, vec2(e.y, -e.x)) / length(e);
}

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float seed = floor(vertexColor.r * 255.0 + 0.5);
    float h = hash(seed + 1.0);
    float glint = vertexColor.g;
    float age = vertexColor.b;
    // Asymmetric clipped foil, not a needle or a uniform diamond.
    vec2 a = vec2(-0.66, -0.34 - 0.22 * h);
    vec2 b = vec2(0.16 + 0.30 * h, -0.67);
    vec2 c = vec2(0.67, 0.05 + 0.31 * hash(seed + 4.0));
    vec2 d = vec2(-0.08 - 0.25 * h, 0.69);
    vec2 e = vec2(-0.57, 0.30);
    float dist = max(max(edge(p,a,b), edge(p,b,c)), max(edge(p,c,d), max(edge(p,d,e), edge(p,e,a))));
    if (h < 0.28) dist = max(dist, max(edge(p,a,c), edge(p,d,a)));
    bool dust = hash(seed + 8.0) < 0.16;
    if (dust) dist = length(p) - 0.24;
    float aa = clamp(fwidth(dist), 0.015, 0.36);
    float coverage = 1.0 - smoothstep(-aa, aa, dist);
    float halo = exp(-max(dist, 0.0) * 19.0) * (1.0 - coverage)
            * (dust ? 0.18 : 0.065) * (1.0 - smoothstep(0.12, 0.30, dist));
    // A shallow fold changes luminance as foil turns. No red/orange cooling phase.
    float fold = smoothstep(-0.08, 0.08, p.x * 0.65 + p.y * 0.42 + h * 0.20);
    float light = clamp(0.30 + 0.38 * fold + 0.32 * glint, 0.0, 1.0);
    vec3 gold = mix(vec3(0.92, 0.77, 0.32), vec3(1.0, 0.97, 0.73), light);
    gold = mix(gold, vec3(1.0, 0.995, 0.92), glint * (dust ? 0.85 : 0.55));
    // Glow recedes first; foil then fades smoothly, without popping or noisy dissolves.
    float alpha = (coverage * (0.78 + 0.20 * glint) + halo * (1.0 - age)) * vertexColor.a;
    if (alpha < 0.001) discard;
    fragColor = vec4(gold, alpha);
}
