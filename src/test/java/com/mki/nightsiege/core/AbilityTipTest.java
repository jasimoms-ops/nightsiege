package com.mki.nightsiege.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mki.nightsiege.client.AbilityTipText;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ability tooltips in the inventory (Simon 08.11.): for all 20 subclasses and every ultimate of
 * their way, Q, F and X each get a tooltip with name, key, text, cooldown and the learned skills that
 * change them, in German and English, without an open placeholder or a missing text.
 */
class AbilityTipTest {

    /** The skills that change F (remote, staff); learned in the test so their lines show too. */
    private static final Set<String> F_MODS = Set.of("e_longrange", "e_seeker", "e_circuit", "e_overvolt",
            "e_fortress", "e_fieldtech", "e_detonator", "s_callrange");

    private static final Map<String, String> SECOND = Map.of("WARRIOR", "schlachtentrance", "ARCHER", "lockpfeil",
            "GUARDIAN", "spiegelschild", "ENGINEER", "magnetfeld", "SUMMONER", "seelentausch");

    private static Map<String, String> lang(String name) throws Exception {
        JsonObject o = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/nightsiege/lang/" + name + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> out = new HashMap<>();
        o.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
        return out;
    }

    @Test
    void everySubclassHasFullTooltipsInBothLanguages() throws Exception {
        SkillTree tree = new SkillTree();
        Map<String, Map<String, String>> langs = Map.of("de_de", lang("de_de"), "en_us", lang("en_us"));
        Set<String> subNodes = new java.util.HashSet<>();
        SkillTree.SUBCLASSES.forEach(i -> subNodes.add(i.node()));
        int subclasses = 0;
        int tips = 0;
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            List<SkillNode> ults = new ArrayList<>();
            for (SkillNode n : tree.all()) {
                if (n.clazz() == info.clazz() && n.effect().target() == SkillEffect.Target.ULTIMATE && info.way().equals(n.path())) {
                    ults.add(n);
                }
            }
            assertFalse(ults.isEmpty(), info.key() + " has no ultimate");
            subclasses++;
            for (SkillNode ult : ults) {
                PlayerSkills p = new PlayerSkills();
                p.chosenClass = info.clazz().name();
                p.setLevel(info.node(), 1);
                p.setLevel(ult.id(), 2);
                for (SkillNode n : tree.all()) {
                    boolean mastery = n.requires() != null && n.requires().contains(ult.id());
                    if (n.clazz() == info.clazz() && !subNodes.contains(n.id()) && n.effect().target() != SkillEffect.Target.ULTIMATE
                            && (mastery || F_MODS.contains(n.id()))) {
                        p.setLevel(n.id(), 1);
                    }
                }
                assertEquals(info.key(), tree.subclassOf(p));
                String f = AbilityTips.fName(tree, p);
                List<AbilityTip> list = AbilityTips.build(tree, p, true, f, SECOND.get(info.clazz().name()), 1800);
                assertEquals(f == null ? 2 : 3, list.stream().filter(t -> t.key.isEmpty()).count(), info.key() + " " + ult.id());
                for (AbilityTip tip : list) {
                    tips++;
                    for (Map.Entry<String, Map<String, String>> l : langs.entrySet()) {
                        Map<String, String> m = l.getValue();
                        String where = l.getKey() + " " + info.key() + " " + ult.id() + " " + tip.slot;
                        assertNotNull(m.get(tip.main.name), where + " name " + tip.main.name);
                        assertNotNull(m.get(tip.main.desc), where + " text " + tip.main.desc);
                        for (AbilityTip.Part mod : tip.mods) {
                            assertNotNull(m.get(mod.desc), where + " mod " + mod.desc);
                        }
                        List<String> lines = AbilityTipText.lines(tip, "Q", m::get);
                        assertTrue(lines.get(0).contains(m.get(tip.main.name)), where + " name line");
                        assertTrue(lines.get(1).contains("Q"), where + " key line");
                        String cdPrefix = m.get(AbilityTips.KEY + "cooldown").replace("%s", "");
                        String noCd = m.get(AbilityTips.KEY + "cooldown_none");
                        assertTrue(lines.stream().anyMatch(s -> s.contains(cdPrefix) || s.contains(noCd)), where + " cooldown");
                        if (tip.slot.equals("q") || tip.slot.equals("x")) {
                            assertTrue(tip.cooldownTicks > 0, where + " cooldown ticks");
                        }
                        for (String line : lines) {
                            assertFalse(line.contains("{") || line.contains("}"), where + " open placeholder: " + line);
                            assertFalse(line.contains("nightsiege."), where + " missing text: " + line);
                            assertFalse(line.contains("%s") || line.contains("%1$s"), where + " open argument: " + line);
                        }
                    }
                }
            }
        }
        assertEquals(20, subclasses);
        assertTrue(tips >= 60, "tips " + tips);
    }

    /** Tor 3.4: every ability with a key in AbilityKeys, for all 20 subclasses: name, key, cooldown, no open placeholder. */
    @Test
    void everyAbilityOnAKeyHasATooltip() throws Exception {
        SkillTree tree = new SkillTree();
        Map<String, Map<String, String>> langs = Map.of("de_de", lang("de_de"), "en_us", lang("en_us"));
        Set<String> subNodes = new java.util.HashSet<>();
        SkillTree.SUBCLASSES.forEach(i -> subNodes.add(i.node()));
        int subclasses = 0;
        int abilities = 0;
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            PlayerSkills p = new PlayerSkills();
            p.chosenClass = info.clazz().name();
            for (SkillNode n : tree.all()) {   // everything of the class but the other subclasses, so the skill lines show too
                if (n.clazz() == info.clazz() && (!subNodes.contains(n.id()) || n.id().equals(info.node()))) {
                    p.setLevel(n.id(), 1);
                }
            }
            assertEquals(info.key(), tree.subclassOf(p));
            List<AbilityKeys.Entry> entries = AbilityKeys.table().get(info.key());
            assertNotNull(entries, info.key() + " has no keys");
            subclasses++;
            List<AbilityTip> tips = AbilityTips.barTips(tree, p);
            for (AbilityKeys.Entry e : entries) {
                if (AbilityKeys.F_ACTION.equals(e.id())) {
                    continue;   // the F field: the test above
                }
                abilities++;
                AbilityTip tip = tips.stream().filter(t -> t.slot.equals(e.id())).findFirst().orElse(null);
                assertNotNull(tip, info.key() + " " + e.id() + " has no tooltip");
                assertEquals(e.key().name(), tip.key, info.key() + " " + e.id() + " key");
                for (Map.Entry<String, Map<String, String>> l : langs.entrySet()) {
                    Map<String, String> m = l.getValue();
                    String where = l.getKey() + " " + info.key() + " " + e.id();
                    assertNotNull(m.get(tip.main.name), where + " name " + tip.main.name);
                    assertNotNull(m.get(tip.main.desc), where + " text " + tip.main.desc);
                    List<String> lines = AbilityTipText.lines(tip, tip.key, m::get);
                    assertTrue(lines.get(0).contains(m.get(tip.main.name)), where + " name line");
                    assertTrue(lines.get(1).contains(tip.key), where + " key line");
                    String cdPrefix = m.get(AbilityTips.KEY + "cooldown").replace("%s", "");
                    String noCd = m.get(AbilityTips.KEY + "cooldown_none");
                    assertTrue(lines.stream().anyMatch(s -> s.contains(cdPrefix) || s.contains(noCd)), where + " cooldown");
                    for (String line : lines) {
                        assertFalse(line.contains("{") || line.contains("}"), where + " open placeholder: " + line);
                        assertFalse(line.contains("nightsiege."), where + " missing text: " + line);
                        assertFalse(line.contains("%s") || line.contains("%1$s"), where + " open argument: " + line);
                    }
                }
            }
        }
        assertEquals(20, subclasses);
        assertTrue(abilities >= 40, "abilities " + abilities);
    }

    @Test
    void theUltTooltipShowsTheRankAndTheMasteries() throws Exception {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        p.setLevel("w_combo", 1);
        p.setLevel("whirlwind", 3);
        p.setLevel("w_bladestorm", 1);
        AbilityTip q = AbilityTips.build(tree, p, true, null, null, 0).get(0);
        assertEquals(AbilityTips.ultCooldown(tree.get("whirlwind"), 3), q.cooldownTicks);
        assertEquals("nightsiege.skill.w_bladestorm", q.mods.get(0).name);
        List<String> en = AbilityTipText.lines(q, "Q", lang("en_us")::get);
        assertTrue(en.get(1).contains("3/3"), en.get(1));
        assertTrue(en.stream().anyMatch(s -> s.contains("§f§n")), "own rank marked");
    }

    /** Tor 3: the key comes from the binding; a gain (no key) gets no key line, the rest stays. */
    @Test
    void theKeyLineFollowsTheBinding() throws Exception {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        p.setLevel("w_combo", 1);
        p.setLevel("whirlwind", 3);
        AbilityTip q = AbilityTips.build(tree, p, true, null, null, 0).get(0);
        List<String> bound = AbilityTipText.lines(q, "Mouse 4", lang("en_us")::get);
        assertTrue(bound.get(1).contains("Mouse 4") && bound.get(1).contains("3/3"), bound.get(1));
        List<String> none = AbilityTipText.lines(q, null, lang("de_de")::get);
        assertTrue(none.stream().noneMatch(s -> s.contains("Taste")), none.toString());
        assertTrue(none.get(1).contains("3/3"), none.get(1));
        assertEquals(bound.size(), none.size());
    }
}
