#version 330
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
in vec4 vertexColor;
in float pSeed;
in float pAge;
out vec4 fragColor;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    vec2 drift = vec2(pSeed * 7.3, pAge * 0.18);
    float n = noise(p * 3.1 + drift) * 0.56
            + noise(p * 6.3 + drift * 1.3) * 0.28
            + noise(p * 13.0 + drift * 1.7) * 0.16;
    float density = 1.0 - smoothstep(0.42, 0.98, length(p) + (n - 0.5) * 0.45);
    float alpha = density * vertexColor.a * (0.72 + n * 0.28);
    alpha *= 0.95 + texture(Sampler0, texCoord * 0.3 + pSeed).r * 0.05;
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    alpha *= smoothstep(0.0, 0.001, gl_FragCoord.z - texture(Sampler1, screenUv).r);
    if (alpha < 0.004) discard;
    float light = 0.65 + n * 0.7 + (1.0 - texCoord.y) * 0.12;
    fragColor = vec4(vertexColor.rgb * light, alpha);
}
