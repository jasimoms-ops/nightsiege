package com.mki.nightsiege.core;

import static com.mki.nightsiege.core.SkillValues.def;

/** Named skill numbers of the Krieger ({@link SkillValues}): read by the handlers, shown in the texts. */
final class SkillValuesWarrior {

    private SkillValuesWarrior() {
    }

    static void register() {
        // --- trunk
        // Display only: Beinarbeit's amount (0.005) on the base speed 0.1, in percent.
        def("w_footwork", "pct", 0.05);

        // --- way of the blade
        def("w_bladedance", "way", 0.2);       // attack speed the blade way itself gives
        def("w_bladedance", "combo", 0.08);    // with Kombo: damage per level instead of speed
        def("w_combo", "hits", 4.0);           // full hits a second (Combo.tick)
        def("w_combo", "stacks", 5);           // streak hits that count
        def("w_combo", "window", 40);          // ticks between two hits of a streak
        def("w_combo", "killstreak", 2);       // streak needed for the kill heal
        def("w_combo", "killheal", 0.16);      // share of the slain monster's life (Simon 26.10.: 0.15 -> 0.06; 27.10.: 0.12, 23:00: 0.16)
        def("bloodlust", "below", 1.01);       // life share below which hits heal (always since 26 October 2026, Simon)
        def("bloodlust", "steal", 0.12);       // share of the damage healed (was config berserkerLifesteal; 27.10., 23:00: 0.16; 28.10., 02:13: back to 0.12)
        // Simon 26.10.: share of the hit on monsters around the target, plus up to splashlow more at 0 life
        // (linear with the life lost); 27.10. (DPS bench): 0.15/0.15 -> 0.11/0.11, 15 % less against groups.
        def("bloodlust", "splash", 0.11);
        def("bloodlust", "splashlow", 0.11);
        def("bloodlust", "radius", 2.5);       // blocks around the target
        def("keen_edge", "crit", 0.5);         // extra damage of a critical hit
        def("w_precision", "chance", 0.10);    // own crit chance (Präzision and Scharfe Klinge are one choice)
        def("w_precision", "crit", 0.5);       // extra damage of its critical hits, before its amount
        def("w_warcry", "radius", 5);
        def("w_warcry", "time", 60);           // Weakness I, ticks
        def("w_warcry", "every", 40);          // ticks (multiple of 10)
        def("w_parry", "counter", 4);          // damage of the strike back
        def("w_frenzy", "combo", 0.4);         // with Kombo: damage at 0 life instead of speed
        def("e_cross_heavy", "slow", 20);      // Slowness II, ticks
        def("w_bladefrenzy", "time", 60);      // Strength I, ticks
        def("w_bloodblade", "steal", 0.05);    // extra life steal in the bloodlust (Simon 26.10.)
        def("w_evasion", "speed", 30);         // Speed I after a dodge, ticks
        def("w_bloodprice", "cost", 1.0);      // life per hit (half a heart)
        def("w_bloodprice", "floor", 6.0);     // only above this life
        def("w_bloodprice", "every", 20);      // Tor 4: the cost at most once in this many ticks (was every hit)
        def("execute", "limit1", 0.25);
        def("execute", "limit2", 0.30);
        def("execute", "limit3", 0.35);
        def("execute", "reach", 5);
        def("execute", "cd1", 500);            // ticks, see CombatEffects.cooldownFor
        def("execute", "cd2", 400);
        def("execute", "cd3", 300);
        def("whirlwind", "dmg1", 7);
        def("whirlwind", "dmg2", 10);
        def("whirlwind", "dmg3", 13);
        def("whirlwind", "radius1", 4);
        def("whirlwind", "radius2", 4.5);
        def("whirlwind", "radius3", 5);
        def("whirlwind", "cd1", 500);
        def("whirlwind", "cd2", 400);
        def("whirlwind", "cd3", 300);
        def("w_chain_execute", "jumps", 2);
        def("w_chain_execute", "radius", 5);
        def("w_bloody_execute", "heal", 6);
        def("w_bloody_execute", "regen", 100); // Regeneration I, ticks
        def("w_bladestorm", "pulses", 3);
        def("w_bladestorm", "gap", 10);        // ticks between two turns
        def("w_bladestorm", "dmg", 1.2);       // Tor 4.10: factor on every turn's damage with Klingensturm
        def("w_bladestorm", "push", 0.25);     // Tor 4.10: push of a turn (0.8 without), so turns 2 and 3 still hit
        def("w_bloodstorm", "heal", 1);        // life per monster hit
        def("w_bloodstorm", "max", 8);         // life per turn at most
        def("w_glass_cannon", "dmg", 0.3);
        def("w_glass_cannon", "hp", 8);
        def("w_unstoppable", "slow", 0.10);    // share of the base walking speed (0.1)

        // --- way of might
        def("sunder", "time", 100);            // ticks
        def("w_heavyswing", "way", 0.3);       // attack speed the might way itself gives
        def("heavy_blow", "slow", 20);         // Slowness II, ticks
        def("heavy_blow", "lifesteal", 0.5);   // Simon 26.10.: factor on the axe fighter's kill heal (meleeLifesteal), -50 %
        def("heavy_blow", "hp", -10);          // Simon 26.10.: life of the axe fighter (SkillService) ...
        def("heavy_blow", "hplow", -6);        // ... Tor 4 (Simon 08.11.): only this much below world level "hplowuntil"
        def("heavy_blow", "hplowuntil", 10);
        def("heavy_blow", "groupcut", 0.2);    // Simon 26.10.: cleave -20 % up to world level "groupfull1" ...
        def("heavy_blow", "groupfull1", 10);   // ... then back to +-0 in a line ...
        def("heavy_blow", "groupfull2", 20);   // ... at this level and above
        def("smite", "hp", -5);                // Simon 26.10.: life of the paladin (SkillService)
        def("w_cleave", "radius", 2.5);
        def("w_headhunter", "above", 0.7);
        def("w_vow", "radius", 6);
        def("w_vow", "time", 60);              // Resistance I, ticks
        def("w_vow", "every", 40);             // ticks (multiple of 10)
        def("w_momentum", "window", 60);       // ticks after a kill
        // Display only: the heal aura's pace and reach are shared (CombatEffects.HEAL_INTERVAL / HEAL_RADIUS).
        def("w_blessing", "every", 60);
        def("w_blessing", "radius", 8);
        def("m_cross_keen", "crit", 0.5);
        def("w_crusader", "heal", 0.5);        // life per monster the cleave hits
        def("w_crusader", "max", 3);           // life per hit at most
        def("w_crusader", "burn", 60);         // ticks on the undead
        def("w_headsman", "slow", 20);         // Slowness III, ticks
        def("w_headsman", "chance", 0.10);     // own crit chance when nothing else gives one
        def("w_holy_strike", "burn", 80);      // ticks
        def("w_holy_strike", "heal", 1);
        def("w_holy_strike", "chance", 0.10);  // own crit chance when nothing else gives one
        def("w_earthshock", "fall", 0.0);      // blocks of falling (Tor 2.2: 1.5 was more than a jump, it never fired)
        def("w_earthshock", "splash", 0.5);
        def("w_earthshock", "radius", 2.5);
        def("w_divine_shield", "every", 600);  // ticks
        def("w_divine_shield", "min", 4);      // smallest hit it catches
        def("ground_slam", "dmg1", 8);
        def("ground_slam", "dmg2", 11);
        def("ground_slam", "dmg3", 14);
        def("ground_slam", "radius1", 5);
        def("ground_slam", "radius2", 6);
        def("ground_slam", "radius3", 7);
        def("ground_slam", "slow", 60);        // Slowness III, ticks
        def("ground_slam", "cd1", 500);
        def("ground_slam", "cd2", 400);
        def("ground_slam", "cd3", 300);
        def("holy_light", "undead1", 15);
        def("holy_light", "undead2", 20);
        def("holy_light", "undead3", 25);
        def("holy_light", "dmg1", 5);
        def("holy_light", "dmg2", 7);
        def("holy_light", "dmg3", 9);
        def("holy_light", "radius1", 10);
        def("holy_light", "radius2", 12);
        def("holy_light", "radius3", 14);
        def("holy_light", "heal", 4);
        def("holy_light", "burn", 100);        // ticks on the undead
        def("holy_light", "cd1", 500);
        def("holy_light", "cd2", 400);
        def("holy_light", "cd3", 300);
        def("w_aftershock", "delay", 20);
        def("w_aftershock", "scale", 0.6);
        def("w_crater", "slow", 60);           // Slowness IV, ticks
        def("w_sunfire", "factor", 1.5);
        def("w_sunfire", "burn", 200);         // ticks on the undead
        def("w_sunfire", "burnother", 60);     // ticks on everyone else
        def("w_redemption", "heal1", 8);
        def("w_redemption", "heal2", 12);
        def("w_redemption", "heal3", 16);
        def("w_redemption", "regen", 100);     // Regeneration II, ticks
        def("w_colossus", "hp", 12);
        def("w_colossus", "slow", 0.10);       // share of the base walking speed (0.1)
        def("w_martyr", "heal", 1.5);          // life per aura pulse for each teammate
        def("w_martyr", "hp", 4);
        def("w_martyr", "every", 60);          // display only: shared aura pace (CombatEffects.HEAL_INTERVAL)
        def("w_martyr", "radius", 8);          // display only: shared aura reach (CombatEffects.HEAL_RADIUS)

        // --- Kampfgeist, combo finishers and F (Simon 05.11.: "ja alles super bau das so"), see WarriorSpiritMath.
        // The meter, every warrior.
        def("w_spirit", "max", 100);
        def("w_spirit", "hit", 4);             // per melee hit on a monster
        def("w_spirit", "kill", 10);           // per kill
        def("w_spirit", "hurt", 6);            // per hit taken from a monster
        def("w_spirit", "idle", 100);          // ticks without fighting before it falls
        def("w_spirit", "decay", 5);           // per second once it falls
        def("w_spirit", "glow", 50);           // the bar glows, the F field lights up
        def("w_spirit", "cost", 50);           // what F costs
        def("w_spirit", "charge", 0.8);        // least attack charge of a swing that counts (no click spam)
        // The chain: every chain-th melee hit is a finisher; it breaks after window ticks without a hit.
        def("w_spirit", "chain", 4);
        def("w_spirit", "window", 40);
        // Kombo 2 (Simon 06.11.: "als Axtkämpfer wird man niemals die volle Kombo bekommen"): with one hit
        // left, the finisher waits this many ticks for the next hit, on any monster.
        def("w_spirit", "ready", 120);
        def("w_spirit", "empcost", 30);        // a finisher at a full meter is empowered and costs this
        def("w_spirit", "empfactor", 2.0);     // ... for double the effect
        // Klingenmeister: finisher Klingenwirbel, F Klingentanz.
        def("w_combo", "fin_radius", 2.5);     // blocks around the warrior
        def("w_combo", "fin_share", 0.6);      // share of the hit on every monster around
        def("w_combo", "f_jumps", 3);
        def("w_combo", "f_radius", 6);         // blocks to the monsters he jumps to
        def("w_combo", "f_gap", 5);            // ticks between two jumps
        def("w_combo", "f_dmg", 6);            // a jump's hit on world level 1 (grows like the monsters' life)
        def("w_combo", "f_cd", 160);           // ticks
        def("w_combo", "knock", 0.3);          // Tor 4.10: factor on the knockback of his sword hits in the Kombo
        // Berserker: finisher Blutiger Hieb, F Blutrausch (Tor 4.10, was Raserei; the id stays "raserei").
        def("bloodlust", "fin_bonus", 0.5);    // extra damage share of the hit ...
        def("bloodlust", "fin_perlost", 0.1);  // ... and this much more per missing tenth of his life
        def("bloodlust", "fin_bleed", 0.3);    // share of the hit the bleeding deals over its time
        def("bloodlust", "fin_bleedtime", 60); // ticks
        def("bloodlust", "f_time", 160);       // ticks of the frenzy (Tor 4.10: 120 -> 160) ...
        def("bloodlust", "f_extend", 20);      // ... each kill in it adds this ...
        def("bloodlust", "f_max", 240);        // ... up to this long in all
        // Tor 4.10: speed, damage and life steal grow with the share of life missing (value + low x missing).
        def("bloodlust", "f_speed", 0.5);      // attack speed in it (was 0.3) ...
        def("bloodlust", "f_speedlow", 0.5);   // ... plus this at 0 life
        def("bloodlust", "f_dmg", 0.25);       // extra melee damage in it ...
        def("bloodlust", "f_dmglow", 0.35);    // ... plus this at 0 life
        def("bloodlust", "f_steal", 2.0);      // factor on his life steal in it ...
        def("bloodlust", "f_steallow", 1.0);   // ... plus this at 0 life (still capped by the life lost, StealPool)
        def("bloodlust", "f_taken", 0.15);     // more damage taken in it
        def("bloodlust", "f_cd", 400);         // Tor 4.10: 200 -> 400
        // Axtkämpfer: finisher Spalter, F Axtwurf.
        def("heavy_blow", "fin_armor", 0.3);   // share of the target's armour it loses ...
        def("heavy_blow", "fin_time", 80);     // ... for this many ticks
        def("heavy_blow", "fin_slow", 20);     // knocked down: Slowness IV, ticks
        def("heavy_blow", "fin_crack", 0.0);   // optional: the target also takes this much more damage (0 = off)
        def("heavy_blow", "fin_chain", 3);     // Kombo 2: his chain is 3 hits (he swings the slowest)
        def("heavy_blow", "fin_jump", 4);      // Kombo 2: blocks to the next monster when the target died
        def("heavy_blow", "f_range", 12);      // blocks the axe flies out
        def("heavy_blow", "f_speed", 2.5);     // blocks a tick (Tor 4.10: 1.5 -> 2.5, along the crosshair)
        def("heavy_blow", "f_width", 1.3);     // blocks beside its path that it hits
        def("heavy_blow", "f_dmg", 7.5);       // a hit on world level 1 (grows like the monsters' life), out and back (Tor 4.10: 5 -> 7.5)
        def("heavy_blow", "f_pierce", 2);      // Tor 4.10: monsters it flies through; it turns at the next (3 hits a way at most)
        def("heavy_blow", "f_cd", 160);
        // Paladin: finisher Lichtschlag, F Weihegrund.
        def("smite", "fin_heal", 2);           // life for every player within fin_radius (half a heart = 1)
        def("smite", "fin_radius", 6);
        def("smite", "fin_burn", 60);          // ticks the undead around burn
        def("smite", "f_radius", 6);
        def("smite", "f_time", 160);           // ticks the ground lasts
        def("smite", "f_regen", 50);           // Regeneration I for the players inside, renewed every second
        def("smite", "f_slow", 30);            // Slowness I for the monsters inside, renewed every second
        def("smite", "f_holy", 1.0);           // holy damage a second on the undead inside (level 1, grows like their life)
        def("smite", "f_cd", 240);
    }
}
