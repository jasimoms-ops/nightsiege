package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * What stood where a dungeon is built (content update, Simon 29 October 2026: "die Dungeons
 * müssen dann doch despawnen, wenn sie fertig sind"). Before the dungeon changes a chunk, the
 * old block of every spot it will set is written down; when the dungeon is won, it goes back.
 *
 * <p>One file per dungeon in {@code <world>/nightsiege/expeditions/<id>.txt.gz}: per chunk a
 * palette of block states (as text, so it survives mods coming and going) and one index per
 * spot, in the order of the dungeon's plan for that chunk.</p>
 */
final class ExpeditionSnapshot {

    private final Path file;
    private final Map<Long, BlockState[]> chunks = new LinkedHashMap<>();
    /** Per chunk the place of every spot (null for a snapshot written before 29 October 2026). */
    private final Map<Long, long[]> places = new HashMap<>();

    private ExpeditionSnapshot(Path file) {
        this.file = file;
    }

    static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("nightsiege").resolve("expeditions").normalize();
    }

    /** The snapshot of a dungeon (empty when there is none yet). */
    static ExpeditionSnapshot load(MinecraftServer server, int id) {
        ExpeditionSnapshot snap = new ExpeditionSnapshot(folder(server).resolve(id + ".txt.gz"));
        if (Files.exists(snap.file)) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(snap.file)), StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String read;
                while ((read = in.readLine()) != null) {
                    lines.add(read);
                }
                int at = 0;
                while (at < lines.size()) {
                    String line = lines.get(at++);
                    if (!line.startsWith("C ")) {
                        continue;
                    }
                    String[] head = line.split(" ");
                    long key = Long.parseLong(head[1]);
                    int count = Integer.parseInt(head[2]);
                    int palette = Integer.parseInt(head[3]);
                    BlockState[] pal = new BlockState[palette];
                    for (int i = 0; i < palette; i++) {
                        pal[i] = parse(at < lines.size() ? lines.get(at++) : null);
                    }
                    String idx = at < lines.size() ? lines.get(at++) : null;
                    BlockState[] states = new BlockState[count];
                    String[] parts = idx == null || idx.isEmpty() ? new String[0] : idx.split(" ");
                    for (int i = 0; i < count && i < parts.length; i++) {
                        int p = Integer.parseInt(parts[i]);
                        states[i] = p >= 0 && p < pal.length ? pal[p] : null;
                    }
                    snap.chunks.put(key, states);
                    if (at < lines.size() && lines.get(at).startsWith("L ")) {
                        // Where each spot is (since 29 October 2026): the dungeon goes back right
                        // even when its plan has changed since it was built.
                        String[] where = lines.get(at++).substring(2).trim().split(" ");
                        if (where.length == count) {
                            long[] places = new long[count];
                            for (int i = 0; i < count; i++) {
                                places[i] = Long.parseLong(where[i]);
                            }
                            snap.places.put(key, places);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Dungeon-Schnappschuss {} nicht lesbar: {}", snap.file, e.toString());
            }
        }
        return snap;
    }

    boolean has(long chunk) {
        return chunks.containsKey(chunk);
    }

    int chunkCount() {
        return chunks.size();
    }

    /** Writes down what stands now at the spots of a chunk, then saves the file. */
    void record(long chunk, ServerLevel level, List<BlockPos> spots) {
        BlockState[] states = new BlockState[spots.size()];
        long[] where = new long[spots.size()];
        for (int i = 0; i < states.length; i++) {
            states[i] = level.getBlockState(spots.get(i));
            where[i] = spots.get(i).asLong();
        }
        chunks.put(chunk, states);
        places.put(chunk, where);
        save();
    }

    /** What stood at the i-th spot of a chunk (null when not known). */
    BlockState old(long chunk, int i) {
        BlockState[] states = chunks.get(chunk);
        return states == null || i < 0 || i >= states.length ? null : states[i];
    }

    /** Where the spots of a chunk are, in the order of {@link #old}; null when the snapshot does not know. */
    List<BlockPos> places(long chunk) {
        long[] where = places.get(chunk);
        BlockState[] states = chunks.get(chunk);
        if (where == null || states == null || where.length != states.length) {
            return null;
        }
        List<BlockPos> out = new ArrayList<>(where.length);
        for (long l : where) {
            out.add(BlockPos.of(l));
        }
        return out;
    }

    /** The chunks written down (the levelled ground's key among them). */
    List<Long> keys() {
        return new ArrayList<>(chunks.keySet());
    }

    void forget(long chunk) {
        places.remove(chunk);
        if (chunks.remove(chunk) != null) {
            save();
        }
    }

    void delete() {
        chunks.clear();
        places.clear();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Dungeon-Schnappschuss {} nicht gelöscht: {}", file, e.toString());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8))) {
                for (Map.Entry<Long, BlockState[]> e : chunks.entrySet()) {
                    Map<BlockState, Integer> index = new HashMap<>();
                    List<BlockState> pal = new ArrayList<>();
                    StringBuilder idx = new StringBuilder();
                    for (BlockState s : e.getValue()) {
                        BlockState state = s == null ? Blocks.AIR.defaultBlockState() : s;
                        Integer i = index.get(state);
                        if (i == null) {
                            i = pal.size();
                            index.put(state, i);
                            pal.add(state);
                        }
                        if (idx.length() > 0) {
                            idx.append(' ');
                        }
                        idx.append(i);
                    }
                    out.write("C " + e.getKey() + " " + e.getValue().length + " " + pal.size() + "\n");
                    for (BlockState s : pal) {
                        out.write(write(s));
                        out.write('\n');
                    }
                    out.write(idx.toString());
                    out.write('\n');
                    long[] where = places.get(e.getKey());
                    if (where != null && where.length == e.getValue().length) {
                        StringBuilder line = new StringBuilder("L");
                        for (long l : where) {
                            line.append(' ').append(l);
                        }
                        out.write(line.toString());
                        out.write('\n');
                    }
                }
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Dungeon-Schnappschuss {} nicht gespeichert: {}", file, e.toString());
        }
    }

    // ------------------------------------------------------------------ block states as text

    static String write(BlockState state) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        boolean first = true;
        for (Property<?> p : state.getProperties()) {
            sb.append(first ? '[' : ',').append(p.getName()).append('=').append(name(state, p));
            first = false;
        }
        if (!first) {
            sb.append(']');
        }
        return sb.toString();
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }

    static BlockState parse(String text) {
        if (text == null || text.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        int open = text.indexOf('[');
        String id = open < 0 ? text : text.substring(0, open);
        Identifier key = Identifier.tryParse(id);
        Block block = key == null ? null : BuiltInRegistries.BLOCK.getValue(key);
        if (block == null || key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
            return Blocks.STONE.defaultBlockState();   // a block of a mod that is gone: stone
        }
        BlockState state = block.defaultBlockState();
        if (open >= 0 && text.endsWith("]")) {
            for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                Property<?> p = block.getStateDefinition().getProperty(pair.substring(0, eq));
                if (p != null) {
                    state = with(state, p, pair.substring(eq + 1));
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> p, String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }
}
