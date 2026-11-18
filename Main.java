package com.klonmc;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.TimeUtils;

public class Main extends ApplicationAdapter {
    static final String VERT =
        "attribute vec3 a_position;\n" +
        "attribute vec4 a_color;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec4 v_color;\n" +
        "void main(){ v_color = a_color; gl_Position = u_projTrans * vec4(a_position, 1.0); }";
    static final String FRAG =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
        "varying vec4 v_color;\n" +
        "void main(){ gl_FragColor = v_color; }";

    static final float[] DY = {0f, 0.9f, 1.7f};

    PerspectiveCamera cam;
    ShaderProgram shader;
    ShapeRenderer sr;
    World world;

    final Vector3 pos = new Vector3();
    final Vector3 dir = new Vector3();
    float yaw, pitch, vy;
    boolean onGround, jump;

    // sentuhan
    int movePtr = -1, lookPtr = -1;
    float ox, oy, jx, jy;            // joystick
    float lx, ly, sx, sy;            // look
    long lookT;
    boolean lookMoved;
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
        cam.far = 50f;
        spawn();
        layout();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean touchDown(int x, int y, int p, int b) {
                float ty = Gdx.graphics.getHeight() - y;
                if (Vector3.dst(x, ty, 0, jumpX, jumpY, 0) < jumpR) { jump = true; return true; }
                if (x < Gdx.graphics.getWidth() / 2 && movePtr < 0) {
                    movePtr = p; ox = x; oy = y; jx = jy = 0;
                } else if (lookPtr < 0) {
                    lookPtr = p; lx = sx = x; ly = sy = y;
                    lookT = TimeUtils.millis(); lookMoved = false;
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
                    if (Math.abs(x - sx) + Math.abs(y - sy) > 20) lookMoved = true;
                }
                return true;
            }
            @Override public boolean touchUp(int x, int y, int p, int b) {
                if (p == movePtr) { movePtr = -1; jx = jy = 0; }
                else if (p == lookPtr) {
                    lookPtr = -1;
                    if (!lookMoved) action(TimeUtils.timeSinceMillis(lookT) > 350); // tahan = pasang
                }
                return true;
            }
            @Override public boolean keyDown(int k) {
                if (k == Input.Keys.SPACE) jump = true;
                return true;
            }
        });
    }

    void spawn() {
        pos.set(8.5f, world.surface(8, 8) + 2, 8.5f);
        vy = 0;
    }

    void layout() {
        jumpR = Gdx.graphics.getHeight() * 0.1f;
        jumpX = Gdx.graphics.getWidth() - jumpR * 1.8f;
        jumpY = jumpR * 1.8f;
    }

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

    void action(boolean place) {
        int[] r = world.raycast(cam.position, cam.direction, 6f);
        if (r == null) return;
        if (!place) {
            world.set(r[0], r[1], r[2], (byte) 0);
        } else {
            boolean inPlayer = r[3] == (int) Math.floor(pos.x) && r[5] == (int) Math.floor(pos.z)
                && (r[4] == (int) Math.floor(pos.y) || r[4] == (int) Math.floor(pos.y + 1));
            if (!inPlayer) world.set(r[3], r[4], r[5], (byte) 2); // pasang tanah
        }
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
        if (pos.y < -20) spawn();

        float pr = pitch, yr = yaw;
        dir.set(-MathUtils.sinDeg(yr) * MathUtils.cosDeg(pr), MathUtils.sinDeg(pr),
                -MathUtils.cosDeg(yr) * MathUtils.cosDeg(pr));
        cam.position.set(pos.x, pos.y + 1.6f, pos.z);
        cam.direction.set(dir);
        cam.up.set(0, 1, 0);
        cam.update();
    }

    @Override
    public void render() {
        update(Math.min(Gdx.graphics.getDeltaTime(), 0.05f));

        Gdx.gl.glClearColor(0.5f, 0.75f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        shader.bind();
        shader.setUniformMatrix("u_projTrans", cam.combined);
        world.render(shader);

        // HUD
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        sr.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(1, 1, 1, 0.9f);
        sr.rect(w / 2 - 12, h / 2 - 1, 24, 2);   // crosshair
        sr.rect(w / 2 - 1, h / 2 - 12, 2, 24);
        sr.setColor(1, 1, 1, 0.25f);
        sr.circle(jumpX, jumpY, jumpR);           // tombol lompat
        if (movePtr >= 0) {                       // joystick
            sr.circle(ox, h - oy, 90);
            sr.setColor(1, 1, 1, 0.5f);
            sr.circle(ox + jx * 90, h - oy + (-jy) * 90, 40);
        }
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
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
        world.dispose();
        shader.dispose();
        sr.dispose();
    }
}
