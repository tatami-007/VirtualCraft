#version 150
in vec2 texCoord;
in vec4 vertexColor;
in vec3 viewPosition;
out vec4 fragColor;
void main() {
    vec2 p = abs(texCoord * 2.0 - 1.0);
    // Narrow analytic diamond: white-hot tip/core and amber body, not a fuzzy billboard.
    float diamond = p.x + p.y;
    float aa = max(fwidth(diamond), 0.015);
    float mask = 1.0 - smoothstep(1.0 - aa, 1.0, diamond);
    if (mask < 0.01) discard;
    float core = 1.0 - smoothstep(0.08, 0.32, p.x + p.y * 0.22);
    vec3 color = mix(vertexColor.rgb, vec3(1.0, 0.98, 0.85), core);
    fragColor = vec4(color, mask * vertexColor.a);
}
