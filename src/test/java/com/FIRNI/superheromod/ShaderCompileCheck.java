package com.FIRNI.superheromod;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import java.nio.file.*;

/**
 * Compiles and links the mod's core shaders on this machine's real OpenGL driver (hidden
 * window, 3.2 core like Minecraft), so a GLSL mistake is caught before the game loads it.
 */
public final class ShaderCompileCheck {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(64, 64, "shader check", 0, 0);
        if (window == 0) throw new IllegalStateException("No GL 3.2 context");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        Path dir = Paths.get("src/main/resources/assets/superheromod/shaders/core");
        boolean failed = false;
        for (String name : new String[]{"film_backdrop", "ghost_fire"}) {
            int vs = compile(GL20.GL_VERTEX_SHADER, Files.readString(dir.resolve(name + ".vsh")), name + ".vsh");
            int fs = compile(GL20.GL_FRAGMENT_SHADER, Files.readString(dir.resolve(name + ".fsh")), name + ".fsh");
            if (vs == 0 || fs == 0) { failed = true; continue; }
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs); GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                System.out.println("LINK FAILED " + name + ":\n" + GL20.glGetProgramInfoLog(program));
                failed = true;
            } else System.out.println("PASS: " + name + " compiles and links");
        }
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        if (failed) throw new AssertionError("Shader compile failed");
    }
    private static int compile(int type, String source, String name) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            System.out.println("COMPILE FAILED " + name + ":\n" + GL20.glGetShaderInfoLog(shader));
            return 0;
        }
        return shader;
    }
}
