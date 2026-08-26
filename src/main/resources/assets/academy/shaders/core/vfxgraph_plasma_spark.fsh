#version 330

uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float streak = exp(-p.x * p.x * 8.0) * (1.0 - smoothstep(0.25, 1.0, abs(p.y)));
    float alpha = vertexColor.a * streak;
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    vec3 color = mix(vertexColor.rgb, vec3(1.0), streak * 0.62);
    fragColor = vec4(color * alpha, alpha);
}
