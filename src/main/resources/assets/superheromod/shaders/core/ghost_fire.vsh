#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 parameters;
out vec2 uv;
out float viewDistance;
void main() {
    vec4 view=ModelViewMat*vec4(Position,1.0);
    gl_Position=ProjMat*view;
    parameters=Color;
    uv=UV0;
    viewDistance=length(view.xyz);
}
