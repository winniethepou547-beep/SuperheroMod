package com.FIRNI.superheromod;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.*;

/**
 * Renders film_backdrop offscreen for a few film moments and writes PNG frames, so the
 * procedural backgrounds can be reviewed without launching the game.
 * Usage: gradlew backdropPreview -Pout=<folder>
 */
public final class BackdropPreview {
    private static final int W = 640, H = 360;
    record Frame(String name, float scene, float time, float[] cam, float[] look, float fov,
                 float[] planet, float[] ring, float[] motion) {}

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/backdrop-preview";
        new File(out).mkdirs();
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(W, H, "preview", 0, 0);
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        Path dir = Paths.get("src/main/resources/assets/superheromod/shaders/core");
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, compile(GL20.GL_VERTEX_SHADER, Files.readString(dir.resolve("film_backdrop.vsh"))));
        GL20.glAttachShader(program, compile(GL20.GL_FRAGMENT_SHADER, Files.readString(dir.resolve("film_backdrop.fsh"))));
        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glLinkProgram(program);
        GL20.glUseProgram(program);
        int fbo = GL30.glGenFramebuffers(), tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, W, H, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        int vao = GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers(); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[]{-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 1, 0}, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL11.glViewport(0, 0, W, H);
        double a25 = Math.toRadians(25);
        Frame[] frames = {
                new Frame("1_descent", 0, 3, f(2.2, 3.4, 1.4), f(0, .9, 0), 60, f(0, 0, 0, 1), f(0, 0, 0, 0), f(.9f, .08f, 1, 0)),
                new Frame("2_hell_road", 4, 5, f(2.4, .8, 9), f(0, 1, -40), 55, f(0, 0, 0, 1), f(0, 0, 0, 0), f(0, 0, 0, 0)),
                new Frame("3_hell_high", 4, 7, f(5, 6, 6), f(0, 1, -10), 50, f(0, 0, 0, 1), f(0, 0, 0, 0), f(0, 0, 0, 0)),
                new Frame("4_stare", 4, 8, f(6.6 * Math.cos(a25), 1.1, 6.6 * Math.sin(a25)), f(0, 1, 0), 48, f(0, 0, 0, 1), f(0, 0, 0, 0), f(0, .15f, 0, 0)),
                new Frame("5_stare_xray", 4, 9, f(-3, 1.4, 3), f(0, 1, 0), 58, f(0, 0, 0, 1), f(0, 0, 0, 0), f(0, .6f, 0, .8f)),
                new Frame("6_plunge", 3, 11, f(0, 3.6, 2.6), f(0, .6, 0), 68, f(0, 0, 0, 1), f(0, 0, 0, 0), f(2, .5f, .9f, 0)),
                new Frame("7_arena_glow", 6, 4, f(1.8, 1.4, 4.5), f(0, 1.4, 0), 50, f(0, 1.62, .2, 1.2), f(0, 0, 0, 0), f(0, 0, 1, 0)),
                new Frame("8_arena_beam", 6, 9, f(4.2, 1.1, 1.6), f(0, 1.1, 1.6), 62, f(0, 1.62, .2, 1.5), f(1, .4f, 3.2f, 0), f(0, 0, 1, 0)),
                new Frame("9_arena_max", 6, 15, f(-2.3, 1.7, .1), f(0, 1.5, 3), 66, f(0, 1.62, .2, 3), f(3, 1.4f, 30, .1f), f(0, 0, 1.3f, 0)),
                new Frame("10_desert_calm", 7, 2, f(5, 2.5, -.4), f(0, 1.1, 1), 60, f(-.6, .18, .8, 1), f(0, 0, 0, 0), f(.2f, 0, .15f, 0)),
                new Frame("11_desert_storm", 7, 12, f(4, 2, 1.5), f(0, 1.8, 1), 68, f(-.6, .18, .8, 1), f(0, 0, 0, 0), f(.6f, 0, .7f, 0)),
                new Frame("12_desert_low", 7, 20, f(-3, .6, .7), f(0, 2.5, 1), 65, f(-.6, .18, .8, 1), f(0, 0, 0, 0), f(.4f, 0, .4f, 0))};
        for (Frame frame : frames) {
            float[] cam = frame.cam, look = frame.look;
            double fx = look[0] - cam[0], fy = look[1] - cam[1], fz = look[2] - cam[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
            fx /= fl; fy /= fl; fz /= fl;
            double rx = -fz, rz = fx, rl = Math.sqrt(rx * rx + rz * rz); rx /= rl; rz /= rl;
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            set3(program, "CamPos", cam[0], cam[1], cam[2]);
            set3(program, "CamF", fx, fy, fz);
            set3(program, "CamR", rx, 0, rz);
            set3(program, "CamU", ux, uy, uz);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "Time"), frame.time);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "Scene"), frame.scene);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "Lens"), (float) Math.tan(Math.toRadians(frame.fov / 2)), W / (float) H);
            set4(program, "Planet", frame.planet); set4(program, "Ring", frame.ring); set4(program, "Motion", frame.motion);
            set4(program, "Tint", f(0, 0, 0, 0));
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
            GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = ((H - 1 - y) * W + x) * 4;
                image.setRGB(x, y, (pixels.get(i) & 255) << 16 | (pixels.get(i + 1) & 255) << 8 | (pixels.get(i + 2) & 255));
            }
            ImageIO.write(image, "png", new File(out, frame.name + ".png"));
            System.out.println("wrote " + frame.name);
        }
        GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
    }
    private static float[] f(double... v) { float[] r = new float[v.length]; for (int i = 0; i < v.length; i++) r[i] = (float) v[i]; return r; }
    private static void set3(int p, String n, double x, double y, double z) { GL20.glUniform3f(GL20.glGetUniformLocation(p, n), (float) x, (float) y, (float) z); }
    private static void set4(int p, String n, float[] v) { GL20.glUniform4f(GL20.glGetUniformLocation(p, n), v[0], v[1], v[2], v[3]); }
    private static int compile(int type, String source) {
        int s = GL20.glCreateShader(type); GL20.glShaderSource(s, source); GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(s));
        return s;
    }
}
