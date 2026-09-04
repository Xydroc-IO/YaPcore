#version 120

#include "/lib/common.glsl"

void main() {
    vec4 tex = texture2D(texture, texcoord.st) * glcolor;
    tex.rgb *= 1.15;
    /* DRAWBUFFERS:012 */
    gl_FragData[0] = tex;
    gl_FragData[1] = vec4(0.5, 0.5, 0.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
