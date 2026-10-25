package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Kits for playtests: armour, a weapon for the class and food that fit the
 * level of a test night, and the skill points a defender would have earned by
 * then. Up to level 5 iron and diamond mixed with one or two enchantments,
 * from 6 diamond, above 10 netherite, all without enchantment glint. Weapons stay
 * unenchanted.
 */
public final class TestKit {

    private static final String[] SLOTS = {"head", "chest", "legs", "feet"};
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

    private TestKit() {
    }

    /** Points a defender would have earned in all nights before this one. */
    public static int pointsBefore(int level, SiegeConfig cfg) {
        int total = 0;
        int interval = Math.max(20, cfg.batchIntervalSeconds * 20) + 1;
        double waves = 0;
        for (int night = 1; night < level; night++) {
            int ticks = SiegeMath.durationSeconds(night, cfg) * 20;
            waves += Math.max(1, (ticks - 1) / interval + 1);
        }
        // The same sums as the nights: full sets of waves, each worth the point factor.
        total = (int) Math.floor(Math.floor(waves / Math.max(1, cfg.wavesPerPoint)) * cfg.pointGainMultiplier + 1e-9);
        return total;
    }

    /** Gives one player the kit and tops up their points. Returns the points added. */
    public static int give(MinecraftServer server, ServerPlayer player, int level, PlayerSkills skills, SkillTree tree,
                           SiegeConfig cfg, Random random, boolean nightStarts) {
        String name = player.getScoreboardName();
        List<String> commands = new ArrayList<>();
        if (cfg.nightKits) {
            // With night kits the night brings armour, weapons and food itself; the test kit
            // only gives the skill points (and food for the day, not when the night starts).
            // Before, its armour and weapons went into the own inventory and came back after
            // the night, so everybody had a second kit (Simon's playtest, 25 October 2026).
            if (!nightStarts) {
                String food = level > 10 ? "minecraft:golden_carrot" : level >= 6 ? "minecraft:cooked_beef" : "minecraft:bread";
                if (!has(player, food)) {
                    commands.add("give " + name + " " + food + " 32");
                }
            }
            run(server, commands);
            int target = pointsBefore(level, cfg);
            int add = Math.max(0, target - skills.earned);
            if (add > 0) {
                skills.award(add);
            }
            player.sendSystemMessage(Component.translatable(nightStarts ? "nightsiege.kit.points_night" : "nightsiege.kit.points",
                    level, target, skills.points()).withStyle(net.minecraft.ChatFormatting.AQUA));
            return add;
        }
        String tier = level > 10 ? "netherite" : level >= 6 ? "diamond" : null;
        int diamonds = Math.min(4, Math.max(0, level - 1));
        for (int i = 0; i < 4; i++) {
            String material = tier != null ? tier : (i < diamonds ? "diamond" : "iron");
            if (tier == null && i >= diamonds && level <= 1) {
                material = "iron";
            }
            commands.add("item replace entity " + name + " armor." + SLOTS[i] + " with minecraft:" + material + "_"
                    + PIECES[i] + enchantments(i, level, random));
        }
        String weapon = level > 10 ? "netherite" : level >= 6 ? "diamond" : "iron";
        // Weapons only when the night starts: by then everyone has chosen class and way.
        if (nightStarts) {
            for (String item : weapons(skills, tree, weapon)) {
                String id = item.split(" ")[0];
                if (!has(player, id)) {
                    commands.add("give " + name + " " + item);
                }
            }
        }
        String food = level > 10 ? "minecraft:golden_carrot" : level >= 6 ? "minecraft:cooked_beef" : "minecraft:bread";
        if (!has(player, food)) {
            commands.add("give " + name + " " + food + " 32");
        }
        run(server, commands);
        int target = pointsBefore(level, cfg);
        int add = Math.max(0, target - skills.earned);
        if (add > 0) {
            skills.award(add);
        }
        player.sendSystemMessage(Component.translatable(nightStarts ? "nightsiege.kit.given_night" : "nightsiege.kit.given",
                level, target, skills.points()).withStyle(net.minecraft.ChatFormatting.AQUA));
        return add;
    }

    private static void run(MinecraftServer server, List<String> commands) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        for (String command : commands) {
            try {
                server.getCommands().performPrefixedCommand(source, command);
            } catch (RuntimeException e) {
                // A single item that does not exist must not stop the rest.
            }
        }
    }

        private static boolean has(ServerPlayer player, String itemId) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(itemId);
        if (id == null) {
            return false;
        }
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** One or two enchantments up to level 5, two from 6, three above 10. */
    private static String enchantments(int slot, int level, Random random) {
        int prot = level > 10 ? 4 : level >= 6 ? 3 : level >= 3 ? 2 : 1;
        List<String> list = new ArrayList<>();
        list.add("\"minecraft:protection\":" + prot);
        int extra = level > 10 ? 2 : level >= 6 ? 1 : random.nextInt(2);
        String[] options = slot == 3
                ? new String[]{"\"minecraft:unbreaking\":2", "\"minecraft:feather_falling\":3", "\"minecraft:depth_strider\":2"}
                : slot == 0
                ? new String[]{"\"minecraft:unbreaking\":2", "\"minecraft:respiration\":2", "\"minecraft:projectile_protection\":2"}
                : new String[]{"\"minecraft:unbreaking\":2", "\"minecraft:projectile_protection\":2", "\"minecraft:fire_protection\":2"};
        List<String> pool = new ArrayList<>(List.of(options));
        for (int i = 0; i < extra && !pool.isEmpty(); i++) {
            list.add(pool.remove(random.nextInt(pool.size())));
        }
        // No glint: it would shimmer over the class outfits.
        return "[minecraft:enchantments={" + String.join(",", list) + "},minecraft:enchantment_glint_override=false,minecraft:unbreakable={}]";
    }

    /** The class's weapons in the given material, unenchanted. */
    private static List<String> weapons(PlayerSkills skills, SkillTree tree, String material) {
        List<String> out = new ArrayList<>();
        SkillClass clazz = skills == null || !skills.hasClass() ? null : skills.clazz();
        String way = skills == null ? "" : skills.pathTaken(tree);
        String sword = "minecraft:" + material + "_sword";
        String axe = "minecraft:" + material + "_axe";
        if (clazz == SkillClass.WARRIOR) {
            if (way.isEmpty() || way.equals(SkillTree.WAY_BLADE)) {
                out.add(sword);
            }
            if (way.isEmpty() || !way.equals(SkillTree.WAY_BLADE)) {
                out.add(axe);
            }
        } else if (clazz == SkillClass.ARCHER) {
            boolean crossbow = way.equals(SkillTree.WAY_CROSSBOW);
            if (way.isEmpty() || !crossbow) {
                out.add("minecraft:bow");
            }
            if (way.isEmpty() || crossbow) {
                out.add("minecraft:crossbow");
            }
            out.add("minecraft:arrow 128");
            out.add(sword);
        } else if (clazz == SkillClass.GUARDIAN) {
            out.add(sword);
            out.add("minecraft:shield");
        } else if (clazz == SkillClass.SUMMONER) {
            out.add("nightsiege:summoning_staff");
            out.add(sword);
        } else {
            out.add(sword);
            out.add("minecraft:bow");
            out.add("minecraft:arrow 64");
        }
        return out;
    }
}
