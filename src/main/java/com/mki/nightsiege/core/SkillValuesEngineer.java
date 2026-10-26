package com.mki.nightsiege.core;

import static com.mki.nightsiege.core.SkillValues.def;

/** Named skill numbers of the Techniker ({@link SkillValues}): read by the handlers, shown in the texts. */
final class SkillValuesEngineer {

    private SkillValuesEngineer() {
    }

    /** Hard caps of the towers (Simon, 25 October 2026); skills and crate gains count on top. */
    static final double CAP_MORTAR = 2;
    static final double CAP_TESLA = 2;
    static final double CAP_TURRET = 3;
    static final double CAP_BREWER = 3;

    // The tier tables are read through TrapKind's built-in tables (base…), never through
    // SkillValues itself, so registering cannot run into the values it is filling.
    static void register() {
        // ----- the traps and towers: their tier tables live in TrapKind, shown here per tier
        floorPlate(TrapKind.SPIKE);
        def("spike_trap", "slow", TrapKind.SPIKE.baseHold(1));          // ticks of slowness per hit
        floorPlate(TrapKind.SNARE);
        for (int t = 1; t <= 3; t++) {
            def("snare_trap", "radius" + t, TrapKind.SNARE.baseRadius(t));
            def("snare_trap", "hold" + t, TrapKind.SNARE.baseHold(t));
            def("snare_trap", "cd" + t, TrapKind.SNARE.baseCooldown(t));
        }
        floorPlate(TrapKind.FLAME);
        for (int t = 1; t <= 3; t++) {
            def("flame_trap", "burn" + t, TrapKind.FLAME.baseHold(t));
            def("flame_trap", "radius" + t, TrapKind.FLAME.baseRadius(t));
            def("flame_trap", "cd" + t, TrapKind.FLAME.baseCooldown(t));
        }
        tower(TrapKind.TURRET, CAP_TURRET);
        // Simon 26.10.: the arrow turret's cooldown x2 up to level 14, /0.7 from level 15; the mortar's x2.
        def("arrow_turret", "slowlow", 2.0);
        def("arrow_turret", "slowuntil", 15);                            // first level with the smaller factor
        def("arrow_turret", "slowhigh", 1.0 / 0.7);
        def("mortar_tower", "slow", 2.0);
        def("arrow_turret", "overcharge", 0.9);                          // share of the full remote (Simon, 26 Oct: all shooting towers 90 %)
        def("arrow_turret", "unmanned", 0.6);                            // alone (Simon, 26 Oct)
        def("arrow_turret", "manned", 1.4);                              // with a gunner in the seat
        def("arrow_turret", "headshot", 2.0);                            // a manned arrow on the head
        def("arrow_turret", "headline", 0.75);                           // share of the height from where the head starts
        tower(TrapKind.TESLA, CAP_TESLA);
        for (int t = 1; t <= 3; t++) {
            def("tesla_coil", "targets" + t, TrapKind.TESLA.baseChainTargets(t));
        }
        def("tesla_coil", "maxtargets", 4);                              // monsters per discharge at most
        def("tesla_coil", "overcharge", 0.9);                            // share of the full remote
        def("tesla_coil", "near", 0.6);                                  // cooldown factor with its engineer close by
        def("tesla_coil", "nearrange", 6);                               // blocks
        def("tesla_coil", "jump", 4);                                    // blocks from monster to monster
        // Manned towers (Simon, 26 October 2026): alone 60 %, with a gunner in the seat 140 %.
        def("tesla_coil", "unmanned", 0.6);
        def("tesla_coil", "manned", 1.4);
        def("tesla_coil", "beampulse", 5);                               // ticks between two beam strikes
        def("tesla_coil", "beamcost", 2.5);                              // energy per beam strike that hits
        def("tesla_coil", "beamrange", 4);                               // blocks more than the coil's own reach
        tower(TrapKind.MORTAR, CAP_MORTAR);
        for (int t = 1; t <= 3; t++) {
            def("mortar_tower", "splash" + t, TrapKind.MORTAR.baseSplash(t));
        }
        def("mortar_tower", "hit", 10);                                  // one direct hit, times trapDamage and the level
        // Shown in the texts only: a direct hit against a zombie's 20 life points with the
        // default trapDamage 0.75 (10 × 0.75 / 20); both grow alike with the level.
        def("mortar_tower", "zombie", 0.375);
        def("mortar_tower", "aimpause", 200);                            // ticks between two new target spots
        def("mortar_tower", "salvo", 3);                                 // shells of the X salvo
        // The bird's-eye view (Simon, 26 October 2026).
        def("mortar_tower", "spotradius", 48);                           // blocks around the core the view may go
        def("e_barrage", "pause", 200);                                  // ticks after a use (10 s)
        def("e_barrage", "shells", 6);
        def("e_barrage", "length", 16);                                  // longest line in blocks
        def("e_area_fire", "pause", 200);
        def("e_area_fire", "shells", 8);
        def("e_area_fire", "radius", 5);                                 // blocks
        def("e_area_fire", "dmg", 0.8);                                  // damage of each shell
        def("e_smoke_salvo", "pause", 200);
        def("e_smoke_salvo", "shells", 2);
        def("e_smoke_salvo", "radius", 5);                               // blocks of smoke per shell
        def("e_smoke_salvo", "time", 80);                                // ticks without a target
        def("e_fire_salvo", "pause", 200);
        def("e_fire_salvo", "shells", 3);
        def("e_fire_salvo", "burn", 100);                                // ticks the ground burns
        def("e_fire_salvo", "dmg", 0.6);                                 // damage of each shell
        def("mortar_tower", "overcharge", 0.9);                          // share of the full remote
        tower(TrapKind.BREWER, CAP_BREWER);
        for (int t = 1; t <= 3; t++) {
            def("potion_tower", "splash" + t, TrapKind.BREWER.baseSplash(t));
        }
        def("potion_tower", "hit", 10);
        def("potion_tower", "zombie", 0.375);
        def("potion_tower", "poison", TrapKind.BREWER.baseHold(1));    // ticks the poison eats
        def("potion_tower", "big", 1.5);                                 // area of the big throw (X)
        def("potion_tower", "overcharge", 0.9);                          // share of the full remote
        // The alchemist's zones and flash grenade (Simon, 26 October 2026).
        def("potion_tower", "zonerange", 10);                            // blocks to the spot looked at
        def("potion_tower", "zonetime", 160);                            // ticks a cloud stays
        def("potion_tower", "zonepause", 400);                           // ticks between two zone potions
        def("potion_tower", "zoneradius", 3.5);                          // blocks
        def("potion_tower", "zoneheal", 2.0);                            // life points per second in the healing cloud
        def("potion_tower", "flashradius", 5);                           // blocks
        def("potion_tower", "flashtime", 80);                            // ticks blind and without a target
        def("potion_tower", "flashpause", 300);                          // ticks until the grenade is back
        def("spike_trap", "overcharge", 30);
        def("snare_trap", "overcharge", 30);
        def("flame_trap", "overcharge", 30);
        def("spike_trap", "breakchance", 0.05);                         // chance to break when it springs (Simon, 26 Oct)
        def("snare_trap", "breakchance", 0.05);
        def("flame_trap", "breakchance", 0.05);

        // ----- trunk
        def("e_battery", "base", 100);                                   // energy of the remote without skills
        def("e_fastcharge", "rate", 5);                                  // energy per second without skills (1.25 every 5 ticks)

        // ----- mechanics
        def("e_longrange", "base", 24);                                  // blocks the remote reaches
        def("e_circuit", "base", 100);                                   // ticks an overcharge lasts
        // Cross talents lend a trap of the other way at this tier.
        for (String cross : new String[]{"m_cross_mine", "m_cross_poison", "c_cross_turret", "c_cross_tesla"}) {
            def(cross, "tier", 2);
        }
        def("e_ammobelt", "speed", 0.2);                                 // turrets shoot this much faster (since 26 Oct: endless arrows)
        def("e_ammobelt", "reload", 40);                                 // energy of a repair with X (name kept for server tweaks)
        def("e_seeker", "base", 0.15);                                   // extra damage of a mark without the skill
        def("e_seeker", "time", 160);                                    // ticks a mark lasts
        def("e_seeker", "mark", 10);                                     // energy of a mark
        def("e_overvolt", "base", 2.0);                                  // damage factor while overcharged
        def("e_overvolt", "power", 3.0);                                 // with overvolt
        def("e_charged", "jump", 4);                                     // blocks
        def("e_network", "share", 0.5);
        def("e_network", "radius", 6);
        def("e_backup", "below", 0.3);
        def("e_backup", "speed", 2.0);
        def("e_fortress", "cost", 0.3);                                  // remote costs this share more
        def("e_fortress", "slots", 1);
        def("e_fieldtech", "cost", 0.4);                                 // remote costs this share less
        def("e_fieldtech", "slots", 1);
        for (int r = 1; r <= 3; r++) {
            def("overcharge", "time" + r, 160 + 40 * r);                 // ticks
            def("thunderstorm", "dmg" + r, 6 + 2 * r);
            def("thunderstorm", "jumps" + r, 4 + 2 * r);
            def("bombardment", "dmg" + r, 8 + 4 * r);
            def("bombardment", "blasts" + r, 3 + 2 * r);
            def("toxic_cloud", "radius" + r, 6 + r);
            def("toxic_cloud", "time" + r, 120 + 40 * r);               // ticks
        }
        def("overcharge", "speed", 3);                                   // fire rate factor
        def("thunderstorm", "range", 16);
        def("thunderstorm", "jump", 6);
        def("e_sustained", "time", 80);
        def("e_ball_lightning", "jumps", 4);
        def("e_ball_lightning", "dmg", 2);
        def("e_superconductor", "time", 80);

        // ----- chemistry
        def("e_detonator", "cost", 20);                                  // energy of an X order without the skill
        def("e_detonator", "save", 4);                                   // less energy per level
        def("e_detonator", "dmg", 0.15);                                 // harder per level
        def("e_acid", "slow", 1);                                        // extra slowness levels
        def("e_chain_reaction", "count", 3);
        def("e_chain_reaction", "dmg", 1.0 / 3.0);
        def("e_chain_reaction", "radius", 0.6);
        def("e_accelerant", "burn", 40);                                 // ticks a potion throw sets alight
        def("e_napalm", "burn", 2.0);                                    // flames burn this many times as long
        def("e_napalm", "radius", 1);
        def("e_napalm", "shellburn", 60);                                // ticks a mortar shell sets alight
        def("e_targetblast", "radius", 2);
        def("bombardment", "radius", 2.5);
        def("bombardment", "reach", 20);
        def("e_firebombs", "burn", 100);                                 // ticks of fire and poison
        def("e_plague", "radius", 3);
        def("e_plague", "spread", 3);
        def("e_pyro", "wear", 0.3);                                      // floor traps wear out this share sooner
        def("e_plague_doctor", "poison", 2.0);
        def("e_plague_doctor", "blast", 0.3);                            // less fire and blast damage
    }

    /** Uses, cap and (for the spike) nothing else per tier of a floor plate. */
    private static void floorPlate(TrapKind kind) {
        for (int t = 1; t <= 3; t++) {
            if (kind.baseDamage(t) > 0) {
                def(kind.id(), "dmg" + t, kind.baseDamage(t));
            }
            def(kind.id(), "uses" + t, kind.baseUses(t));
            def(kind.id(), "max" + t, kind.baseMaxPlaced(t));
        }
    }

    /** Range, cooldown, how many may stand and the hard cap of a tower. */
    private static void tower(TrapKind kind, double cap) {
        def(kind.id(), "cap", cap);
        for (int t = 1; t <= 3; t++) {
            if (kind.baseDamage(t) > 0) {
                def(kind.id(), "dmg" + t, kind.baseDamage(t));
            }
            def(kind.id(), "max" + t, Math.min(cap, kind.baseMaxPlaced(t)));
            def(kind.id(), "range" + t, kind.baseRadius(t));
            def(kind.id(), "cd" + t, kind.baseCooldown(t));
        }
    }
}
