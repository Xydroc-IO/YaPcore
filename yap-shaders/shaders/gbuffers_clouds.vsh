#version 120

#include "/lib/common.glsl"

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    viewPos = view.xyz;
    worldPos = (gbufferModelViewInverse * view).xyz + cameraPosition;
    texcoord = gl_MultiTexCoord0;
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * view;
}
