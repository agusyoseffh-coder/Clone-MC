package com.klonmc;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.HashMap;
import java.util.Map;

public class World {
    public static final int RADIUS = 2; // 5x5 chunk, ringan untuk HP kentang

    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final int seed = 1337;

    public World() {
        for (int cx = -RADIUS; cx <= RADIUS; cx++)
            for (int cz = -RADIUS; cz <= RADIUS; cz++)
                chunks.put(key(cx, cz), generate(cx, cz));
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private Chunk generate(int cx, int cz) {
        Chunk c = new Chunk(cx, cz);
        for (int x = 0; x < Chunk.S; x++)
            for (int z = 0; z < Chunk.S; z++) {
                int wx = cx * Chunk.S + x, wz = cz * Chunk.S + z;
                int h = (int) (20 + noise(wx / 32f, wz / 32f) * 18 + noise(wx / 12f, wz / 12f) * 6);
                for (int y = 0; y <= h; y++) {
                    byte id = (y == h) ? 1 : (y >= h - 3 ? (byte) 2 : (byte) 3);
                    c.blocks[Chunk.idx(x, y, z)] = id;
                }
            }
        return c;
    }

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

    public byte get(int x, int y, int z) {
        if (y < 0 || y >= Chunk.H) return 0;
        Chunk c = chunks.get(key(Math.floorDiv(x, Chunk.S), Math.floorDiv(z, Chunk.S)));
        if (c == null) return 0;
        return c.blocks[Chunk.idx(Math.floorMod(x, Chunk.S), y, Math.floorMod(z, Chunk.S))];
    }

    public void set(int x, int y, int z, byte id) {
        if (y < 0 || y >= Chunk.H) return;
        int cx = Math.floorDiv(x, Chunk.S), cz = Math.floorDiv(z, Chunk.S);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return;
        int lx = Math.floorMod(x, Chunk.S), lz = Math.floorMod(z, Chunk.S);
        c.blocks[Chunk.idx(lx, y, lz)] = id;
        c.dirty = true;
        if (lx == 0) markDirty(cx - 1, cz);
        if (lx == Chunk.S - 1) markDirty(cx + 1, cz);
        if (lz == 0) markDirty(cx, cz - 1);
        if (lz == Chunk.S - 1) markDirty(cx, cz + 1);
    }

    private void markDirty(int cx, int cz) {
        Chunk c = chunks.get(key(cx, cz));
        if (c != null) c.dirty = true;
    }

    public int surface(int x, int z) {
        for (int y = Chunk.H - 1; y >= 0; y--) if (get(x, y, z) != 0) return y;
        return 0;
    }

    /** Hasil: {hitX, hitY, hitZ, prevX, prevY, prevZ} atau null. */
    public int[] raycast(Vector3 o, Vector3 d, float max) {
        int px = (int) Math.floor(o.x), py = (int) Math.floor(o.y), pz = (int) Math.floor(o.z);
        for (float t = 0; t < max; t += 0.02f) {
            int x = (int) Math.floor(o.x + d.x * t);
            int y = (int) Math.floor(o.y + d.y * t);
            int z = (int) Math.floor(o.z + d.z * t);
            if (get(x, y, z) != 0) return new int[]{x, y, z, px, py, pz};
            px = x; py = y; pz = z;
        }
        return null;
    }

    public void render(ShaderProgram shader) {
        for (Chunk c : chunks.values()) {
            if (c.dirty) c.build(this);
            c.render(shader);
        }
    }

    public void dispose() {
        for (Chunk c : chunks.values()) c.dispose();
    }
}
