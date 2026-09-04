#version 120

#include "/lib/common.glsl"

void main() {
    vec4 albedo = texture2D(texture, texcoord.st) * glcolor;
    albedo.rgb *= texture2D(lightmap, lmcoord.st).rgb;
    /* DRAWBUFFERS:012 */
    gl_FragData[0] = albedo;
    gl_FragData[1] = vec4(encodeNormal(normalize(normal)), 0.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
