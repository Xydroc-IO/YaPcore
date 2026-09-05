#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"

void main() {
    vec4 albedo = texture2D(texture, texcoord.st) * glcolor;
    if (albedo.a < 0.1) discard;
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = albedo;
}
