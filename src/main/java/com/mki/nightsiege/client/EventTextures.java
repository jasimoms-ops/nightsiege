package com.mki.nightsiege.client;

import com.mki.nightsiege.game.EventAtmosphere;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Event looks of the common monsters. While an event is shown, zombies,
 * skeletons, creepers and spiders wear its texture: shadowy with glowing eyes
 * in the eclipse, iced over in the frost night, charred at a rift.
 */
public final class EventTextures {

    private static final String[][] MOBS = {
            {"textures/entity/zombie/zombie.png", "zombie"},
            {"textures/entity/zombie/zombie_baby.png", "zombie_baby"},
            {"textures/entity/skeleton/skeleton.png", "skeleton"},
            {"textures/entity/creeper/creeper.png", "creeper"},
            {"textures/entity/spider/spider.png", "spider"},
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
                map.put(Identifier.withDefaultNamespace(mob[0]),
                        Identifier.fromNamespaceAndPath("nightsiege", "textures/entity/event/" + folder + "/" + mob[1] + ".png"));
            }
            SWAPS.put(event, map);
        }
    }

    private EventTextures() {
    }

    /** Events whose zombies and skeletons also wear a layer of clothes. */
    private static final java.util.Set<String> CLOTHED = java.util.Set.of(EventAtmosphere.BLOOD_MOON);

    /** The clothes of a mob ("zombie", "skeleton") in the event shown now, or null. */
    public static Identifier clothing(String mob) {
        EventAtmosphere.State state = EventAtmosphere.client();
        if (state.strength() < 0.5f || !CLOTHED.contains(state.shown())) {
            return null;
        }
        return Identifier.fromNamespaceAndPath("nightsiege", "textures/entity/event/"
                + state.shown().toLowerCase(java.util.Locale.ROOT) + "/" + mob + "_outer.png");
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
