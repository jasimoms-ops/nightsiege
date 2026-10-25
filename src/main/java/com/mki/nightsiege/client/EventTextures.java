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
 * frost night. The eyes and the event's details glow (the "_e" files). The 3D gear on top
 * comes from {@link EventGear}.
 */
public final class EventTextures {

    private static final String[][] MOBS = {
            {"textures/entity/zombie/zombie.png", "zombie"},
            {"textures/entity/zombie/zombie_baby.png", "zombie_baby"},
            {"textures/entity/zombie/husk.png", "husk"},
            {"textures/entity/zombie/husk_baby.png", "husk_baby"},
            {"textures/entity/zombie/drowned.png", "drowned"},
            {"textures/entity/zombie/drowned_baby.png", "drowned_baby"},
            {"textures/entity/zombie/drowned_outer_layer.png", "drowned_outer_layer"},
            {"textures/entity/zombie/drowned_outer_layer_baby.png", "drowned_outer_layer_baby"},
            {"textures/entity/skeleton/skeleton.png", "skeleton"},
            {"textures/entity/skeleton/stray.png", "stray"},
            {"textures/entity/skeleton/stray_overlay.png", "stray_overlay"},
            {"textures/entity/skeleton/bogged.png", "bogged"},
            {"textures/entity/skeleton/bogged_overlay.png", "bogged_overlay"},
            {"textures/entity/skeleton/parched.png", "parched"},
            {"textures/entity/skeleton/wither_skeleton.png", "wither_skeleton"},
            {"textures/entity/creeper/creeper.png", "creeper"},
            {"textures/entity/spider/spider.png", "spider"},
            {"textures/entity/spider/cave_spider.png", "cave_spider"},
            {"textures/entity/spider/spider_eyes.png", "spider_eyes"},
    };
    private static final String[] EVENTS = {
            EventAtmosphere.ECLIPSE, EventAtmosphere.FROST, EventAtmosphere.NETHER_RIFT,
            EventAtmosphere.THUNDER, EventAtmosphere.FOG, EventAtmosphere.BLOOD_MOON,
    };
    private static final Map<String, Map<Identifier, Identifier>> SWAPS = new HashMap<>();

    static {
        for (String event : EVENTS) {
            Map<Identifier, Identifier> map = new HashMap<>();
            String folder = event.toLowerCase(java.util.Locale.ROOT);
            for (String[] mob : MOBS) {
                map.put(Identifier.withDefaultNamespace(mob[0]), texture(folder, mob[1]));
            }
            SWAPS.put(event, map);
        }
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
        EventAtmosphere.State state = EventAtmosphere.client();
        if (state.strength() < 0.5f || !SWAPS.containsKey(state.shown())) {
            return null;
        }
        return state.shown().toLowerCase(java.util.Locale.ROOT);
    }

    /** The clothes of a mob ("zombie", "skeleton") in the event shown now, or null. */
    public static Identifier clothing(String mob) {
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
