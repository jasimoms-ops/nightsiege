package com.mki.nightsiege.core;

import static com.mki.nightsiege.core.SkillValues.def;

/** Named skill numbers of the Beschwörer ({@link SkillValues}): read by the handlers, shown in the texts. */
final class SkillValuesSummoner {

    private SkillValuesSummoner() {
    }

    static void register() {
        // ----- Trunk: the shared mind of every companion (Simon 26.10.)
        def("s_soulbond", "leash", 10.0);          // blocks: companions stay and fight this close to their summoner
        def("s_soulbond", "buffer", 3.0);          // blocks more for monsters that attack a player
        def("s_soulbond", "teleport", 16.0);       // blocks: further away, a companion is beside its summoner at once
        def("s_soulbond", "stuck", 60);            // ticks without getting anywhere: back beside the summoner
        def("s_soulbond", "tauntpause", 40);       // ticks a monster turns on no companion after a taunt ended
        def("s_callrange", "base", 16.0);          // blocks: reach of the staff (attack, order, spells)
        def("s_callrange", "order", 16.0);         // blocks more for the order (attack / hold)
        def("s_callrange", "attacktime", 200);     // ticks the ordered target stays the target (or until it falls)
        def("s_callrange", "holdtime", 400);       // ticks the companions hold the ordered spot

        // ----- Way of the light (wild): companions and the call strike
        def("s_packcall", "pause", 400);           // ticks: pause of a wolf call on level 1 (−amount s per further level)
        def("s_packcall", "slots", 2);             // places for wolves before the tamer
        def("s_packcall", "startwolves", 2);       // wolves at the start of a night
        def("s_packcall", "health", 40.0);         // a wolf before the skills and the night
        def("s_packcall", "damage", 3.0);
        def("s_packcall", "strike", 5.0);          // Ruf-Hieb: damage before the night
        def("s_packcall", "strikepause", 20);      // ticks
        def("s_packcall", "strikeboost", 0.2);     // share more damage of the companions after a hit
        def("s_packcall", "strikeboosttime", 80);  // ticks
        def("s_tamer", "max", 4);                  // most wolves, with every skill
        def("s_golemsmith", "pause", 1200);        // ticks: pause of a golem call with Rudelruf 1
        def("s_golemsmith", "step", 180);          // ticks: shorter for every further level of Rudelruf
        def("s_golemsmith", "health", 60.0);       // the golem before the skills and the night (vanilla 100)
        def("s_golemsmith", "damage", 9.0);        // vanilla 15
        def("s_golemsmith", "tauntradius", 4.0);   // blocks: the golem's mockery on order
        def("s_golemsmith", "taunttime", 60);      // ticks
        def("s_golemsmith", "slam", 6.0);          // ground slam on order, before the skills and the night
        def("s_golemsmith", "slamradius", 3.5);    // blocks
        def("s_golemsmith", "slamreach", 3.5);     // blocks to the ordered target before it slams
        def("s_golemsmith", "slamevery", 200);     // ticks
        def("s_alpha", "health", 2.0);             // factor
        def("s_alpha", "damage", 2.0);             // factor
        def("s_alpha", "size", 1.35);
        def("s_snowguard", "slow", 50);            // ticks of Slowness II per snowball
        def("s_howl", "every", 400);               // ticks
        def("s_howl", "length", 100);              // ticks
        def("l_cross_bones", "health", 30.0);
        def("l_cross_bones", "pause", 600);        // ticks after the skeleton fell
        def("s_packleader", "every", 400);         // ticks
        def("s_packleader", "radius", 8.0);        // blocks
        def("s_golemwork", "push", 0.9);
        def("s_golemwork", "lift", 0.35);
        def("s_wildness", "below", 0.5);           // share of health
        def("s_care", "every", 100);               // ticks
        def("l_cross_harvest", "heal", 0.2);       // share of the companion's health
        def("s_wild_hunt", "count1", 4);
        def("s_wild_hunt", "count2", 5);
        def("s_wild_hunt", "count3", 6);
        def("s_wild_hunt", "dmg1", 7.0);           // bite before the skills and the night
        def("s_wild_hunt", "dmg2", 8.0);
        def("s_wild_hunt", "dmg3", 9.0);
        def("s_wild_hunt", "duration", 200);       // ticks
        def("s_wild_hunt", "speed", 0.3);          // share faster
        def("s_titan", "health", 300.0);
        def("s_titan", "duration", 300);           // ticks
        def("s_titan", "firstslam", 40);           // ticks
        def("s_titan", "slam", 60);                // ticks between slams
        def("s_titan", "radius", 4.5);             // blocks
        def("s_titan", "dmg1", 9.0);               // slam before the skills and the night
        def("s_titan", "dmg2", 12.0);
        def("s_titan", "dmg3", 15.0);
        def("s_titan", "push", 0.5);
        def("s_titan", "lift", 0.4);
        def("s_moonpack", "heal", 1.0);            // health per bite
        def("s_titanfist", "push", 1.6);
        def("s_titanfist", "lift", 0.9);
        def("s_orecore", "heal", 1.0);             // share healed when the golem stands
        def("s_beastlord", "cost", 6.0);           // health the keystone takes (read in SkillService)
        def("s_shepherd", "slots", 1);             // places for wolves and golems
        def("s_shepherd", "health", 2.0);          // factor (not on top of the alpha's)
        def("s_shepherd", "damage", 2.0);

        // ----- Way of the dead: soul magic (Simon 26.10.)
        def("d_raise", "bolt", 5.0);               // Seelengeschoss: damage before the levels and the night
        def("d_raise", "boltpause", 20);           // ticks
        def("d_raise", "boltspeed", 1.3);          // blocks per tick
        def("d_raise", "spellpause", 20);          // ticks between two spells
        def("d_raise", "spellrange", 8.0);         // blocks more than the staff for the spells' target
        def("d_raise", "start", 3);                // souls at the start of a night
        def("d_raise", "cap", 10);                 // most souls
        def("d_raise", "range", 16.0);             // blocks for souls and corpses
        def("d_raise", "slots", 2);                // raised corpses at once (the army apart)
        def("d_raise", "minhealth", 30.0);
        def("d_raise", "power", 1.5);              // damage factor of the raised dead
        def("d_raise", "splash", 0.3);             // share of their melee hit on monsters around the target
        def("d_raise", "splashradius", 2.0);       // blocks around the target
        def("d_necro", "raisecost", 2);            // souls: Leiche erwecken
        def("d_necro", "raiselife", 160);          // ticks the corpse fights
        def("d_necro", "burst", 6.0);              // damage of its burst before the skills and the night
        def("d_necro", "burstradius", 3.0);        // blocks
        def("d_necro", "wallcost", 2);             // souls: Knochenwall
        def("d_necro", "walllife", 120);           // ticks
        def("d_necro", "wallwidth", 5);            // blocks
        def("d_necro", "wallheight", 2);           // blocks
        def("d_necro", "walldistance", 3.0);       // blocks in front of the summoner
        def("d_necro", "cursecost", 3);            // souls: Fluchzone
        def("d_necro", "curselife", 120);          // ticks
        def("d_necro", "curseradius", 4.0);        // blocks
        def("d_necro", "curse", 0.25);             // share more damage taken in the zone
        def("d_necro", "curseslow", 0);            // Slowness amplifier in the zone (0 = Slowness I)
        def("d_soulcatcher", "swarmcost", 2);      // souls: Geisterschwarm
        def("d_soulcatcher", "ghosts", 3);
        def("d_soulcatcher", "ghost", 5.0);        // damage per ghost before the skills and the night
        def("d_soulcatcher", "ghostspeed", 0.7);   // blocks per tick
        def("d_soulcatcher", "spread", 6.0);       // blocks: the neighbours of the target the ghosts go for
        def("d_soulcatcher", "shieldcost", 2);     // souls: Seelenschild
        def("d_soulcatcher", "shield", 8.0);       // health of the barrier (4 hearts)
        def("d_soulcatcher", "shieldrange", 16.0); // blocks to the teammate looked at
        def("d_soulcatcher", "chaincost", 3);      // souls: Seelenkette
        def("d_soulcatcher", "chainradius", 5.0);  // blocks around the target
        def("d_soulcatcher", "chainlife", 80);     // ticks
        def("d_soulcatcher", "chainslow", 3);      // Slowness amplifier (3 = Slowness IV)
        def("d_archers", "range", 6.0);            // blocks to the second target of the bolt
        def("d_rot", "duration", 60);              // ticks of Weakness
        def("d_corpse", "radius", 1.0);            // blocks more for the corpse's burst
        def("d_corpse", "ghostradius", 2.5);       // blocks of a ghost's splash
        def("d_corpse", "ghostshare", 0.5);        // share of the amount a ghost's splash deals
        def("d_cross_wolf", "pause", 1200);        // ticks after the wolf fell
        def("d_unholy", "max", 10);                // most souls that count
        def("d_army", "duration", 160);            // ticks
        def("d_army", "radius", 20.0);             // blocks
        def("d_army", "max", 4);                   // most raised at once
        def("d_army", "str1", 0.9);                // strength by rank
        def("d_army", "str2", 1.0);
        def("d_army", "str3", 1.1);
        def("d_soulstorm", "dmg1", 2.0);           // damage per soul by rank
        def("d_soulstorm", "dmg2", 3.0);
        def("d_soulstorm", "dmg3", 4.0);
        def("d_soulstorm", "radius", 10.0);        // blocks
        def("d_echo", "delay", 20);                // ticks
        def("d_army", "age", 600);                 // ticks a fallen monster can still rise
        def("d_lich", "cost", 8.0);                // health the keystone takes (read in SkillService)
        def("d_soulkeeper", "cap", 20);            // most souls
        def("d_champion", "every", 4);
        def("d_champion", "health", 2.0);
        def("d_champion", "damage", 2.0);
        def("d_champion", "size", 1.3);
    }
}
