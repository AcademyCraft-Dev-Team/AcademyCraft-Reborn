#version 330
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
in vec4 vertexColor;
in float pSeed;
in float pAge;
out vec4 fragColor;
void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float r = length(p);
    float core = exp(-r * r * 24.0);
    float glow = exp(-r * r * 5.5) * 0.30;
    float rays = exp(-abs(p.y) * 85.0) * exp(-abs(p.x) * 5.0) * 0.18;
    float edge = 1.0 - smoothstep(0.70, 1.0, r);
    float grain = texture(Sampler0, texCoord * 0.3 + pAge * 0.01).r;
    float alpha = vertexColor.a * (core + glow + rays) * edge * (0.97 + grain * 0.03);
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    alpha *= smoothstep(0.0, 0.001, gl_FragCoord.z - sceneDepth);
    if (alpha < 0.002) discard;
    fragColor = vec4(vertexColor.rgb * alpha, alpha);
}
