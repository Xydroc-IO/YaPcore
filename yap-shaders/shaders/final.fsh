#version 120

uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;

varying vec4 texcoord;

void main() {
    vec3 color = texture2D(colortex0, texcoord.st).rgb;

    // ACES-ish filmic tonemap keep water highlights
    color = color * 1.05;
    color = color / (color + vec3(0.85)) * 1.2;
    color = pow(max(color, 0.0), vec3(0.95));

    vec2 uv = texcoord.st - 0.5;
    float vig = 1.0 - dot(uv, uv) * 0.28;
    color *= vig;

    gl_FragData[0] = vec4(color, 1.0);
}
