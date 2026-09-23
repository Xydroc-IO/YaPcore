#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform PaniniConfig {
    vec4 Params; // x = d, y = desired horizontal tangent, z = rendered tangent, w = vertical tangent
};

in vec2 texCoord;
out vec4 fragColor;

float paniniInverse(float p, float d) {
    float signP = p < 0.0 ? -1.0 : 1.0;
    float mag = abs(p);
    if (d < 1e-4 || mag < 1e-5) {
        return p;
    }
    float a = 1.0 + d;
    float A = a * a - (mag * d) * (mag * d);
    float B = -2.0 * a * mag;
    float C = mag * mag * (1.0 - d * d);
    float disc = B * B - 4.0 * A * C;
    if (disc < 0.0 || abs(A) < 1e-5) {
        return p;
    }
    float u = (-B + sqrt(disc)) / (2.0 * A);
    if (u < mag) {
        return p;
    }
    return signP * u;
}

void main() {
    float d = Params.x;
    float edge = max(Params.y, 1e-4);
    float renderEdge = max(Params.z, edge);
    float vert = max(Params.w, 1e-4);
    vec2 ndc = texCoord * 2.0 - 1.0;
    float u = paniniInverse(ndc.x * edge, d);
    float denom = 1.0 + d * sqrt(1.0 + u * u);
    float v = ndc.y * vert * denom / (1.0 + d);
    float sx = u / renderEdge;
    float sy = v / (vert * renderEdge / edge);
    vec2 src = clamp(vec2(sx, sy) * 0.5 + 0.5, 0.0, 1.0);
    fragColor = texture(InSampler, src);
}
