package thaumcraft.client.lib.warp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Finite OBJ bridge for the original sonic hemisphere, including the old loader's UV inset. */
public record SourceSonicMesh(int vertexCount, List<Triangle> triangles) {
    public record Corner(float x, float y, float z, float u, float v) {}
    public record Triangle(Corner a, Corner b, Corner c, float nx, float ny, float nz) {}
    public SourceSonicMesh { triangles = List.copyOf(triangles); }
    public static SourceSonicMesh read(Reader source) throws IOException {
        List<float[]> vertices = new ArrayList<>(), uv = new ArrayList<>();
        List<Triangle> triangles = new ArrayList<>();
        BufferedReader lines = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        String line;
        while ((line = lines.readLine()) != null) {
            String[] words = line.trim().split("\\s+");
            if (words.length == 0) continue;
            switch (words[0]) {
                case "v" -> vertices.add(new float[]{finite(words[1]), finite(words[2]), finite(words[3])});
                case "vt" -> uv.add(new float[]{finite(words[1]), 1 - finite(words[2])});
                case "f" -> {
                    if (words.length != 4) throw new IOException("Sonic hemisphere requires triangular faces");
                    float[][] points = new float[3][], tex = new float[3][];
                    for (int i = 0; i < 3; i++) {
                        String[] indices = words[i + 1].split("/");
                        points[i] = vertices.get(Integer.parseInt(indices[0]) - 1);
                        tex[i] = uv.get(Integer.parseInt(indices[1]) - 1);
                    }
                    float meanU = (tex[0][0] + tex[1][0] + tex[2][0]) / 3,
                        meanV = (tex[0][1] + tex[1][1] + tex[2][1]) / 3;
                    Corner[] corners = new Corner[3];
                    for (int i = 0; i < 3; i++) corners[i] = new Corner(points[i][0], points[i][1], points[i][2],
                        tex[i][0] + (tex[i][0] > meanU ? -0.0005F : 0.0005F),
                        tex[i][1] + (tex[i][1] > meanV ? -0.0005F : 0.0005F));
                    double ax = points[1][0] - points[0][0], ay = points[1][1] - points[0][1], az = points[1][2] - points[0][2];
                    double bx = points[2][0] - points[0][0], by = points[2][1] - points[0][1], bz = points[2][2] - points[0][2];
                    double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                    double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (length < 1.0E-4) throw new IOException("Degenerate sonic hemisphere triangle");
                    triangles.add(new Triangle(corners[0], corners[1], corners[2], (float)(nx / length), (float)(ny / length), (float)(nz / length)));
                }
                default -> { /* Original normals/material directives do not affect its face-normal renderer. */ }
            }
        }
        if (vertices.size() != 91 || triangles.size() != 160) throw new IOException("Unexpected source sonic hemisphere topology");
        return new SourceSonicMesh(vertices.size(), triangles);
    }
    private static float finite(String value) throws IOException {
        float result = Float.parseFloat(value);
        if (!Float.isFinite(result)) throw new IOException("Non-finite sonic mesh coordinate");
        return result;
    }
    public static int frame(int age, float partialTick, int lifetime) {
        return Math.min(15, (int)(14.0F * ((age + partialTick) / lifetime)) + 1);
    }
}
