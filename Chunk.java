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

    // urutan: +X, -X, +Y, -Y, +Z, -Z
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
    // 0 = udara, 1 = rumput, 2 = tanah, 3 = batu
    static final float[][] COLOR = {
        {0,0,0}, {0.35f,0.7f,0.25f}, {0.55f,0.4f,0.25f}, {0.5f,0.5f,0.5f}
    };
    static final short[] TRI = {0, 1, 2, 0, 2, 3};

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
                    for (int f = 0; f < 6; f++) {
                        if (w.get(wx + DIR[f][0], y + DIR[f][1], wz + DIR[f][2]) != 0) continue;
                        float[] c = COLOR[b];
                        float sh = SHADE[f];
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
        if (mesh != null) mesh.dispose();
    }
}
