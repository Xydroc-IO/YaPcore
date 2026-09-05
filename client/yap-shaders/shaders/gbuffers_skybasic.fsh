#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"
#include "/lib/sky.glsl"

varying vec3 viewDir;

void main() {
    vec3 dir = normalize(viewDir);
    // Full procedural twilight — no crude skyColor luma mix that leaves
    // stars on one side and brown sludge on the other.
    vec3 col = yapAtmosphere(dir, sunPosition, skyColor, fogColor);
    /* DRAWBUFFERS:012 */
    gl_FragData[0] = vec4(col, 1.0);
    gl_FragData[1] = vec4(0.5, 0.5, 1.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
