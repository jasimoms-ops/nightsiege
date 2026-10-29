package com.mki.nightsiege.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mki.nightsiege.NightSiegeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * The ground the team bots defend (balance tool, 29 October 2026).
 *
 * <ul>
 *   <li><b>The copy of Simon's castle</b> (his choice, 01:17): the blocks around the server's core,
 *       taken from the world files (chunks, sections 48–127), kept as a small gzip JSON outside
 *       the mod (tools/sim). Blocks only: no chests' contents, no signs' text, no players.</li>
 *   <li><b>A standard castle</b> while the copy is not there: a stone wall ring with two doors and
 *       an archway, a few houses outside, like his base.</li>
 * </ul>
 */
public final class SimCastle {

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private SimCastle() {
    }

    /** What was built: where the core stands and what kind of ground it is. */
    public record Ground(BlockPos core, String kind, int blocks) {
    }

    /**
     * Builds the ground near {@code near}: the copy from {@code copyFile} when it can be read, the
     * standard castle otherwise. The core block itself is not set here.
     */
    public static Ground build(ServerLevel level, BlockPos near, Path copyFile) {
        if (copyFile != null && Files.isRegularFile(copyFile)) {
            try (InputStream in = new GZIPInputStream(Files.newInputStream(copyFile))) {
                return placeCopy(level, near, in);
            } catch (Exception e) {
                NightSiegeMod.LOGGER.warn("[Night Siege sim] Burg-Kopie {} nicht lesbar ({}), nehme die Standard-Burg",
                        copyFile, e.toString());
            }
        }
        return standard(level, near);
    }

    // ------------------------------------------------------------------ the copy

