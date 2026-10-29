package com.mki.nightsiege.client;

import com.mki.nightsiege.game.EventAtmosphere;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Event looks of the monsters. While an event is shown, the zombie and skeleton families,
 * creepers and spiders wear its textures (and their overlays and the spider's eyes take its
 * colours), zombies and skeletons also its clothes: blood-cursed at the blood moon, shadow
 * walkers in the eclipse, fog ghosts, storm-charged, charred at a rift, ice-bound in the
 * frost night. The eyes and the event's details glow (the "_glow" files, drawn by
 * {@link EventGear.Glow} so they shine with or without Entity Texture Features). The 3D gear
 * on top comes from {@link EventGear}. Which textures each event changes is written by the
 * generator into {@link EventGearShapes#TEXTURES}.
 */
public final class EventTextures {

    private static final String[] EVENTS = {
            EventAtmosphere.ECLIPSE, EventAtmosphere.FROST, EventAtmosphere.NETHER_RIFT,
            EventAtmosphere.THUNDER, EventAtmosphere.FOG, EventAtmosphere.BLOOD_MOON,
    };
    private static final Map<String, Map<Identifier, Identifier>> SWAPS = new HashMap<>();
    /** The boss folders (boss_grave_king …): their swaps, by folder. */
    private static final Map<String, Map<Identifier, Identifier>> BOSS_SWAPS = new HashMap<>();
    /** Boss folders whose zombie or skeleton also wears clothes. */
    private static final java.util.Set<String> BOSS_CLOTHED = java.util.Set.of("boss_grave_king", "boss_bone_lich",
            "champion_stone_crypt", "champion_desert_crypt", "champion_deep_cellar", "champion_basalt_forge");
    /** The folder of the boss being drawn right now, or null (set around each monster's submit). */
    private static String bossFolder;
    /** Our event texture → its glow map, where there is one. */
    private static final Map<Identifier, Identifier> GLOWS = new HashMap<>();

    static {
        for (String event : EVENTS) {
            Map<Identifier, Identifier> map = new HashMap<>();
            String folder = event.toLowerCase(java.util.Locale.ROOT);
            for (String entry : EventGearShapes.TEXTURES.getOrDefault(folder, java.util.List.of())) {
                int eq = entry.indexOf('=');
                String name = entry.substring(eq + 1);
                Identifier ours = texture(folder, name);
                map.put(Identifier.withDefaultNamespace("textures/entity/" + entry.substring(0, eq)), ours);
                if (EventGearShapes.GLOWS.contains(folder + "/" + name)) {
                    GLOWS.put(ours, texture(folder, name + "_glow"));
                }
            }
            SWAPS.put(event, map);
        }
        for (Map.Entry<String, java.util.List<String>> e : EventGearShapes.TEXTURES.entrySet()) {
            String folder = e.getKey();
            if (!folder.startsWith("boss_") && !folder.startsWith("foe_") && !folder.startsWith("champion_")) {
                continue;
            }
            Map<Identifier, Identifier> map = new HashMap<>();
            for (String entry : e.getValue()) {
                int eq = entry.indexOf('=');
                String name = entry.substring(eq + 1);
                Identifier ours = texture(folder, name);
                map.put(Identifier.withDefaultNamespace("textures/entity/" + entry.substring(0, eq)), ours);
                if (EventGearShapes.GLOWS.contains(folder + "/" + name)) {
                    GLOWS.put(ours, texture(folder, name + "_glow"));
                }
            }
            BOSS_SWAPS.put(folder, map);
        }
    }

    /** The monster drawn next wears this boss look ("grave_king" …), or none (null). */
    public static void renderingBoss(String look) {
        String folder = look == null ? null : look.startsWith("foe_") || look.startsWith("champion_") ? look
                : "boss_" + (look.equals("warlord_rider") ? "warlord" : look);
        bossFolder = folder != null && BOSS_SWAPS.containsKey(folder) ? folder : null;
    }

    /** Monsters of other mods that wear the event's tint and gear (their textures stay theirs). */
    private static final java.util.Set<String> MOD_NAMESPACES = java.util.Set.of("variantsandventures", "takesapillage", "friendsandfoes");

    /** True for a monster of one of the mods whose monsters join the event look. */
    public static boolean fromEventMod(net.minecraft.world.entity.EntityType<?> type) {
        if (type == null) {
            return false;
        }
        net.minecraft.resources.Identifier key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && MOD_NAMESPACES.contains(key.getNamespace());
    }

    /** The glow map of one of our event textures, or null. */
    public static Identifier glowOf(Identifier eventTexture) {
        return GLOWS.get(eventTexture);
    }

    /** A colour the mod monsters are tinted with in the event shown now (ARGB), or -1. */
    public static int modTint() {
        if (bossFolder != null) {
            return -1;
        }
        String event = shownEvent();
        if (event == null) {
            return -1;
        }
        return switch (event) {
            case "blood_moon" -> 0xFFD08C88;
            case "eclipse" -> 0xFF8A7AA8;
            case "fog" -> 0xFFC4CCC8;
            case "thunder" -> 0xFF98A8C4;
            case "nether_rift" -> 0xFFB07858;
            case "frost" -> 0xFFB4D2F0;
            default -> -1;
        };
    }

    private EventTextures() {
    }

    private static Identifier texture(String folder, String name) {
        return Identifier.fromNamespaceAndPath("nightsiege", "textures/entity/event/" + folder + "/" + name + ".png");
    }

    /** Events whose zombies and skeletons also wear a layer of clothes (hoods, capes, rags). */
    private static final java.util.Set<String> CLOTHED = java.util.Set.of(EventAtmosphere.BLOOD_MOON,
            EventAtmosphere.ECLIPSE, EventAtmosphere.FOG, EventAtmosphere.THUNDER,
            EventAtmosphere.NETHER_RIFT, EventAtmosphere.FROST);

    /** The folder of the event shown now (blood_moon, eclipse ...), or null when none has a monster look. */
    public static String shownEvent() {
        if (bossFolder != null) {
            return bossFolder;
        }
        EventAtmosphere.State state = EventAtmosphere.client();
        if (state.strength() < 0.5f || !SWAPS.containsKey(state.shown())) {
            return null;
        }
        return state.shown().toLowerCase(java.util.Locale.ROOT);
    }

    /** The clothes of a mob ("zombie", "skeleton") in the event shown now, or null. */
    public static Identifier clothing(String mob) {
        if (bossFolder != null) {
            return BOSS_CLOTHED.contains(bossFolder) ? texture(bossFolder, mob + "_outer") : null;
        }
        EventAtmosphere.State state = EventAtmosphere.client();
        if (state.strength() < 0.5f || !CLOTHED.contains(state.shown())) {
            return null;
        }
        return texture(state.shown().toLowerCase(java.util.Locale.ROOT), mob + "_outer");
    }

    /** The gear texture of a family in an event (and its glow with {@code glow}). */
    public static Identifier gear(String event, String family, boolean glow) {
        return texture(event, "gear_" + family + (glow ? "_glow" : ""));
    }

    public static Identifier swap(Identifier texture) {
        if (bossFolder != null) {
            Identifier swapped = BOSS_SWAPS.get(bossFolder).get(texture);
            return swapped == null ? texture : swapped;
        }
        EventAtmosphere.State state = EventAtmosphere.client();
        if (state.strength() < 0.5f) {
            return texture;
        }
        Map<Identifier, Identifier> map = SWAPS.get(state.shown());
        if (map == null) {
            return texture;
        }
        Identifier swapped = map.get(texture);
        return swapped == null ? texture : swapped;
    }
}
