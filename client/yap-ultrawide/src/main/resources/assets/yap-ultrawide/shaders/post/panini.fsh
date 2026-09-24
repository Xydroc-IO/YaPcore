#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform PaniniConfig {
    vec4 Params; // x = horizontal widen, y = where the side squeeze starts
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float widen = max(Params.x, 1.0);
    float start = clamp(Params.y, 0.0, 0.95);
    vec2 ndc = texCoord * 2.0 - 1.0;
    float ax = abs(ndc.x);
    float t = clamp((ax - start) / max(1.0 - start, 1e-3), 0.0, 1.0);
    t = t * t * (3.0 - 2.0 * t);
    // Center matches the FOV slider. The side strips pack the extra width.
    // Y is copied straight so the sky is not resampled.
    float srcX = mix(ndc.x / widen, sign(ndc.x), t);
    fragColor = texture(InSampler, clamp(vec2(srcX, ndc.y) * 0.5 + 0.5, 0.0, 1.0));
}
