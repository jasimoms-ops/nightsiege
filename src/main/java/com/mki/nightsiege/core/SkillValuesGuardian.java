package com.mki.nightsiege.core;

import static com.mki.nightsiege.core.SkillValues.def;

/** Named skill numbers of the Wächter ({@link SkillValues}): read by the handlers, shown in the texts. */
final class SkillValuesGuardian {

    private SkillValuesGuardian() {
    }

    static void register() {
        // ----- trunk
        def("g_challenger", "radius", 6.0);        // aggro radius of every guardian, blocks
        def("g_challenger", "chance", 0.6);        // chance to turn a monster without the node
        def("g_challenger", "bulwark", 4.0);       // the bulwark's extra aggro radius, blocks; it turns them for sure (30.10.)

        // ----- way of the shield
        def("g_ironward", "toughness", 1.0);       // armour toughness per level, only with a shield at hand
        def("g_shieldwall", "absorb", 0.5);        // absorption per level and blocked hit (health)
        def("g_shieldwall", "cap", 4.0);           // at most this much absorption from the wall (health)
        def("g_shieldwall", "block", 10.0);        // ticks of extra shield energy per level (Simon 26.10.: was riposte only)
        // The riposte's block limit (3 s, 2 s rest) gave way to the shield energy of every shield (Simon 26.10.).
        def("riposte", "perfect", 2.0);            // perfect blocks throw back this many times as much
        def("b_cross_riposte", "perfect", 2.0);
        def("g_bastion", "count", 4.0);            // monsters counted at most
        def("g_bastion", "radius", 8.0);
        def("g_bastion", "group", 0.75);           // Simon 26.10.: the bulwark's damage on groups (hedgehog, vengeance, charge, debris)
        def("b_cross_bastion", "count", 4.0);
        def("b_cross_bastion", "radius", 8.0);
        def("g_shieldthrow", "targets", 3.0);
        def("g_shieldthrow", "range", 14.0);       // reach to the first monster
        def("g_shieldthrow", "chain", 6.0);        // reach from monster to monster
        def("g_shieldthrow", "cd", 160.0);         // ticks
        def("g_shieldthrow", "bonus", 0.05);       // shield fighter: more damage per monster close by
        def("g_shieldthrow", "near", 4.0);         // ... within this many blocks
        def("g_shieldthrow", "bonuscap", 0.3);     // ... at most
        def("g_shieldbash", "range", 4.0);
        def("g_shieldbash", "slow", 40.0);         // ticks of Slowness III
        def("g_ironhide", "interval", 100.0);      // ticks
        def("g_ironhide", "radius", 5.0);
        def("g_ironhide", "count", 4.0);
        def("g_ironhide", "cap", 8.0);             // health
        def("g_counterstrike", "window", 60.0);    // ticks after the perfect block
        def("s_cross_dressing", "interval", 60.0); // ticks between two pulses of the heal aura
        def("s_cross_dressing", "radius", 8.0);
        def("g_boomerang", "targets", 4.0);
        def("g_vengeance", "radius", 3.5);
        def("g_hedgehog", "mult", 3.0);            // thorns times this much
        def("g_hedgehog", "dmg", 4.0);             // thorn damage when the thorns node is missing
        def("g_hedgehog", "radius", 2.5);
        def("g_laststand", "below", 0.3);          // share of health
        def("g_laststand", "time", 80.0);          // ticks of Resistance II
        def("g_laststand", "cd", 600.0);           // ticks
        def("g_revenge", "cap", 8.0);
        def("s_cross_ward", "base", 0.0);          // barrier = base + amount (health); the amount holds all 4
        def("s_cross_ward", "interval", 160.0);
        def("s_cross_ward", "radius", 12.0);
        // ultimates (one value per rank)
        def("shield_charge", "dmg1", 6.0);
        def("shield_charge", "dmg2", 9.0);
        def("shield_charge", "dmg3", 12.0);
        def("shield_charge", "range", 6.0);
        def("shield_charge", "slow", 40.0);        // ticks of Slowness III
        def("taunt", "radius1", 10.0);
        def("taunt", "radius2", 13.0);
        def("taunt", "radius3", 16.0);
        def("taunt", "res1", 80.0);                // ticks of Resistance II for the guardian
        def("taunt", "res2", 100.0);
        def("taunt", "res3", 120.0);
        def("taunt", "absorb1", 8.0);              // absorption for everyone in the radius (health)
        def("taunt", "absorb2", 12.0);
        def("taunt", "absorb3", 16.0);
        def("taunt", "absorbtime", 200.0);         // ticks
        def("g_debris", "hits", 3.0);
        def("g_debris", "dmg", 4.0);
        def("g_debris", "radius", 2.0);
        def("g_rampart", "slow", 40.0);            // ticks of Slowness X
        def("g_iron_will", "count", 4.0);          // the bastion counts this many attackers
        def("g_shared_burden", "res", 200.0);      // ticks of Resistance I for teammates
        def("g_shared_burden", "barrier", 4.0);    // health
        def("g_living_wall", "hp", 12.0);
        def("g_bladeshield", "mult", 2.0);
        def("g_bladeshield", "hp", 8.0);

        // ----- way of blessing
        def("field_dressing", "interval", 60.0);
        def("field_dressing", "radius", 8.0);
        def("g_wardsign", "radius", 6.0);
        def("g_healing_hand", "base", 2.0);        // heal = base + amount per level (health)
        def("g_healing_hand", "cd", 120.0);        // ticks
        def("g_healing_hand", "self", 0.5);        // share when healing yourself
        def("g_healing_hand", "range", 16.0);      // shield action: teammate in view within this reach
        def("g_ward", "base", 2.0);                // barrier = base + amount per level (health)
        def("g_ward", "interval", 160.0);
        def("g_ward", "radius", 12.0);
        def("guardian_angel", "reach", 24.0);
        def("guardian_angel", "hp", 8.0);
        def("guardian_angel", "regen", 100.0);     // ticks of Regeneration II
        def("guardian_angel", "res", 60.0);        // ticks of Resistance II
        def("g_triage", "below", 0.4);
        def("g_triage", "mult", 2.0);
        def("g_dome", "radius", 4.0);
        def("g_dome", "time", 100.0);              // ticks
        def("g_dome", "cd", 500.0);                // ticks
        def("g_wellspring", "heal", 1.5);          // health per second
        def("g_grace", "time", 60.0);              // ticks of Speed I
        def("mass_heal", "heal1", 10.0);
        def("mass_heal", "heal2", 14.0);
        def("mass_heal", "heal3", 18.0);
        def("mass_heal", "radius", 12.0);
        def("mass_heal", "regen", 100.0);          // ticks of Regeneration II
        def("g_sanctuary", "radius", 8.0);
        def("g_sanctuary", "time1", 120.0);        // ticks
        def("g_sanctuary", "time2", 140.0);
        def("g_sanctuary", "time3", 160.0);
        def("g_sanctuary", "factor", 0.4);         // damage taken inside
        def("g_lifespring", "radius", 6.0);
        def("g_lifespring", "time", 120.0);
        def("g_lifespring", "heal", 1.5);          // health per second
        def("g_lightcircle", "heal", 1.5);         // health per second
        def("g_lightcircle", "burn", 3.0);         // seconds of fire
        def("g_lightcircle", "undead", 3.0);       // damage per second to the undead
        def("g_eternal_watch", "time", 60.0);      // extra ticks of the sanctuary
        def("g_eternal_watch", "barrier", 4.0);    // health
        def("g_saint", "dmg", 0.7);                // own damage against monsters
        def("g_patron", "hp", 4.0);
    }
}
