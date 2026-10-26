package com.mki.nightsiege.core;

import static com.mki.nightsiege.core.SkillValues.def;

/** Named skill numbers of the Beschwörer ({@link SkillValues}): read by the handlers, shown in the texts. */
final class SkillValuesSummoner {

    private SkillValuesSummoner() {
    }

    static void register() {
        // ----- Trunk
        def("s_callrange", "base", 14.0);         // blocks: fight radius without the skill

        // ----- Way of the wild
        def("s_packcall", "pause", 400);          // ticks: pause of a wolf call on level 1 (−amount s per further level)
        def("s_packcall", "slots", 2);            // places for wolves and golems
        def("s_packcall", "startwolves", 2);      // wolves at the start of a night
        def("s_golemsmith", "pause", 1200);       // ticks: pause of a golem call with Rudelruf 1
        def("s_golemsmith", "step", 180);         // ticks: shorter for every further level of Rudelruf
        def("s_alpha", "health", 2.0);            // factor
        def("s_alpha", "damage", 2.0);            // factor
        def("s_alpha", "size", 1.35);
        def("s_snowguard", "slow", 50);           // ticks of Slowness II per snowball
        def("s_howl", "every", 400);              // ticks
        def("s_howl", "length", 100);             // ticks
        def("l_cross_bones", "health", 30.0);
        def("l_cross_bones", "pause", 600);       // ticks after the skeleton fell
        def("s_packleader", "every", 400);        // ticks
        def("s_packleader", "radius", 8.0);       // blocks
        def("s_golemwork", "push", 0.9);
        def("s_golemwork", "lift", 0.35);
        def("s_wildness", "below", 0.5);          // share of health
        def("s_care", "every", 100);              // ticks
        def("l_cross_harvest", "heal", 0.2);      // share of the companion's health
        def("s_wild_hunt", "count1", 6);
        def("s_wild_hunt", "count2", 7);
        def("s_wild_hunt", "count3", 8);
        def("s_wild_hunt", "dmg1", 7.0);          // bite before the skills and the night
        def("s_wild_hunt", "dmg2", 8.0);
        def("s_wild_hunt", "dmg3", 9.0);
        def("s_wild_hunt", "duration", 200);      // ticks
        def("s_wild_hunt", "speed", 0.3);         // share faster
        def("s_titan", "health", 300.0);
        def("s_titan", "duration", 300);          // ticks
        def("s_titan", "firstslam", 40);          // ticks
        def("s_titan", "slam", 60);               // ticks between slams
        def("s_titan", "radius", 4.5);            // blocks
        def("s_titan", "dmg1", 9.0);              // slam before the skills and the night
        def("s_titan", "dmg2", 12.0);
        def("s_titan", "dmg3", 15.0);
        def("s_titan", "push", 0.5);
        def("s_titan", "lift", 0.4);
        def("s_moonpack", "heal", 1.0);           // health per bite
        def("s_titanfist", "push", 1.6);
        def("s_titanfist", "lift", 0.9);
        def("s_orecore", "heal", 1.0);            // share healed when no place is free
        def("s_beastlord", "cost", 6.0);          // health the keystone takes (read in SkillService)
        def("s_shepherd", "slots", 1);            // places for wolves and golems
        def("s_shepherd", "health", 2.0);         // factor (not on top of the alpha's)
        def("s_shepherd", "damage", 2.0);

        // ----- Way of the dead
        def("d_raise", "base", 30.0);             // seconds a raised monster fights before the levels
        def("d_raise", "souls", 3);               // cost of a raising
        def("d_raise", "start", 3);               // souls at the start of a night
        def("d_raise", "cap", 10);                // most souls
        def("d_raise", "range", 16.0);            // blocks for souls and corpses
        def("d_raise", "slots", 2);
        def("d_raise", "minhealth", 30.0);
        def("d_raise", "startghosts", 2);
        def("d_raise", "startlife", 1200);        // ticks the start ghosts fly
        def("d_necro", "slotlevel", 3);           // level that gives one place more
        def("d_soulcatcher", "life", 300);        // ticks a ghost stays
        def("d_soulcatcher", "health", 30.0);
        def("d_soulcatcher", "strength", 0.6);    // share of tonight's monster damage
        def("d_archers", "range", 8.0);           // blocks further for raised archers
        def("d_swarm", "ghosts", 2);
        def("d_soulshield", "souls", 5);
        def("d_soulshield", "duration", 600);     // ticks
        def("d_rot", "duration", 60);             // ticks of Weakness
        def("d_corpse", "share", 0.5);            // share of the servant's health as damage
        def("d_corpse", "radius", 3.5);           // blocks
        def("d_cross_wolf", "pause", 1200);       // ticks after the wolf fell
        def("d_unholy", "max", 6);                // most servants that count
        def("d_army", "duration", 200);           // ticks
        def("d_army", "radius", 20.0);            // blocks
        def("d_army", "max", 24);                 // most raised at once
        def("d_army", "str1", 0.9);               // strength by rank
        def("d_army", "str2", 1.0);
        def("d_army", "str3", 1.1);
        def("d_soulstorm", "dmg1", 2.0);          // damage per soul by rank
        def("d_soulstorm", "dmg2", 3.0);
        def("d_soulstorm", "dmg3", 4.0);
        def("d_soulstorm", "radius", 10.0);       // blocks
        def("d_echo", "delay", 20);               // ticks
        def("d_army", "age", 600);                // ticks a fallen monster can still rise
        def("d_lich", "cost", 8.0);               // health the keystone takes (read in SkillService)
        def("d_soulkeeper", "cap", 20);           // most souls
        def("d_champion", "every", 4);
        def("d_champion", "health", 2.0);
        def("d_champion", "damage", 2.0);
        def("d_champion", "size", 1.3);
    }
}
