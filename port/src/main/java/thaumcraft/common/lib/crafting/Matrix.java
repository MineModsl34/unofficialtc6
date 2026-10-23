package thaumcraft.common.lib.crafting;

import java.util.Objects;
import thaumcraft.api.crafting.Part;

/** Source row/column rotation; cell Part references (including null holes) remain shared. */
public class Matrix {
    int rows, cols;
    Part[][] matrix;
    public Matrix(Part[][] source) {
        Objects.requireNonNull(source, "matrix");
        if (source.length == 0 || source[0] == null || source[0].length == 0) throw new IllegalArgumentException("Empty dust matrix");
        rows = source.length; cols = source[0].length;
        matrix = new Part[rows][cols];
        for (int row = 0; row < rows; row++) {
            if (source[row] == null || source[row].length != cols) throw new IllegalArgumentException("Ragged dust matrix");
            System.arraycopy(source[row], 0, matrix[row], 0, cols);
        }
    }
    public void Rotate90DegRight(int times) {
        // The original negative count did nothing; positive multiples of four preserve shape.
        for (int a = 0; a < (times < 0 ? 0 : times % 4); a++) {
            Part[][] rotated = new Part[cols][rows];
            for (int x = 0; x < rows; x++) for (int z = 0; z < cols; z++) rotated[z][rows - x - 1] = matrix[x][z];
            matrix = rotated; int previous = rows; rows = cols; cols = previous;
        }
    }
    public Part[][] getMatrix() { return matrix; }
}
