package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Survival safety, package "ss-speichern" (Simon 31.10.): the save survives a broken file, the reload
 * command keeps what is in memory, the PlayerBuilt marks wait for the 30 s write, the spotter's
 * camera is checked, other mods' keys still fire in the night, and vanilla monsters stay vanilla
 * (they fight each other, they hit as in vanilla). Every line starts with "[ss-speichern]".
 */
final class SsSpeichernScenes {

    private static final String MARK_DIM = "nightsiege:ss_test";

    private SsSpeichernScenes() {
    }

    private static void log(String line) {
        System.out.println("[ss-speichern] " + line);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.ensureCore(context, game);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("difficulty normal");
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(20);
        saveScene(context, game);
        reloadScene(context, game);
        builtScene(context);
        spotScene(context);
        keysScene(context);
        mobsScene(context);
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
        log("done");
    }

    private static List<Path> kept(Path save, String part) {
        List<Path> out = new ArrayList<>();
        try (var files = Files.list(save.getParent())) {
            files.filter(p -> p.getFileName().toString().startsWith(save.getFileName() + part)).forEach(out::add);
        } catch (Exception e) {
            log("list failed " + e);
        }
        return out;
    }

    private static boolean marked(SiegeManager manager, long key) {
        return manager.save().playerBuilt != null && manager.save().playerBuilt.get(MARK_DIM) != null
                && manager.save().playerBuilt.get(MARK_DIM).contains(key);
    }

