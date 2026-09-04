#version 120

#include "/lib/common.glsl"
#include "/lib/sky.glsl"

varying vec3 viewDir;

void main() {
    vec3 dir = normalize(viewDir);
    float dayness = clamp(luma(skyColor) * 2.5, 0.0, 1.0);
    vec3 dayCol = yapAtmosphere(dir, sunPosition, skyColor, fogColor);
    vec3 nightCol = vec3(0.02, 0.03, 0.08)
                  + vec3(0.12, 0.16, 0.28) * pow(max(dir.y, 0.0), 2.0);
    float moon = max(dot(dir, normalize(moonPosition)), 0.0);
    nightCol += vec3(0.55, 0.6, 0.75) * pow(moon, 128.0);
    vec3 col = mix(nightCol, dayCol, dayness);
    /* DRAWBUFFERS:012 */
    gl_FragData[0] = vec4(col, 1.0);
    gl_FragData[1] = vec4(0.5, 0.5, 0.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
