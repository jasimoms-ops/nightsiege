package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Temporary: sky, moon, fog and cloud rendering API for the blood moon look. */
class SkyProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, SkyProbeTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sig(Method m) {
        StringBuilder sb = new StringBuilder();
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) sb.append("s:");
        if (Modifier.isPrivate(mod)) sb.append("p:");
        sb.append(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
        return sb.append(')').append(m.getReturnType().getSimpleName()).toString();
    }

    private static void emit(String head, List<String> parts) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String p : parts) {
            if (sb.length() + p.length() > 1500) {
                System.out.println("PROBE: " + head + (n++ == 0 ? "" : "+") + " " + sb);
                sb.setLength(0);
            }
            sb.append(p).append(' ');
        }
        System.out.println("PROBE: " + head + (n == 0 ? "" : "+") + " " + sb);
    }

    /** Declared methods (private too), optionally filtered by name parts. */
    private static void methods(String n, String... parts) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        List<String> out = new ArrayList<>();
        try {
            for (var ct : c.getDeclaredConstructors()) {
                if (parts.length > 0) break;
                StringBuilder sb = new StringBuilder("<init>(");
                for (Class<?> p : ct.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
                out.add(sb.append(')').toString());
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.isSynthetic()) continue;
                boolean hit = parts.length == 0;
                for (String x : parts) hit |= m.getName().toLowerCase().contains(x.toLowerCase());
                if (hit) out.add(sig(m));
            }
        } catch (Throwable t) {
            out.add("ERR " + t);
        }
        String sup = c.getSuperclass() == null ? "" : c.getSuperclass().getSimpleName();
        emit("M " + c.getName().substring(c.getName().lastIndexOf('.') + 1) + " ext " + sup, out);
    }

    private static void fields(String n, String... parts) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        List<String> out = new ArrayList<>();
        try {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic()) continue;
                boolean hit = parts.length == 0;
                for (String x : parts) hit |= f.getName().toLowerCase().contains(x.toLowerCase());
                if (!hit) continue;
                String v = "";
                if (Modifier.isStatic(f.getModifiers()) && (f.getType() == String.class || f.getType().getSimpleName().equals("Identifier"))) {
                    try {
                        f.setAccessible(true);
                        v = "=" + f.get(null);
                    } catch (Throwable ignored) {
                        // static init needs the game
                    }
                }
                out.add((Modifier.isStatic(f.getModifiers()) ? "s:" : "") + f.getName() + ":" + f.getType().getSimpleName() + v);
            }
        } catch (Throwable t) {
            out.add("ERR " + t);
        }
        emit("F " + c.getSimpleName(), out);
    }

    private static JarFile gameJar() throws Exception {
        URL url = SkyProbeTest.class.getClassLoader().getResource("net/minecraft/client/Minecraft.class");
        System.out.println("PROBE: jar " + url);
        if (url == null || !"jar".equals(url.getProtocol())) return null;
        return ((JarURLConnection) url.openConnection()).getJarFile();
    }

    private static void text(JarFile jar, String path) throws Exception {
        JarEntry e = jar.getJarEntry(path);
        if (e == null) {
            System.out.println("PROBE: nofile " + path);
            return;
        }
        try (InputStream in = jar.getInputStream(e)) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
            System.out.println("PROBE: file " + path + " " + (s.length() > 1500 ? s.substring(0, 1500) : s));
        }
    }

    @Test
    void probe() throws Exception {
        JarFile jar = gameJar();
        if (jar != null) {
            Pattern cls = Pattern.compile("net/minecraft/(client/renderer/[^$]*(Sky|Fog|Moon|Celestial|Star|Cloud|Weather|Lightmap|LightTexture|DimensionSpecial|Environment)[^$]*"
                    + "|client/renderer/(fog|sky|state)/[^$]*|world/attribute/[^$]*|world/timeline/[^$]*|world/level/MoonPhase|client/renderer/texture/atlas/[^$]*Atlas[^$]*)\\.class");
            Pattern res = Pattern.compile("assets/minecraft/(atlases/.*|textures/environment/.*|shaders/core/(sky|stars|position_tex|celestial)[^/]*)");
            List<String> classes = new ArrayList<>();
            List<String> assets = new ArrayList<>();
            for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements(); ) {
                String name = en.nextElement().getName();
                if (cls.matcher(name).matches()) classes.add(name.substring("net/minecraft/".length(), name.length() - 6));
                if (res.matcher(name).matches()) assets.add(name.substring("assets/minecraft/".length()));
            }
            emit("CLASSES", classes);
            emit("ASSETS", assets);
            text(jar, "assets/minecraft/atlases/celestials.json");
            text(jar, "assets/minecraft/atlases/celestial.json");
            text(jar, "assets/minecraft/atlases/environment.json");
        }

        methods("net.minecraft.client.renderer.SkyRenderer");
        fields("net.minecraft.client.renderer.SkyRenderer");
        methods("net.minecraft.client.renderer.LevelRenderer", "sky", "fog", "cloud", "weather", "addMainPass", "renderLevel", "star", "moon", "sun");
        methods("net.minecraft.client.renderer.fog.FogRenderer");
        fields("net.minecraft.client.renderer.fog.FogRenderer");
        methods("net.minecraft.client.renderer.CloudRenderer", "render", "color", "build");
        methods("net.minecraft.client.renderer.LightTexture");
        methods("net.minecraft.client.renderer.Lightmap");
        methods("net.minecraft.client.multiplayer.ClientLevel", "sky", "cloud", "star", "moon", "sun", "fog", "time", "dayTime", "environment", "attribute");
        methods("net.minecraft.client.Camera", "attribute", "environment", "fog", "probe");
        methods("net.minecraft.client.renderer.DimensionSpecialEffects");
        methods("net.minecraft.world.attribute.EnvironmentAttributeProbe");
        methods("net.minecraft.world.attribute.EnvironmentAttributeSystem");
        fields("net.minecraft.world.attribute.EnvironmentAttributes");
        methods("net.minecraft.world.attribute.EnvironmentAttribute");
        fields("net.minecraft.world.level.MoonPhase");
        methods("net.minecraft.world.level.MoonPhase");
        methods("net.minecraft.client.renderer.state.SkyRenderState");
        fields("net.minecraft.client.renderer.state.SkyRenderState");
        methods("net.minecraft.client.renderer.GameRenderer", "render", "fog", "lightmap", "lightTexture");
        methods("net.minecraft.client.renderer.texture.TextureAtlas", "getSprite", "location");
        methods("net.minecraft.client.resources.model.AtlasManager");
        methods("net.minecraft.client.renderer.texture.atlas.SpriteSource");
        fields("net.minecraft.data.AtlasIds");
        fields("net.minecraft.client.resources.model.AtlasIds");
        fields("net.minecraft.client.renderer.texture.TextureAtlas", "LOCATION");
        methods("net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry");
        methods("net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents");
        fields("net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents");
        fields("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents");
    }
}
