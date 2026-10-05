#version 150
uniform sampler2D Sampler0;
uniform float Fade;
in vec2 texCoord;
in vec4 vertexColor;
in vec3 viewPosition;
out vec4 fragColor;
// Original warm-gold afterimage, with stable skin-pixel dissolution.
float cellHash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void main() {
    vec4 skin = texture(Sampler0, texCoord);
    if (skin.a < 0.1 || Fade <= 0.0) discard;
    float noise = cellHash(floor(texCoord * 64.0));
    if (noise > min(1.0, Fade * 1.6)) discard;
    vec3 normal = normalize(cross(dFdx(viewPosition), dFdy(viewPosition)));
    float rim = pow(1.0 - abs(dot(normal, normalize(-viewPosition))), 2.0);
    float detail = dot(skin.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 gold = mix(vec3(0.57, 0.28, 0.055), vertexColor.rgb, 0.4 + detail * 0.6);
    gold = mix(gold, vec3(1.0, 0.96, 0.76), rim * 0.8);
    fragColor = vec4(gold, skin.a * vertexColor.a * Fade * (0.55 + rim * 0.45));
}
