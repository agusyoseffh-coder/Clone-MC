package com.klonmc;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

public class Chunk {
    public static final int S = 16, H = 64;

    public final int cx, cz;
    public final byte[] blocks = new byte[S * S * H];
    public boolean dirty = true;
    private Mesh mesh;

    // urutan sisi: +X, -X, +Y(atas), -Y(bawah), +Z, -Z
    static final int[][] DIR = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final int[][][] QUAD = {
        {{1,0,0},{1,1,0},{1,1,1},{1,0,1}},
        {{0,0,1},{0,1,1},{0,1,0},{0,0,0}},
        {{0,1,1},{1,1,1},{1,1,0},{0,1,0}},
        {{0,0,0},{1,0,0},{1,0,1},{0,0,1}},
        {{1,0,1},{1,1,1},{0,1,1},{0,0,1}},
        {{0,0,0},{0,1,0},{1,1,0},{1,0,0}}
    };
    static final float[] SHADE = {0.8f, 0.8f, 1f, 0.5f, 0.65f, 0.65f};
    static final short[] TRI = {0, 1, 2, 0, 2, 3};

    // 0 udara, 1 rumput, 2 tanah, 3 batu, 4 pasir, 5 kayu, 6 daun, 7 papan, 8 cobblestone, 9 bata
    static final float[][] COLOR = {
        {0, 0, 0},
        {0.35f, 0.70f, 0.25f},
        {0.55f, 0.40f, 0.25f},
        {0.50f, 0.50f, 0.50f},
        {0.85f, 0.80f, 0.50f},
        {0.40f, 0.30f, 0.15f},
        {0.20f, 0.55f, 0.15f},
        {0.70f, 0.55f, 0.30f},
        {0.40f, 0.40f, 0.42f},
        {0.60f, 0.25f, 0.20f}
    };
    static final float[] LOG_TOP = {0.70f, 0.55f, 0.30f};

    public static float[] color(int id, int face) {
        if (id == 1 && face != 2) return COLOR[2];          // sisi dan bawah rumput = tanah
        if (id == 5 && (face == 2 || face == 3)) return LOG_TOP;
        return COLOR[id];
    }

    public Chunk(int cx, int cz) { this.cx = cx; this.cz = cz; }

    public static int idx(int x, int y, int z) { return (y * S + z) * S + x; }

    public void build(World w) {
        dirty = false;
        FloatArray v = new FloatArray();
        ShortArray in = new ShortArray();
        int n = 0;
        for (int y = 0; y < H; y++)
            for (int z = 0; z < S; z++)
                for (int x = 0; x < S; x++) {
                    byte b = blocks[idx(x, y, z)];
                    if (b == 0) continue;
                    int wx = cx * S + x, wz = cz * S + z;
                    float var = 0.94f + ((wx * 73856093 ^ y * 19349663 ^ wz * 83492791) & 3) * 0.02f;
                    for (int f = 0; f < 6; f++) {
                        if (w.get(wx + DIR[f][0], y + DIR[f][1], wz + DIR[f][2]) != 0) continue;
                        float[] c = color(b, f);
                        float sh = SHADE[f] * var;
                        float col = Color.toFloatBits(c[0] * sh, c[1] * sh, c[2] * sh, 1f);
                        for (int q = 0; q < 4; q++) {
                            v.add(wx + QUAD[f][q][0]);
                            v.add(y + QUAD[f][q][1]);
                            v.add(wz + QUAD[f][q][2]);
                            v.add(col);
                        }
                        for (int k = 0; k < 6; k++) in.add((short) (n + TRI[k]));
                        n += 4;
                    }
                }
        if (mesh != null) { mesh.dispose(); mesh = null; }
        if (n == 0) return;
        mesh = new Mesh(true, n, in.size, VertexAttribute.Position(), VertexAttribute.ColorPacked());
        mesh.setVertices(v.items, 0, v.size);
        mesh.setIndices(in.items, 0, in.size);
    }

    public void render(ShaderProgram shader) {
        if (mesh != null) mesh.render(shader, GL20.GL_TRIANGLES);
    }

    public void dispose() {
        if (mesh != null) { mesh.dispose(); mesh = null; }
    }
}
