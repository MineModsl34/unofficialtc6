#version 150
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 position;
out vec2 texCoord;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    position = (ModelViewMat * vec4(Position, 1.0)).xyz;
    texCoord = UV0;
}
