package com.klonmc;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class World {
    public static final int RADIUS = 2;      // jarak muat chunk (kecil = ringan)
    static final int SAND_LEVEL = 22;

    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final int seed = 1337;

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    // ---------- Generasi dunia ----------

    private float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263 + seed * 1442695041;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= h >> 16;
        return (h & 0xffff) / 65535f;
    }

    private float noise(float x, float z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        float fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float a = hash(x0, z0), b = hash(x0 + 1, z0);
        float c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
        return MathUtils.lerp(MathUtils.lerp(a, b, fx), MathUtils.lerp(c, d, fx), fz);
    }

    private int height(int x, int z) {
        return (int) (20 + noise(x / 32f, z / 32f) * 18 + noise(x / 12f, z / 12f) * 6);
    }

    private boolean isTree(int x, int z) {
        return height(x, z) > SAND_LEVEL && hash(x * 31 + 7, z * 17 + 3) < 0.012f;
    }

    private void put(Chunk c, int wx, int y, int wz, int id, boolean onlyAir) {
        int lx = wx - c.cx * Chunk.S, lz = wz - c.cz * Chunk.S;
        if (lx < 0 || lx >= Chunk.S || lz < 0 || lz >= Chunk.S || y < 0 || y >= Chunk.H) return;
        int i = Chunk.idx(lx, y, lz);
        if (onlyAir && c.blocks[i] != 0) return;
        c.blocks[i] = (byte) id;
    }

    private Chunk generate(int cx, int cz) {
        Chunk c = new Chunk(cx, cz);
        for (int x = 0; x < Chunk.S; x++)
            for (int z = 0; z < Chunk.S; z++) {
                int wx = cx * Chunk.S + x, wz = cz * Chunk.S + z;
                int h = height(wx, wz);
                boolean sand = h <= SAND_LEVEL;
                for (int y = 0; y <= h; y++) {
                    int id;
                    if (y == h) id = sand ? 4 : 1;
                    else if (y >= h - 3) id = sand ? 4 : 2;
                    else id = 3;
                    c.blocks[Chunk.idx(x, y, z)] = (byte) id;
                }
            }
        // pohon (dicek juga di luar batas chunk supaya daun tidak terpotong)
        for (int tx = cx * Chunk.S - 2; tx < cx * Chunk.S + Chunk.S + 2; tx++)
            for (int tz = cz * Chunk.S - 2; tz < cz * Chunk.S + Chunk.S + 2; tz++) {
                if (!isTree(tx, tz)) continue;
                int gh = height(tx, tz);
                int th = 4 + (int) (hash(tx + 91, tz + 57) * 2f);
                for (int y = gh + 1; y <= gh + th; y++) put(c, tx, y, tz, 5, false);
                for (int y = gh + th - 2; y <= gh + th + 1; y++) {
                    int r = (y >= gh + th) ? 1 : 2;
                    for (int dx = -r; dx <= r; dx++)
                        for (int dz = -r; dz <= r; dz++) {
                            if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                            if (dx == 0 && dz == 0 && y <= gh + th) continue;
                            put(c, tx + dx, y, tz + dz, 6, true);
                        }
                }
            }
        return c;
    }

    // ---------- Muat / buang chunk (dunia tak terbatas) ----------

    private void addChunk(int cx, int cz) {
        Chunk c = generate(cx, cz);
        chunks.put(key(cx, cz), c);
        markDirty(cx - 1, cz); markDirty(cx + 1, cz);
        markDirty(cx, cz - 1); markDirty(cx, cz + 1);
    }

    /** all = true: muat semuanya sekaligus. false: maksimal satu chunk per frame. */
    public void update(float px, float pz, boolean all) {
        int pcx = Math.floorDiv((int) Math.floor(px), Chunk.S);
        int pcz = Math.floorDiv((int) Math.floor(pz), Chunk.S);
        int gen = 0;
        outer:
        for (int r = 0; r <= RADIUS; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (chunks.containsKey(key(pcx + dx, pcz + dz))) continue;
                    addChunk(pcx + dx, pcz + dz);
                    gen++;
                    if (!all && gen >= 1) break outer;
                }
        Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Chunk c = it.next().getValue();
            if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > RADIUS + 1) {
                c.dispose();
                it.remove();
            }
        }
    }

    // ---------- Akses blok ----------

    public byte get(int x, int y, int z) {
        if (y < 0 || y >= Chunk.H) return 0;
        Chunk c = chunks.get(key(Math.floorDiv(x, Chunk.S), Math.floorDiv(z, Chunk.S)));
        if (c == null) return 0;
        return c.blocks[Chunk.idx(Math.floorMod(x, Chunk.S), y, Math.floorMod(z, Chunk.S))];
    }

    private void markDirty(int cx, int cz) {
        Chunk c = chunks.get(key(cx, cz));
        if (c != null) c.dirty = true;
    }

    public int surface(int x, int z) {
        for (int y = Chunk.H - 1; y >= 0; y--) if (get(x, y, z) != 0) return y;
        return 0;
    }

    // ---------- Render ----------

    public void buildAll() {
        for (Chunk c : chunks.values()) if (c.dirty) c.build(this);
    }

    public void render(ShaderProgram shader) {
        int builds = 0;
        for (Chunk c : chunks.values()) {
            if (c.dirty && builds < 2) { c.build(this); builds++; }
            c.render(shader);
        }
    }

    public void dispose() {
        for (Chunk c : chunks.values()) c.dispose();
    }
}
