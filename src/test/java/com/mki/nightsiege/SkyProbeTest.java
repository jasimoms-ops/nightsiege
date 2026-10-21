package com.mki.nightsiege;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Temporary: sky, moon, fog and environment attribute API for the blood moon look.
 * Prints full signatures and a compact outline of the calls inside the methods a
 * mixin would hook, so the targets are not guessed.
 */
class SkyProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, SkyProbeTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static void sigs(String n, Predicate<String> filter) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        System.out.println("PROBE: CLASS " + c.getName() + " ext " + (c.getSuperclass() == null ? "-" : c.getSuperclass().getName())
                + " iface " + java.util.Arrays.toString(c.getInterfaces()) + (c.isInterface() ? " INTERFACE" : "") + (c.isRecord() ? " RECORD" : ""));
        try {
            for (var ct : c.getDeclaredConstructors()) {
                System.out.println("PROBE:   ctor " + ct.toGenericString());
            }
            for (Method m : c.getDeclaredMethods()) {
                if (filter.test(m.getName())) System.out.println("PROBE:   m " + m.toGenericString());
            }
            for (Field f : c.getDeclaredFields()) {
                String v = "";
                if (Modifier.isStatic(f.getModifiers()) && (f.getType().isPrimitive() || f.getType() == String.class)) {
                    try {
                        f.setAccessible(true);
                        v = " = " + f.get(null);
                    } catch (Throwable ignored) {
                        v = " = ?";
                    }
                }
                System.out.println("PROBE:   f " + f.toGenericString() + v);
            }
        } catch (Throwable t) {
            System.out.println("PROBE:   ERR " + t);
        }
    }

    /** One line per method: the calls, field accesses and constants inside it. */
    private static void outline(String n, Predicate<String> filter) {
        String path = n.replace('.', '/') + ".class";
        try (InputStream in = SkyProbeTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                System.out.println("PROBE: nobytes " + n);
                return;
            }
            ClassReader reader = new ClassReader(in);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
                    if (value != null) System.out.println("PROBE: const " + n.substring(n.lastIndexOf('.') + 1) + "." + name + " = " + value);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    if (!filter.test(name)) return null;
                    String head = n.substring(n.lastIndexOf('.') + 1) + "." + name + desc;
                    List<String> ops = new ArrayList<>();
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int op, String owner, String mname, String mdesc, boolean itf) {
                            ops.add(op(op) + " " + owner.substring(owner.lastIndexOf('/') + 1) + "." + mname + mdesc);
                        }

                        @Override
                        public void visitFieldInsn(int op, String owner, String fname, String fdesc) {
                            ops.add(op(op) + " " + owner.substring(owner.lastIndexOf('/') + 1) + "." + fname + ":" + fdesc);
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            ops.add("ldc " + value + (value instanceof Float ? "f" : ""));
                        }

                        @Override
                        public void visitInsn(int op) {
                            if (op >= 2 && op <= 8) ops.add("iconst " + (op - 3));
                            else if (op >= 11 && op <= 13) ops.add("fconst " + (op - 11));
                            else if (op >= 172 && op <= 177) ops.add("return");
                        }

                        @Override
                        public void visitIntInsn(int op, int operand) {
                            ops.add((op == 16 ? "bipush " : op == 17 ? "sipush " : "int" + op + " ") + operand);
                        }

                        @Override
                        public void visitTypeInsn(int op, String type) {
                            if (op == Opcodes.NEW) ops.add("new " + type.substring(type.lastIndexOf('/') + 1));
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String iname, String idesc, org.objectweb.asm.Handle bsm, Object... args) {
                            StringBuilder sb = new StringBuilder("indy " + iname);
                            for (Object a : args) if (a instanceof org.objectweb.asm.Handle h) sb.append(" ->").append(h.getName());
                            ops.add(sb.toString());
                        }

                        @Override
                        public void visitEnd() {
                            System.out.println("PROBE: OUT " + head + " :: " + String.join(" | ", ops));
                        }
                    };
                }
            }, 0);
        } catch (Throwable t) {
            System.out.println("PROBE: ERR outline " + n + " " + t);
        }
    }

    private static String op(int op) {
        return switch (op) {
            case 182 -> "iv";
            case 183 -> "isp";
            case 184 -> "ist";
            case 185 -> "iif";
            case 178 -> "gets";
            case 179 -> "puts";
            case 180 -> "getf";
            case 181 -> "putf";
            default -> "op" + op;
        };
    }

    private static Predicate<String> any() {
        return s -> true;
    }

    private static Predicate<String> has(String... parts) {
        return s -> {
            for (String p : parts) if (s.toLowerCase().contains(p.toLowerCase())) return true;
            return false;
        };
    }

    @Test
    void probe() {
        sigs("org.spongepowered.asm.mixin.MixinEnvironment$CompatibilityLevel", has("JAVA"));
        sigs("net.minecraft.client.renderer.texture.SpriteContents", has("name", "width", "height"));
        sigs("net.minecraft.client.Screenshot", any());
        sigs("net.minecraft.client.Minecraft", has("screenshot", "getMainRenderTarget", "isPaused", "getWindow", "createWorldOpenFlows", "gameRenderer", "player", "getConnection", "isSameThread"));
        sigs("net.minecraft.world.entity.Entity", has("setXRot", "setYRot", "snapTo"));
        sigs("net.minecraft.client.gui.screens.worldselection.WorldOpenFlows", any());
        sigs("net.minecraft.client.main.GameConfig$QuickPlayData", any());
        sigs("net.minecraft.client.quickplay.QuickPlay", has("connect", "join"));
    }
}
