#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"
#include "/lib/wind.glsl"

attribute vec2 mc_Entity;
attribute vec4 at_midBlock;

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    vec3 wp = (gbufferModelViewInverse * view).xyz + cameraPosition;
    float tip = clamp(gl_MultiTexCoord0.t, 0.0, 1.0);
    wp = yapApplyWind(wp, mc_Entity.x, tip, at_midBlock.xyz);
    view = gbufferModelView * vec4(wp - cameraPosition, 1.0);

    viewPos = view.xyz;
    worldPos = wp;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    texcoord = gl_MultiTexCoord0;
    lmcoord = gl_TextureMatrix[1] * gl_MultiTexCoord1;
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * view;
}
