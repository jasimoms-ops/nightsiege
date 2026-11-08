package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tor 4.7 ("Zauberstab für Heiler", Simon: "Geschoss auf Distanz"): the medic and the warden carry the
 * healers' staff instead of the sword; a bolt deals a full kit sword hit, 1.33 a second, up to 16 blocks.
 */
class HeilerstabTest {

    @Test
    void bothHealersCarryTheStaffInsteadOfTheSword() {
        for (String node : new String[]{"g_healing_hand", "g_ward"}) {
            SkillTree.SubclassInfo info = SkillTree.subclassByNode(node);
            assertNotNull(info, node);
            assertTrue(NightKit.healerStaff(info.clazz(), info.way()), node + ": the kit gives the staff");
            KitWeapons.Look look = KitWeapons.of(info.key());
            assertEquals(KitWeapons.Kind.HEALER_STAFF, look.kind(), node + ": its look goes on the staff, not a sword");
        }
        assertEquals(SkillTree.SUB_MEDIC, SkillTree.subclassByNode("g_healing_hand").key());
        assertEquals(SkillTree.SUB_WARDEN, SkillTree.subclassByNode("g_ward").key());
        assertNotEquals(KitWeapons.of(SkillTree.SUB_MEDIC).key(), KitWeapons.of(SkillTree.SUB_WARDEN).key());
        // The tanks keep the mace, nobody else gets the staff.
        assertFalse(NightKit.healerStaff(SkillClass.GUARDIAN, SkillTree.WAY_SHIELD));
        assertFalse(NightKit.healerStaff(SkillClass.GUARDIAN, ""));
        for (SkillClass c : SkillClass.values()) {
            if (c != SkillClass.GUARDIAN) {
                assertFalse(NightKit.healerStaff(c, SkillTree.WAY_BLESSING), c.name());
            }
        }
    }

    @Test
    void theBoltIsAKitSwordHitAndTheStaffStaysAHealersWeapon() {
        for (int level : new int[]{1, 6, 11, 14, 20}) {
            int sharp = NightKit.sharpness(level);
            assertEquals(NightKit.swordHit(level, sharp), NightKit.healerBolt(level, sharp), 1e-9, "level " + level);
            assertTrue(NightKit.healerBolt(level, sharp) > NightKit.healerBolt(1, NightKit.sharpness(1)) - 1e-9);
        }
        // Damage a second at full use: 0.83 of the sword's, so that with the easier use at range it ends near
        // the sword's in play and at most 1.3 times (healers must not become a damage class).
        double share = NightKit.healerStaffShare();
        assertEquals(0.8333, share, 1e-3);
        assertTrue(share >= 0.75 && share <= 1.0, "share " + share);
        assertTrue(1.3 / share >= 1.5, "the staff needs 50 % more use than the sword to reach +30 %");
        assertEquals(15, NightKit.HEALER_BOLT_PAUSE);
        assertEquals(16.0, NightKit.HEALER_BOLT_RANGE, 1e-9);
        assertTrue(NightKit.HEALER_BOLT_RANGE >= 8.0, "a bolt on distance (scene: target at 8+ blocks)");
        // The bolt reaches its farthest target well within its life (60 ticks).
        assertTrue(NightKit.HEALER_BOLT_RANGE / NightKit.HEALER_BOLT_SPEED < 60);
    }

    @Test
    void namesAndTooltipInBothLanguages() throws Exception {
        for (String lang : new String[]{"de_de", "en_us"}) {
            String text;
            try (InputStream in = getClass().getResourceAsStream("/assets/nightsiege/lang/" + lang + ".json")) {
                assertNotNull(in, lang);
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String key : new String[]{"item.nightsiege.healer_staff", "nightsiege.keyhint.healer_staff",
                    "nightsiege.tooltip.healer_staff", KitWeapons.of(SkillTree.SUB_MEDIC).nameKey(),
                    KitWeapons.of(SkillTree.SUB_WARDEN).nameKey()}) {
                assertTrue(text.contains("\"" + key + "\""), lang + ": " + key);
            }
        }
        assertNotNull(getClass().getResource("/assets/nightsiege/textures/item/healer_staff.png"));
        assertNotNull(getClass().getResource("/assets/nightsiege/items/healer_staff.json"));
    }
}
