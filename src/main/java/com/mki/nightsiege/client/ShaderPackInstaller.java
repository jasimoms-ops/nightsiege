package com.mki.nightsiege.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Puts the Night Siege shader pack (a changed copy of Photon by SixthSurge,
 * licence inside the zip) into the game's shaderpacks folder and keeps it
 * current: when the mod brings a different version, the old file is replaced.
 * The pack is not switched on; pick "NightSiege-Photon" in the shader menu.
 */
public final class ShaderPackInstaller {

    public static final String FILE = "NightSiege-Photon.zip";
    private static final String RESOURCE = "/assets/nightsiege/shaderpacks/" + FILE;

    private ShaderPackInstaller() {
    }

    public static void install() {
        try (InputStream in = ShaderPackInstaller.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            byte[] pack = in.readAllBytes();
            Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
            Path target = dir.resolve(FILE);
            if (Files.isRegularFile(target) && Files.size(target) == pack.length
                    && Arrays.equals(sha256(Files.readAllBytes(target)), sha256(pack))) {
                return;
            }
            Files.createDirectories(dir);
            Path temp = dir.resolve(FILE + ".tmp");
            Files.write(temp, pack);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Night Siege] shader pack installed: " + target);
        } catch (IOException | RuntimeException e) {
            System.out.println("[Night Siege] could not install the shader pack: " + e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            return data;
        }
    }
}