    /**
     * Places the copied chunks so that the copied core lands on {@code near}'s x/z (its height
     * stays the copy's, the ground around it is the copy's too).
     */
    static Ground placeCopy(ServerLevel level, BlockPos near, InputStream gzipJson) throws Exception {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(gzipJson, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray c = root.getAsJsonArray("core");
        int ox = c.get(0).getAsInt();
        int oy = c.get(1).getAsInt();
        int oz = c.get(2).getAsInt();
        int dx = near.getX() - ox;
        int dz = near.getZ() - oz;
        Map<String, BlockState> cache = new HashMap<>();
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (JsonElement ce : root.getAsJsonArray("chunks")) {
            JsonObject chunk = ce.getAsJsonObject();
            int cx = chunk.get("x").getAsInt();
            int cz = chunk.get("z").getAsInt();
            for (JsonElement se : chunk.getAsJsonArray("s")) {
                JsonObject sec = se.getAsJsonObject();
                int sy = sec.get("y").getAsInt();
                JsonArray pal = sec.getAsJsonArray("p");
                BlockState[] palette = new BlockState[pal.size()];
                for (int i = 0; i < palette.length; i++) {
                    palette[i] = cache.computeIfAbsent(pal.get(i).getAsString(), SimCastle::parseState);
                }
                String data = sec.has("d") ? sec.get("d").getAsString() : "";
                int[] index = unpack(data, palette.length);
                for (int i = 0; i < 4096; i++) {
                    BlockState state = palette[index == null ? 0 : Math.min(index[i], palette.length - 1)];
                    int x = cx * 16 + (i & 15) + dx;
                    int z = cz * 16 + ((i >> 4) & 15) + dz;
                    int y = sy * 16 + (i >> 8);
                    pos.set(x, y, z);
                    if (level.getBlockState(pos) != state) {
                        level.setBlock(pos, state, FLAGS);
                        placed++;
                    }
                }
            }
        }
        BlockPos core = new BlockPos(near.getX(), oy, near.getZ());
        NightSiegeMod.LOGGER.info("[Night Siege sim] Burg-Kopie gesetzt: {} Blöcke, Core bei {}", placed, core.toShortString());
        return new Ground(core, "kopie", placed);
    }

    /** The indices of one section's 4096 blocks (null: all are the palette's first). */
    private static int[] unpack(String base64, int paletteSize) {
        if (base64 == null || base64.isEmpty() || paletteSize <= 1) {
            return null;
        }
        byte[] raw = Base64.getDecoder().decode(base64);
        int longs = raw.length / 8;
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1;
        int[] out = new int[4096];
        for (int i = 0; i < 4096; i++) {
            int li = i / perLong;
            if (li >= longs) {
                break;
            }
            long word = 0;
            for (int b = 0; b < 8; b++) {
                word = (word << 8) | (raw[li * 8 + b] & 0xFFL);
            }
            out[i] = (int) ((word >>> ((i % perLong) * bits)) & mask);
        }
        return out;
    }

    /** "minecraft:oak_door[facing=east,half=lower]" as a block state (unknown parts: their default). */
    static BlockState parseState(String text) {
        String name = text;
        String props = "";
        int open = text.indexOf('[');
        if (open >= 0 && text.endsWith("]")) {
            name = text.substring(0, open);
            props = text.substring(open + 1, text.length() - 1);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(name));
        if (block == null) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (!props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=", 2);
                if (p.length != 2) {
                    continue;
                }
                Property<?> property = block.getStateDefinition().getProperty(p[0]);
                if (property != null) {
                    state = with(state, property, p[1]);
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> v = property.getValue(value);
        return v.isPresent() ? state.setValue(property, v.get()) : state;
    }

    // ------------------------------------------------------------------ the standard castle

    /**
     * A castle like Simon's: a stone ring wall (27 × 27, two thick, six high, merlons on top) with
     * a door in the east and the west and an open archway in the south, the core in the middle,
     * three houses outside, on flat grass of radius 72.
     */
    static Ground standard(ServerLevel level, BlockPos near) {
        int y = Math.max(level.getMinY() + 8, Math.min(near.getY(), level.getMaxY() - 40));
        BlockPos core = new BlockPos(near.getX(), y, near.getZ());
        int placed = 0;
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = 72;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int dy = -6; dy <= 24; dy++) {
                    BlockState want = dy < -3 ? stone : dy < -1 ? dirt : dy == -1 ? grass : air;
                    pos.set(core.getX() + x, y + dy, core.getZ() + z);
                    if (level.getBlockState(pos) != want) {
                        level.setBlock(pos, want, FLAGS);
                        placed++;
                    }
                }
            }
        }
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                if (ring < 13) {
                    continue;
                }
                for (int dy = 0; dy < 6; dy++) {
                    set(level, core.offset(x, dy, z), (x * 7 + z * 3 + dy) % 5 == 0 ? mossy : wall);
                    placed++;
                }
                if (ring == 14 && ((x + z) & 1) == 0) {
                    set(level, core.offset(x, 6, z), wall);   // merlons on the outer row
                }
            }
        }
        // Doors in the east and the west: a door in the outer row, the inner row open.
        for (int side : new int[]{1, -1}) {
            int outer = 14 * side;
            int inner = 13 * side;
            String facing = side > 0 ? "east" : "west";
            set(level, core.offset(inner, 0, 0), air);
            set(level, core.offset(inner, 1, 0), air);
            set(level, core.offset(outer, 0, 0), parseState("minecraft:oak_door[facing=" + facing + ",half=lower,hinge=left,open=false]"));
            set(level, core.offset(outer, 1, 0), parseState("minecraft:oak_door[facing=" + facing + ",half=upper,hinge=left,open=false]"));
        }
        // The archway in the south: three wide, three high.
        for (int x = -1; x <= 1; x++) {
            for (int dy = 0; dy < 3; dy++) {
                set(level, core.offset(x, dy, 13), air);
                set(level, core.offset(x, dy, 14), air);
            }
        }
        // Three houses outside the wall (5 × 5, cobblestone, a plank roof, a door to the castle).
        int[][] houses = {{24, 9}, {-23, -15}, {7, -27}};
        for (int[] h : houses) {
            BlockPos base = core.offset(h[0], 0, h[1]);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                    for (int dy = 0; dy < 4; dy++) {
                        if (dy == 3) {
                            set(level, base.offset(x, dy, z), Blocks.OAK_PLANKS.defaultBlockState());
                        } else if (edge) {
                            set(level, base.offset(x, dy, z), Blocks.COBBLESTONE.defaultBlockState());
                        }
                    }
                }
            }
            set(level, base.offset(0, 0, 2), air);
            set(level, base.offset(0, 1, 2), air);
        }
        NightSiegeMod.LOGGER.info("[Night Siege sim] Standard-Burg gesetzt bei {}", core.toShortString());
        return new Ground(core, "standard", placed);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
    }

    /** For the report: every distinct block of the copy (to check the file without the game). */
    static List<String> paletteOf(InputStream gzipJson) {
        List<String> out = new ArrayList<>();
        JsonObject root = JsonParser.parseReader(new InputStreamReader(gzipJson, StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement ce : root.getAsJsonArray("chunks")) {
            for (JsonElement se : ce.getAsJsonObject().getAsJsonArray("s")) {
                for (JsonElement p : se.getAsJsonObject().getAsJsonArray("p")) {
                    if (!out.contains(p.getAsString())) {
                        out.add(p.getAsString());
                    }
                }
            }
        }
        return out;
    }
}
