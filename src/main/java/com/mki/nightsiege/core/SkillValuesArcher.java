package com.mki.nightsiege.core;

import static com.mki.nightsiege.core.SkillValues.def;

/** Named skill numbers of the Bogenschütze ({@link SkillValues}): read by the handlers, shown in the texts. */
final class SkillValuesArcher {

    private SkillValuesArcher() {
    }

    static void register() {
        // Times in ticks (text format "s"), distances in blocks, shares as fractions.

        // ----- trunk
        // Movement speed is a share of Minecraft's base speed 0.1 (amount 0.005 = 5 %); for the text only.
        def("a_lightfoot", "pct", 0.05);            // for the text only
        def("a_quiver", "extra", 0.5);              // infinity bow: the free extra arrow hits this hard

        // ----- way of the bow
        def("a_steady", "full", 0.05);             // a fully drawn bow shot: this much more damage per level
        def("a_pierce", "share", 0.7);              // damage of each monster behind the target
        def("a_pierce", "range", 8.0);              // blocks behind the target
        def("a_longshot", "range", 15.0);           // farther than this many blocks
        def("swift_draw", "extra", 0.85);           // damage of the quick shot's extra arrows (Simon 26.10.)
        // Simon 26.10.: every archer subclass has 4 more life (two hearts) and always a night totem.
        def("swift_draw", "hp", 4);
        def("heavy_draw", "hp", 4);
        def("shrapnel", "hp", 4);
        def("a_netbolt", "hp", 4);
        def("a_netbolt", "splash", 2.0);            // Simon 27.10. (more crowd control): monsters this close to the target are slowed too, half as long
        def("heavy_draw", "pause", 12);             // bow cooldown after every shot
        def("heavy_draw", "power", 1);              // extra Power level on the kit bow (KitService)
        def("double_shot", "angle", 4.0);
        def("a_volley", "every", 4);
        def("a_volley", "arrows", 2);               // extra arrows of the fan
        def("a_volley", "angle", 8.0);
        def("piercing", "share", 0.7);
        def("a_knockarrow", "push", 1.0);
        def("a_knockarrow", "lift", 0.25);
        def("a_fire", "bonus", 0.15);               // own arrows against burning monsters
        def("a_poison", "dur", 80);
        def("b_mark", "dur", 120);
        def("a_firevolley", "mult", 1.5);           // damage of the extra arrows
        def("a_firevolley", "radius", 2.0);         // a burning arrow sets monsters around the target alight
        def("a_firevolley", "burn", 80);
        def("a_venomburst", "radius", 3.0);
        def("a_venomburst", "dur", 80);
        def("a_eagle", "crit", 0.5);
        def("b_cross_shrapnel", "radius", 2.5);
        def("a_retreat", "dur", 40);
        def("arrow_storm", "dur1", 100);
        def("arrow_storm", "dur2", 140);
        def("arrow_storm", "dur3", 180);
        def("arrow_storm", "arrows", 2);
        def("arrow_storm", "angle", 8.0);
        def("power_shot", "mult1", 3.0);
        def("power_shot", "mult2", 3.75);
        def("power_shot", "mult3", 4.5);
        def("power_shot", "speed", 1.25);           // faster flight, and arrow damage grows with speed
        def("power_shot", "armed", 200);            // the next shot within this time
        def("b_hunt", "radius1", 24.0);
        def("b_hunt", "radius2", 32.0);
        def("b_hunt", "radius3", 40.0);
        def("b_hunt", "bonus1", 0.4);
        def("b_hunt", "bonus2", 0.5);
        def("b_hunt", "bonus3", 0.6);
        def("b_hunt", "dur", 300);
        def("a_tempest", "extra", 40);
        def("a_tempest", "arrows", 2);
        def("a_tempest", "angle", 16.0);
        def("a_sniper", "mult", 1.5);
        def("a_sniper", "share", 0.8);
        def("a_sniper", "count", 6);
        def("b_pack", "radius", 1.5);
        def("b_pack", "dur", 200);
        def("b_pack", "range", 20.0);
        def("a_deadeye", "mult", 1.35);
        def("a_deadeye", "health", 8.0);            // read in SkillService.keystone
        def("a_skirmisher", "mult", 0.9);
        def("a_skirmisher", "speed", 0.15);         // share of the base speed 0.1, read in SkillService.keystone

        // ----- way of the crossbow
        def("a_lightxbow", "pct", 0.06);            // amount 0.006 of the base speed 0.1; for the text only
        def("a_steelbolts", "dur", 100);            // torn armour, counts on top of a mark
        def("shrapnel", "radius", 2.5);
        def("a_explosive", "radius", 2.5);
        def("a_multiload", "arrows", 2);
        def("a_multiload", "angle", 7.0);
        def("a_snarefield", "radius", 4.0);         // 27.10.: was 3 blocks, 3 s, Slowness III
        def("a_snarefield", "dur", 80);
        def("a_snarefield", "level", 3);            // Slowness IV
        def("a_shackle", "dur", 60);                // 27.10.: was 2 s
        def("a_punch", "share", 0.7);
        def("a_tripwire", "radius", 4.0);           // 27.10.: was 3 blocks, 2 s, every 5 s
        def("a_tripwire", "dur", 60);
        def("a_tripwire", "cd", 80);
        def("x_mark", "dur", 120);
        def("a_clusterbolt", "radius", 4.0);
        def("a_clusterbolt", "base", 3.0);          // blast without explosive bolts
        def("a_clusterbolt", "bonus", 4.0);         // on top of the blast
        def("a_reloadrush", "dur", 60);
        def("x_cross_double", "angle", 4.0);
        def("bolt_volley", "count1", 7);
        def("bolt_volley", "count2", 9);
        def("bolt_volley", "count3", 11);
        def("bolt_volley", "dmg1", 6.0);
        def("bolt_volley", "dmg2", 8.0);
        def("bolt_volley", "dmg3", 10.0);
        def("bolt_volley", "spread", 50.0);
        def("a_netrain", "radius1", 12.0);
        def("a_netrain", "radius2", 14.0);
        def("a_netrain", "radius3", 16.0);
        def("a_netrain", "dur1", 100);              // 27.10.: +1 s each
        def("a_netrain", "dur2", 120);
        def("a_netrain", "dur3", 140);
        def("a_netrain", "dmg1", 6.0);
        def("a_netrain", "dmg2", 8.0);
        def("a_netrain", "dmg3", 10.0);
        def("x_hunt", "radius1", 24.0);
        def("x_hunt", "radius2", 32.0);
        def("x_hunt", "radius3", 40.0);
        def("x_hunt", "bonus1", 0.4);
        def("x_hunt", "bonus2", 0.5);
        def("x_hunt", "bonus3", 0.6);
        def("x_hunt", "dur", 300);
        def("a_barrage", "radius", 3.0);
        def("a_barrage", "dmg", 6.0);
        def("a_widenet", "dur", 100);
        def("x_pack", "radius", 1.5);
        def("x_pack", "dur", 200);
        def("x_pack", "range", 20.0);
        def("a_siegeengine", "mult", 1.4);
        def("a_siegeengine", "speed", 0.15);        // share of the base speed 0.1, read in SkillService.keystone
        def("a_watchful", "dur", 120);
        def("a_watchful", "health", 4.0);           // read in SkillService.keystone

        // ----- arrow rain on F (Simon 30.10.: every archer, "je Unterklasse eigen", 5 s between two rains)
        // Damage per hit is for level 1; it grows like the monsters' life and the config's archer factors apply.
        // Per monster and rain, after the subclass factors: about 3 (swift 12 × 0.25, heavy 4 × 0.43 × 1.75,
        // bolter 6 × 1.0 × 0.6, trapper 6 × 0.35 and slowed).
        def("arrow_rain", "dur", 60);               // ticks the rain falls
        def("arrow_rain", "pause", 100);            // ticks after the rain until the next one (Simon: 5 s)
        def("arrow_rain", "range", 32.0);           // blocks to the spot looked at
        def("arrow_rain", "mult", 1.0);             // on every hit (for balancing)
        def("arrow_rain", "radius_swift", 4.0);
        def("arrow_rain", "every_swift", 5);
        def("arrow_rain", "dmg_swift", 0.25);
        def("arrow_rain", "radius_heavy", 3.5);
        def("arrow_rain", "every_heavy", 15);
        def("arrow_rain", "dmg_heavy", 0.43);
        def("arrow_rain", "push_heavy", 0.4);
        def("arrow_rain", "radius_bolter", 3.0);
        def("arrow_rain", "every_bolter", 10);
        def("arrow_rain", "dmg_bolter", 1.0);
        def("arrow_rain", "radius_trapper", 4.5);
        def("arrow_rain", "every_trapper", 10);
        def("arrow_rain", "dmg_trapper", 0.35);
        def("arrow_rain", "slow_trapper", 30);       // ticks of Slowness II, renewed by every hit
    }
}
