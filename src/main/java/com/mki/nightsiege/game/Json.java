package com.mki.nightsiege.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mki.nightsiege.NightSiegeMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Small Gson helper for the JSON files the mod keeps (the config and the save of a world).
 *
 * <p>Survival safety (Simon 31.10.): the save holds every skill, point, core and, during a night, the
 * players' own inventories (the kit stashes), so a file cut short must never cost anything. A write
 * goes to {@code <name>.tmp} in the same folder, is flushed to the disk, the file before it is kept
 * as {@code <name>.bak}, and only then the new file takes the old one's place in one rename. The
 * writing itself runs on a thread of its own (the text is built on the caller's thread), so the
 * disk never holds up a server tick; {@link #flush()} waits for it (reads, server stop, JVM exit).
 * A file that cannot be read is never taken for a fresh start: it is copied to
 * {@code <name>.kaputt-<yyyyMMdd-HHmmss>}, the {@code .bak} is tried, and the operators are told.
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /** A file that could not be read: whether the .bak stood in, and where the broken one was kept. */
    public record Notice(String file, boolean recovered, String kept) {
    }

    /** What went wrong with the files since this server started, for the operators (told on join). */
    private static final Map<String, Notice> NOTICES = new LinkedHashMap<>();
    /** Files whose content on disk is known to be good (read fine, or written here): only those become the .bak. */
    private static final Set<Path> GOOD = ConcurrentHashMap.newKeySet();
    /** Broken files already kept ("size|mtime|copy name" by path): read again and again, one copy. */
    private static final Map<Path, String> KEPT = new ConcurrentHashMap<>();
    /** The newest text of each file still to be written; the writer takes it (a burst of writes is one). */
    private static final Map<Path, String> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Night Siege Speichern");
        t.setDaemon(true);
        return t;
    });

    static {
        // A server that goes down without its stop events still gets its last writes out.
        Runtime.getRuntime().addShutdownHook(new Thread(Json::flush, "Night Siege Speichern (Ende)"));
    }

    private Json() {
    }

    public static Path configFile(String name) {
        return FabricLoader.getInstance().getConfigDir().resolve(name);
    }

    /** Where the file before the last write is kept. */
    public static Path backupOf(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    private static Path key(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * Reads a file. Null when it is missing (the first start) – and also when it is broken and no
     * .bak can stand in, but then the broken file is kept as {@code .kaputt-…} first and the
     * operators are told, so a fresh start never wipes anything for good.
     */
    public static <T> T read(Path path, Class<T> type) {
        flush();   // a write still on its way first
        Path file = key(path);
        if (!Files.exists(file)) {
            Path bak = backupOf(file);
            if (Files.isRegularFile(bak)) {
                // Gone, but a .bak is there (deleted by hand?): a fresh start as asked, and the .bak is
                // set aside so the next writes cannot replace it.
                Kept kept = keepBroken(bak, ".alt-");
                if (kept.fresh()) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] {} fehlt – frischer Stand. Die alte Sicherung bleibt als {} erhalten.",
                            file.getFileName(), kept.name());
                }
            }
            return null;
        }
        try {
            T value = parse(file, type);
            GOOD.add(file);
            return value;
        } catch (IOException | RuntimeException broken) {
            GOOD.remove(file);   // the broken file must not become the .bak at the next write
            Kept kept = keepBroken(file, ".kaputt-");
            String name = file.getFileName().toString();
            if (kept.fresh()) {
                NightSiegeMod.LOGGER.error("[Night Siege] !!! {} ist BESCHÄDIGT und wird nicht benutzt ({}). "
                        + "Die kaputte Datei bleibt als {} erhalten.", name, broken.toString(), kept.name());
            }
            Path bak = backupOf(file);
            if (Files.isRegularFile(bak)) {
                try {
                    T value = parse(bak, type);
                    if (kept.fresh()) {
                        NightSiegeMod.LOGGER.warn("[Night Siege] {}: die Sicherung {} wird geladen (Stand vor dem letzten Speichern).",
                                name, bak.getFileName());
                    }
                    notice(new Notice(name, true, kept.name()));
                    return value;
                } catch (IOException | RuntimeException alsoBroken) {
                    Kept keptBak = keepBroken(bak, ".kaputt-");
                    if (keptBak.fresh()) {
                        NightSiegeMod.LOGGER.error("[Night Siege] !!! Auch die Sicherung {} ist beschädigt ({}), erhalten als {}.",
                                bak.getFileName(), alsoBroken.toString(), keptBak.name());
                    }
                }
            }
            if (kept.fresh()) {
                NightSiegeMod.LOGGER.error("[Night Siege] !!! {}: keine lesbare Sicherung – es geht mit einem frischen Stand weiter. "
                        + "Nichts ist gelöscht: die kaputte Datei liegt als {} daneben.", name, kept.name());
            }
            notice(new Notice(name, false, kept.name()));
            return null;
        }
    }

    private static <T> T parse(Path file, Class<T> type) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                T value = GSON.fromJson(reader, type);
                if (value == null) {
                    throw new IOException("leer");   // an empty file is a write cut short, not a first start
                }
                return value;
            } catch (FileSystemException busy) {
                // Windows: held for a moment by a virus scanner or a backup tool, not broken
                if (attempt >= 3 || busy instanceof java.nio.file.NoSuchFileException) {
                    throw busy;
                }
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    throw busy;
                }
            }
        }
    }

    private record Kept(String name, boolean fresh) {
    }

    /** Keeps a broken file beside it as .kaputt-<time> (once per state of the file). */
    private static Kept keepBroken(Path file, String suffix) {
        String state;
        try {
            state = Files.size(file) + "|" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            state = "?";
        }
        String before = KEPT.get(file);
        if (before != null && before.startsWith(state + "|")) {
            return new Kept(before.substring(state.length() + 1), false);
        }
        String stamp = LocalDateTime.now().format(STAMP);
        Path copy = file.resolveSibling(file.getFileName() + suffix + stamp);
        for (int n = 2; Files.exists(copy); n++) {
            copy = file.resolveSibling(file.getFileName() + suffix + stamp + "-" + n);
        }
        String name = copy.getFileName().toString();
        try {
            Files.copy(file, copy);
        } catch (IOException copyFailed) {
            try {
                Files.move(file, copy);   // a full disk: a rename needs no room
            } catch (IOException moveFailed) {
                NightSiegeMod.LOGGER.error("[Night Siege] !!! {} konnte nicht beiseitegelegt werden: {}",
                        file.getFileName(), moveFailed.toString());
                name = "?";
            }
        }
        KEPT.put(file, state + "|" + name);
        return new Kept(name, true);
    }

    private static void notice(Notice notice) {
        synchronized (NOTICES) {
            NOTICES.put(notice.file() + "|" + notice.recovered(), notice);
        }
    }

    /** What had to be recovered or started fresh since this server started (for the operators). */
    public static List<Notice> notices() {
        synchronized (NOTICES) {
            return new ArrayList<>(NOTICES.values());
        }
    }

    /** A server starts: the notices of the one before (single player: another world) are done. */
    public static void clearNotices() {
        synchronized (NOTICES) {
            NOTICES.clear();
        }
    }

    /** Serialises an object for the network, never for a file. */
    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String text, Class<T> type) {
        return GSON.fromJson(text, type);
    }

    /**
     * Writes a file safely (see the class comment). The text is made now, on this thread; the disk
     * work follows on the writer thread, and {@link #flush()} waits for it.
     */
    public static void write(Path path, Object value) {
        String text;
        try {
            text = GSON.toJson(value);
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.error("[Night Siege] {} konnte nicht geschrieben werden (die alte Datei bleibt): {}",
                    path.getFileName(), e.toString());
            return;
        }
        Path file = key(path);
        if (PENDING.put(file, text) == null) {
            WRITER.execute(() -> {
                String newest = PENDING.remove(file);
                if (newest != null) {
                    writeNow(file, newest, true);
                }
            });
        }
    }

    /** Waits until every write handed over so far is on the disk (at most 30 seconds). */
    public static void flush() {
        try {
            WRITER.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Speichern hängt: {}", e.toString());
        }
    }

    /**
     * Writes the text now, on this thread: temp file, flushed to the disk, then in place of the old
     * one (kept as .bak when asked). False when it failed; the old file then stays as it was.
     */
    public static boolean writeNow(Path path, String text, boolean backup) {
        Path file = key(path);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
            commit(tmp, file, backup);
            return true;
        } catch (IOException | RuntimeException e) {
            NightSiegeMod.LOGGER.error("[Night Siege] {} konnte nicht geschrieben werden (die alte Datei bleibt): {}",
                    file.getFileName(), e.toString());
            return false;
        }
    }

    /**
     * Puts a finished temp file in the target's place: flushed to the disk, the target before it kept
     * as .bak (only when it is known to be good), then one rename (StatsRecorder, ExpeditionSnapshot).
     */
    public static void commit(Path tmp, Path target, boolean backup) throws IOException {
        Path file = key(target);
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        if (backup && GOOD.contains(file) && Files.isRegularFile(file)) {
            try {
                Files.copy(file, backupOf(file), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Sicherung {} nicht geschrieben: {}", backupOf(file).getFileName(), e.toString());
            }
        }
        replace(tmp, file);
        GOOD.add(file);
        syncFolder(file.getParent());
    }

    /**
     * The rename. Windows: a virus scanner, the indexer or an open editor can hold the target for a
     * moment (AccessDenied), so it is tried a few times; a file system that cannot rename atomically
     * gets a plain replace, and as the very last way the temp file is copied over.
     */
    private static void replace(Path tmp, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (FileSystemException e) {
                last = e;
                try {
                    Thread.sleep(40L << attempt);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (IOException e) {
            last = e;
        }
        try {
            Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            if (last != null) {
                e.addSuppressed(last);
            }
            throw e;
        }
    }

    /** The rename itself on the disk too (Linux, macOS); Windows cannot open a folder for this. */
    private static void syncFolder(Path folder) {
        if (WINDOWS || folder == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(folder, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | RuntimeException ignored) {
            // not every file system allows it; the rename is done either way
        }
    }
}
