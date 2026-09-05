#version 120

uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;

varying vec4 texcoord;

void main() {
    vec3 color = texture2D(colortex0, texcoord.st).rgb;

    // Mild filmic tonemap — avoid blowing block-edge light leaks into chalk white
    color = color / (color + vec3(0.95)) * 1.08;
    color = pow(max(color, 0.0), vec3(0.98));

    vec2 uv = texcoord.st - 0.5;
    float vig = 1.0 - dot(uv, uv) * 0.22;
    color *= vig;

    gl_FragData[0] = vec4(color, 1.0);
}
