#version 150
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec3 projective;
out vec4 fragColor;
void main(){fragColor=textureProj(Sampler0,projective)*vertexColor;}