    /** A broken save: kept as .kaputt-…, the .bak loaded; both broken: kept, fresh start, nothing lost. */
    private static void saveScene(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                Path save = manager.savePath();
                Path bak = Json.backupOf(save);
                int coresBefore = manager.save().cores.size();
                manager.save().playerBuilt.computeIfAbsent(MARK_DIM, k -> new java.util.HashSet<>()).add(4242L);
                manager.persist();
                Json.flush();
                manager.persist();   // the .bak now holds the mark too
                Json.flush();
                log("save written exists=" + Files.isRegularFile(save) + " bak=" + Files.isRegularFile(bak)
                        + " bakHasMark=" + Files.readString(bak).contains("4242") + " cores=" + coresBefore);
                int keptBefore = kept(save, ".kaputt-").size();
                String garbage = "{\"cores\": [ kaputt \u0000\u0000";
                Files.writeString(save, garbage, StandardCharsets.UTF_8);
                manager.reload();   // the path of a server start
                List<Path> keptNow = kept(save, ".kaputt-");
                boolean keptGarbage = false;
                for (Path p : keptNow) {
                    keptGarbage |= Files.readString(p, StandardCharsets.UTF_8).equals(garbage);
                }
                boolean recoveredNotice = false;
                for (Json.Notice n : Json.notices()) {
                    recoveredNotice |= n.recovered() && n.file().equals(save.getFileName().toString());
                }
                log("broken save kaputtFiles=" + (keptNow.size() - keptBefore) + " keptGarbage=" + keptGarbage
                        + " progressKept=" + marked(manager, 4242L) + " cores=" + manager.save().cores.size()
                        + " noticeRecovered=" + recoveredNotice);
                manager.tellFileProblems(player);
                manager.persist();
                Json.flush();
                boolean readable = Json.fromJson(Files.readString(save), com.mki.nightsiege.game.SiegeSave.class) != null;
                log("save rewritten readable=" + readable + " hasMark=" + Files.readString(save).contains("4242"));

                // Both broken: a fresh start, and both files kept.
                String good = Files.readString(save);
                int keptBak = kept(save, ".bak.kaputt-").size();
                keptBefore = kept(save, ".kaputt-").size();
                Files.writeString(save, "kaputt", StandardCharsets.UTF_8);
                Files.writeString(bak, "auch kaputt", StandardCharsets.UTF_8);
                manager.reload();
                boolean resetNotice = false;
                for (Json.Notice n : Json.notices()) {
                    resetNotice |= !n.recovered() && n.file().equals(save.getFileName().toString());
                }
                log("both broken fresh=" + !marked(manager, 4242L) + " cores=" + manager.save().cores.size()
                        + " keptSave=" + (kept(save, ".kaputt-").size() - keptBefore) + " keptBak=" + (kept(save, ".bak.kaputt-").size() - keptBak)
                        + " noticeReset=" + resetNotice);
                Json.flush();
                Files.writeString(save, good, StandardCharsets.UTF_8);   // the test world gets its save back
                manager.reload();
                log("restored progress=" + marked(manager, 4242L) + " cores=" + manager.save().cores.size());
            } catch (Exception e) {
                log("save scene failed " + e);
            }
        });
        context.waitTicks(20);
    }

    /** /nightsiege reload writes what is in memory first. */
    private static void reloadScene(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.onServer(context, (player, manager) ->
                manager.save().playerBuilt.computeIfAbsent(MARK_DIM, k -> new java.util.HashSet<>()).add(777L));   // in memory only
        context.waitTicks(20);
        game.getServer().runCommand("nightsiege reload");
        context.waitTicks(20);
        EventLookTest.onServer(context, (player, manager) -> log("reload keeps unsaved=" + marked(manager, 777L)
                + " progress=" + marked(manager, 4242L)));
        context.waitTicks(20);
    }

    /** A new PlayerBuilt chunk is not written on the spot, but within 30 s. */
    private static void builtScene(ClientGameTestContext context) {
        final boolean[] seen = new boolean[1];
        EventLookTest.onServer(context, (player, manager) -> {
            manager.persist();
            ServerLevel level = (ServerLevel) player.level();
            com.mki.nightsiege.game.CoreRecord core = manager.mainCore();
            // A fresh chunk near the core with a block of nature in it.
            net.minecraft.core.BlockPos pos = null;
            for (int dx = 2; dx < 12 && pos == null; dx++) {
                net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(core.x + dx * 16, core.y + 3, core.z + 16);
                if (!com.mki.nightsiege.game.PlayerBuilt.marked(level, at.getX() >> 4, at.getZ() >> 4)) {
                    pos = at;
                }
            }
            if (pos == null) {
                log("built no free chunk");
                return;
            }
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
            com.mki.nightsiege.game.PlayerBuilt.mark(level, pos);
            seen[0] = com.mki.nightsiege.game.PlayerBuilt.marked(level, pos.getX() >> 4, pos.getZ() >> 4);
            int far = 0;
            for (var e : manager.save().playerBuilt.values()) {
                far += e.size();
            }
            int reach = Math.max(manager.config().campMaxDistance + 64, manager.config().expeditionMaxDistance + 256) + 64;
            net.minecraft.core.BlockPos away = new net.minecraft.core.BlockPos(core.x + reach, core.y + 3, core.z);   // beyond every site
            level.setBlock(away, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
            com.mki.nightsiege.game.PlayerBuilt.mark(level, away);
            boolean farKept = com.mki.nightsiege.game.PlayerBuilt.marked(level, away.getX() >> 4, away.getZ() >> 4);
            log("built marked=" + seen[0] + " pendingRightAfter=" + manager.persistPending() + " farMarked=" + farKept
                    + " chunks=" + far);
            level.setBlock(away, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        });
        context.waitTicks(640);
        EventLookTest.onServer(context, (player, manager) -> log("built pendingAfter32s=" + manager.persistPending()));
        context.waitTicks(20);
    }

    /** spot:cam from a player whose view the server never opened (not a demolition expert in a night). */
    private static void spotScene(ClientGameTestContext context) {
        EventLookTest.onServer(context, (player, manager) -> {
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            manager.onCoreAction(player, String.format(java.util.Locale.ROOT, "spot:cam:%.1f:%.1f",
                    player.getX() + 150, player.getZ()));
            log("spot cam unopened open=" + com.mki.nightsiege.game.SpotterChunks.isOpen(player));
        });
        context.waitTicks(20);
    }

    /** In the night (keys owned): another mod's mapping on N still fires, vanilla's save-hotbar on C does not. */
    private static void keysScene(ClientGameTestContext context) {
        context.runOnClient(client -> {
            net.minecraft.client.KeyMapping gain = com.mki.nightsiege.client.GainClient.gainKey(0);
            net.minecraft.client.KeyMapping slot = com.mki.nightsiege.client.GainClient.slotKey(3);
            net.minecraft.client.KeyMapping voice = new net.minecraft.client.KeyMapping("key.ss_test.voice",
                    org.lwjgl.glfw.GLFW.GLFW_KEY_N, gain.getCategory());
            net.minecraft.client.KeyMapping hotbar = client.options.keySaveHotbarActivator;
            com.mojang.blaze3d.platform.InputConstants.Key n = com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.n");
            com.mojang.blaze3d.platform.InputConstants.Key c = com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.c");
            while (voice.consumeClick() || gain.consumeClick() || slot.consumeClick() || hotbar.consumeClick()) {
                // drained
            }
            com.mki.nightsiege.client.NightKeys.setOwned(true);
            net.minecraft.client.KeyMapping.click(n);
            boolean voiceClick = voice.consumeClick();
            boolean gainClick = gain.consumeClick();
            net.minecraft.client.KeyMapping.set(n, true);
            boolean voiceDown = voice.isDown();
            net.minecraft.client.KeyMapping.set(n, false);
            boolean hotbarOnC = hotbar.matches(c);
            boolean slotOnC = slot.matches(c);
            net.minecraft.client.KeyMapping.click(c);
            boolean hotbarClick = hotbar.consumeClick();
            boolean slotClick = slot.consumeClick();
            com.mki.nightsiege.client.NightKeys.setOwned(false);
            voice.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            net.minecraft.client.KeyMapping.resetMapping();
            log("keys N voice=" + voiceClick + " voiceDown=" + voiceDown + " gain=" + gainClick
                    + " | C (hotbarOnC=" + hotbarOnC + " slotOnC=" + slotOnC + ") hotbar=" + hotbarClick + " slot=" + slotClick);
        });
        context.waitTicks(10);
    }

    /** Outside a night: a vanilla skeleton's arrow hurts a vanilla creeper; a vanilla zombie hits as in vanilla. */
    private static void mobsScene(ClientGameTestContext context) {
        final java.util.UUID[] ids = new java.util.UUID[3];
        final float[] creeperHealth = new float[1];
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            net.minecraft.core.BlockPos at = player.blockPosition();
            net.minecraft.world.entity.monster.skeleton.Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
            net.minecraft.world.entity.monster.Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
            if (skeleton == null || creeper == null) {
                log("mobs spawn failed");
                return;
            }
            skeleton.snapTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 90f, 0f);
            creeper.snapTo(at.getX() + 10.5, at.getY(), at.getZ() + 0.5, 90f, 0f);
            for (net.minecraft.world.entity.Mob mob : new net.minecraft.world.entity.Mob[] {skeleton, creeper}) {
                mob.setNoAi(true);
                mob.setPersistenceRequired();
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                level.addFreshEntity(mob);
            }
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            ids[0] = skeleton.getUUID();
            ids[1] = creeper.getUUID();
            creeperHealth[0] = creeper.getHealth();
            boolean direct = creeper.hurtServer(level, level.damageSources().mobAttack(skeleton), 1f);
            float afterDirect = creeper.getHealth();
            skeleton.addTag(com.mki.nightsiege.game.MobFactory.TAG);   // a siege skeleton: still no infighting
            creeper.invulnerableTime = 0;
            boolean siege = creeper.hurtServer(level, level.damageSources().mobAttack(skeleton), 1f);
            skeleton.removeTag(com.mki.nightsiege.game.MobFactory.TAG);
            creeper.invulnerableTime = 0;
            creeperHealth[0] = creeper.getHealth();
            skeleton.performRangedAttack(creeper, 1.0f);
            log("infight direct=" + direct + " health " + creeperHealth[0] + "->" + afterDirect + " siegeSkeleton=" + siege
                    + " infighting(vanilla)=" + SiegeManager.infighting(skeleton, creeper) + " night=" + manager.isActive());
        });
        context.waitTicks(30);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            net.minecraft.world.entity.Entity creeper = level.getEntity(ids[1]);
            float now = creeper instanceof net.minecraft.world.entity.LivingEntity l ? l.getHealth() : -1f;
            log("infight arrow creeper " + creeperHealth[0] + "->" + now + " hurt=" + (now >= 0 && now < creeperHealth[0]));
            for (java.util.UUID id : new java.util.UUID[] {ids[0], ids[1]}) {
                net.minecraft.world.entity.Entity e = id == null ? null : level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
            // The zombie: vanilla damage, the factors only for the mod's own.
            net.minecraft.world.entity.monster.zombie.Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            if (zombie == null) {
                return;
            }
            zombie.snapTo(player.getX() + 1.0, player.getY(), player.getZ(), 0f, 0f);
            zombie.setNoAi(true);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            level.addFreshEntity(zombie);
            ids[2] = zombie.getUUID();
            net.minecraft.world.damagesource.DamageSource src = level.damageSources().mobAttack(zombie);
            float vanilla = com.mki.nightsiege.game.GuardianEffects.scale(player, level, src, 6f);
            zombie.addTag(com.mki.nightsiege.game.MobFactory.TAG);
            float own = com.mki.nightsiege.game.GuardianEffects.scale(player, level, src, 6f);
            zombie.removeTag(com.mki.nightsiege.game.MobFactory.TAG);
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            float h0 = player.getHealth();
            boolean hit = zombie.doHurtTarget(level, player);
            float h1 = player.getHealth();
            double attack = zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            log("zombie scale vanilla=" + vanilla + " (of 6.0) siegeTagged=" + own + " | real hit=" + hit + " lost=" + (h0 - h1)
                    + " attack=" + attack + " vanillaDamage=" + (Math.abs((h0 - h1) - attack) < 0.01));
            zombie.discard();
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(20);
    }
}
