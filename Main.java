package com.klonmc;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public class Main extends ApplicationAdapter {
    static final String VERT =
        "attribute vec3 a_position;\n" +
        "attribute vec4 a_color;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec4 v_color;\n" +
        "varying float v_fog;\n" +
        "void main(){ v_color = a_color; vec4 p = u_projTrans * vec4(a_position, 1.0);" +
        " v_fog = clamp((p.w - 18.0) / 16.0, 0.0, 1.0); gl_Position = p; }";
    static final String FRAG =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
        "varying vec4 v_color;\n" +
        "varying float v_fog;\n" +
        "uniform float u_light;\n" +
        "uniform vec3 u_sky;\n" +
        "void main(){ vec3 c = v_color.rgb * u_light; gl_FragColor = vec4(mix(c, u_sky, v_fog), 1.0); }";

    static final float[] DY = {0f, 0.9f, 1.7f};
    static final float DAY_LENGTH = 240f; // detik
    static final int SAVE_VERSION = 2;

    PerspectiveCamera cam;
    ShaderProgram shader;
    ShapeRenderer sr;
    World world;
    final Matrix4 hud = new Matrix4();

    final Vector3 pos = new Vector3();
    final Vector3 dir = new Vector3();
    float yaw, pitch, vy;
    boolean onGround, jump;
    float time = 60f;                 // mulai tengah hari

    // sentuhan
    int movePtr = -1, lookPtr = -1;
    float ox, oy, jx, jy;
    float lx, ly;

    // tata letak HUD
    float jumpX, jumpY, jumpR;

    @Override
    public void create() {
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) throw new RuntimeException(shader.getLog());
        sr = new ShapeRenderer();
        world = new World();

        cam = new PerspectiveCamera(70, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.near = 0.1f;
        cam.far = 40f;

        boolean loaded = load();
        if (!loaded) pos.set(8.5f, 0, 8.5f);
        world.update(pos.x, pos.z, true);
        if (!loaded) pos.y = world.surface((int) Math.floor(pos.x), (int) Math.floor(pos.z)) + 2;
        world.buildAll();
        layout();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean touchDown(int x, int y, int p, int b) {
                float ty = Gdx.graphics.getHeight() - y;
                if (Vector3.dst(x, ty, 0, jumpX, jumpY, 0) < jumpR) { jump = true; return true; }
                if (x < Gdx.graphics.getWidth() / 2 && movePtr < 0) {
                    movePtr = p; ox = x; oy = y; jx = jy = 0;
                } else if (lookPtr < 0) {
                    lookPtr = p; lx = x; ly = y;
                }
                return true;
            }
            @Override public boolean touchDragged(int x, int y, int p) {
                if (p == movePtr) {
                    jx = MathUtils.clamp((x - ox) / 120f, -1, 1);
                    jy = MathUtils.clamp((y - oy) / 120f, -1, 1);
                } else if (p == lookPtr) {
                    yaw -= (x - lx) * 0.2f;
                    pitch = MathUtils.clamp(pitch - (y - ly) * 0.2f, -89, 89);
                    lx = x; ly = y;
                }
                return true;
            }
            @Override public boolean touchUp(int x, int y, int p, int b) {
                if (p == movePtr) { movePtr = -1; jx = jy = 0; }
                else if (p == lookPtr) { lookPtr = -1; }
                return true;
            }
            @Override public boolean keyDown(int k) {
                if (k == com.badlogic.gdx.Input.Keys.SPACE) jump = true;
                return true;
            }
        });
    }

    void layout() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        hud.setToOrtho2D(0, 0, w, h);
        jumpR = h * 0.09f;
        jumpX = w - jumpR * 1.8f;
        jumpY = jumpR * 1.8f;
    }

    // ---------- Fisika ----------

    boolean blocked(float x, float y, float z) {
        for (float dy : DY)
            for (int i = 0; i < 4; i++) {
                float cx = x + ((i & 1) == 0 ? -0.3f : 0.3f);
                float cz = z + ((i & 2) == 0 ? -0.3f : 0.3f);
                if (world.get((int) Math.floor(cx), (int) Math.floor(y + dy), (int) Math.floor(cz)) != 0)
                    return true;
            }
        return false;
    }

    void update(float dt) {
        float f = -jy, s = jx;
        float fx = -MathUtils.sinDeg(yaw), fz = -MathUtils.cosDeg(yaw);
        float rx = MathUtils.cosDeg(yaw), rz = -MathUtils.sinDeg(yaw);
        float sp = 4.5f * dt;
        float dx = (fx * f + rx * s) * sp, dz = (fz * f + rz * s) * sp;
        if (!blocked(pos.x + dx, pos.y, pos.z)) pos.x += dx;
        if (!blocked(pos.x, pos.y, pos.z + dz)) pos.z += dz;

        vy -= 25f * dt;
        if (jump && onGround) vy = 8f;
        jump = false;
        float ny = pos.y + vy * dt;
        if (blocked(pos.x, ny, pos.z)) { onGround = vy < 0; vy = 0; }
        else { pos.y = ny; onGround = false; }
        if (pos.y < -20) {
            pos.y = world.surface((int) Math.floor(pos.x), (int) Math.floor(pos.z)) + 2;
            vy = 0;
        }

        dir.set(-MathUtils.sinDeg(yaw) * MathUtils.cosDeg(pitch), MathUtils.sinDeg(pitch),
                -MathUtils.cosDeg(yaw) * MathUtils.cosDeg(pitch));
        cam.position.set(pos.x, pos.y + 1.6f, pos.z);
        cam.direction.set(dir);
        cam.up.set(0, 1, 0);
        cam.update();
    }

    // ---------- Render ----------

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        time += dt;
        update(dt);
        world.update(pos.x, pos.z, false);

        // siang-malam
        float sun = MathUtils.sin(time / DAY_LENGTH * MathUtils.PI2);
        float k = MathUtils.clamp(sun * 1.5f + 0.5f, 0f, 1f);
        float light = 0.3f + 0.7f * k;
        float skyR = MathUtils.lerp(0.02f, 0.50f, k);
        float skyG = MathUtils.lerp(0.03f, 0.75f, k);
        float skyB = MathUtils.lerp(0.08f, 1.00f, k);

        Gdx.gl.glClearColor(skyR, skyG, skyB, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        shader.bind();
        shader.setUniformMatrix("u_projTrans", cam.combined);
        shader.setUniformf("u_light", light);
        shader.setUniformf("u_sky", skyR, skyG, skyB);
        world.render(shader);

        drawHud();
    }

    void drawHud() {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        float h = Gdx.graphics.getHeight();
        sr.setProjectionMatrix(hud);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(1, 1, 1, 0.25f);
        sr.circle(jumpX, jumpY, jumpR);           // tombol lompat
        if (movePtr >= 0) {                       // joystick
            sr.circle(ox, h - oy, 90);
            sr.setColor(1, 1, 1, 0.5f);
            sr.circle(ox + jx * 90, h - oy - jy * 90, 40);
        }
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // ---------- Simpan / muat ----------

    void save() {
        try {
            DataOutputStream o = new DataOutputStream(Gdx.files.local("world.sav").write(false));
            o.writeInt(SAVE_VERSION);
            o.writeFloat(pos.x); o.writeFloat(pos.y); o.writeFloat(pos.z);
            o.writeFloat(yaw); o.writeFloat(pitch); o.writeFloat(time);
            o.close();
        } catch (Exception e) {
            // abaikan
        }
    }

    boolean load() {
        FileHandle f = Gdx.files.local("world.sav");
        if (!f.exists()) return false;
        try {
            DataInputStream in = new DataInputStream(f.read());
            if (in.readInt() != SAVE_VERSION) { in.close(); return false; }
            float px = in.readFloat(), py = in.readFloat(), pz = in.readFloat();
            pos.set(px, py, pz);
            yaw = in.readFloat(); pitch = in.readFloat(); time = in.readFloat();
            in.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void pause() {
        save();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        layout();
    }

    @Override
    public void dispose() {
        save();
        world.dispose();
        shader.dispose();
        sr.dispose();
    }
}
